package com.amazondemo.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order Event - Published to Kafka when order status changes
 *
 * WHY KAFKA FOR ORDERS?
 * - Orders need to notify Inventory Service (deduct stock)
 * - Orders need to notify Notification Service (send email/SMS)
 * - Orders need to notify Payment Service
 * - Kafka guarantees delivery and allows replay of events
 * - Decouples Order Service from other services (loose coupling)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {

    private String eventId;
    private String eventType;  // ORDER_PLACED, ORDER_CONFIRMED, ORDER_CANCELLED, ORDER_DELIVERED
    private String orderId;
    private String userId;
    private String userEmail;
    private BigDecimal totalAmount;
    private List<OrderItemEvent> items;
    private String status;
    private LocalDateTime eventTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemEvent {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}
