-- ============================================================
-- INVENTORY SERVICE - V1 Initial Schema
-- ============================================================
-- Migration: V1__init_inventory_schema.sql
-- Description: Creates the inventory management schema:
--   - inventory : Stock levels per product with reservation support
--
-- Key design:
--   - quantity_available : Can be ordered NOW
--   - quantity_reserved  : Reserved for pending orders (not yet deducted)
--   - quantity_on_hand   = available + reserved (total physical stock)
--   - Pessimistic locking used on reservation writes (SELECT FOR UPDATE)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TABLE: inventory
-- ============================================================
CREATE TABLE IF NOT EXISTS inventory (
    id                   VARCHAR(36)  NOT NULL DEFAULT gen_random_uuid()::text,
    product_id           VARCHAR(36)  NOT NULL,
    quantity_available   INT          NOT NULL DEFAULT 0,
    quantity_reserved    INT          NOT NULL DEFAULT 0,
    low_stock_threshold  INT          NOT NULL DEFAULT 10,   -- Alert when available <= this
    track_inventory      BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at           TIMESTAMP,

    CONSTRAINT pk_inventory              PRIMARY KEY (id),
    CONSTRAINT uq_inventory_product_id   UNIQUE (product_id),
    CONSTRAINT chk_inventory_available   CHECK (quantity_available >= 0),
    CONSTRAINT chk_inventory_reserved    CHECK (quantity_reserved >= 0),
    CONSTRAINT chk_inventory_threshold   CHECK (low_stock_threshold >= 0)
);

-- Fast lookups by product ID (most frequent query)
CREATE INDEX IF NOT EXISTS idx_inventory_product_id    ON inventory (product_id);
-- Alert queries: find all low-stock items
CREATE INDEX IF NOT EXISTS idx_inventory_low_stock     ON inventory (quantity_available, low_stock_threshold);
-- Partial: track only managed inventory
CREATE INDEX IF NOT EXISTS idx_inventory_tracked       ON inventory (product_id)
    WHERE track_inventory = TRUE;

-- ============================================================
-- SEED: Sample inventory data for demo purposes
-- ============================================================
INSERT INTO inventory (product_id, quantity_available, quantity_reserved, low_stock_threshold)
VALUES
    ('demo-prod-001', 100, 0, 10),
    ('demo-prod-002', 50,  0, 5),
    ('demo-prod-003', 0,   0, 10)  -- Out of stock demo item
ON CONFLICT (product_id) DO NOTHING;

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE inventory IS 'Stock level tracking per product with reservation support for concurrent orders';
COMMENT ON COLUMN inventory.quantity_available IS 'Stock available for new orders (pessimistic locked on write)';
COMMENT ON COLUMN inventory.quantity_reserved IS 'Stock reserved for pending (unpaid) orders';
COMMENT ON COLUMN inventory.low_stock_threshold IS 'Triggers LOW_STOCK_ALERT Kafka event when available drops to this level';
COMMENT ON COLUMN inventory.track_inventory IS 'FALSE = unlimited stock (digital/service products)';
