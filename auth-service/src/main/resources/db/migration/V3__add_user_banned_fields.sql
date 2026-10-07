ALTER TABLE auth_users
ADD COLUMN is_banned BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN banned_at TIMESTAMPTZ,
ADD COLUMN ban_reason VARCHAR(255);

CREATE INDEX idx_auth_users_is_banned ON auth_users(is_banned);
