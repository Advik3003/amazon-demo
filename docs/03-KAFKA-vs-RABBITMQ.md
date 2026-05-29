# Kafka vs RabbitMQ - When to Use Which

## Quick Comparison

| Feature | Kafka | RabbitMQ |
|---------|-------|----------|
| Type | Distributed log / event streaming | Message broker |
| Delivery | At-least-once (configurable) | At-least-once + exactly-once |
| Order | Ordered within partition | Ordered within queue |
| Replay | Yes (stored on disk) | No (deleted after consumption) |
| Throughput | Millions msg/sec | Thousands msg/sec |
| Routing | Topics + partitions | Exchanges + routing keys |
| Use case | Event streaming, analytics | Task queues, RPC, notifications |
| Retention | Days/weeks (configurable) | Until acknowledged |

## Why We Use BOTH

### Kafka is Used For:
1. **Order Events**: `order-events` topic
   - ORDER_PLACED, ORDER_CONFIRMED, ORDER_SHIPPED, ORDER_DELIVERED, ORDER_CANCELLED
   - Multiple consumers (inventory, notification, analytics)
   - Replay capability if a service goes down

2. **Product Events**: `product-events` topic
   - PRODUCT_CREATED, PRODUCT_UPDATED, PRODUCT_DELETED
   - Feeds the MongoDB read model (CQRS)

3. **Payment Events**: `payment-events` topic
   - PAYMENT_SUCCESS, PAYMENT_FAILED
   - Triggers order status updates

**WHY KAFKA FOR THESE?**
- Multiple services consume the same events (fan-out)
- If notification service goes down, it can replay events when it comes back
- High throughput needed for product/order events

### RabbitMQ is Used For:
1. **Payment Processing Queue**: async payment handling
2. **Email Notification Queue**: rate-limited email delivery

**WHY RABBITMQ FOR THESE?**
- Strict ordering needed
- Dead-letter queues for failed payments
- Built-in retry with exponential backoff
- Priority queues for VIP customers

## Event Flow Diagram

```
[Order Service] ──── Kafka: order-events ──→ [Inventory Service]
                                          └──→ [Notification Service]
                                          └──→ [Analytics Service]

[Payment Service] ── Kafka: payment-events → [Order Service]
                                          └──→ [Notification Service]

[Product Service] ── Kafka: product-events → [ProductEventConsumer (same svc)]
                                              → Updates MongoDB read model
```

## Interview Questions

**Q: Why Kafka instead of RabbitMQ for events?**
A: Kafka retains messages (log-based), allowing replay. When notification service was down during ORDER_PLACED, it can replay those events to send emails after recovery.

**Q: Why not just use Kafka for everything?**
A: Kafka is complex to set up and manage. For simple task queues, RabbitMQ is simpler and has better routing capabilities.

**Q: What happens if Kafka is down?**
A: The producer (Order Service) will retry sending the event. If Kafka is down for extended time, use saga pattern with local DB-based outbox pattern.
