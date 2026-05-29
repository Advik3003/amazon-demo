package com.amazondemo.order.controller.v2;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.order.dto.CreateOrderRequest;
import com.amazondemo.order.dto.OrderResponse;
import com.amazondemo.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Order Controller V2 - Web Layer Tests
 * =======================================
 * Tests path versioning and enriched V2 payload for order APIs.
 */
@WebMvcTest(com.amazondemo.order.controller.v2.OrderControllerV2.class)
@WithMockUser
@DisplayName("OrderController V2 - Path Versioning Tests")
class OrderControllerV2Test {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private OrderResponse sampleOrderResponse;

    @BeforeEach
    void setUp() {
        OrderResponse.OrderItemDto item = new OrderResponse.OrderItemDto();
        item.setId("item-001");
        item.setProductId("prod-001");
        item.setProductName("Test Product");
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("49.99"));
        item.setTotalPrice(new BigDecimal("99.98"));

        sampleOrderResponse = new OrderResponse();
        sampleOrderResponse.setId("order-001");
        sampleOrderResponse.setOrderNumber("ORD-2026-001");
        sampleOrderResponse.setUserId("user-001");
        sampleOrderResponse.setItems(List.of(item));
        sampleOrderResponse.setSubtotal(new BigDecimal("99.98"));
        sampleOrderResponse.setTaxAmount(new BigDecimal("10.00"));
        sampleOrderResponse.setShippingCost(BigDecimal.ZERO);
        sampleOrderResponse.setTotalAmount(new BigDecimal("109.98"));
        sampleOrderResponse.setStatus("PENDING");
        sampleOrderResponse.setPaymentStatus("PENDING");
        sampleOrderResponse.setShippingFullName("John Doe");
        sampleOrderResponse.setShippingStreet("123 Main St");
        sampleOrderResponse.setShippingCity("New York");
        sampleOrderResponse.setShippingState("NY");
        sampleOrderResponse.setShippingZipCode("10001");
        sampleOrderResponse.setShippingCountry("US");
        sampleOrderResponse.setCreatedAt(LocalDateTime.now());
    }

    @Test
    @DisplayName("GET /api/v2/orders - should return V2 enriched payload with apiVersion")
    void getUserOrdersV2_shouldReturnEnrichedPayload() throws Exception {
        PageResponse<OrderResponse> v1Page = PageResponse.<OrderResponse>builder()
                .content(List.of(sampleOrderResponse))
                .pageNumber(0).pageSize(10).totalElements(1).totalPages(1).last(true)
                .build();

        when(orderService.getUserOrders(eq("user-001"), any(Pageable.class))).thenReturn(v1Page);

        mockMvc.perform(get("/api/v2/orders")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].apiVersion").value("v2"))
                .andExpect(jsonPath("$.data.content[0].orderNumber").value("ORD-2026-001"));
    }

    @Test
    @DisplayName("GET /api/v2/orders/{id} - should return V2 payload with structured shipping address")
    void getOrderByIdV2_shouldReturnStructuredShippingAddress() throws Exception {
        when(orderService.getOrderById("order-001", "user-001")).thenReturn(sampleOrderResponse);

        mockMvc.perform(get("/api/v2/orders/order-001")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.apiVersion").value("v2"))
                .andExpect(jsonPath("$.data.shippingAddress").exists())
                .andExpect(jsonPath("$.data.shippingAddress.city").value("New York"))
                .andExpect(jsonPath("$.data.timeline").exists());
    }

    @Test
    @DisplayName("GET /api/v2/orders/{id} - canCancel should be true for PENDING orders")
    void getOrderByIdV2_canCancelTrueForPendingOrder() throws Exception {
        when(orderService.getOrderById("order-001", "user-001")).thenReturn(sampleOrderResponse);

        mockMvc.perform(get("/api/v2/orders/order-001")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canCancel").value(true))
                .andExpect(jsonPath("$.data.canReturn").value(false));
    }

    @Test
    @DisplayName("GET /api/v2/orders/{id}/timeline - should return order timeline")
    void getOrderTimeline_shouldReturnTimelineObject() throws Exception {
        when(orderService.getOrderById("order-001", "user-001")).thenReturn(sampleOrderResponse);

        mockMvc.perform(get("/api/v2/orders/order-001/timeline")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStage").value("PENDING"))
                .andExpect(jsonPath("$.data.nextStage").value("CONFIRMED"));
    }

    @Test
    @DisplayName("PATCH /api/v2/orders/{id}/cancel - should return cancelled V2 order")
    void cancelOrderV2_shouldReturnCancelledOrder() throws Exception {
        sampleOrderResponse.setStatus("CANCELLED");
        when(orderService.cancelOrder(eq("order-001"), eq("user-001"), anyString()))
                .thenReturn(sampleOrderResponse);

        mockMvc.perform(patch("/api/v2/orders/order-001/cancel")
                        .with(csrf())
                        .header("X-User-Id", "user-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Changed my mind\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.apiVersion").value("v2"));
    }

    @Test
    @DisplayName("V2 path /api/v2/orders distinct from V1 /api/v1/orders")
    void v2PathDistinctFromV1() throws Exception {
        when(orderService.getOrderById("order-001", "user-001")).thenReturn(sampleOrderResponse);

        // V2 includes structured shippingAddress object
        mockMvc.perform(get("/api/v2/orders/order-001")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shippingAddress.fullName").value("John Doe"));
    }

    @Test
    @DisplayName("V2 summary should count total items and quantity")
    void getOrderByIdV2_shouldHaveSummaryWithCounts() throws Exception {
        when(orderService.getOrderById("order-001", "user-001")).thenReturn(sampleOrderResponse);

        mockMvc.perform(get("/api/v2/orders/order-001")
                        .header("X-User-Id", "user-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").exists())
                .andExpect(jsonPath("$.data.summary.totalItems").value(1))
                .andExpect(jsonPath("$.data.summary.totalQuantity").value(2))
                .andExpect(jsonPath("$.data.summary.freeShipping").value(true));
    }
}

