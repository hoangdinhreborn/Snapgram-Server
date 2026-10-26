CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    actor_id UUID,
    target_type VARCHAR(50),
    target_id VARCHAR(255),
    payload JSONB NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_schema = CURRENT_SCHEMA() AND table_name = 'notification_notifications'
    ) THEN
        INSERT INTO notifications (id, user_id, type, actor_id, target_type, target_id, payload, is_read, created_at)
        SELECT
            id,
            user_id,
            type,
            actor_id,
            target_type,
            target_id::VARCHAR(255),
            COALESCE(payload, '{}'::jsonb),
            COALESCE(is_read, FALSE),
            COALESCE(created_at, NOW())
        FROM notification_notifications
        ON CONFLICT (id) DO NOTHING;

        DROP TABLE notification_notifications CASCADE;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(500) NOT NULL,
    platform VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_schema = CURRENT_SCHEMA() AND table_name = 'notification_device_tokens'
    ) THEN
        INSERT INTO device_tokens (id, user_id, token, platform, last_seen_at, created_at)
        SELECT
            id,
            user_id,
            token,
            platform,
            COALESCE(last_seen_at, NOW()),
            COALESCE(created_at, NOW())
        FROM notification_device_tokens
        ON CONFLICT (id) DO NOTHING;

        DROP TABLE notification_device_tokens CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read
    ON notifications (user_id, is_read);

CREATE INDEX IF NOT EXISTS idx_notifications_target
    ON notifications (target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_device_tokens_user
    ON device_tokens (user_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_tokens_user_token
    ON device_tokens (user_id, token);
