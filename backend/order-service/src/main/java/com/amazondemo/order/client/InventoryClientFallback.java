package com.amazondemo.order.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fallback implementation for InventoryClient.
 * Called when inventory-service is unavailable (circuit breaker open).
 *
 * CIRCUIT BREAKER FALLBACK STRATEGY:
 * - Stock check fails? -> Assume out of stock (safe default - prevent overselling)
 * - Reserve fails? -> Return failure (order cannot proceed)
 */
@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public StockCheckResponse checkStock(String productId, int quantity) {
        log.warn("CIRCUIT BREAKER: inventory-service unavailable, returning out-of-stock for product: {}", productId);
        return StockCheckResponse.builder()
                .productId(productId)
                .availableQuantity(0)
                .inStock(false)
                .build();
    }

    @Override
    public ReservationResponse reserveStock(ReserveStockRequest request) {
        log.warn("CIRCUIT BREAKER: inventory-service unavailable, reservation failed");
        return ReservationResponse.builder()
                .success(false)
                .message("Inventory service temporarily unavailable. Please try again later.")
                .build();
    }

    @Override
    public void releaseReservation(ReleaseStockRequest request) {
        log.warn("CIRCUIT BREAKER: inventory-service unavailable, could not release reservation for order: {}",
                request.getOrderId());
    }
}
