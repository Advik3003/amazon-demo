package com.amazondemo.product.controller;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.product.dto.ProductRequest;
import com.amazondemo.product.dto.ProductResponse;
import com.amazondemo.product.service.ProductCommandService;
import com.amazondemo.product.service.ProductQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product Controller
 * ===================
 * Exposes product APIs following CQRS pattern:
 * - POST/PUT/DELETE -> Command Service -> PostgreSQL + Kafka
 * - GET -> Query Service -> Redis -> MongoDB
 *
 * URL STRUCTURE:
 * GET  /api/v1/products           - List all products (paginated)
 * GET  /api/v1/products/{id}      - Get single product
 * GET  /api/v1/products/search    - Search products
 * GET  /api/v1/products/featured  - Get featured products
 * POST /api/v1/products           - Create product (ADMIN/SELLER only)
 * PUT  /api/v1/products/{id}      - Update product
 * DELETE /api/v1/products/{id}    - Delete product
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management and search APIs")
public class ProductController {

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;

    // ==================== QUERY ENDPOINTS ====================

    @GetMapping
    @Operation(summary = "List all products with pagination and sorting")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return ResponseEntity.ok(ApiResponse.success(
                queryService.getAllProducts(pageable), "Products fetched"));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(
                queryService.getProductById(productId), "Product fetched"));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by text query")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> searchProducts(
            @Parameter(description = "Search query") @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                queryService.searchProducts(q, pageable), "Search results"));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "Get products by category")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getByCategory(
            @PathVariable String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(
                queryService.getProductsByCategory(categoryId, pageable), "Products by category"));
    }

    @GetMapping("/featured")
    @Operation(summary = "Get featured products for homepage")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> getFeaturedProducts(
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(0, size);
        return ResponseEntity.ok(ApiResponse.success(
                queryService.getFeaturedProducts(pageable), "Featured products"));
    }

    // ==================== COMMAND ENDPOINTS ====================

    @PostMapping
    @Operation(summary = "Create a new product (ADMIN/SELLER)")
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
            @Valid @RequestBody ProductRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        commandService.createProduct(request, userId), "Product created"));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "Update a product")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String productId,
            @Valid @RequestBody ProductRequest request) {

        return ResponseEntity.ok(ApiResponse.success(
                commandService.updateProduct(productId, request), "Product updated"));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "Delete a product")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String productId) {
        commandService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.success("Product deleted"));
    }
}
