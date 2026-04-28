-- Payments Schema

CREATE TABLE IF NOT EXISTS payments.payments (
    id                      UUID            PRIMARY KEY,
    claim_id                UUID            NOT NULL,
    customer_id             VARCHAR(100)    NOT NULL,
    amount                  DECIMAL(12,2)   NOT NULL,
    currency                VARCHAR(10)     NOT NULL DEFAULT 'USD',
    status                  VARCHAR(20)     NOT NULL,
    gateway_transaction_id  VARCHAR(100),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    settled_at              TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_payments_claim_id
    ON payments.payments(claim_id);

CREATE INDEX IF NOT EXISTS idx_payments_customer_id
    ON payments.payments(customer_id);
