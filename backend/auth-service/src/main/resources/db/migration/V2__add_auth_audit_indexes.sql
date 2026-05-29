-- ============================================================
-- AUTH SERVICE - V2 Audit & Performance Indexes
-- ============================================================
-- Migration: V2__add_auth_audit_indexes.sql
-- Description: Adds indexes for audit queries and security monitoring
-- ============================================================

-- Composite index for account lockout queries
CREATE INDEX IF NOT EXISTS idx_users_lock_check
    ON users (account_non_locked, failed_login_attempts)
    WHERE enabled = TRUE;

-- Partial index: only active non-expired refresh tokens
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_active
    ON refresh_tokens (user_id, expiry_date)
    WHERE revoked = FALSE;

-- Index for cleanup job (purge expired tokens)
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expiry
    ON refresh_tokens (expiry_date)
    WHERE revoked = FALSE;

-- Add comment metadata for documentation
COMMENT ON TABLE users IS 'User authentication credentials managed by auth-service';
COMMENT ON TABLE user_roles IS 'RBAC roles assigned to users - supports multiple roles per user';
COMMENT ON TABLE refresh_tokens IS 'JWT refresh token store with rotation - revoked on use';

COMMENT ON COLUMN users.failed_login_attempts IS 'Incremented on wrong password, reset on success. Account locked at 5.';
COMMENT ON COLUMN users.account_non_locked IS 'FALSE when failed_login_attempts >= 5';
COMMENT ON COLUMN refresh_tokens.revoked IS 'Set TRUE on token rotation or explicit logout';
