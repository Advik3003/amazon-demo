-- ============================================================
-- PAYMENT SERVICE - V2 Refund Management
-- ============================================================
-- Migration: V2__add_payment_refunds.sql
-- Description: Adds refund tracking table
-- ============================================================

-- ============================================================
-- TABLE: refunds
-- ============================================================
CREATE TABLE IF NOT EXISTS refunds (
    id                VARCHAR(36)    NOT NULL DEFAULT gen_random_uuid()::text,
    payment_id        VARCHAR(36)    NOT NULL,
    order_id          VARCHAR(36)    NOT NULL,
    amount            DECIMAL(10,2)  NOT NULL,
    reason            TEXT,
    status            VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    refund_reference  VARCHAR(255),            -- Gateway refund transaction ID
    requested_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    processed_at      TIMESTAMP,
    processed_by      VARCHAR(100),

    CONSTRAINT pk_refunds            PRIMARY KEY (id),
    CONSTRAINT fk_refunds_payment    FOREIGN KEY (payment_id)
        REFERENCES payments (id),
    CONSTRAINT chk_refund_status     CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    CONSTRAINT chk_refund_amount     CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_refunds_payment_id ON refunds (payment_id);
CREATE INDEX IF NOT EXISTS idx_refunds_order_id   ON refunds (order_id);
CREATE INDEX IF NOT EXISTS idx_refunds_status     ON refunds (status, requested_at DESC);

COMMENT ON TABLE refunds IS 'Refund requests linked to successful payments';
COMMENT ON COLUMN refunds.refund_reference IS 'External gateway refund transaction ID for reconciliation';
