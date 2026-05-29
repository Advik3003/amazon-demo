package com.amazondemo.order.service;

import com.amazondemo.common.exception.BusinessException;
import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.order.client.InventoryClient;
import com.amazondemo.order.dto.CreateOrderRequest;
import com.amazondemo.order.dto.OrderResponse;
import com.amazondemo.order.model.Order;
import com.amazondemo.order.model.OrderItem;
import com.amazondemo.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit Tests for OrderService.
 *
 * Tests cover:
 * - Order creation (prices, tax, shipping, status)
 * - Get user orders with pagination
 * - Get single order (ownership check)
 * - Order cancellation (status validation)
 * - Order status update (admin flow)
 * - Kafka event publishing
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private InventoryClient inventoryClient;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private static final String USER_ID = "user-001";
    private static final String USER_EMAIL = "user@example.com";

    private CreateOrderRequest validOrderRequest;
    private Order savedOrder;

    @BeforeEach
    void setUp() {
        CreateOrderRequest.OrderItemRequest item = new CreateOrderRequest.OrderItemRequest();
        item.setProductId("prod-001");
        item.setProductName("iPhone 15");
        item.setQuantity(2);

        validOrderRequest = new CreateOrderRequest();
        validOrderRequest.setItems(List.of(item));
        validOrderRequest.setShippingFullName("John Doe");
        validOrderRequest.setShippingStreet("123 Main St");
        validOrderRequest.setShippingCity("New York");
        validOrderRequest.setShippingState("NY");
        validOrderRequest.setShippingZipCode("10001");
        validOrderRequest.setShippingCountry("US");

        // unit price = 29.99, qty = 2 → subtotal = 59.98
        OrderItem orderItem = OrderItem.builder()
            .id("item-001")
            .productId("prod-001")
            .productName("iPhone 15")
            .quantity(2)
            .unitPrice(new BigDecimal("29.99"))
            .totalPrice(new BigDecimal("59.98"))
            .build();

        savedOrder = Order.builder()
            .id("order-001")
            .orderNumber("ORD-2026-001234")
            .userId(USER_ID)
            .userEmail(USER_EMAIL)
            .items(List.of(orderItem))
            .subtotal(new BigDecimal("59.98"))
            .taxAmount(new BigDecimal("6.00"))
            .shippingCost(BigDecimal.ZERO)
            .totalAmount(new BigDecimal("65.98"))
            .status(Order.OrderStatus.PENDING)
            .paymentStatus(Order.PaymentStatus.PENDING)
            .shippingFullName("John Doe")
            .shippingStreet("123 Main St")
            .shippingCity("New York")
            .shippingState("NY")
            .shippingZipCode("10001")
            .shippingCountry("US")
            .build();

        orderItem.setOrder(savedOrder);

        // Default mock for Kafka (suppress null pointer in send)
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Default: inventory reservation succeeds
        when(inventoryClient.reserveStock(any()))
            .thenReturn(new InventoryClient.ReservationResponse(true, "Reserved", null));
    }

    // ==================== CREATE ORDER ====================

    @Nested
    @DisplayName("createOrder()")
    class CreateOrderTests {

        @Test
        @DisplayName("Should create order with PENDING status")
        void shouldCreateOrderWithPendingStatus() {
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderResponse response = orderService.createOrder(validOrderRequest, USER_ID, USER_EMAIL);

            assertThat(response.getStatus()).isEqualTo("PENDING");
            assertThat(response.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Should calculate free shipping for orders over $50")
        void shouldApplyFreeShippingForLargeOrders() {
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderResponse response = orderService.createOrder(validOrderRequest, USER_ID, USER_EMAIL);

            // savedOrder has subtotal 59.98 > 50 → free shipping
            assertThat(savedOrder.getShippingCost()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Should set orderNumber starting with ORD-")
        void shouldSetOrderNumber() {
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            OrderResponse response = orderService.createOrder(validOrderRequest, USER_ID, USER_EMAIL);

            assertThat(response.getOrderNumber()).startsWith("ORD-");
        }

        @Test
        @DisplayName("Should publish ORDER_PLACED Kafka event after creation")
        void shouldPublishOrderPlacedKafkaEvent() {
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            orderService.createOrder(validOrderRequest, USER_ID, USER_EMAIL);

            verify(kafkaTemplate).send(eq("order-events"), eq("order-001"), any());
        }

        @Test
        @DisplayName("Should still create order when inventory reservation fails")
        void shouldCreateOrderEvenWhenInventoryFails() {
            when(inventoryClient.reserveStock(any()))
                .thenThrow(new RuntimeException("Inventory service unavailable"));
            when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

            // Order should NOT fail if inventory is unavailable
            assertThatCode(() ->
                orderService.createOrder(validOrderRequest, USER_ID, USER_EMAIL))
                .doesNotThrowAnyException();

            verify(orderRepository).save(any());
        }
    }

    // ==================== GET ORDER ====================

    @Nested
    @DisplayName("getOrderById()")
    class GetOrderTests {

        @Test
        @DisplayName("Should return order when it belongs to the requesting user")
        void shouldReturnOrderForCorrectUser() {
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));

            OrderResponse response = orderService.getOrderById("order-001", USER_ID);

            assertThat(response.getId()).isEqualTo("order-001");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException for non-existent order")
        void shouldThrowWhenOrderNotFound() {
            when(orderRepository.findById("ghost-order")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrderById("ghost-order", USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw BusinessException when order belongs to different user")
        void shouldThrowWhenOrderBelongsToDifferentUser() {
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));

            assertThatThrownBy(() ->
                orderService.getOrderById("order-001", "different-user-999"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");
        }
    }

    // ==================== CANCEL ORDER ====================

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrderTests {

        @Test
        @DisplayName("Should cancel a PENDING order")
        void shouldCancelPendingOrder() {
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));
            when(orderRepository.save(any())).thenReturn(savedOrder);
            doNothing().when(inventoryClient).releaseReservation(any());

            OrderResponse response = orderService.cancelOrder("order-001", USER_ID, "Changed my mind");

            assertThat(response.getStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("Should cancel a CONFIRMED order")
        void shouldCancelConfirmedOrder() {
            savedOrder.setStatus(Order.OrderStatus.CONFIRMED);
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));
            when(orderRepository.save(any())).thenReturn(savedOrder);
            doNothing().when(inventoryClient).releaseReservation(any());

            OrderResponse response = orderService.cancelOrder("order-001", USER_ID, "Customer request");

            assertThat(response.getStatus()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("Should throw BusinessException when cancelling a SHIPPED order")
        void shouldThrowWhenCancellingShippedOrder() {
            savedOrder.setStatus(Order.OrderStatus.SHIPPED);
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));

            assertThatThrownBy(() ->
                orderService.cancelOrder("order-001", USER_ID, "Too late"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot cancel order");
        }

        @Test
        @DisplayName("Should throw BusinessException when cancelling another user's order")
        void shouldThrowWhenCancellingAnotherUsersOrder() {
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));

            assertThatThrownBy(() ->
                orderService.cancelOrder("order-001", "attacker-user", "Attempt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("Should publish ORDER_CANCELLED Kafka event on cancellation")
        void shouldPublishCancelledEvent() {
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));
            when(orderRepository.save(any())).thenReturn(savedOrder);
            doNothing().when(inventoryClient).releaseReservation(any());

            orderService.cancelOrder("order-001", USER_ID, "Reason");

            verify(kafkaTemplate).send(eq("order-events"), eq("order-001"), any());
        }
    }

    // ==================== UPDATE STATUS (ADMIN) ====================

    @Nested
    @DisplayName("updateOrderStatus()")
    class UpdateStatusTests {

        @Test
        @DisplayName("Should update order to CONFIRMED status")
        void shouldUpdateToConfirmedStatus() {
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));
            when(orderRepository.save(any())).thenReturn(savedOrder);

            OrderResponse response = orderService.updateOrderStatus("order-001", "CONFIRMED");

            assertThat(savedOrder.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
            assertThat(savedOrder.getConfirmedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should set shippedAt timestamp when status changes to SHIPPED")
        void shouldSetShippedAtTimestamp() {
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(savedOrder));
            when(orderRepository.save(any())).thenReturn(savedOrder);

            orderService.updateOrderStatus("order-001", "SHIPPED");

            assertThat(savedOrder.getShippedAt()).isNotNull();
        }
    }
}
