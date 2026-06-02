package com.amazondemo.order.controller;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.exception.BusinessException;
import com.amazondemo.common.exception.GlobalExceptionHandler;
import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.order.dto.CreateOrderRequest;
import com.amazondemo.order.dto.OrderResponse;
import com.amazondemo.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller Tests for OrderController.
 *
 * Tests HTTP layer concerns:
 * - Status codes (200, 201, 400, 403, 404)
 * - Request header validation (X-User-Id)
 * - JSON response structure
 * - Pagination parameters
 */
@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
@WithMockUser
@DisplayName("OrderController Web Layer Tests")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private OrderService orderService;
    @MockBean  private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private OrderResponse sampleOrderResponse;
    private static final String USER_ID = "user-001";
    private static final String USER_EMAIL = "user@example.com";

    @BeforeEach
    void setUp() {
        OrderResponse.OrderItemDto item = OrderResponse.OrderItemDto.builder()
            .id("item-001")
            .productId("prod-001")
            .productName("iPhone 15")
            .quantity(2)
            .unitPrice(new BigDecimal("29.99"))
            .totalPrice(new BigDecimal("59.98"))
            .build();

        sampleOrderResponse = OrderResponse.builder()
            .id("order-001")
            .orderNumber("ORD-2026-000001")
            .userId(USER_ID)
            .items(List.of(item))
            .subtotal(new BigDecimal("59.98"))
            .taxAmount(new BigDecimal("6.00"))
            .shippingCost(BigDecimal.ZERO)
            .totalAmount(new BigDecimal("65.98"))
            .status("PENDING")
            .paymentStatus("PENDING")
            .shippingFullName("John Doe")
            .shippingCity("New York")
            .shippingCountry("US")
            .build();
    }

    // ==================== CREATE ORDER ====================

    @Nested
    @DisplayName("POST /api/v1/orders")
    class CreateOrderTests {

        private CreateOrderRequest validRequest;

        @BeforeEach
        void setUp() {
            CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
            item.setProductId("prod-001");
            item.setProductName("iPhone 15");
            item.setQuantity(2);

            validRequest = new CreateOrderRequest();
            validRequest.setItems(List.of(item));
            validRequest.setShippingFullName("John Doe");
            validRequest.setShippingStreet("123 Main St");
            validRequest.setShippingCity("New York");
            validRequest.setShippingState("NY");
            validRequest.setShippingZipCode("10001");
            validRequest.setShippingCountry("US");
        }

        @Test
        @DisplayName("Should return 201 when order is created successfully")
        void shouldReturn201OnOrderCreation() throws Exception {
            when(orderService.createOrder(any(), eq(USER_ID), eq(USER_EMAIL), isNull()))
                .thenReturn(sampleOrderResponse);

            mockMvc.perform(post("/api/v1/orders")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", USER_ID)
                    .header("X-User-Email", USER_EMAIL)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-2026-000001"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Order placed successfully"));
        }

        @Test
        @DisplayName("Should return 400 when items list is empty")
        void shouldReturn400WhenItemsEmpty() throws Exception {
            validRequest.setItems(List.of());

            mockMvc.perform(post("/api/v1/orders")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", USER_ID)
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should pass X-User-Id header to service")
        void shouldPassUserIdToService() throws Exception {
            when(orderService.createOrder(any(), eq("user-xyz"), anyString(), isNull()))
                .thenReturn(sampleOrderResponse);

            mockMvc.perform(post("/api/v1/orders")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", "user-xyz")
                    .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());

            verify(orderService).createOrder(any(), eq("user-xyz"), anyString(), isNull());
        }
    }

    // ==================== GET ORDER BY ID ====================

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId}")
    class GetOrderTests {

        @Test
        @DisplayName("Should return 200 and order details")
        void shouldReturn200WithOrderDetails() throws Exception {
            when(orderService.getOrderById("order-001", USER_ID))
                .thenReturn(sampleOrderResponse);

            mockMvc.perform(get("/api/v1/orders/order-001")
                    .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("order-001"))
                .andExpect(jsonPath("$.data.orderNumber").value("ORD-2026-000001"))
                .andExpect(jsonPath("$.data.items", hasSize(1)));
        }

        @Test
        @DisplayName("Should return 404 when order not found")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            when(orderService.getOrderById("nonexistent", USER_ID))
                .thenThrow(new ResourceNotFoundException("Order", "id", "nonexistent"));

            mockMvc.perform(get("/api/v1/orders/nonexistent")
                    .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 400/403 when accessing another user's order")
        void shouldReturn4xxWhenAccessDenied() throws Exception {
            when(orderService.getOrderById("order-001", "attacker"))
                .thenThrow(new BusinessException("Access denied: This order doesn't belong to you"));

            mockMvc.perform(get("/api/v1/orders/order-001")
                    .header("X-User-Id", "attacker"))
                .andExpect(status().is4xxClientError());
        }
    }

    // ==================== CANCEL ORDER ====================

    @Nested
    @DisplayName("PATCH /api/v1/orders/{orderId}/cancel")
    class CancelOrderTests {

        @Test
        @DisplayName("Should return 200 when order is cancelled successfully")
        void shouldReturn200OnCancellation() throws Exception {
            sampleOrderResponse.setStatus("CANCELLED");
            when(orderService.cancelOrder(eq("order-001"), eq(USER_ID), anyString()))
                .thenReturn(sampleOrderResponse);

            mockMvc.perform(patch("/api/v1/orders/order-001/cancel")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", USER_ID)
                    .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("Should return 4xx when trying to cancel shipped order")
        void shouldReturn4xxWhenCancellingShippedOrder() throws Exception {
            when(orderService.cancelOrder(any(), any(), any()))
                .thenThrow(new BusinessException("Cannot cancel order in status: SHIPPED"));

            mockMvc.perform(patch("/api/v1/orders/order-001/cancel")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-User-Id", USER_ID)
                    .content("{}"))
                .andExpect(status().is4xxClientError());
        }
    }
}
