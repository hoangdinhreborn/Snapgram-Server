CREATE TABLE auth_email_verifications (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,           -- SHA-256 of raw token
    new_email    VARCHAR(255),                           -- NULL = verify current; set = pending email change
    expires_at   TIMESTAMPTZ  NOT NULL,                  -- 24-hour TTL
    used_at      TIMESTAMPTZ,                            -- NULL = unused; timestamp = consumed (single-use)
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_auth_email_ver_user_id    ON auth_email_verifications(user_id);
CREATE INDEX idx_auth_email_ver_token_hash ON auth_email_verifications(token_hash);
CREATE INDEX idx_auth_email_ver_expires_at ON auth_email_verifications(expires_at);
CREATE INDEX idx_auth_email_ver_used_at    ON auth_email_verifications(used_at);

-- ─── auth_password_resets ─────────────────────────────────────
-- Short-lived (15 min) single-use tokens for password reset flow.
-- ip_address stored for audit / rate-limit abuse detection.
-- ─────────────────────────────────────────────────────────────
CREATE TABLE auth_password_resets (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,           -- SHA-256 of raw token
    expires_at   TIMESTAMPTZ  NOT NULL,                  -- 15-minute TTL
    used_at      TIMESTAMPTZ,                            -- NULL = unused; timestamp = consumed (single-use)
    ip_address   VARCHAR(45),                            -- IPv4 or IPv6 of requester
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_auth_pw_reset_user_id    ON auth_password_resets(user_id);
CREATE INDEX idx_auth_pw_reset_token_hash ON auth_password_resets(token_hash);
CREATE INDEX idx_auth_pw_reset_expires_at ON auth_password_resets(expires_at);
CREATE INDEX idx_auth_pw_reset_used_at    ON auth_password_resets(used_at);
