package com.amazondemo.product.service;

import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.product.dto.ProductRequest;
import com.amazondemo.product.dto.ProductResponse;
import com.amazondemo.product.event.ProductEventPublisher;
import com.amazondemo.product.model.Category;
import com.amazondemo.product.model.Product;
import com.amazondemo.product.repository.CategoryRepository;
import com.amazondemo.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product Command Service - CQRS Write Side
 * ==========================================
 * Handles all product write operations (Create, Update, Delete).
 * After each operation, publishes a Kafka event to update the read model.
 *
 * @CacheEvict - invalidates cache when product changes
 * (prevents serving stale cached data)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCommandService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductEventPublisher eventPublisher;

    /**
     * Create a new product
     * 1. Save to PostgreSQL
     * 2. Publish PRODUCT_CREATED event to Kafka
     */
    @Transactional
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request, String sellerId) {
        log.info("Creating product: {} by seller: {}", request.getName(), sellerId);

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .originalPrice(request.getOriginalPrice())
                .category(category)
                .brand(request.getBrand())
                .imageUrls(request.getImageUrls() != null ? request.getImageUrls() : java.util.List.of())
                .featured(request.isFeatured())
                .sellerId(sellerId)
                .build();

        Product saved = productRepository.save(product);

        // Publish event to Kafka (async - doesn't block response)
        eventPublisher.publishProductCreated(saved);

        log.info("Product created: {}", saved.getId());
        return toResponse(saved);
    }

    /**
     * Update an existing product
     */
    @Transactional
    @CacheEvict(value = {"products", "product"}, key = "#productId")
    public ProductResponse updateProduct(String productId, ProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", productId));

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getOriginalPrice() != null) product.setOriginalPrice(request.getOriginalPrice());
        if (request.getBrand() != null) product.setBrand(request.getBrand());
        if (request.getImageUrls() != null) product.setImageUrls(request.getImageUrls());
        product.setFeatured(request.isFeatured());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));
            product.setCategory(category);
        }

        Product saved = productRepository.save(product);
        eventPublisher.publishProductUpdated(saved);

        return toResponse(saved);
    }

    /**
     * Delete a product
     */
    @Transactional
    @CacheEvict(value = {"products", "product"}, key = "#productId")
    public void deleteProduct(String productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", "id", productId);
        }
        productRepository.deleteById(productId);
        eventPublisher.publishProductDeleted(productId);
        log.info("Product deleted: {}", productId);
    }

    private ProductResponse toResponse(Product product) {
        Double discountPct = null;
        if (product.getOriginalPrice() != null && product.getOriginalPrice().compareTo(product.getPrice()) > 0) {
            discountPct = (product.getOriginalPrice().subtract(product.getPrice()))
                    .divide(product.getOriginalPrice(), 4, java.math.RoundingMode.HALF_UP)
                    .multiply(java.math.BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .discountPercentage(discountPct)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .brand(product.getBrand())
                .imageUrls(product.getImageUrls())
                .averageRating(product.getAverageRating())
                .reviewCount(product.getReviewCount())
                .status(product.getStatus().name())
                .featured(product.isFeatured())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
