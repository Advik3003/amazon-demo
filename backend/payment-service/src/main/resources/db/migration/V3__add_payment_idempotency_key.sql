-- ============================================================
-- PAYMENT SERVICE - V3 Idempotency Key
-- ============================================================
-- Repeated payment requests with same Idempotency-Key
-- must return the original payment result.

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payments_idempotency_key
    ON payments (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

