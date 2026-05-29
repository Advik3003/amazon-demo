-- ============================================================
-- USER SERVICE - V1 Initial Schema
-- ============================================================
-- Migration: V1__init_user_schema.sql
-- Description: Creates user profile management schema:
--   - user_profiles : Rich profile data (extends auth-service users)
--   - addresses     : Multiple shipping/billing addresses per user
--
-- Note: user_profiles.id IS the same UUID as auth-service users.id
-- This is an intentional cross-service ID sharing pattern.
-- ============================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TABLE: user_profiles
-- ============================================================
CREATE TABLE IF NOT EXISTS user_profiles (
    id                  VARCHAR(36)  NOT NULL,            -- Same ID as auth-service user
    email               VARCHAR(255) NOT NULL,
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    phone_number        VARCHAR(30),
    profile_image_url   VARCHAR(1000),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP,

    CONSTRAINT pk_user_profiles        PRIMARY KEY (id),
    CONSTRAINT uq_user_profiles_email  UNIQUE (email),
    CONSTRAINT chk_user_profiles_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED'))
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_email  ON user_profiles (email);
CREATE INDEX IF NOT EXISTS idx_user_profiles_status ON user_profiles (status);

-- ============================================================
-- TABLE: addresses
-- ============================================================
CREATE TABLE IF NOT EXISTS addresses (
    id            VARCHAR(36)  NOT NULL DEFAULT gen_random_uuid()::text,
    user_id       VARCHAR(36)  NOT NULL,
    full_name     VARCHAR(200) NOT NULL,
    street        VARCHAR(500) NOT NULL,
    apartment     VARCHAR(200),
    city          VARCHAR(100) NOT NULL,
    state         VARCHAR(100) NOT NULL,
    zip_code      VARCHAR(20)  NOT NULL,
    country       VARCHAR(100) NOT NULL,
    phone_number  VARCHAR(30),
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    type          VARCHAR(20)  NOT NULL DEFAULT 'HOME',

    CONSTRAINT pk_addresses           PRIMARY KEY (id),
    CONSTRAINT fk_addresses_user      FOREIGN KEY (user_id)
        REFERENCES user_profiles (id) ON DELETE CASCADE,
    CONSTRAINT chk_address_type       CHECK (type IN ('HOME','WORK','OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_addresses_user_id   ON addresses (user_id);
CREATE INDEX IF NOT EXISTS idx_addresses_default   ON addresses (user_id)
    WHERE is_default = TRUE;

-- ============================================================
-- Comments
-- ============================================================
COMMENT ON TABLE user_profiles IS 'Rich user profile - bounded context of user-service, shares ID with auth-service';
COMMENT ON TABLE addresses IS 'Multiple addresses per user; is_default is the pre-selected shipping address';
COMMENT ON COLUMN user_profiles.id IS 'Intentionally same UUID as auth-service users.id - cross-service identity';
COMMENT ON COLUMN addresses.is_default IS 'Pre-selected at checkout. Only one default per user enforced at application layer.';
