package com.amazondemo.inventory.event;

import com.amazondemo.common.event.OrderEvent;
import com.amazondemo.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Inventory Event Consumer
 * =========================
 * Listens to order events and updates inventory accordingly.
 *
 * WHY EVENT-DRIVEN FOR INVENTORY?
 * - Order service doesn't need to know about inventory details
 * - Loose coupling between services
 * - If inventory service is down, events are queued in Kafka
 * - When inventory service comes back, it processes all pending events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "order-events", groupId = "inventory-service-consumer")
    public void handleOrderEvent(OrderEvent event) {
        log.info("Received order event: {} for order: {}", event.getEventType(), event.getOrderId());

        switch (event.getEventType()) {
            case "ORDER_PLACED" -> {
                // Already reserved via Feign, but handle idempotently
                log.debug("Order placed - stock already reserved via Feign");
            }
            case "ORDER_CANCELLED" -> {
                // Release stock reservation
                if (event.getItems() != null) {
                    event.getItems().forEach(item -> {
                        try {
                            inventoryService.releaseReservation(item.getProductId(), item.getQuantity());
                        } catch (Exception e) {
                            log.error("Failed to release stock for product: {}", item.getProductId(), e);
                        }
                    });
                }
            }
            case "ORDER_CONFIRMED" -> {
                // Payment confirmed - deduct stock from reservation
                if (event.getItems() != null) {
                    event.getItems().forEach(item -> {
                        try {
                            inventoryService.deductStock(item.getProductId(), item.getQuantity());
                        } catch (Exception e) {
                            log.error("Failed to deduct stock for product: {}", item.getProductId(), e);
                        }
                    });
                }
            }
            default -> log.debug("Unhandled order event type: {}", event.getEventType());
        }
    }
}
