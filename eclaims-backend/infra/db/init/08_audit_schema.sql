-- Audit Schema — Append-Only
-- RULE: No UPDATE or DELETE on this table — ever.
-- 7-year retention required for insurance regulatory compliance.
-- oldValue/newValue are JSON snapshots for change tracking and fraud investigation.

CREATE TABLE IF NOT EXISTS audit.audit_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(100) NOT NULL UNIQUE,    -- Kafka eventId for dedup
    correlation_id  VARCHAR(100),                    -- ties to originating HTTP request
    user_id         VARCHAR(100),                    -- who performed the action
    user_role       VARCHAR(50),                     -- role at time of action
    action          VARCHAR(100) NOT NULL,           -- e.g. CLAIM_SUBMITTED, STATUS_CHANGED
    entity_type     VARCHAR(50)  NOT NULL,           -- Claim, Payment, Workshop
    entity_id       VARCHAR(100) NOT NULL,           -- the aggregate ID
    old_value       TEXT,                            -- JSON snapshot of previous state
    new_value       TEXT,                            -- JSON snapshot of new state
    ip_address      VARCHAR(50),                     -- for fraud investigation
    user_agent      VARCHAR(500),                    -- browser/client type
    session_id      VARCHAR(200),                    -- Keycloak session ID
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Performance indexes for compliance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_entity
    ON audit.audit_log(entity_type, entity_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_user_time
    ON audit.audit_log(user_id, occurred_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_action_time
    ON audit.audit_log(action, occurred_at DESC);

-- Revoke UPDATE and DELETE on audit log — append-only enforcement at DB level
-- (Run as superuser after schema creation)
-- REVOKE UPDATE, DELETE ON audit.audit_log FROM eclaims;
-- GRANT INSERT, SELECT ON audit.audit_log TO eclaims;

COMMENT ON TABLE audit.audit_log IS
    'Append-only immutable audit trail. 7-year retention. No UPDATE or DELETE permitted.';
