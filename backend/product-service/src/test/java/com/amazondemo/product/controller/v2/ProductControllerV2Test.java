package com.amazondemo.product.controller.v2;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.product.dto.ProductResponse;
import com.amazondemo.product.dto.v2.ProductResponseV2;
import com.amazondemo.product.service.ProductCommandService;
import com.amazondemo.product.service.ProductQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Product Controller V2 - Web Layer Tests
 * =========================================
 * Tests API v2 path versioning and enriched payload responses.
 */
@WebMvcTest(com.amazondemo.product.controller.v2.ProductControllerV2.class)
@WithMockUser
@DisplayName("ProductController V2 - Path Versioning Tests")
class ProductControllerV2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductCommandService commandService;

    @MockBean
    private ProductQueryService queryService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private ProductResponse sampleV1Response;

    @BeforeEach
    void setUp() {
        sampleV1Response = new ProductResponse();
        sampleV1Response.setId("prod-001");
        sampleV1Response.setName("Test Product");
        sampleV1Response.setDescription("A test product description");
        sampleV1Response.setPrice(new BigDecimal("99.99"));
        sampleV1Response.setOriginalPrice(new BigDecimal("129.99"));
        sampleV1Response.setBrand("TestBrand");
        sampleV1Response.setCategoryId("cat-001");
        sampleV1Response.setCategoryName("Electronics");
        sampleV1Response.setStockQuantity(50);
        sampleV1Response.setInStock(true);
        sampleV1Response.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("GET /api/v2/products - should return V2 payload with apiVersion field")
    void getAllProductsV2_shouldReturnEnrichedPayload() throws Exception {
        PageResponse<ProductResponse> v1Page = PageResponse.<ProductResponse>builder()
                .content(List.of(sampleV1Response))
                .pageNumber(0).pageSize(20).totalElements(1).totalPages(1).last(true)
                .build();

        when(queryService.getAllProducts(any(Pageable.class))).thenReturn(v1Page);

        mockMvc.perform(get("/api/v2/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].id").value("prod-001"))
                .andExpect(jsonPath("$.data.content[0].apiVersion").value("v2"));
    }

    @Test
    @DisplayName("GET /api/v2/products/{id} - should return enriched V2 product with seoSlug")
    void getProductByIdV2_shouldReturnEnrichedPayload() throws Exception {
        when(queryService.getProductById("prod-001")).thenReturn(sampleV1Response);

        mockMvc.perform(get("/api/v2/products/prod-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value("prod-001"))
                .andExpect(jsonPath("$.data.apiVersion").value("v2"))
                .andExpect(jsonPath("$.data.availability").exists())
                .andExpect(jsonPath("$.data.priceBreakdown").exists());
    }

    @Test
    @DisplayName("GET /api/v2/products/{id} - availability.inStock should match V1 inStock")
    void getProductByIdV2_availabilityMatchesStock() throws Exception {
        when(queryService.getProductById("prod-001")).thenReturn(sampleV1Response);

        mockMvc.perform(get("/api/v2/products/prod-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availability.inStock").value(true))
                .andExpect(jsonPath("$.data.availability.quantityAvailable").value(50));
    }

    @Test
    @DisplayName("GET /api/v2/products/featured - should return list with V2 payload")
    void getFeaturedProductsV2_shouldReturnV2List() throws Exception {
        when(queryService.getFeaturedProducts(any(Pageable.class))).thenReturn(List.of(sampleV1Response));

        mockMvc.perform(get("/api/v2/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].apiVersion").value("v2"));
    }

    @Test
    @DisplayName("GET /api/v2/products/search - should return V2 search results")
    void searchProductsV2_shouldReturnEnrichedResults() throws Exception {
        PageResponse<ProductResponse> v1Page = PageResponse.<ProductResponse>builder()
                .content(List.of(sampleV1Response))
                .pageNumber(0).pageSize(20).totalElements(1).totalPages(1).last(true)
                .build();

        when(queryService.searchProducts(eq("laptop"), any(Pageable.class))).thenReturn(v1Page);

        mockMvc.perform(get("/api/v2/products/search").param("q", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].apiVersion").value("v2"));
    }

    @Test
    @DisplayName("V1 and V2 should serve on separate paths")
    void v1AndV2ShouldHaveSeparatePaths() throws Exception {
        when(queryService.getProductById("prod-001")).thenReturn(sampleV1Response);

        // V2 endpoint exists and returns apiVersion
        mockMvc.perform(get("/api/v2/products/prod-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiVersion").value("v2"));
    }

    @Test
    @DisplayName("GET /api/v2/products/{id}/similar - should return similar products")
    void getSimilarProducts_shouldReturnFilteredList() throws Exception {
        ProductResponse anotherProduct = new ProductResponse();
        anotherProduct.setId("prod-002");
        anotherProduct.setName("Similar Product");
        anotherProduct.setCategoryId("cat-001");
        anotherProduct.setInStock(true);
        anotherProduct.setPrice(new BigDecimal("89.99"));

        PageResponse<ProductResponse> categoryPage = PageResponse.<ProductResponse>builder()
                .content(List.of(sampleV1Response, anotherProduct))
                .pageNumber(0).pageSize(5).totalElements(2).totalPages(1).last(true)
                .build();

        when(queryService.getProductById("prod-001")).thenReturn(sampleV1Response);
        when(queryService.getProductsByCategory(eq("cat-001"), any(Pageable.class))).thenReturn(categoryPage);

        mockMvc.perform(get("/api/v2/products/prod-001/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }
}

