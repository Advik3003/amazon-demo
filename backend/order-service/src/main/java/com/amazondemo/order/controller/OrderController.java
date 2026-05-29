package com.amazondemo.order.controller;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.order.dto.CreateOrderRequest;
import com.amazondemo.order.dto.OrderResponse;
import com.amazondemo.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order management APIs")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place a new order")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String userEmail) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        orderService.createOrder(request, userId, userEmail),
                        "Order placed successfully"));
    }

    @GetMapping
    @Operation(summary = "Get current user's orders")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getUserOrders(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getUserOrders(userId, pageable), "Orders fetched"));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId) {

        return ResponseEntity.ok(ApiResponse.success(
                orderService.getOrderById(orderId, userId), "Order fetched"));
    }

    @PatchMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.getOrDefault("reason", "Customer requested cancellation") : "Customer requested cancellation";
        return ResponseEntity.ok(ApiResponse.success(
                orderService.cancelOrder(orderId, userId, reason), "Order cancelled"));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Update order status (Admin)")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        return ResponseEntity.ok(ApiResponse.success(
                orderService.updateOrderStatus(orderId, body.get("status")), "Status updated"));
    }
}
