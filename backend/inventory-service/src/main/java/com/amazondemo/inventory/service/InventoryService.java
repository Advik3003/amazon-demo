package com.amazondemo.inventory.service;

import com.amazondemo.common.exception.BusinessException;
import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.inventory.model.Inventory;
import com.amazondemo.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public Inventory getByProductId(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));
    }

    public boolean checkStock(String productId, int quantity) {
        return inventoryRepository.findByProductId(productId)
                .map(inv -> inv.getQuantityAvailable() >= quantity)
                .orElse(false);
    }

    /**
     * Reserve stock for an order (uses pessimistic locking to prevent race conditions)
     */
    @Transactional
    public boolean reserveStock(String productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

        if (inventory.getQuantityAvailable() < quantity) {
            log.warn("Insufficient stock for product: {} - Available: {} Requested: {}",
                    productId, inventory.getQuantityAvailable(), quantity);
            return false;
        }

        inventory.setQuantityAvailable(inventory.getQuantityAvailable() - quantity);
        inventory.setQuantityReserved(inventory.getQuantityReserved() + quantity);
        inventoryRepository.save(inventory);

        log.info("Stock reserved for product: {} - Quantity: {}", productId, quantity);
        return true;
    }

    /**
     * Release reserved stock (on order cancellation)
     */
    @Transactional
    public void releaseReservation(String productId, int quantity) {
        inventoryRepository.findByProductId(productId).ifPresent(inventory -> {
            inventory.setQuantityReserved(Math.max(0, inventory.getQuantityReserved() - quantity));
            inventory.setQuantityAvailable(inventory.getQuantityAvailable() + quantity);
            inventoryRepository.save(inventory);
            log.info("Reservation released for product: {} - Quantity: {}", productId, quantity);
        });
    }

    /**
     * Deduct stock after confirmed payment
     */
    @Transactional
    public void deductStock(String productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductIdWithLock(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", "productId", productId));

        inventory.setQuantityReserved(Math.max(0, inventory.getQuantityReserved() - quantity));
        inventoryRepository.save(inventory);

        if (inventory.isLowStock()) {
            log.warn("LOW STOCK ALERT: Product {} has only {} units left", productId, inventory.getQuantityAvailable());
        }
    }

    @Transactional
    public Inventory updateStock(String productId, int quantity) {
        Inventory inventory = inventoryRepository.findByProductId(productId)
                .orElse(Inventory.builder().productId(productId).build());

        inventory.setQuantityAvailable(quantity);
        return inventoryRepository.save(inventory);
    }

    public List<Inventory> getLowStockItems() {
        return inventoryRepository.findAll().stream()
                .filter(Inventory::isLowStock)
                .toList();
    }
}
