-- ============================================================
-- ORDER SERVICE - V3 Idempotency Key
-- ============================================================
-- Repeated order creation requests with same Idempotency-Key
-- must return the originally created order instead of creating
-- duplicate rows.

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_idempotency_key
    ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

