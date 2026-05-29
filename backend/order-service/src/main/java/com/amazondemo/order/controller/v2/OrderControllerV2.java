package com.amazondemo.order.controller.v2;

import com.amazondemo.common.dto.PageResponse;
import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.order.dto.CreateOrderRequest;
import com.amazondemo.order.dto.OrderResponse;
import com.amazondemo.order.dto.v2.OrderResponseV2;
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

/**
 * Order Controller V2
 * ====================
 * Enhanced order APIs for v2 clients.
 *
 * PATH-BASED VERSIONING: /api/v2/orders
 * ----------------------------------------
 * V2 differences:
 *  1. Richer OrderResponseV2: structured shipping address, timeline, canCancel/canReturn flags
 *  2. GET /api/v2/orders/{id}/timeline  - standalone timeline endpoint (new)
 *  3. All responses include `apiVersion: "v2"`
 */
@RestController
@RequestMapping("/api/v2/orders")
@RequiredArgsConstructor
@Tag(name = "Orders V2", description = "Enhanced order APIs with timeline and structured payload (API Version 2)")
public class OrderControllerV2 {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "[V2] Place a new order - returns enriched V2 payload")
    public ResponseEntity<ApiResponse<OrderResponseV2>> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String userEmail) {

        OrderResponse created = orderService.createOrder(request, userId, userEmail);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(OrderResponseV2.fromV1(created), "Order placed successfully (v2)"));
    }

    @GetMapping
    @Operation(summary = "[V2] Get user orders - with timeline and cancellation flags")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponseV2>>> getUserOrders(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String statusFilter) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PageResponse<OrderResponse> v1Page = orderService.getUserOrders(userId, pageable);

        PageResponse<OrderResponseV2> v2Page = toV2Page(v1Page, statusFilter);
        return ResponseEntity.ok(ApiResponse.success(v2Page, "Orders fetched (v2)"));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "[V2] Get order - with structured address and timeline")
    public ResponseEntity<ApiResponse<OrderResponseV2>> getOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId) {

        OrderResponse v1 = orderService.getOrderById(orderId, userId);
        return ResponseEntity.ok(ApiResponse.success(OrderResponseV2.fromV1(v1), "Order fetched (v2)"));
    }

    @GetMapping("/{orderId}/timeline")
    @Operation(summary = "[V2 NEW] Get order status timeline")
    public ResponseEntity<ApiResponse<OrderResponseV2.OrderTimelineV2>> getOrderTimeline(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId) {

        OrderResponse v1 = orderService.getOrderById(orderId, userId);
        OrderResponseV2 v2 = OrderResponseV2.fromV1(v1);
        return ResponseEntity.ok(ApiResponse.success(v2.getTimeline(), "Order timeline (v2)"));
    }

    @PatchMapping("/{orderId}/cancel")
    @Operation(summary = "[V2] Cancel an order - returns enriched V2 payload")
    public ResponseEntity<ApiResponse<OrderResponseV2>> cancelOrder(
            @PathVariable String orderId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody(required = false) Map<String, String> body) {

        String reason = body != null ? body.getOrDefault("reason", "Customer requested cancellation")
                                     : "Customer requested cancellation";
        OrderResponse cancelled = orderService.cancelOrder(orderId, userId, reason);
        return ResponseEntity.ok(ApiResponse.success(OrderResponseV2.fromV1(cancelled), "Order cancelled (v2)"));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "[V2] Update order status (Admin)")
    public ResponseEntity<ApiResponse<OrderResponseV2>> updateStatus(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        OrderResponse updated = orderService.updateOrderStatus(orderId, body.get("status"));
        return ResponseEntity.ok(ApiResponse.success(OrderResponseV2.fromV1(updated), "Status updated (v2)"));
    }

    // ==================== PRIVATE HELPERS ====================

    private PageResponse<OrderResponseV2> toV2Page(PageResponse<OrderResponse> v1Page, String statusFilter) {
        var v2Content = v1Page.getContent()
                .stream()
                .filter(o -> "ALL".equalsIgnoreCase(statusFilter) ||
                             statusFilter.equalsIgnoreCase(o.getStatus()))
                .map(OrderResponseV2::fromV1)
                .toList();

        return PageResponse.<OrderResponseV2>builder()
                .content(v2Content)
                .pageNumber(v1Page.getPageNumber())
                .pageSize(v1Page.getPageSize())
                .totalElements(v1Page.getTotalElements())
                .totalPages(v1Page.getTotalPages())
                .last(v1Page.isLast())
                .build();
    }
}
