package com.amazondemo.inventory.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Inventory Entity
 * =================
 * Tracks stock levels for each product.
 *
 * STOCK MANAGEMENT:
 * - quantityAvailable: Current available stock
 * - quantityReserved: Stock reserved for pending orders (not yet deducted)
 * - quantityOnHand = quantityAvailable + quantityReserved
 *
 * RESERVATION FLOW:
 * 1. Order placed -> reserve stock (available decreases, reserved increases)
 * 2. Payment confirmed -> release reservation (reserved decreases, stock is "sold")
 * 3. Order cancelled -> release reservation (reserved decreases, available increases)
 */
@Entity
@Table(name = "inventory",
        uniqueConstraints = @UniqueConstraint(columnNames = "productId"))
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String productId;

    @Builder.Default
    private int quantityAvailable = 0;

    @Builder.Default
    private int quantityReserved = 0;

    @Builder.Default
    private int lowStockThreshold = 10;

    @Builder.Default
    private boolean trackInventory = true;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /** Computed: is stock available for ordering? */
    public boolean isInStock() {
        return !trackInventory || quantityAvailable > 0;
    }

    /** Computed: is stock running low? */
    public boolean isLowStock() {
        return quantityAvailable <= lowStockThreshold;
    }
}
