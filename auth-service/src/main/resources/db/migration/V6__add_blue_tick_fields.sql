-- Blue Tick (verified badge) cap qua event payment.completed.
-- Cong don thoi han: verified_until = GREATEST(now, verified_until) + duration.

ALTER TABLE auth_users
    ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS verified_until TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_auth_users_verified_until
    ON auth_users(verified_until) WHERE is_verified = TRUE;

-- Idempotency cho consumer payment.completed (eventId stable tu payment-service).
CREATE TABLE IF NOT EXISTS auth_payment_processed_events (
    event_id VARCHAR(128) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    product_code VARCHAR(64) NOT NULL,
    granted_until TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auth_payment_processed_user_id
    ON auth_payment_processed_events(user_id);
