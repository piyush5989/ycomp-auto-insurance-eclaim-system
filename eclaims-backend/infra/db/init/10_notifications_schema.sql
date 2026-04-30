-- Notifications Schema
-- Persists in-app notifications per customer.
-- Populated by Kafka consumers (claim-events, repair-events).
-- API: GET /api/v1/notifications/me  PATCH /api/v1/notifications/{id}/read

CREATE SCHEMA IF NOT EXISTS notifications;

CREATE TABLE IF NOT EXISTS notifications.customer_notifications (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(100)    NOT NULL,           -- Keycloak subject
    type        VARCHAR(50)     NOT NULL,            -- CLAIM_STATUS_CHANGED | REPAIR_STATUS_UPDATED | PAYMENT_CONFIRMED
    title       VARCHAR(200)    NOT NULL,
    message     VARCHAR(1000)   NOT NULL,
    claim_id    UUID,                                -- nullable — links to claims.claims for deeplink
    is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Primary query: unread notifications per customer ordered newest-first
CREATE INDEX IF NOT EXISTS idx_notifications_customer_unread
    ON notifications.customer_notifications(customer_id, is_read, created_at DESC);
