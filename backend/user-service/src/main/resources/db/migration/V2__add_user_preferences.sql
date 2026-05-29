-- ============================================================
-- USER SERVICE - V2 User Preferences & Wishlist
-- ============================================================
-- Migration: V2__add_user_preferences.sql
-- Description: Adds tables for user preferences and wishlist
-- ============================================================

-- ============================================================
-- TABLE: user_preferences  (notification & marketing settings)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id                   VARCHAR(36)  NOT NULL,
    email_order_updates       BOOLEAN NOT NULL DEFAULT TRUE,
    email_promotions          BOOLEAN NOT NULL DEFAULT FALSE,
    email_newsletters         BOOLEAN NOT NULL DEFAULT FALSE,
    sms_order_updates         BOOLEAN NOT NULL DEFAULT FALSE,
    push_notifications        BOOLEAN NOT NULL DEFAULT TRUE,
    preferred_language        VARCHAR(10)  DEFAULT 'en',
    preferred_currency        VARCHAR(10)  DEFAULT 'USD',
    updated_at                TIMESTAMP    DEFAULT NOW(),

    CONSTRAINT pk_user_preferences   PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preferences   FOREIGN KEY (user_id)
        REFERENCES user_profiles (id) ON DELETE CASCADE
);

-- ============================================================
-- TABLE: wishlists
-- ============================================================
CREATE TABLE IF NOT EXISTS wishlists (
    id           VARCHAR(36)  NOT NULL DEFAULT gen_random_uuid()::text,
    user_id      VARCHAR(36)  NOT NULL,
    product_id   VARCHAR(36)  NOT NULL,
    added_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_wishlists        PRIMARY KEY (id),
    CONSTRAINT uq_wishlist_item    UNIQUE (user_id, product_id),  -- No duplicates
    CONSTRAINT fk_wishlists_user   FOREIGN KEY (user_id)
        REFERENCES user_profiles (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_wishlists_user_id   ON wishlists (user_id, added_at DESC);
CREATE INDEX IF NOT EXISTS idx_wishlists_product   ON wishlists (product_id);

COMMENT ON TABLE user_preferences IS 'Per-user notification and locale preferences';
COMMENT ON TABLE wishlists IS 'Saved product wishlist - one row per user-product pair';
