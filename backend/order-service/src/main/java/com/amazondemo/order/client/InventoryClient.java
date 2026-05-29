package com.amazondemo.order.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Inventory Service Feign Client
 * ================================
 * Feign Client makes HTTP calls to inventory-service look like local method calls.
 *
 * HOW IT WORKS:
 * 1. Spring generates implementation at runtime
 * 2. lb://inventory-service uses Eureka for service discovery and load balancing
 * 3. Resilience4j circuit breaker wraps each call
 *
 * @FeignClient(fallback = ...) - if inventory-service is down,
 * fallback class returns safe defaults instead of throwing exception
 *
 * INTERVIEW TIP: Feign Client + Circuit Breaker = Resilient Microservices
 */
@FeignClient(
    name = "inventory-service",
    url = "${feign.inventory-service.url:}",
    fallback = InventoryClientFallback.class
)
public interface InventoryClient {

    @GetMapping("/api/v1/inventory/check")
    StockCheckResponse checkStock(@RequestParam String productId, @RequestParam int quantity);

    @PostMapping("/api/v1/inventory/reserve")
    ReservationResponse reserveStock(@RequestBody ReserveStockRequest request);

    @PostMapping("/api/v1/inventory/release")
    void releaseReservation(@RequestBody ReleaseStockRequest request);

    // ==================== DTOs ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class StockCheckResponse {
        private String productId;
        private int availableQuantity;
        private boolean inStock;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ReserveStockRequest {
        private String orderId;
        private List<ReserveItem> items;

        @Data
        @AllArgsConstructor
        @NoArgsConstructor
        public static class ReserveItem {
            private String productId;
            private int quantity;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class ReservationResponse {
        private boolean success;
        private String message;
        private String reservationId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class ReleaseStockRequest {
        private String orderId;
    }
}
