-- Claims Schema
-- All monetary values: DECIMAL(12,2) — never FLOAT/DOUBLE
-- All IDs: UUID — globally unique, microservice-safe
-- All statuses: VARCHAR enum — never integer codes
-- Timestamps: managed by @PrePersist/@PreUpdate in JPA entity

CREATE TABLE IF NOT EXISTS claims.claims (
    id                      UUID PRIMARY KEY,
    policy_number           VARCHAR(20)     NOT NULL,
    customer_id             VARCHAR(100)    NOT NULL,
    customer_email          VARCHAR(255)    NOT NULL,
    customer_phone          VARCHAR(20),
    vehicle_registration    VARCHAR(20)     NOT NULL,
    claim_type              VARCHAR(30)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL DEFAULT 'SUBMITTED',
    incident_date           DATE            NOT NULL,
    incident_location       VARCHAR(500),
    description             VARCHAR(2000),
    police_report_filed     BOOLEAN         NOT NULL DEFAULT FALSE,
    police_report_number    VARCHAR(50),
    assigned_surveyor_id    VARCHAR(100),
    assigned_adjustor_id    VARCHAR(100),
    assessed_amount         DECIMAL(12,2),
    approved_amount         DECIMAL(12,2),
    workshop_id             VARCHAR(100),
    rejection_reason        VARCHAR(1000),
    fraud_flag              BOOLEAN         NOT NULL DEFAULT FALSE,
    fraud_reason            VARCHAR(500),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Natural key uniqueness — idempotent claim creation
ALTER TABLE claims.claims
    ADD CONSTRAINT IF NOT EXISTS uq_claim_natural_key
    UNIQUE (policy_number, incident_date, vehicle_registration);

-- Performance indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_customer_id
    ON claims.claims(customer_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_status_date
    ON claims.claims(status, created_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_policy_number
    ON claims.claims(policy_number);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_natural_key
    ON claims.claims(policy_number, incident_date, vehicle_registration);

-- Partial index for assigned surveyor queue — only for ASSIGNED status
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_assigned_surveyor
    ON claims.claims(assigned_surveyor_id)
    WHERE status = 'ASSIGNED';

-- Claim status history for audit trail
CREATE TABLE IF NOT EXISTS claims.claim_history (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID        NOT NULL REFERENCES claims.claims(id),
    previous_status VARCHAR(30),
    new_status      VARCHAR(30) NOT NULL,
    changed_by      VARCHAR(100),
    change_reason   VARCHAR(500),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_claim_history_claim_id
    ON claims.claim_history(claim_id);

-- Seed test policies cache (mirrors PolicyServiceStubAdapter)
CREATE TABLE IF NOT EXISTS claims.policy_cache (
    policy_number       VARCHAR(20)     PRIMARY KEY,
    customer_id         VARCHAR(100)    NOT NULL,
    customer_email      VARCHAR(255)    NOT NULL,
    coverage_type       VARCHAR(50),
    policy_start_date   DATE,
    policy_end_date     DATE,
    cached_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
