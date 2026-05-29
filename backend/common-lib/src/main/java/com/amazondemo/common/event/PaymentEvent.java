package com.amazondemo.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Event - Published via RabbitMQ when payment status changes
 *
 * WHY RABBITMQ FOR PAYMENTS?
 * - Payment is a critical async operation that needs guaranteed delivery
 * - RabbitMQ provides message acknowledgment and dead-letter queues
 * - If payment processing fails, the message stays in queue for retry
 * - Separate from Kafka to show both messaging systems in use
 *
 * KAFKA vs RABBITMQ:
 * - Kafka: High throughput, event streaming, log-based (orders, analytics)
 * - RabbitMQ: Message queuing, routing, guaranteed delivery (payments, emails)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private String eventId;
    private String eventType;  // PAYMENT_INITIATED, PAYMENT_SUCCESS, PAYMENT_FAILED, PAYMENT_REFUNDED
    private String paymentId;
    private String orderId;
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String transactionId;
    private String status;
    private LocalDateTime eventTime;
    private String failureReason;
}
