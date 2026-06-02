package com.amazondemo.order.service;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.event.OrderEvent;
import com.amazondemo.common.exception.BusinessException;
import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.order.client.InventoryClient;
import com.amazondemo.order.dto.CreateOrderRequest;
import com.amazondemo.order.dto.OrderResponse;
import com.amazondemo.order.model.Order;
import com.amazondemo.order.model.OrderItem;
import com.amazondemo.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Order Service - Business Logic
 * ================================
 * Orchestrates the order creation flow:
 * 1. Validate items
 * 2. Check inventory (via Feign Client to inventory-service)
 * 3. Calculate prices
 * 4. Create order in PostgreSQL
 * 5. Reserve inventory
 * 6. Publish ORDER_PLACED event to Kafka
 * 7. Notification Service receives event and sends email
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ORDER_EVENTS_TOPIC = "order-events";

    /**
     * Create a new order
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String userId, String userEmail, String idempotencyKey) {
        log.info("Creating order for user: {}", userId);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing order for idempotency key: {}", idempotencyKey);
                return toResponse(existing.get());
            }
        }

        // Build order items with fetched prices
        List<OrderItem> orderItems = buildOrderItems(request.getItems());

        // Calculate totals
        BigDecimal subtotal = orderItems.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal taxAmount = subtotal.multiply(BigDecimal.valueOf(0.1)); // 10% tax
        BigDecimal shippingCost = subtotal.compareTo(BigDecimal.valueOf(50)) >= 0
                ? BigDecimal.ZERO : BigDecimal.valueOf(9.99); // Free shipping over $50
        BigDecimal total = subtotal.add(taxAmount).add(shippingCost);

        // Create order
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .idempotencyKey(idempotencyKey)
                .userId(userId)
                .userEmail(userEmail)
                .subtotal(subtotal)
                .taxAmount(taxAmount)
                .shippingCost(shippingCost)
                .totalAmount(total)
                .shippingFullName(request.getShippingFullName())
                .shippingStreet(request.getShippingStreet())
                .shippingCity(request.getShippingCity())
                .shippingState(request.getShippingState())
                .shippingZipCode(request.getShippingZipCode())
                .shippingCountry(request.getShippingCountry())
                .notes(request.getNotes())
                .status(Order.OrderStatus.PENDING)
                .build();

        // Set bidirectional relationship
        orderItems.forEach(item -> item.setOrder(order));
        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        // Try to reserve inventory (Circuit Breaker protects this call)
        try {
            reserveInventory(savedOrder);
        } catch (Exception e) {
            log.error("Inventory reservation failed for order: {} - {}", savedOrder.getId(), e.getMessage());
            // Don't fail the order - payment will trigger inventory deduction
        }

        // Publish ORDER_PLACED event to Kafka
        publishOrderEvent(savedOrder, "ORDER_PLACED");

        log.info("Order created: {} for user: {}", savedOrder.getOrderNumber(), userId);
        return toResponse(savedOrder);
    }

    /**
     * Get orders for a specific user (with pagination)
     */
    public PageResponse<OrderResponse> getUserOrders(String userId, Pageable pageable) {
        Page<Order> page = orderRepository.findByUserId(userId, pageable);
        return PageResponse.of(page.map(this::toResponse));
    }

    /**
     * Get a single order (only if it belongs to the user)
     */
    public OrderResponse getOrderById(String orderId, String userId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Security check - users can only see their own orders
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("Access denied: This order doesn't belong to you");
        }

        return toResponse(order);
    }

    /**
     * Cancel an order
     */
    @Transactional
    public OrderResponse cancelOrder(String orderId, String userId, String reason) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (!order.getUserId().equals(userId)) {
            throw new BusinessException("Access denied");
        }

        // Can only cancel PENDING or CONFIRMED orders
        if (order.getStatus() != Order.OrderStatus.PENDING &&
                order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new BusinessException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancelledAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);

        // Release inventory reservation
        try {
            inventoryClient.releaseReservation(new InventoryClient.ReleaseStockRequest(orderId));
        } catch (Exception e) {
            log.warn("Could not release inventory for cancelled order: {}", orderId);
        }

        publishOrderEvent(saved, "ORDER_CANCELLED");

        return toResponse(saved);
    }

    /**
     * Admin: Update order status
     */
    @Transactional
    public OrderResponse updateOrderStatus(String orderId, String status) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        Order.OrderStatus newStatus = Order.OrderStatus.valueOf(status);
        order.setStatus(newStatus);

        // Set timestamps based on status
        switch (newStatus) {
            case CONFIRMED -> order.setConfirmedAt(LocalDateTime.now());
            case SHIPPED -> order.setShippedAt(LocalDateTime.now());
            case DELIVERED -> order.setDeliveredAt(LocalDateTime.now());
            default -> {}
        }

        Order saved = orderRepository.save(order);
        publishOrderEvent(saved, "ORDER_STATUS_UPDATED");

        return toResponse(saved);
    }

    // ==================== PRIVATE HELPERS ====================

    private List<OrderItem> buildOrderItems(List<CreateOrderRequest.OrderItemRequest> itemRequests) {
        return itemRequests.stream().map(req -> {
            // In a real app, fetch price from product-service
            // For now, we use a dummy price (product-service would provide it)
            BigDecimal unitPrice = BigDecimal.valueOf(29.99); // TODO: fetch from product-service

            return OrderItem.builder()
                    .productId(req.getProductId())
                    .productName(req.getProductName() != null ? req.getProductName() : "Product")
                    .productImageUrl(req.getProductImageUrl())
                    .quantity(req.getQuantity())
                    .unitPrice(unitPrice)
                    .totalPrice(unitPrice.multiply(BigDecimal.valueOf(req.getQuantity())))
                    .build();
        }).collect(Collectors.toList());
    }

    private void reserveInventory(Order order) {
        List<InventoryClient.ReserveStockRequest.ReserveItem> items = order.getItems().stream()
                .map(item -> new InventoryClient.ReserveStockRequest.ReserveItem(
                        item.getProductId(), item.getQuantity()))
                .toList();

        InventoryClient.ReservationResponse response = inventoryClient.reserveStock(
                InventoryClient.ReserveStockRequest.builder()
                        .orderId(order.getId())
                        .items(items)
                        .build());

        if (!response.isSuccess()) {
            log.warn("Inventory reservation failed for order: {} - {}", order.getId(), response.getMessage());
        }
    }

    private void publishOrderEvent(Order order, String eventType) {
        List<OrderEvent.OrderItemEvent> eventItems = order.getItems().stream()
                .map(item -> OrderEvent.OrderItemEvent.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getUnitPrice())
                        .build())
                .toList();

        OrderEvent event = OrderEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .orderId(order.getId())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .totalAmount(order.getTotalAmount())
                .items(eventItems)
                .status(order.getStatus().name())
                .eventTime(LocalDateTime.now())
                .build();

        kafkaTemplate.send(ORDER_EVENTS_TOPIC, Objects.requireNonNull(order.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} event for order: {}", eventType, order.getId(), ex);
                    } else {
                        log.info("Order event published: {} for order: {}", eventType, order.getOrderNumber());
                    }
                });
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDateTime.now().getYear() + "-" +
                String.format("%06d", (long)(Math.random() * 1000000));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderResponse.OrderItemDto> itemDtos = order.getItems().stream()
                .map(item -> OrderResponse.OrderItemDto.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .productImageUrl(item.getProductImageUrl())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .items(itemDtos)
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingCost(order.getShippingCost())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .shippingFullName(order.getShippingFullName())
                .shippingStreet(order.getShippingStreet())
                .shippingCity(order.getShippingCity())
                .shippingState(order.getShippingState())
                .shippingZipCode(order.getShippingZipCode())
                .shippingCountry(order.getShippingCountry())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
