package com.amazondemo.product.service;

import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.product.dto.ProductRequest;
import com.amazondemo.product.dto.ProductResponse;
import com.amazondemo.product.event.ProductEventPublisher;
import com.amazondemo.product.model.Category;
import com.amazondemo.product.model.Product;
import com.amazondemo.product.repository.CategoryRepository;
import com.amazondemo.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for ProductCommandService (CQRS Write Side).
 *
 * Tests cover:
 * - Create product (success, invalid category, published event)
 * - Update product (success, not found)
 * - Delete product (success, not found)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProductCommandService Unit Tests")
class ProductCommandServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductEventPublisher eventPublisher;

    @InjectMocks
    private ProductCommandService productCommandService;

    private Category electronicsCategory;
    private ProductRequest validRequest;

    @BeforeEach
    void setUp() {
        electronicsCategory = Category.builder()
            .id("cat-001")
            .name("Electronics")
            .slug("electronics")
            .build();

        validRequest = new ProductRequest();
        validRequest.setName("iPhone 15 Pro");
        validRequest.setDescription("Latest Apple smartphone");
        validRequest.setPrice(new BigDecimal("999.99"));
        validRequest.setOriginalPrice(new BigDecimal("1099.99"));
        validRequest.setBrand("Apple");
        validRequest.setCategoryId("cat-001");
        validRequest.setImageUrls(List.of("https://example.com/iphone.jpg"));
        validRequest.setFeatured(true);
    }

    // ==================== CREATE PRODUCT ====================

    @Nested
    @DisplayName("createProduct()")
    class CreateProductTests {

        @Test
        @DisplayName("Should create product and return response with correct fields")
        void shouldCreateProductSuccessfully() {
            Product savedProduct = Product.builder()
                .id("prod-001")
                .name("iPhone 15 Pro")
                .price(new BigDecimal("999.99"))
                .category(electronicsCategory)
                .brand("Apple")
                .status(Product.ProductStatus.ACTIVE)
                .build();

            when(categoryRepository.findById("cat-001"))
                .thenReturn(Optional.of(electronicsCategory));
            when(productRepository.save(any(Product.class)))
                .thenReturn(savedProduct);

            ProductResponse response = productCommandService.createProduct(validRequest, "admin-001");

            assertThat(response.getId()).isEqualTo("prod-001");
            assertThat(response.getName()).isEqualTo("iPhone 15 Pro");
            assertThat(response.getPrice()).isEqualByComparingTo("999.99");
        }

        @Test
        @DisplayName("Should publish PRODUCT_CREATED event after successful creation")
        void shouldPublishKafkaEventAfterCreation() {
            Product savedProduct = Product.builder()
                .id("prod-001")
                .name("iPhone 15 Pro")
                .status(Product.ProductStatus.ACTIVE)
                .build();

            when(categoryRepository.findById("cat-001"))
                .thenReturn(Optional.of(electronicsCategory));
            when(productRepository.save(any())).thenReturn(savedProduct);

            productCommandService.createProduct(validRequest, "admin-001");

            verify(eventPublisher).publishProductCreated(savedProduct);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException for non-existent category")
        void shouldThrowWhenCategoryNotFound() {
            when(categoryRepository.findById("cat-999"))
                .thenReturn(Optional.empty());

            validRequest.setCategoryId("cat-999");

            assertThatThrownBy(() ->
                productCommandService.createProduct(validRequest, "admin-001"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");

            verify(productRepository, never()).save(any());
            verify(eventPublisher, never()).publishProductCreated(any());
        }

        @Test
        @DisplayName("Should set seller ID from parameter")
        void shouldSetSellerIdFromParameter() {
            when(categoryRepository.findById("cat-001"))
                .thenReturn(Optional.of(electronicsCategory));
            when(productRepository.save(any())).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId("prod-001");
                return p;
            });

            productCommandService.createProduct(validRequest, "seller-xyz");

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productRepository).save(captor.capture());
            assertThat(captor.getValue().getSellerId()).isEqualTo("seller-xyz");
        }

        @Test
        @DisplayName("Should create product without category when categoryId is null")
        void shouldCreateProductWithoutCategory() {
            validRequest.setCategoryId(null);
            Product savedProduct = Product.builder()
                .id("prod-001")
                .name("iPhone 15 Pro")
                .status(Product.ProductStatus.ACTIVE)
                .build();
            when(productRepository.save(any())).thenReturn(savedProduct);

            ProductResponse response = productCommandService.createProduct(validRequest, "admin-001");

            assertThat(response).isNotNull();
            verify(categoryRepository, never()).findById(any());
        }
    }

    // ==================== UPDATE PRODUCT ====================

    @Nested
    @DisplayName("updateProduct()")
    class UpdateProductTests {

        @Test
        @DisplayName("Should update product fields and publish event")
        void shouldUpdateProductSuccessfully() {
            Product existingProduct = Product.builder()
                .id("prod-001")
                .name("Old Name")
                .price(new BigDecimal("799.99"))
                .status(Product.ProductStatus.ACTIVE)
                .build();

            ProductRequest updateRequest = new ProductRequest();
            updateRequest.setName("New Name");
            updateRequest.setPrice(new BigDecimal("899.99"));
            updateRequest.setDescription("Updated description");

            when(productRepository.findById("prod-001"))
                .thenReturn(Optional.of(existingProduct));
            when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ProductResponse response = productCommandService.updateProduct("prod-001", updateRequest);

            assertThat(response.getName()).isEqualTo("New Name");
            assertThat(response.getPrice()).isEqualByComparingTo("899.99");
            verify(eventPublisher).publishProductUpdated(existingProduct);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when product not found")
        void shouldThrowWhenProductNotFound() {
            when(productRepository.findById("nonexistent"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                productCommandService.updateProduct("nonexistent", validRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product");
        }
    }

    // ==================== DELETE PRODUCT ====================

    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProductTests {

        @Test
        @DisplayName("Should delete product and publish event")
        void shouldDeleteProduct() {
            when(productRepository.existsById("prod-001")).thenReturn(true);
            doNothing().when(productRepository).deleteById("prod-001");

            productCommandService.deleteProduct("prod-001");

            verify(productRepository).deleteById("prod-001");
            verify(eventPublisher).publishProductDeleted("prod-001");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when deleting non-existent product")
        void shouldThrowWhenDeletingNonExistentProduct() {
            when(productRepository.existsById("ghost")).thenReturn(false);

            assertThatThrownBy(() -> productCommandService.deleteProduct("ghost"))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(productRepository, never()).deleteById(any());
        }
    }
}
