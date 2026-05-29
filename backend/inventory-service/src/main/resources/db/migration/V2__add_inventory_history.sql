-- ============================================================
-- INVENTORY SERVICE - V2 Stock Movement History
-- ============================================================
-- Migration: V2__add_inventory_history.sql
-- Description: Adds audit trail for all inventory movements
--   Used for tracing stock discrepancies and analytics.
-- ============================================================

-- ============================================================
-- TABLE: inventory_movements
-- ============================================================
CREATE TABLE IF NOT EXISTS inventory_movements (
    id              VARCHAR(36)  NOT NULL DEFAULT gen_random_uuid()::text,
    product_id      VARCHAR(36)  NOT NULL,
    movement_type   VARCHAR(30)  NOT NULL, -- RESERVE, RELEASE, DEDUCT, RESTOCK, ADJUST
    quantity_change INT          NOT NULL, -- Positive = added, Negative = removed
    quantity_before INT          NOT NULL,
    quantity_after  INT          NOT NULL,
    reference_id    VARCHAR(36),           -- orderId, purchaseOrderId, etc.
    reference_type  VARCHAR(30),           -- ORDER, PURCHASE_ORDER, MANUAL_ADJUST
    notes           TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(100) DEFAULT 'SYSTEM',

    CONSTRAINT pk_inventory_movements PRIMARY KEY (id),
    CONSTRAINT chk_movement_type CHECK (movement_type IN
        ('RESERVE','RELEASE','DEDUCT','RESTOCK','ADJUST','INITIAL'))
);

CREATE INDEX IF NOT EXISTS idx_inv_movement_product  ON inventory_movements (product_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inv_movement_ref      ON inventory_movements (reference_id, reference_type);
CREATE INDEX IF NOT EXISTS idx_inv_movement_type     ON inventory_movements (movement_type, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_inv_movement_date     ON inventory_movements (created_at DESC);

-- ============================================================
-- SEED: Initial stock movement records for demo data
-- ============================================================
INSERT INTO inventory_movements
    (product_id, movement_type, quantity_change, quantity_before, quantity_after, notes, created_by)
VALUES
    ('demo-prod-001', 'INITIAL', 100, 0, 100, 'Initial stock setup', 'FLYWAY_SEED'),
    ('demo-prod-002', 'INITIAL', 50,  0, 50,  'Initial stock setup', 'FLYWAY_SEED'),
    ('demo-prod-003', 'INITIAL', 0,   0, 0,   'Product registered with zero stock', 'FLYWAY_SEED')
ON CONFLICT DO NOTHING;

COMMENT ON TABLE inventory_movements IS 'Immutable audit trail of all stock changes - append-only';
COMMENT ON COLUMN inventory_movements.quantity_change IS 'Positive for additions (RESTOCK/RELEASE), negative for reductions (RESERVE/DEDUCT)';
