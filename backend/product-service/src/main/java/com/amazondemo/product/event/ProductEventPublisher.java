package com.amazondemo.product.event;

import com.amazondemo.product.model.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Product Event Publisher
 * ========================
 * Publishes product events to Kafka so other services can react.
 *
 * EVENTS PUBLISHED:
 * - PRODUCT_CREATED: When a new product is added
 * - PRODUCT_UPDATED: When product details change
 * - PRODUCT_DELETED: When a product is removed
 *
 * CONSUMERS:
 * - ProductEventConsumer (in same service) -> updates MongoDB read model
 * - inventory-service -> creates initial inventory record
 *
 * KAFKA TOPIC: product-events
 * WHY KAFKA?
 * - Async processing (product creation doesn't wait for MongoDB sync)
 * - Decoupled (product service doesn't know about inventory service)
 * - Durable (events are persisted in Kafka, can replay if needed)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PRODUCT_EVENTS_TOPIC = "product-events";

    public void publishProductCreated(Product product) {
        publish("PRODUCT_CREATED", product);
    }

    public void publishProductUpdated(Product product) {
        publish("PRODUCT_UPDATED", product);
    }

    public void publishProductDeleted(String productId) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", "PRODUCT_DELETED");
        event.put("productId", productId);

        kafkaTemplate.send(PRODUCT_EVENTS_TOPIC, productId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PRODUCT_DELETED event for id: {}", productId, ex);
                    } else {
                        log.debug("PRODUCT_DELETED event published for id: {}", productId);
                    }
                });
    }

    private void publish(String eventType, Product product) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventId", UUID.randomUUID().toString());
        event.put("eventType", eventType);
        event.put("productId", product.getId());
        event.put("name", product.getName());
        event.put("description", product.getDescription());
        event.put("price", product.getPrice());
        event.put("originalPrice", product.getOriginalPrice());
        event.put("categoryId", product.getCategory() != null ? product.getCategory().getId() : null);
        event.put("categoryName", product.getCategory() != null ? product.getCategory().getName() : null);
        event.put("brand", product.getBrand());
        event.put("imageUrls", product.getImageUrls());
        event.put("status", product.getStatus().name());
        event.put("featured", product.isFeatured());
        event.put("averageRating", product.getAverageRating());
        event.put("reviewCount", product.getReviewCount());
        event.put("createdAt", product.getCreatedAt() != null ? product.getCreatedAt().toString() : null);

        // Use product ID as the Kafka key (ensures ordering for same product)
        kafkaTemplate.send(PRODUCT_EVENTS_TOPIC, product.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} event for product: {}", eventType, product.getId(), ex);
                    } else {
                        log.debug("{} event published for product: {}", eventType, product.getId());
                    }
                });
    }
}
