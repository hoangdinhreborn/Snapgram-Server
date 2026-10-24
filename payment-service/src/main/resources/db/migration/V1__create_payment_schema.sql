CREATE TABLE payment_orders (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    order_id VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL DEFAULT 'VNPAY',
    product_code VARCHAR(64) NOT NULL,
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(8) NOT NULL DEFAULT 'VND',
    order_info VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    vnpay_transaction_no VARCHAR(64),
    vnpay_response_code VARCHAR(8),
    vnpay_bank_code VARCHAR(32),
    vnpay_pay_date TIMESTAMPTZ,
    provider_transaction_no VARCHAR(64),
    completed_event_id VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expired_at TIMESTAMPTZ NOT NULL
);

-- provider_transaction_no nullable -> partial unique (Postgres coi NULL != NULL)
CREATE UNIQUE INDEX uq_payment_orders_provider_txn
    ON payment_orders(provider, provider_transaction_no)
    WHERE provider_transaction_no IS NOT NULL;

CREATE UNIQUE INDEX uq_payment_orders_completed_event_id
    ON payment_orders(completed_event_id)
    WHERE completed_event_id IS NOT NULL;

CREATE INDEX idx_payment_orders_user_id ON payment_orders(user_id);
CREATE INDEX idx_payment_orders_status ON payment_orders(status);
CREATE INDEX idx_payment_orders_expired_at ON payment_orders(expired_at) WHERE status = 'PENDING';

-- Idempotency IPN: moi callback xu ly thanh cong ghi 1 dong, event_id stable.
CREATE TABLE payment_event_log (
    id UUID PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    order_id VARCHAR(64) NOT NULL REFERENCES payment_orders(order_id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL DEFAULT 'VNPAY',
    response_code VARCHAR(8),
    transaction_no VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_event_log_order_id ON payment_event_log(order_id);
