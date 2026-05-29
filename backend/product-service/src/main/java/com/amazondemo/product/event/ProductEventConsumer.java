package com.amazondemo.product.event;

import com.amazondemo.product.model.ProductReadModel;
import com.amazondemo.product.repository.ProductReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product Event Consumer - CQRS Read Model Updater
 * ==================================================
 * Listens to Kafka events from the command side and updates MongoDB read model.
 *
 * CQRS PATTERN EXPLANATION:
 * - Command side writes to PostgreSQL (strong consistency)
 * - This consumer reads events and updates MongoDB (eventual consistency)
 * - There's a small delay (milliseconds) before MongoDB reflects the change
 * - This is called "eventual consistency" - acceptable for read models
 *
 * @KafkaListener - Spring Kafka annotation to consume messages
 * - topics: which Kafka topic to listen to
 * - groupId: consumer group (ensures each event is processed once per group)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private final ProductReadRepository productReadRepository;

    @KafkaListener(topics = "product-events", groupId = "product-service-read-model")
    @SuppressWarnings("unchecked")
    public void handleProductEvent(ConsumerRecord<String, Map<String, Object>> record) {
        Map<String, Object> event = record.value();
        String eventType = (String) event.get("eventType");
        String productId = (String) event.get("productId");

        log.debug("Received product event: {} for product: {}", eventType, productId);

        try {
            switch (eventType) {
                case "PRODUCT_CREATED" -> syncToMongoDB(event, false);
                case "PRODUCT_UPDATED" -> syncToMongoDB(event, true);
                case "PRODUCT_DELETED" -> productReadRepository.deleteById(productId);
                default -> log.warn("Unknown product event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Failed to process product event: {} - Error: {}", eventType, e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void syncToMongoDB(Map<String, Object> event, boolean isUpdate) {
        String productId = (String) event.get("productId");

        // For updates, check if document exists and preserve stock info
        ProductReadModel existing = null;
        if (isUpdate) {
            existing = productReadRepository.findById(productId).orElse(null);
        }

        BigDecimal price = event.get("price") != null
                ? new BigDecimal(event.get("price").toString()) : BigDecimal.ZERO;
        BigDecimal originalPrice = event.get("originalPrice") != null
                ? new BigDecimal(event.get("originalPrice").toString()) : null;

        ProductReadModel readModel = ProductReadModel.builder()
                .id(productId)
                .name((String) event.get("name"))
                .description((String) event.get("description"))
                .price(price)
                .originalPrice(originalPrice)
                .categoryId((String) event.get("categoryId"))
                .categoryName((String) event.get("categoryName"))
                .brand((String) event.get("brand"))
                .imageUrls((List<String>) event.get("imageUrls"))
                .status((String) event.get("status"))
                .featured(Boolean.TRUE.equals(event.get("featured")))
                .averageRating(event.get("averageRating") != null
                        ? ((Number) event.get("averageRating")).doubleValue() : 0.0)
                .reviewCount(event.get("reviewCount") != null
                        ? ((Number) event.get("reviewCount")).intValue() : 0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                // Preserve stock info from previous version (if updating)
                .stockQuantity(existing != null ? existing.getStockQuantity() : 0)
                .inStock(existing != null && existing.isInStock())
                .build();

        productReadRepository.save(readModel);
        log.debug("Product synced to MongoDB: {}", productId);
    }
}
