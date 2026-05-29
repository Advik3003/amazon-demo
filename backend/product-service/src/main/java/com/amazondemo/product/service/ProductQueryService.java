package com.amazondemo.product.service;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.product.dto.ProductResponse;
import com.amazondemo.product.model.ProductReadModel;
import com.amazondemo.product.repository.ProductReadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Product Query Service - CQRS Read Side
 * ========================================
 * Handles all product read operations.
 * Reads from MongoDB (read model).
 * Results are cached in Redis.
 *
 * @Cacheable - caches the result in Redis
 * - value: cache name
 * - key: cache key (Spring Expression Language)
 * - On cache HIT: returns cached value immediately (no MongoDB call)
 * - On cache MISS: calls MongoDB, stores result in cache, returns it
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductQueryService {

    private final ProductReadRepository productReadRepository;

    /**
     * Get all products with pagination, sorting, filtering
     * Cached for 5 minutes (high traffic endpoint)
     */
    @Cacheable(value = "products", key = "#pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort")
    public PageResponse<ProductResponse> getAllProducts(Pageable pageable) {
        Page<ProductReadModel> page = productReadRepository.findAll(pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    /**
     * Get a single product by ID
     * Cached individually
     */
    @Cacheable(value = "product", key = "#productId")
    public ProductResponse getProductById(String productId) {
        ProductReadModel product = productReadRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));
        return toResponse(product);
    }

    /**
     * Search products by text (name, description, brand)
     * MongoDB text index handles the search
     */
    public PageResponse<ProductResponse> searchProducts(String query, Pageable pageable) {
        Page<ProductReadModel> page = productReadRepository.searchByText(query, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    /**
     * Get products by category
     */
    @Cacheable(value = "products", key = "'category_' + #categoryId + '_' + #pageable.pageNumber")
    public PageResponse<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        Page<ProductReadModel> page = productReadRepository.findByCategoryId(categoryId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    /**
     * Get featured products for homepage
     */
    @Cacheable(value = "products", key = "'featured'")
    public List<ProductResponse> getFeaturedProducts(Pageable pageable) {
        return productReadRepository.findByFeaturedTrue(pageable)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ==================== PRIVATE MAPPER ====================

    private ProductResponse toResponse(ProductReadModel model) {
        Double discountPct = null;
        if (model.getOriginalPrice() != null && model.getOriginalPrice().compareTo(model.getPrice()) > 0) {
            discountPct = (model.getOriginalPrice().subtract(model.getPrice()))
                    .divide(model.getOriginalPrice(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return ProductResponse.builder()
                .id(model.getId())
                .name(model.getName())
                .description(model.getDescription())
                .price(model.getPrice())
                .originalPrice(model.getOriginalPrice())
                .discountPercentage(discountPct)
                .categoryId(model.getCategoryId())
                .categoryName(model.getCategoryName())
                .brand(model.getBrand())
                .imageUrls(model.getImageUrls())
                .averageRating(model.getAverageRating())
                .reviewCount(model.getReviewCount())
                .status(model.getStatus())
                .featured(model.isFeatured())
                .stockQuantity(model.getStockQuantity())
                .inStock(model.isInStock())
                .createdAt(model.getCreatedAt())
                .build();
    }
}
