CREATE TABLE notification_notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    actor_id UUID,
    target_type VARCHAR(50),
    target_id UUID,
    payload JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_user_id ON notification_notifications(user_id);
CREATE INDEX idx_notification_user_read ON notification_notifications(user_id, is_read);
CREATE INDEX idx_notification_created_at ON notification_notifications(created_at);

CREATE TABLE notification_device_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    platform VARCHAR(20) NOT NULL,
    last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_notification_device_tokens_user_id ON notification_device_tokens(user_id);
