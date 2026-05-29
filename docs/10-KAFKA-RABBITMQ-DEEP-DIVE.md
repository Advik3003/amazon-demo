# Kafka & RabbitMQ Deep Dive — Tutorial

> **What you'll learn**: How Kafka and RabbitMQ work, why we use both, complete
> code walkthroughs, and everything needed for interviews.

---

## 1. Why Two Message Brokers?

A common interview question: *"Why use both Kafka AND RabbitMQ?"*

```
KAFKA                               RABBITMQ
─────────────────────────           ──────────────────────
✓ Ordered event log (log-based)     ✓ Task queues (delete after consume)
✓ Replay events from the past       ✓ Request/reply patterns
✓ Multiple consumers per event      ✓ Complex routing (exchanges/bindings)
✓ Millions of events/second         ✓ Message acknowledgment
✓ Long retention (days/weeks)       ✓ Priority queues
✓ CQRS sync, audit logs             ✓ Payment notifications

Used for:                           Used for:
  Product sync (CQRS)                 Payment processing
  Order events → notifications        Email/SMS delivery
  Inventory events                    Retry with delay
```

**Rule of thumb:**
- **Kafka**: "Something happened, multiple systems need to know"
- **RabbitMQ**: "Do this task exactly once, with retry, acknowledgment"

---

## 2. Apache Kafka — Complete Guide

### Core Concepts

```
PRODUCER → TOPIC → PARTITION → CONSUMER GROUP
                          │
                          ▼
           ┌─────────────────────────────────┐
           │ PARTITION 0                      │
           │ offset: 0  1  2  3  4  5  6     │
           │ msgs:  [A][B][C][D][E][F][G]    │
           │                    ▲             │
           │               Consumer A         │
           │               (offset=5)         │
           └─────────────────────────────────┘
           ┌─────────────────────────────────┐
           │ PARTITION 1                      │
           │ offset: 0  1  2  3  4            │
           │ msgs:  [H][I][J][K][L]          │
           │                 ▲               │
           │            Consumer B            │
           │            (offset=4)            │
           └─────────────────────────────────┘
```

**Key rules:**
- Messages in a partition are **ordered** (offset 0, 1, 2, 3...)
- Messages across partitions are **NOT ordered**
- One partition → at most one consumer in a consumer group
- Multiple consumer groups can read the same topic (fan-out)

### Kafka in This Project

```
TOPIC: product-events
  │
  ├── Producer: ProductEventPublisher (product-service write side)
  └── Consumer: ProductEventConsumer (product-service read side)
                Partition key = productId (ordering per product)

TOPIC: order-events
  │
  ├── Producer: OrderService (order-service)
  ├── Consumer: InventoryService (update stock)
  └── Consumer: NotificationService (send emails)

TOPIC: inventory-events
  │
  ├── Producer: InventoryService
  └── Consumer: OrderService (check availability)
```

### Producer Configuration

```java
// Spring Kafka producer config (from application.yml)
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all           # Wait for all replicas to acknowledge (data safety)
      retries: 3
      properties:
        enable.idempotence: true              # Prevents duplicate messages on retry
        max.in.flight.requests.per.connection: 1  # Required for idempotence
```

