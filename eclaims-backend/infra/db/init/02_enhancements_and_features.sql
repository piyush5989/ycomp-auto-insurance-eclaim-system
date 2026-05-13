-- eClaims Enhancements and Features
-- Consolidated enhancements, additional features, and improvements

-- ===== CLAIMS ENHANCEMENTS =====
-- Add region and rental tracking
ALTER TABLE claims.claims
    ADD COLUMN IF NOT EXISTS region VARCHAR(50),
    ADD COLUMN IF NOT EXISTS rental_car_needed BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS rental_provider_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rental_start_date DATE,
    ADD COLUMN IF NOT EXISTS rental_end_date DATE,
    ADD COLUMN IF NOT EXISTS internal_notes TEXT,
    ADD COLUMN IF NOT EXISTS priority_level VARCHAR(20) DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS estimated_settlement_amount DECIMAL(12,2);

-- Claim endorsements support
CREATE TABLE IF NOT EXISTS claims.claim_endorsements (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID            NOT NULL REFERENCES claims.claims(id),
    endorsement_type VARCHAR(50)    NOT NULL,
    description     VARCHAR(1000),
    amount_change   DECIMAL(12,2)   DEFAULT 0.00,
    status          VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    added_by        VARCHAR(100)    NOT NULL,
    approved_by     VARCHAR(100),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    approved_at     TIMESTAMPTZ,
    note            TEXT            NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_claim_endorsements_claim_id
    ON claims.claim_endorsements(claim_id);

-- ===== WORKSHOPS ENHANCEMENTS =====
-- Add provider type and Keycloak integration
ALTER TABLE workshops.workshops
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(30) NOT NULL DEFAULT 'REPAIR_WORKSHOP',
    ADD COLUMN IF NOT EXISTS keycloak_user_id VARCHAR(36) UNIQUE;

-- Work order status history
CREATE TABLE IF NOT EXISTS workshops.work_order_status_history (
    id                        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id            UUID            NOT NULL REFERENCES workshops.work_orders(id),
    previous_status          VARCHAR(30),
    status                   VARCHAR(30)     NOT NULL,
    changed_by               VARCHAR(100),
    change_reason            VARCHAR(500),
    changed_at               TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    changed_by_user_id       VARCHAR(255),
    estimated_completion_date DATE,
    note                     TEXT
);

CREATE INDEX IF NOT EXISTS idx_work_order_status_history_work_order_id
    ON workshops.work_order_status_history(work_order_id);

CREATE INDEX IF NOT EXISTS idx_workshops_provider_type
    ON workshops.workshops(provider_type, active);

CREATE INDEX IF NOT EXISTS idx_workshops_zip_provider
    ON workshops.workshops(zip_code, provider_type, active);

CREATE INDEX IF NOT EXISTS idx_workshops_keycloak_user
    ON workshops.workshops(keycloak_user_id);

-- ===== DOCUMENT ENHANCEMENTS =====
-- Add versioning and status tracking
ALTER TABLE documents.documents
    ADD COLUMN IF NOT EXISTS version         INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS parent_id       UUID        REFERENCES documents.documents(id),
    ADD COLUMN IF NOT EXISTS status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS checksum_sha256 VARCHAR(64);

-- Document audit log
CREATE TABLE IF NOT EXISTS documents.document_audit_log (
    id             UUID         PRIMARY KEY,
    document_id    UUID         NOT NULL,
    claim_id       UUID         NOT NULL,
    action         VARCHAR(30)  NOT NULL,
    actor_user_id  VARCHAR(100) NOT NULL,
    actor_role     VARCHAR(100),
    actor_ip       VARCHAR(45),
    correlation_id VARCHAR(36),
    occurred_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    metadata       JSONB
);

CREATE INDEX IF NOT EXISTS idx_doc_audit_document_id
    ON documents.document_audit_log(document_id);

CREATE INDEX IF NOT EXISTS idx_doc_audit_claim_id
    ON documents.document_audit_log(claim_id);

-- ===== SURVEYOR ENHANCEMENTS =====
-- Add Keycloak sync and zip code coverage
ALTER TABLE workflow.surveyors
    ADD COLUMN IF NOT EXISTS keycloak_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS zip_codes TEXT,
    ADD COLUMN IF NOT EXISTS coverage_radius_km INTEGER DEFAULT 50;

CREATE UNIQUE INDEX IF NOT EXISTS uq_surveyors_keycloak_id
    ON workflow.surveyors(keycloak_id)
    WHERE keycloak_id IS NOT NULL;

-- ===== PERFORMANCE INDEXES =====
-- Priority-based claim processing
CREATE INDEX IF NOT EXISTS idx_claims_priority_status
    ON claims.claims(priority_level, status, created_at);

-- Document status filtering
CREATE INDEX IF NOT EXISTS idx_documents_status
    ON documents.documents(status);

CREATE INDEX IF NOT EXISTS idx_documents_parent_id
    ON documents.documents(parent_id);

-- Claims incident date filtering
CREATE INDEX IF NOT EXISTS idx_claims_incident_date
    ON claims.claims(incident_date);

-- Regional surveyor assignment
CREATE INDEX IF NOT EXISTS idx_surveyors_region_active
    ON workflow.surveyors(region, active) WHERE active = TRUE;