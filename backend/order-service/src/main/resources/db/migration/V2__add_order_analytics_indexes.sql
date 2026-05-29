-- ============================================================
-- ORDER SERVICE - V2 Analytics & Performance Indexes
-- ============================================================
-- Migration: V2__add_order_analytics_indexes.sql
-- Description: Adds indexes for analytics dashboards and reporting
-- ============================================================

-- Partial indexes for active order monitoring
CREATE INDEX IF NOT EXISTS idx_orders_active
    ON orders (user_id, created_at DESC)
    WHERE status NOT IN ('CANCELLED', 'REFUNDED');

-- Index for payment reconciliation queries
CREATE INDEX IF NOT EXISTS idx_orders_payment_pending
    ON orders (created_at)
    WHERE payment_status = 'PENDING';

-- Index for shipping department queries (fulfillment)
CREATE INDEX IF NOT EXISTS idx_orders_shipping
    ON orders (created_at ASC)
    WHERE status IN ('CONFIRMED', 'PROCESSING');

-- Daily revenue reporting index
CREATE INDEX IF NOT EXISTS idx_orders_revenue_date
    ON orders (created_at, total_amount)
    WHERE status NOT IN ('CANCELLED', 'REFUNDED');

-- Admin order search by email
CREATE INDEX IF NOT EXISTS idx_orders_user_email ON orders (user_email);

-- Add table/column comments for documentation
COMMENT ON COLUMN orders.shipping_full_name IS 'Address snapshot - copied at order time, not linked to user address';
COMMENT ON COLUMN orders.tax_amount IS 'Calculated at 10% of subtotal at time of order';
COMMENT ON COLUMN orders.shipping_cost IS '0 for orders over $50, else $9.99';
COMMENT ON COLUMN order_items.total_price IS 'Denormalized: unit_price * quantity, stored for performance';
