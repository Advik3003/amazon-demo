-- ============================================================
-- ORDER SERVICE - V1 Initial Schema
-- ============================================================
-- Migration: V1__init_order_schema.sql
-- Description: Creates the order management schema:
--   - orders       : Order header with status and shipping info
--   - order_items  : Line items per order (products ordered)
--
-- Design decisions:
--   - Shipping address is SNAPSHOTTED (not FK to address table)
--     so address changes don't affect historical orders
--   - Status and PaymentStatus are VARCHAR (enum stored as string)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TABLE: orders
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
    id                   VARCHAR(36)    NOT NULL DEFAULT gen_random_uuid()::text,
    order_number         VARCHAR(50)    NOT NULL,           -- Human-readable: ORD-2026-001234
    user_id              VARCHAR(36)    NOT NULL,
    user_email           VARCHAR(255),

    -- Financials
    subtotal             DECIMAL(10,2)  NOT NULL,
    tax_amount           DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    shipping_cost        DECIMAL(10,2)  NOT NULL DEFAULT 0.00,
    total_amount         DECIMAL(10,2)  NOT NULL,

    -- Status
    status               VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    payment_status       VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    payment_id           VARCHAR(255),

    -- Shipping address snapshot
    shipping_full_name   VARCHAR(200),
    shipping_street      VARCHAR(500),
    shipping_city        VARCHAR(100),
    shipping_state       VARCHAR(100),
    shipping_zip_code    VARCHAR(20),
    shipping_country     VARCHAR(100),

    -- Metadata
    notes                TEXT,
    cancellation_reason  TEXT,

    -- Timestamps
    created_at           TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP,
    confirmed_at         TIMESTAMP,
    shipped_at           TIMESTAMP,
    delivered_at         TIMESTAMP,
    cancelled_at         TIMESTAMP,

    CONSTRAINT pk_orders             PRIMARY KEY (id),
    CONSTRAINT uq_orders_number      UNIQUE (order_number),
    CONSTRAINT chk_orders_status     CHECK (status IN
        ('PENDING','CONFIRMED','PROCESSING','SHIPPED','DELIVERED','CANCELLED','REFUNDED')),
    CONSTRAINT chk_orders_pay_status CHECK (payment_status IN
        ('PENDING','PAID','FAILED','REFUNDED')),
    CONSTRAINT chk_orders_total      CHECK (total_amount >= 0)
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id    ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_status     ON orders (status);
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_number     ON orders (order_number);
-- Composite: user orders sorted by date (most common query)
CREATE INDEX IF NOT EXISTS idx_orders_user_date  ON orders (user_id, created_at DESC);

-- ============================================================
-- TABLE: order_items
-- ============================================================
CREATE TABLE IF NOT EXISTS order_items (
    id                VARCHAR(36)   NOT NULL DEFAULT gen_random_uuid()::text,
    order_id          VARCHAR(36)   NOT NULL,
    product_id        VARCHAR(36)   NOT NULL,
    product_name      VARCHAR(255),
    product_image_url VARCHAR(1000),
    quantity          INT           NOT NULL,
    unit_price        DECIMAL(10,2) NOT NULL,
    total_price       DECIMAL(10,2) NOT NULL,

    CONSTRAINT pk_order_items         PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_qty    CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price  CHECK (unit_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_order_items_order_id   ON order_items (order_id);
CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items (product_id);

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE orders IS 'Order headers - ACID compliant, immutable shipping snapshot';
COMMENT ON TABLE order_items IS 'Line items per order - quantities and prices at time of purchase';
COMMENT ON COLUMN orders.status IS 'PENDING->CONFIRMED->PROCESSING->SHIPPED->DELIVERED | CANCELLED | REFUNDED';
COMMENT ON COLUMN orders.order_number IS 'Human-readable format: ORD-YYYY-NNNNNN';
