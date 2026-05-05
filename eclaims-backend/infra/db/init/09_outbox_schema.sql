-- Transactional Outbox Tables
-- Claims outbox: atomically written in the same DB transaction as the domain aggregate save.
-- OutboxRelayService polls unpublished rows and publishes to Kafka, then marks published.
--
-- SKIP LOCKED on SELECT ensures multiple relay instances never double-process the same event.
-- 7-day retention: published events older than 7 days are purged by ClaimDataRetentionService.

CREATE TABLE IF NOT EXISTS claims.outbox_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(100) NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,   -- JSON-serialised DomainEvent<T>
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    published       BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Partial index — only on unpublished rows; after publish the row drops off this index.
CREATE INDEX IF NOT EXISTS idx_claims_outbox_unpublished
    ON claims.outbox_events(created_at ASC)
    WHERE published = FALSE;

-- Payments outbox: same pattern for the payments module.
CREATE TABLE IF NOT EXISTS payments.outbox_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    VARCHAR(100) NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    published       BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_payments_outbox_unpublished
    ON payments.outbox_events(created_at ASC)
    WHERE published = FALSE;
