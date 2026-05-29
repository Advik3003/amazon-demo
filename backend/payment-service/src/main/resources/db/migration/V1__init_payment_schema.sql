-- ============================================================
-- PAYMENT SERVICE - V1 Initial Schema
-- ============================================================
-- Migration: V1__init_payment_schema.sql
-- Description: Creates the payment processing schema:
--   - payments : Payment transaction records (immutable audit trail)
--
-- Design notes:
--   - Payments are APPEND-ONLY (no UPDATE on existing records)
--   - Each payment attempt is a new row (retry = new record)
--   - This enables full audit trail and dispute resolution
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TABLE: payments
-- ============================================================
CREATE TABLE IF NOT EXISTS payments (
    id               VARCHAR(36)    NOT NULL DEFAULT gen_random_uuid()::text,
    order_id         VARCHAR(36)    NOT NULL,
    user_id          VARCHAR(36)    NOT NULL,
    amount           DECIMAL(10,2)  NOT NULL,
    currency         VARCHAR(10)    NOT NULL DEFAULT 'USD',
    payment_method   VARCHAR(30),
    status           VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    transaction_id   VARCHAR(255),             -- External payment gateway reference
    failure_reason   TEXT,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP,

    CONSTRAINT pk_payments              PRIMARY KEY (id),
    CONSTRAINT chk_payments_status      CHECK (status IN
        ('PENDING','PROCESSING','SUCCESS','FAILED','REFUNDED')),
    CONSTRAINT chk_payments_method      CHECK (payment_method IN
        ('CREDIT_CARD','DEBIT_CARD','PAYPAL','BANK_TRANSFER','COD') OR payment_method IS NULL),
    CONSTRAINT chk_payments_amount      CHECK (amount > 0),
    CONSTRAINT chk_payments_currency    CHECK (currency IN ('USD','EUR','GBP','INR'))
);

CREATE INDEX IF NOT EXISTS idx_payments_order_id      ON payments (order_id);
CREATE INDEX IF NOT EXISTS idx_payments_user_id       ON payments (user_id);
CREATE INDEX IF NOT EXISTS idx_payments_status        ON payments (status);
CREATE INDEX IF NOT EXISTS idx_payments_transaction   ON payments (transaction_id);
CREATE INDEX IF NOT EXISTS idx_payments_created_at    ON payments (created_at DESC);
-- Composite: user payment history
CREATE INDEX IF NOT EXISTS idx_payments_user_date     ON payments (user_id, created_at DESC);

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE payments IS 'Immutable payment audit trail - append-only, each attempt is a separate row';
COMMENT ON COLUMN payments.transaction_id IS 'External payment gateway reference (Stripe, PayPal, etc.)';
COMMENT ON COLUMN payments.status IS 'PENDING -> PROCESSING -> SUCCESS|FAILED; can become REFUNDED later';
