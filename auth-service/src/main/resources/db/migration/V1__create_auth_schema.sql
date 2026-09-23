CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE TABLE auth_users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    avatar_url VARCHAR(500),
    bio TEXT,
    website VARCHAR(500),
    pronouns VARCHAR(50),
    category VARCHAR(100),
    is_private BOOLEAN NOT NULL DEFAULT FALSE,
    totp_secret VARCHAR(255),
    two_fa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    backup_codes_hash TEXT,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    show_activity_status BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_users_username_trgm ON auth_users USING GIN (username gin_trgm_ops);
CREATE INDEX idx_auth_users_display_name_trgm ON auth_users USING GIN (display_name gin_trgm_ops);

CREATE TABLE auth_user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_auth_user_roles_user_role UNIQUE (user_id, role)
);
CREATE INDEX idx_auth_user_roles_user_id ON auth_user_roles(user_id);

CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    jti VARCHAR(100) NOT NULL UNIQUE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auth_refresh_tokens_user_id ON auth_refresh_tokens(user_id);
CREATE INDEX idx_auth_refresh_tokens_expires_at ON auth_refresh_tokens(expires_at);
CREATE INDEX idx_auth_refresh_tokens_revoked_at ON auth_refresh_tokens(revoked_at);

CREATE TABLE auth_blocks (
    id UUID PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_auth_blocks_pair UNIQUE (blocker_id, blocked_id),
    CONSTRAINT ck_auth_blocks_not_self CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_auth_blocks_blocker_id ON auth_blocks(blocker_id);
CREATE INDEX idx_auth_blocks_blocked_id ON auth_blocks(blocked_id);

CREATE TABLE auth_notes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    content VARCHAR(60) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_auth_notes_user_id ON auth_notes(user_id);
CREATE INDEX idx_auth_notes_expires_at ON auth_notes(expires_at);
