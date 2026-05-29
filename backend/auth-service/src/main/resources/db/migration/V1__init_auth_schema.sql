-- ============================================================
-- AUTH SERVICE - V1 Initial Schema
-- ============================================================
-- Migration: V1__init_auth_schema.sql
-- Description: Creates the initial authentication schema:
--   - users          : User credentials and account state
--   - user_roles     : RBAC roles per user (join table)
--   - refresh_tokens : JWT refresh token store with rotation support
--
-- Naming convention: snake_case, plural table names
-- UUID primary keys for distributed system compatibility
-- ============================================================

-- Enable UUID extension (PostgreSQL)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================
-- TABLE: users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id                       VARCHAR(36)  NOT NULL DEFAULT gen_random_uuid()::text,
    email                    VARCHAR(255) NOT NULL,
    password                 VARCHAR(255) NOT NULL,          -- BCrypt hash
    first_name               VARCHAR(100) NOT NULL,
    last_name                VARCHAR(100) NOT NULL,
    enabled                  BOOLEAN      NOT NULL DEFAULT TRUE,
    account_non_expired      BOOLEAN      NOT NULL DEFAULT TRUE,
    credentials_non_expired  BOOLEAN      NOT NULL DEFAULT TRUE,
    account_non_locked       BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts    INT          NOT NULL DEFAULT 0,
    created_at               TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP,
    last_login_at            TIMESTAMP,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Index for fast login lookups
CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
CREATE INDEX IF NOT EXISTS idx_users_enabled ON users (enabled);

-- ============================================================
-- TABLE: user_roles  (ElementCollection join table)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_roles (
    user_id  VARCHAR(36)  NOT NULL,
    role     VARCHAR(50)  NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles (user_id);

-- ============================================================
-- TABLE: refresh_tokens
-- ============================================================
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id           VARCHAR(36)  NOT NULL DEFAULT gen_random_uuid()::text,
    token        VARCHAR(512) NOT NULL,
    user_id      VARCHAR(36)  NOT NULL,
    expiry_date  TIMESTAMP    NOT NULL,
    revoked      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMP    DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token   ON refresh_tokens (token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_revoked ON refresh_tokens (revoked, expiry_date);

-- ============================================================
-- SEED: Default admin user  (password: Admin@123456)
-- BCrypt hash of "Admin@123456" with strength 12
-- ============================================================
INSERT INTO users (id, email, password, first_name, last_name, enabled)
VALUES (
    'admin-00000000-0000-0000-0000-000000000001',
    'admin@amazondemo.com',
    '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewdBPj3bp.Gm3wCW',
    'Admin',
    'User',
    TRUE
) ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role)
VALUES (
    'admin-00000000-0000-0000-0000-000000000001',
    'ROLE_ADMIN'
) ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role)
VALUES (
    'admin-00000000-0000-0000-0000-000000000001',
    'ROLE_USER'
) ON CONFLICT DO NOTHING;
