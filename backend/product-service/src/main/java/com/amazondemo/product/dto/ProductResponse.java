package com.amazondemo.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
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
}
