package com.amazondemo.inventory.service;

import com.amazondemo.common.exception.ResourceNotFoundException;
import com.amazondemo.inventory.model.Inventory;
import com.amazondemo.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for InventoryService.
 *
 * Tests cover:
 * - Stock check (available / insufficient)
 * - Stock reservation (success, insufficient, not found)
 * - Reservation release on cancellation
 * - Stock deduction after payment
 * - Low stock detection
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Unit Tests")
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private static final String PRODUCT_ID = "prod-001";

    private Inventory buildInventory(int available, int reserved) {
        return Inventory.builder()
            .id("inv-001")
            .productId(PRODUCT_ID)
            .quantityAvailable(available)
            .quantityReserved(reserved)
            .lowStockThreshold(5)
            .build();
    }

    // ==================== CHECK STOCK ====================

    @Nested
    @DisplayName("checkStock()")
    class CheckStockTests {

        @Test
        @DisplayName("Should return true when available stock >= requested quantity")
        void shouldReturnTrueWhenSufficientStock() {
            when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(buildInventory(10, 0)));

            assertThat(inventoryService.checkStock(PRODUCT_ID, 5)).isTrue();
            assertThat(inventoryService.checkStock(PRODUCT_ID, 10)).isTrue();
        }

        @Test
        @DisplayName("Should return false when available stock < requested quantity")
        void shouldReturnFalseWhenInsufficientStock() {
            when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(buildInventory(3, 0)));

            assertThat(inventoryService.checkStock(PRODUCT_ID, 5)).isFalse();
        }

        @Test
        @DisplayName("Should return false when product not in inventory")
        void shouldReturnFalseWhenProductNotFound() {
            when(inventoryRepository.findByProductId("ghost-product"))
                .thenReturn(Optional.empty());

            assertThat(inventoryService.checkStock("ghost-product", 1)).isFalse();
        }
    }

    // ==================== RESERVE STOCK ====================

    @Nested
    @DisplayName("reserveStock()")
    class ReserveStockTests {

        @Test
        @DisplayName("Should reserve stock and adjust available/reserved counts")
        void shouldReserveStockSuccessfully() {
            Inventory inventory = buildInventory(10, 0);
            when(inventoryRepository.findByProductIdWithLock(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            boolean result = inventoryService.reserveStock(PRODUCT_ID, 3);

            assertThat(result).isTrue();
            assertThat(inventory.getQuantityAvailable()).isEqualTo(7);
            assertThat(inventory.getQuantityReserved()).isEqualTo(3);
            verify(inventoryRepository).save(inventory);
        }

        @Test
        @DisplayName("Should return false when stock is insufficient")
        void shouldReturnFalseWhenInsufficientStock() {
            Inventory inventory = buildInventory(2, 0);
            when(inventoryRepository.findByProductIdWithLock(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));

            boolean result = inventoryService.reserveStock(PRODUCT_ID, 5);

            assertThat(result).isFalse();
            // Counts should NOT be modified
            assertThat(inventory.getQuantityAvailable()).isEqualTo(2);
            verify(inventoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when product not in inventory")
        void shouldThrowWhenProductNotFound() {
            when(inventoryRepository.findByProductIdWithLock("unknown-prod"))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.reserveStock("unknown-prod", 1))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10})
        @DisplayName("Should succeed for various valid quantities")
        void shouldSucceedForVariousValidQuantities(int quantity) {
            Inventory inventory = buildInventory(20, 0);
            when(inventoryRepository.findByProductIdWithLock(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            assertThat(inventoryService.reserveStock(PRODUCT_ID, quantity)).isTrue();
        }
    }

    // ==================== RELEASE RESERVATION ====================

    @Nested
    @DisplayName("releaseReservation()")
    class ReleaseReservationTests {

        @Test
        @DisplayName("Should release reservation and restore available stock")
        void shouldReleaseReservationSuccessfully() {
            Inventory inventory = buildInventory(7, 3);
            when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            inventoryService.releaseReservation(PRODUCT_ID, 3);

            assertThat(inventory.getQuantityAvailable()).isEqualTo(10);
            assertThat(inventory.getQuantityReserved()).isEqualTo(0);
        }

        @Test
        @DisplayName("Should not allow reserved count to go below 0")
        void shouldNotAllowNegativeReservedCount() {
            Inventory inventory = buildInventory(5, 1);
            when(inventoryRepository.findByProductId(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            // Try to release more than reserved
            inventoryService.releaseReservation(PRODUCT_ID, 10);

            assertThat(inventory.getQuantityReserved()).isEqualTo(0); // clamped to 0
        }

        @Test
        @DisplayName("Should do nothing when product not found in inventory")
        void shouldDoNothingWhenProductNotFound() {
            when(inventoryRepository.findByProductId("ghost"))
                .thenReturn(Optional.empty());

            assertThatCode(() -> inventoryService.releaseReservation("ghost", 5))
                .doesNotThrowAnyException();

            verify(inventoryRepository, never()).save(any());
        }
    }

    // ==================== DEDUCT STOCK ====================

    @Nested
    @DisplayName("deductStock()")
    class DeductStockTests {

        @Test
        @DisplayName("Should deduct from reserved quantity after payment")
        void shouldDeductFromReservedStock() {
            Inventory inventory = buildInventory(7, 3);
            when(inventoryRepository.findByProductIdWithLock(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            inventoryService.deductStock(PRODUCT_ID, 3);

            assertThat(inventory.getQuantityReserved()).isEqualTo(0);
            verify(inventoryRepository).save(inventory);
        }

        @Test
        @DisplayName("Should log low stock warning when stock is below threshold")
        void shouldDeductStockWithLowStockWarning() {
            // threshold = 5, available = 4 → low stock
            Inventory inventory = buildInventory(4, 2);
            when(inventoryRepository.findByProductIdWithLock(PRODUCT_ID))
                .thenReturn(Optional.of(inventory));
            when(inventoryRepository.save(any())).thenReturn(inventory);

            // Should complete without throwing (warning is logged)
            assertThatCode(() -> inventoryService.deductStock(PRODUCT_ID, 2))
                .doesNotThrowAnyException();
        }
    }

    // ==================== LOW STOCK ====================

    @Test
    @DisplayName("getLowStockItems() should return only items below threshold")
    void shouldReturnOnlyLowStockItems() {
        Inventory normalStock = buildInventory(20, 0);   // 20 > threshold(5) → not low
        Inventory lowStock1 = buildInventory(3, 0);      // 3 < threshold(5) → low
        Inventory lowStock2 = buildInventory(1, 2);      // 1 < threshold(5) → low

        when(inventoryRepository.findAll())
            .thenReturn(List.of(normalStock, lowStock1, lowStock2));

        List<Inventory> result = inventoryService.getLowStockItems();

        assertThat(result).hasSize(2).doesNotContain(normalStock);
    }

    @Test
    @DisplayName("getLowStockItems() should return empty list when all items have sufficient stock")
    void shouldReturnEmptyListWhenNoLowStock() {
        when(inventoryRepository.findAll())
            .thenReturn(List.of(buildInventory(100, 0), buildInventory(50, 5)));

        assertThat(inventoryService.getLowStockItems()).isEmpty();
    }
}