### Producer Code

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishOrderPlaced(Order order) {
        OrderEvent event = OrderEvent.builder()
            .eventType("ORDER_PLACED")
            .orderId(order.getId())
            .userId(order.getUserId())
            .totalAmount(order.getTotalAmount())
            .items(order.getItems().stream()
                .map(item -> OrderItemEvent.builder()
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .price(item.getPrice())
                    .build())
                .collect(Collectors.toList()))
            .timestamp(LocalDateTime.now())
            .build();

        // send() returns a CompletableFuture
        CompletableFuture<SendResult<String, Object>> future =
            kafkaTemplate.send("order-events", order.getId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send order event: {} - {}", order.getId(), ex.getMessage());
                // TODO: save to outbox table for retry
            } else {
                log.info("Order event sent: partition={}, offset={}",
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}
```

### Consumer Configuration

```java
spring:
  kafka:
    consumer:
      group-id: notification-service-group  # Consumer group ID
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest           # Start from beginning if new group
      enable-auto-commit: false             # Manual commit for at-least-once
      properties:
        spring.json.trusted.packages: "com.amazondemo.*"
```

### Consumer Code

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = "order-events",
        groupId = "notification-service-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderEvent(
            @Payload OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment  // For manual commit
    ) {
        log.info("Received order event: type={} orderId={} partition={} offset={}",
            event.getEventType(), event.getOrderId(), partition, offset);

        try {
            switch (event.getEventType()) {
                case "ORDER_PLACED" -> {
                    notificationService.sendOrderConfirmationEmail(
                        event.getUserId(), event.getOrderId());
                    notificationService.createNotification(
                        event.getUserId(),
                        "Order Confirmed",
                        "Your order #" + event.getOrderId() + " has been placed!",
                        "ORDER", event.getOrderId(), "ORDER");
                }
                case "ORDER_SHIPPED" -> {
                    notificationService.sendShipmentNotification(event);
                }
                case "ORDER_DELIVERED" -> {
                    notificationService.sendDeliveryNotification(event);
                }
            }

            // Only commit offset after successful processing (at-least-once delivery)
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process order event: {}", event.getOrderId(), e);
            // Don't acknowledge → Kafka will re-deliver after timeout
            // After max retries → goes to Dead Letter Topic
        }
    }

    // Dead Letter Topic handler
    @KafkaListener(topics = "order-events.DLT", groupId = "notification-dlq-group")
    public void handleDeadLetterEvent(OrderEvent event) {
        log.error("Order event landed in DLT (unprocessable): {}", event.getOrderId());
        // Alert ops team, persist to error table for manual review
    }
}
```

### Kafka Consumer Groups — Fan-Out Pattern

```
order-events topic
        │
        ├── notification-service-group   ← sends emails
        ├── inventory-service-group      ← updates stock
        └── analytics-service-group      ← records for reporting

Each group reads ALL messages independently.
Within a group, messages are distributed across consumers.
```

```java
// Inventory service consumes the SAME topic
@KafkaListener(topics = "order-events", groupId = "inventory-service-group")
public void handleOrderForInventory(OrderEvent event) {
    if ("ORDER_PLACED".equals(event.getEventType())) {
        // Reduce stock for each ordered item
        event.getItems().forEach(item ->
            inventoryService.reduceStock(item.getProductId(), item.getQuantity())
        );
    }
    if ("ORDER_CANCELLED".equals(event.getEventType())) {
        // Return stock
        event.getItems().forEach(item ->
            inventoryService.returnStock(item.getProductId(), item.getQuantity())
        );
    }
}
```

### Partition Key — Why it Matters

```java
// Sending with a partition key
kafkaTemplate.send("order-events",
    order.getUserId(),    // KEY = userId → all orders for same user → same partition
    event               // VALUE = the event
);

// WHY:
// Same user's orders go to same partition → processed in order
// ORDER_PLACED before ORDER_SHIPPED before ORDER_DELIVERED
// (for the same user/order)

// If no key → round-robin across partitions → no ordering guarantee
```

---

## 3. RabbitMQ — Complete Guide

### Core Concepts

```
PRODUCER → EXCHANGE → BINDING → QUEUE → CONSUMER

Exchange types:
  direct  : route by exact routing key  (payment.success → payment.success queue)
  topic   : route by pattern            (order.* → order.notifications queue)
  fanout  : broadcast to ALL queues
  headers : route by message headers
```

### RabbitMQ in This Project

```
payment-service
    │
    └── Publishes to: payment.exchange (direct)
                routing key: "payment.success" or "payment.failed"
                    │
                    ├── binding "payment.success" → queue: payment-notifications
                    └── binding "payment.failed"  → queue: payment-failed-notifications

notification-service
    └── Consumes: payment-notifications queue
                 payment-failed-notifications queue
```

### RabbitMQ Configuration

```java
@Configuration
public class RabbitMQConfig {

    // Exchange declaration
    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange("payment.exchange", true, false);
        //                          name                durable  auto-delete
    }

    // Queue declarations (with Dead Letter Queue)
    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable("payment-notifications")
            .withArgument("x-dead-letter-exchange", "payment.dlx")    // If processing fails
            .withArgument("x-dead-letter-routing-key", "payment.dlq")
            .withArgument("x-message-ttl", 86400000)                   // 24 hours TTL
            .build();
    }

    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable("payment-failed-notifications").build();
    }

    // Dead Letter Queue
    @Bean
    public Queue paymentDlq() {
        return QueueBuilder.durable("payment-dlq").build();
    }

    // Bindings (Exchange → Queue)
    @Bean
    public Binding paymentSuccessBinding() {
        return BindingBuilder
            .bind(paymentSuccessQueue())
            .to(paymentExchange())
            .with("payment.success");       // Routing key
    }

    @Bean
    public Binding paymentFailedBinding() {
        return BindingBuilder
            .bind(paymentFailedQueue())
            .to(paymentExchange())
            .with("payment.failed");
    }

    // Message converter (JSON)
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jacksonMessageConverter());
        return template;
    }
}
```

### Producer Code (Payment Service)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final RabbitTemplate rabbitTemplate;

    public PaymentResult processPayment(PaymentRequest request) {

        // ... process payment logic (dummy for this project) ...
        boolean success = simulatePaymentGateway(request);

        PaymentEvent event = PaymentEvent.builder()
            .orderId(request.getOrderId())
            .userId(request.getUserId())
            .amount(request.getAmount())
            .status(success ? "SUCCESS" : "FAILED")
            .transactionId(UUID.randomUUID().toString())
            .timestamp(LocalDateTime.now())
            .build();

        String routingKey = success ? "payment.success" : "payment.failed";

        // Publish to RabbitMQ exchange
        rabbitTemplate.convertAndSend(
            "payment.exchange",    // Exchange name
            routingKey,            // Routing key determines which queue
            event                  // Message body
        );

        log.info("Payment event published: orderId={} status={}", request.getOrderId(), event.getStatus());
        return new PaymentResult(success, event.getTransactionId());
    }
}
```

### Consumer Code (Notification Service)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationConsumer {

    private final NotificationService notificationService;

    @RabbitListener(queues = "payment-notifications")  // Listens to specific queue
    public void handlePaymentSuccess(PaymentEvent event,
                                      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                                      Channel channel) throws IOException {
        log.info("Processing payment success: orderId={}", event.getOrderId());
        try {
            notificationService.sendPaymentSuccessEmail(event.getUserId(), event.getOrderId(), event.getAmount());
            notificationService.createNotification(
                event.getUserId(), "Payment Confirmed",
                "Payment of $" + event.getAmount() + " confirmed for order #" + event.getOrderId(),
                "PAYMENT", event.getOrderId(), "ORDER"
            );

            // Manual ACK — message removed from queue
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process payment notification for: {}", event.getOrderId(), e);
            // NACK — message goes to Dead Letter Queue after max retries
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = "payment-failed-notifications")
    public void handlePaymentFailed(PaymentEvent event) {
        log.warn("Payment failed for order: {}", event.getOrderId());
        notificationService.sendPaymentFailedEmail(event.getUserId(), event.getOrderId());
    }

    // Dead Letter Queue — messages that couldn't be processed
    @RabbitListener(queues = "payment-dlq")
    public void handleDeadLetterMessage(PaymentEvent event) {
        log.error("Payment event in DLQ — requires manual intervention: orderId={}", event.getOrderId());
        // Alert ops, save to error table
    }
}
```

---

## 4. Kafka vs RabbitMQ — Comparison

| Feature | Kafka | RabbitMQ |
|---------|-------|---------|
| **Message model** | Log (messages persist) | Queue (messages deleted after consume) |
| **Replay** | Yes — seek to any offset | No — once consumed, gone |
| **Ordering** | Per partition | Per queue (with single consumer) |
| **Fan-out** | Multiple consumer groups, naturally | Fanout exchange needed |
| **Throughput** | Millions/sec | ~50k-100k/sec |
| **Routing** | By topic/partition key | Exchange/binding rules |
| **Message size** | Default 1MB (configurable) | Default 128MB |
| **Delivery guarantee** | At-least-once (configurable) | At-least-once or exactly-once |
| **Message TTL** | Retention period (hours/days) | Per-queue TTL |
| **Protocol** | Kafka custom protocol | AMQP |
| **Delay/Schedule** | Not built-in | Delay queues (plugins) |
| **Dead Letter** | DLT (Dead Letter Topic) | DLQ (Dead Letter Queue) |

---

## 5. Message Delivery Guarantees

### At-Least-Once (What we use)

```
Producer: send → Broker: stored → Consumer: process → Consumer: ACK → Broker: delete offset
          │                                               │
          └── if no ACK received ──────────────────────→ re-deliver
```

Consequence: Consumer may receive the same message twice.
Solution: **Idempotent consumers** — process the same message multiple times safely.

```java
// Idempotent consumer example
@KafkaListener(topics = "order-events")
public void handleOrder(OrderEvent event) {
    // Check if we already processed this
    if (processedEventRepository.existsById(event.getEventId())) {
        log.info("Duplicate event ignored: {}", event.getEventId());
        return;  // Skip duplicate
    }

    // Process
    processOrder(event);

    // Mark as processed
    processedEventRepository.save(new ProcessedEvent(event.getEventId()));
}
```

### Exactly-Once (Kafka Transactions)

```java
// Enable transactions in KafkaTemplate
@Bean
public KafkaTemplate<String, Object> kafkaTemplate() {
    var template = new KafkaTemplate<String, Object>(producerFactory());
    template.setTransactionIdPrefix("order-service-");
    return template;
}

// Use @Transactional to wrap Kafka + DB operations atomically
@Transactional("kafkaTransactionManager")
public void processAndPublish(Order order) {
    orderRepository.save(order);           // DB operation
    kafkaTemplate.send("order-events", order.getId(), event);  // Kafka operation
    // Both succeed or both rollback
}
```

---

## 6. Transactional Outbox Pattern

**Problem:** "What if the service crashes between writing to DB and publishing to Kafka?"

```
BAD (non-transactional):
  1. Save order to PostgreSQL ✓
  2. Publish to Kafka         CRASH ✗
  → Order saved but event never sent → inconsistency!

GOOD (Transactional Outbox):
  1. Save order to PostgreSQL }
  2. Save event to outbox table} ← SAME TRANSACTION (atomic)
  3. Background job reads outbox → publishes to Kafka → marks as sent
  → Even if step 3 crashes, the event is NOT lost (it's in the outbox table)
```

```sql
-- outbox_events table
CREATE TABLE outbox_events (
    id          UUID PRIMARY KEY,
    event_type  VARCHAR(100) NOT NULL,
    payload     JSONB NOT NULL,
    published   BOOLEAN DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
```

---

## 7. Interview Questions: Kafka & RabbitMQ

**Q: Explain Kafka consumer group and partition.**
> A consumer group is a set of consumers that cooperatively consume a topic.
> Each partition is assigned to exactly one consumer in the group. This enables
> parallel processing while maintaining per-partition ordering.
> If you have 3 partitions and 3 consumers → each consumer handles 1 partition.
> If you have 3 partitions and 5 consumers → 2 consumers sit idle.

**Q: How do you ensure message ordering in Kafka?**
> Use a partition key. Messages with the same key always go to the same partition.
> Within a partition, messages are strictly ordered by offset.
> Example: use `orderId` as key → all events for the same order are ordered.

**Q: What is a Dead Letter Queue/Topic?**
> A DLQ/DLT is a special queue/topic where messages go after failing processing
> N times. It prevents problematic messages from blocking the main queue forever.
> In RabbitMQ: configure `x-dead-letter-exchange`. In Kafka: `@DltHandler` in
> Spring Kafka's `@RetryableTopic`.

**Q: What is the difference between message acknowledgment in Kafka and RabbitMQ?**
> - **Kafka**: Consumers track their own offset. "Acknowledging" = committing offset.
>   If consumer crashes, it restarts from last committed offset.
> - **RabbitMQ**: Broker tracks which messages are delivered. Consumer sends explicit
>   ACK/NACK. If no ACK, broker re-delivers to another consumer.

**Q: How would you implement delayed messages in RabbitMQ?**
> Use the RabbitMQ Delayed Message Plugin:
> ```java
> @Bean
> public CustomExchange delayedExchange() {
>     return new CustomExchange("delayed.exchange", "x-delayed-message", ...);
> }
> // Send with delay header
> rabbitTemplate.convertAndSend("delayed.exchange", routingKey, message, msg -> {
>     msg.getMessageProperties().setDelay(30000); // 30 seconds
>     return msg;
> });
> ```
