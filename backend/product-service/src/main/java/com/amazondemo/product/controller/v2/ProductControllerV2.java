package com.amazondemo.product.controller.v2;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.product.dto.ProductRequest;
import com.amazondemo.product.dto.ProductResponse;
import com.amazondemo.product.dto.v2.ProductResponseV2;
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
 * Product Controller V2
 * ======================
 * API Version 2 of the Product endpoints.
 *
 * PATH-BASED VERSIONING: /api/v2/products
 * ----------------------------------------
 * V2 enhancements over V1:
 *  1. Richer response payload (tags, specs, SEO slug, price breakdown, availability)
 *  2. `apiVersion` field in every response for client-side version detection
 *  3. Additional filtering: brand filter on list endpoint
 *  4. `/api/v2/products/{id}/similar` - find similar products (new endpoint)
 *
 * VERSIONING STRATEGY:
 *  - V1 (/api/v1/) remains STABLE — backward compatible, no breaking changes
 *  - V2 (/api/v2/) is CURRENT — new features and richer payloads
 *  - Clients indicate version in URL path (PathVariable pattern)
 *  - Both versions run simultaneously; V1 deprecated Q3-2027
 *
 * @see com.amazondemo.product.controller.ProductController  V1 controller
 */
@RestController
@RequestMapping("/api/v2/products")
@RequiredArgsConstructor
@Tag(name = "Products V2", description = "Enhanced product APIs with richer payload (API Version 2)")
public class ProductControllerV2 {

    private final ProductCommandService commandService;
    private final ProductQueryService queryService;

    // ==================== QUERY ENDPOINTS ====================

    @GetMapping
    @Operation(summary = "[V2] List products - with brand filter and enriched payload")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponseV2>>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by brand name") @RequestParam(required = false) String brand) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ?
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        PageResponse<ProductResponse> v1Page = queryService.getAllProducts(pageable);
        PageResponse<ProductResponseV2> v2Page = toV2Page(v1Page, brand);

        return ResponseEntity.ok(ApiResponse.success(v2Page, "Products fetched (v2)"));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "[V2] Get product by ID - with specs, tags and availability")
    public ResponseEntity<ApiResponse<ProductResponseV2>> getProduct(@PathVariable String productId) {
        ProductResponse v1 = queryService.getProductById(productId);
        return ResponseEntity.ok(ApiResponse.success(ProductResponseV2.fromV1(v1), "Product fetched (v2)"));
    }

    @GetMapping("/search")
    @Operation(summary = "[V2] Search products - enriched response payload")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponseV2>>> searchProducts(
            @Parameter(description = "Search query") @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<ProductResponse> v1Page = queryService.searchProducts(q, pageable);
        return ResponseEntity.ok(ApiResponse.success(toV2Page(v1Page, null), "Search results (v2)"));
    }

    @GetMapping("/category/{categoryId}")
    @Operation(summary = "[V2] Get products by category - enriched payload")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponseV2>>> getByCategory(
            @PathVariable String categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        PageResponse<ProductResponse> v1Page = queryService.getProductsByCategory(categoryId, pageable);
        return ResponseEntity.ok(ApiResponse.success(toV2Page(v1Page, null), "Products by category (v2)"));
    }

    @GetMapping("/featured")
    @Operation(summary = "[V2] Get featured products - enriched with availability and price breakdown")
    public ResponseEntity<ApiResponse<List<ProductResponseV2>>> getFeaturedProducts(
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(0, size);
        List<ProductResponseV2> v2List = queryService.getFeaturedProducts(pageable)
                .stream().map(ProductResponseV2::fromV1).toList();
        return ResponseEntity.ok(ApiResponse.success(v2List, "Featured products (v2)"));
    }

    @GetMapping("/{productId}/similar")
    @Operation(summary = "[V2 NEW] Get similar products by category")
    public ResponseEntity<ApiResponse<List<ProductResponseV2>>> getSimilarProducts(
            @PathVariable String productId,
            @RequestParam(defaultValue = "5") int limit) {

        ProductResponse product = queryService.getProductById(productId);
        Pageable pageable = PageRequest.of(0, limit);
        List<ProductResponseV2> similar = queryService.getProductsByCategory(product.getCategoryId(), pageable)
                .getContent()
                .stream()
                .filter(p -> !p.getId().equals(productId))
                .map(ProductResponseV2::fromV1)
                .limit(limit)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(similar, "Similar products (v2)"));
    }

    // ==================== COMMAND ENDPOINTS ====================

    @PostMapping
    @Operation(summary = "[V2] Create product - returns enriched V2 payload")
    public ResponseEntity<ApiResponse<ProductResponseV2>> createProduct(
            @Valid @RequestBody ProductRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId) {

        ProductResponse created = commandService.createProduct(request, userId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(ProductResponseV2.fromV1(created), "Product created (v2)"));
    }

    @PutMapping("/{productId}")
    @Operation(summary = "[V2] Update product - returns enriched V2 payload")
    public ResponseEntity<ApiResponse<ProductResponseV2>> updateProduct(
            @PathVariable String productId,
            @Valid @RequestBody ProductRequest request) {

        ProductResponse updated = commandService.updateProduct(productId, request);
        return ResponseEntity.ok(ApiResponse.success(ProductResponseV2.fromV1(updated), "Product updated (v2)"));
    }

    @DeleteMapping("/{productId}")
    @Operation(summary = "[V2] Delete product")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String productId) {
        commandService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.success("Product deleted (v2)"));
    }

    // ==================== PRIVATE HELPERS ====================

    private PageResponse<ProductResponseV2> toV2Page(PageResponse<ProductResponse> v1Page, String brandFilter) {
        List<ProductResponseV2> v2Content = v1Page.getContent()
                .stream()
                .filter(p -> brandFilter == null || brandFilter.isBlank() ||
                             brandFilter.equalsIgnoreCase(p.getBrand()))
                .map(ProductResponseV2::fromV1)
                .toList();

        return PageResponse.<ProductResponseV2>builder()
                .content(v2Content)
                .pageNumber(v1Page.getPageNumber())
                .pageSize(v1Page.getPageSize())
                .totalElements(v1Page.getTotalElements())
                .totalPages(v1Page.getTotalPages())
                .last(v1Page.isLast())
                .build();
    }
}
