-- Outbox giao dich: event payment.completed duoc ghi cung transaction voi don hang,
-- sau do dispatcher gui Kafka va retry -> khong mat event khi Kafka loi.
CREATE TABLE payment_outbox (
    id UUID PRIMARY KEY,
    order_id VARCHAR(64) NOT NULL UNIQUE REFERENCES payment_orders(order_id) ON DELETE CASCADE,
    event_id VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lease_until TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMPTZ
);

CREATE INDEX idx_payment_outbox_dispatch
    ON payment_outbox (status, next_attempt_at, created_at);
