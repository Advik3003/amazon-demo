package com.amazondemo.product.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Product Read Model - QUERY SIDE (MongoDB)
 * ==========================================
 * Denormalized read model optimized for queries.
 * This document is created/updated by consuming Kafka events from the command side.
 *
 * WHY MONGODB FOR READ MODEL?
 * - Flexible schema (product attributes vary by category)
 * - No joins needed (everything is embedded)
 * - Fast reads with proper indexing
 * - Good for full-text search
 *
 * @Document - MongoDB document (equivalent of JPA @Entity)
 * @TextIndexed - enables full-text search on this field
 * @Indexed - creates a database index for fast lookups
 */
@Document(collection = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductReadModel {

    @Id
    private String id;

    @TextIndexed(weight = 2)  // Higher weight = more important for search
    private String name;

    @TextIndexed
    private String description;

    @Indexed
    private BigDecimal price;

    private BigDecimal originalPrice;

    @Indexed
    private String categoryId;

    private String categoryName;

    @Indexed
    private String brand;

    private List<String> imageUrls;

    @Indexed
    private double averageRating;

    private int reviewCount;

    @Indexed
    private String status;

    private String sellerId;
    private boolean featured;
    private long viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Embedded inventory info (denormalized for faster reads)
    private int stockQuantity;
    private boolean inStock;
}
