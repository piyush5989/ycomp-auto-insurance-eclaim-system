-- eClaims Database Schema Initialization
-- Consolidated schemas and core tables for POC
-- Each schema corresponds to one bounded-context module.
-- Rule: A module's persistence layer may ONLY access its own schema.
-- Cross-module data access is via API calls or event consumption only.

-- ===== SCHEMAS =====
CREATE SCHEMA IF NOT EXISTS claims;
CREATE SCHEMA IF NOT EXISTS documents;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS workshops;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS customers;
CREATE SCHEMA IF NOT EXISTS notifications;

-- Confirm schemas created
SELECT schema_name FROM information_schema.schemata
WHERE schema_name IN ('claims','documents','workflow','workshops','payments','reporting','audit','customers','notifications');

-- ===== CLAIMS SCHEMA =====
-- All monetary values: DECIMAL(12,2) — never FLOAT/DOUBLE
-- All IDs: UUID — globally unique, microservice-safe
-- All statuses: VARCHAR enum — never integer codes
-- Timestamps: managed by @PrePersist/@PreUpdate in JPA entity

CREATE TABLE IF NOT EXISTS claims.claims (
    id                      UUID PRIMARY KEY,
    policy_number           VARCHAR(20)     NOT NULL,
    customer_id             VARCHAR(100)    NOT NULL,
    customer_email          VARCHAR(255)    NOT NULL,
    vehicle_registration    VARCHAR(20)     NOT NULL,
    idempotency_key         VARCHAR(120),
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

-- Performance indexes
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_customer_id
    ON claims.claims(customer_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_status_date
    ON claims.claims(status, created_at);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_policy_number
    ON claims.claims(policy_number);

-- Idempotency key uniqueness (only when key provided)
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_claims_idempotency_key
    ON claims.claims(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

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

-- ===== DOCUMENTS SCHEMA =====
CREATE TABLE IF NOT EXISTS documents.documents (
    id                  UUID            PRIMARY KEY,
    claim_id            UUID            NOT NULL,
    document_type       VARCHAR(40)     NOT NULL,
    filename            VARCHAR(255)    NOT NULL,
    content_type        VARCHAR(100),
    file_size_bytes     BIGINT,
    storage_key         VARCHAR(500)    NOT NULL,
    uploaded_by_user_id VARCHAR(100)    NOT NULL,
    uploaded_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    archived            BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_claim_id
    ON documents.documents(claim_id);

CREATE INDEX IF NOT EXISTS idx_documents_type
    ON documents.documents(document_type);

-- ===== WORKFLOW SCHEMA =====
CREATE TABLE IF NOT EXISTS workflow.surveyors (
    id      UUID        PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    email   VARCHAR(255) NOT NULL,
    phone   VARCHAR(20),
    region  VARCHAR(50),
    active  BOOLEAN     NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS workflow.assignments (
    id              UUID        PRIMARY KEY,
    claim_id        UUID        NOT NULL,
    surveyor_id     UUID        NOT NULL REFERENCES workflow.surveyors(id),
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active          BOOLEAN     NOT NULL DEFAULT TRUE,
    correlation_id  VARCHAR(100)
);

CREATE INDEX IF NOT EXISTS idx_assignments_claim_id
    ON workflow.assignments(claim_id);

CREATE INDEX IF NOT EXISTS idx_assignments_surveyor_active
    ON workflow.assignments(surveyor_id) WHERE active = TRUE;

-- Workflow instances for escalation tracking
CREATE TABLE IF NOT EXISTS workflow.workflow_instances (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID        NOT NULL,
    current_step    VARCHAR(50) NOT NULL,
    escalated       BOOLEAN     NOT NULL DEFAULT FALSE,
    escalation_reason VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Adjustors for claim adjudication
CREATE TABLE IF NOT EXISTS workflow.adjustors (
    id            UUID            PRIMARY KEY,
    name          VARCHAR(100)    NOT NULL,
    email         VARCHAR(255)    NOT NULL,
    phone         VARCHAR(20),
    region        VARCHAR(50),
    field_office  VARCHAR(100),
    service_areas TEXT,
    active        BOOLEAN         NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_adjustors_region_active
    ON workflow.adjustors(region, active) WHERE active = TRUE;

-- ===== WORKSHOPS SCHEMA =====
CREATE TABLE IF NOT EXISTS workshops.workshops (
    id          UUID            PRIMARY KEY,
    name        VARCHAR(150)    NOT NULL,
    address     VARCHAR(300),
    city        VARCHAR(100),
    zip_code    VARCHAR(20),
    phone       VARCHAR(20),
    email       VARCHAR(255),
    rating      DECIMAL(3,2)    DEFAULT 0.0,
    active      BOOLEAN         NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS workshops.work_orders (
    id                          UUID            PRIMARY KEY,
    claim_id                    UUID            NOT NULL,
    workshop_id                 UUID            NOT NULL REFERENCES workshops.workshops(id),
    estimated_cost              DECIMAL(12,2),
    final_cost                  DECIMAL(12,2),
    repair_status               VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    estimated_completion_date   DATE,
    work_description            VARCHAR(2000),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_work_orders_claim_id
    ON workshops.work_orders(claim_id);

-- ===== PAYMENTS SCHEMA =====
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

-- ===== REPORTING SCHEMA =====
-- Read Model (pre-aggregated, materialised by Kafka consumers)
-- NEVER join against this schema from other modules — read model only

CREATE TABLE IF NOT EXISTS reporting.claim_kpi_snapshots (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    region                  VARCHAR(50) NOT NULL DEFAULT 'global',
    total_claims            BIGINT      NOT NULL DEFAULT 0,
    submitted_today         BIGINT      NOT NULL DEFAULT 0,
    pending_assignment      BIGINT      NOT NULL DEFAULT 0,
    under_survey            BIGINT      NOT NULL DEFAULT 0,
    under_adjudication      BIGINT      NOT NULL DEFAULT 0,
    approved_this_month     BIGINT      NOT NULL DEFAULT 0,
    rejected_this_month     BIGINT      NOT NULL DEFAULT 0,
    settled_this_month      BIGINT      NOT NULL DEFAULT 0,
    total_settled_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    average_cycle_hours     DECIMAL(8,2) NOT NULL DEFAULT 0,
    fraud_flagged           BIGINT      NOT NULL DEFAULT 0,
    generated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_kpi_snapshots_region_time
    ON reporting.claim_kpi_snapshots(region, generated_at DESC);

CREATE TABLE IF NOT EXISTS reporting.regional_kpi_snapshots (
    id                          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    region                      VARCHAR(50)   NOT NULL,
    total_claims                BIGINT        NOT NULL DEFAULT 0,
    submitted_today             BIGINT        NOT NULL DEFAULT 0,
    pending_assignment          BIGINT        NOT NULL DEFAULT 0,
    under_survey                BIGINT        NOT NULL DEFAULT 0,
    under_adjudication          BIGINT        NOT NULL DEFAULT 0,
    approved_this_month         BIGINT        NOT NULL DEFAULT 0,
    rejected_this_month         BIGINT        NOT NULL DEFAULT 0,
    settled_this_month          BIGINT        NOT NULL DEFAULT 0,
    total_settled_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    avg_processing_time_hours   DECIMAL(8,2)  NOT NULL DEFAULT 0,
    fraud_flagged               BIGINT        NOT NULL DEFAULT 0,
    generated_at                TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_regional_kpi_snapshots_region_time
    ON reporting.regional_kpi_snapshots(region, generated_at DESC);

-- ===== AUDIT SCHEMA =====
-- Append-Only — No UPDATE or DELETE permitted
-- 7-year retention required for insurance regulatory compliance

CREATE TABLE IF NOT EXISTS audit.audit_log (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(100) NOT NULL UNIQUE,
    correlation_id  VARCHAR(100),
    user_id         VARCHAR(100),
    user_role       VARCHAR(50),
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(50)  NOT NULL,
    entity_id       VARCHAR(100) NOT NULL,
    old_value       TEXT,
    new_value       TEXT,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    session_id      VARCHAR(200),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Performance indexes for compliance queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_entity
    ON audit.audit_log(entity_type, entity_id);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_user_time
    ON audit.audit_log(user_id, occurred_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_action_time
    ON audit.audit_log(action, occurred_at DESC);

COMMENT ON TABLE audit.audit_log IS
    'Append-only immutable audit trail. 7-year retention. No UPDATE or DELETE permitted.';

-- ===== CUSTOMERS SCHEMA =====
CREATE TABLE IF NOT EXISTS customers.customer_profiles (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     VARCHAR(100)    NOT NULL UNIQUE,
    phone           VARCHAR(20),
    address_line1   VARCHAR(200),
    address_line2   VARCHAR(200),
    city            VARCHAR(100),
    state           VARCHAR(100),
    zip_code        VARCHAR(20),
    country         VARCHAR(100)    DEFAULT 'US',
    billing_cycle   VARCHAR(20)     NOT NULL DEFAULT 'MONTHLY',
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_customer_profiles_customer_id
    ON customers.customer_profiles(customer_id);

-- ===== NOTIFICATIONS SCHEMA =====
CREATE TABLE IF NOT EXISTS notifications.customer_notifications (
    id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id VARCHAR(100)    NOT NULL,
    type        VARCHAR(50)     NOT NULL,
    title       VARCHAR(200)    NOT NULL,
    message     VARCHAR(1000)   NOT NULL,
    claim_id    UUID,
    is_read     BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- Primary query: unread notifications per customer ordered newest-first
CREATE INDEX IF NOT EXISTS idx_notifications_customer_unread
    ON notifications.customer_notifications(customer_id, is_read, created_at DESC);