package com.amazondemo.inventory.controller;

import com.amazondemo.common.response.ApiResponse;
import com.amazondemo.inventory.model.Inventory;
import com.amazondemo.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock management APIs")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<Inventory>> getInventory(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getByProductId(productId), "Inventory fetched"));
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkStock(
            @RequestParam String productId,
            @RequestParam int quantity) {
        boolean available = inventoryService.checkStock(productId, quantity);
        Inventory inv = inventoryService.getByProductId(productId);
        return ResponseEntity.ok(Map.of(
                "productId", productId,
                "availableQuantity", inv.getQuantityAvailable(),
                "inStock", available
        ));
    }

    @PostMapping("/reserve")
    public ResponseEntity<Map<String, Object>> reserveStock(
            @RequestBody ReserveRequest request) {

        boolean allReserved = true;
        for (var item : request.getItems()) {
            boolean reserved = inventoryService.reserveStock(item.getProductId(), item.getQuantity());
            if (!reserved) {
                allReserved = false;
                break;
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", allReserved,
                "orderId", request.getOrderId(),
                "message", allReserved ? "Stock reserved successfully" : "Insufficient stock"
        ));
    }

    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> releaseReservation(
            @RequestBody Map<String, String> request) {
        // In a real app, track which products were reserved per order
        return ResponseEntity.ok(ApiResponse.success("Reservation released"));
    }

    @PutMapping("/{productId}")
    public ResponseEntity<ApiResponse<Inventory>> updateStock(
            @PathVariable String productId,
            @RequestBody Map<String, Integer> request) {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.updateStock(productId, request.get("quantity")),
                "Stock updated"));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<Inventory>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.success(
                inventoryService.getLowStockItems(), "Low stock items"));
    }

    @Data
    static class ReserveRequest {
        private String orderId;
        private List<ReserveItem> items;

        @Data
        static class ReserveItem {
            private String productId;
            private int quantity;
        }
    }
}
