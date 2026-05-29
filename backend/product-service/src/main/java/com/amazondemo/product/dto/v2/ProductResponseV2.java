package com.amazondemo.product.dto.v2;

import com.amazondemo.product.dto.ProductResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Product Response V2
 * ====================
 * Enhanced payload for API v2 consumers.
 *
 * V2 additions over V1:
 *  - apiVersion      : Explicit version tag in response body
 *  - tags            : Free-form searchable tags
 *  - specifications  : Key-value technical spec map (RAM, Color, Weight, etc.)
 *  - seoSlug         : URL-friendly slug for SEO
 *  - priceBreakdown  : Detailed pricing with tax and savings
 *  - availability    : Richer stock info (estimated delivery, warehouse, etc.)
 *
 * Backward compatibility: V1 clients should migrate to V2 by Q3-2027.
 * V1 remains supported until deprecation announcement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductResponseV2 {

    // --- Core fields (same as V1) ---
    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Double discountPercentage;
    private String categoryId;
    private String categoryName;
    private String brand;
    private List<String> imageUrls;
    private double averageRating;
    private int reviewCount;
    private String status;
    private boolean featured;
    private int stockQuantity;
    private boolean inStock;
    private LocalDateTime createdAt;

    // --- V2 NEW FIELDS ---
    private String apiVersion = "v2";
    private List<String> tags;
    private Map<String, String> specifications;  // e.g., {"RAM": "16GB", "Color": "Black"}
    private String seoSlug;                       // URL-friendly: "apple-macbook-pro-m3-16gb"
    private PriceBreakdown priceBreakdown;
    private AvailabilityInfo availability;
    private String sellerName;
    private String sellerRating;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceBreakdown {
        private BigDecimal basePrice;
        private BigDecimal taxAmount;
        private BigDecimal taxRate;
        private BigDecimal savings;
        private BigDecimal finalPrice;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AvailabilityInfo {
        private boolean inStock;
        private int quantityAvailable;
        private String estimatedDelivery;   // e.g., "2-3 business days"
        private boolean freeShipping;
        private String warehouse;           // e.g., "US-EAST-1"
    }

    /** Factory method: convert V1 response to V2 with enriched fields */
    public static ProductResponseV2 fromV1(ProductResponse v1) {
        return ProductResponseV2.builder()
                .id(v1.getId())
                .name(v1.getName())
                .description(v1.getDescription())
                .price(v1.getPrice())
                .originalPrice(v1.getOriginalPrice())
                .discountPercentage(v1.getDiscountPercentage())
                .categoryId(v1.getCategoryId())
                .categoryName(v1.getCategoryName())
                .brand(v1.getBrand())
                .imageUrls(v1.getImageUrls())
                .averageRating(v1.getAverageRating())
                .reviewCount(v1.getReviewCount())
                .status(v1.getStatus())
                .featured(v1.isFeatured())
                .stockQuantity(v1.getStockQuantity())
                .inStock(v1.isInStock())
                .createdAt(v1.getCreatedAt())
                .apiVersion("v2")
                .seoSlug(generateSlug(v1.getBrand(), v1.getName()))
                .availability(AvailabilityInfo.builder()
                        .inStock(v1.isInStock())
                        .quantityAvailable(v1.getStockQuantity())
                        .estimatedDelivery(v1.isInStock() ? "2-3 business days" : "Out of stock")
                        .freeShipping(v1.getPrice() != null && v1.getPrice().compareTo(new BigDecimal("50")) >= 0)
                        .warehouse("US-EAST-1")
                        .build())
                .priceBreakdown(buildPriceBreakdown(v1.getPrice(), v1.getOriginalPrice()))
                .build();
    }

    private static String generateSlug(String brand, String name) {
        if (name == null) return null;
        String base = (brand != null ? brand + "-" + name : name);
        return base.toLowerCase()
                   .replaceAll("[^a-z0-9\\s-]", "")
                   .replaceAll("\\s+", "-")
                   .replaceAll("-+", "-");
    }

    private static PriceBreakdown buildPriceBreakdown(BigDecimal price, BigDecimal originalPrice) {
        if (price == null) return null;
        BigDecimal taxRate = new BigDecimal("0.10");
        BigDecimal taxAmount = price.multiply(taxRate).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal savings = originalPrice != null ? originalPrice.subtract(price) : BigDecimal.ZERO;
        return PriceBreakdown.builder()
                .basePrice(price)
                .taxRate(taxRate)
                .taxAmount(taxAmount)
                .savings(savings.compareTo(BigDecimal.ZERO) > 0 ? savings : null)
                .finalPrice(price.add(taxAmount))
                .currency("USD")
                .build();
    }
}
