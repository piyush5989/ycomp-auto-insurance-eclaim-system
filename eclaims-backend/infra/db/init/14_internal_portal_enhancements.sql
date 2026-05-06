-- Internal Portal Enhancements
-- Schema changes for region-based assignment, case manager overrides, and regional reporting

-- ══════════════════════════════════════════════════════════════════════════════
-- CLAIMS SCHEMA ENHANCEMENTS
-- ══════════════════════════════════════════════════════════════════════════════

-- Add region field extracted from incident location or assigned surveyor's region
ALTER TABLE claims.claims
ADD COLUMN IF NOT EXISTS region VARCHAR(50);

-- Add case manager override tracking
ALTER TABLE claims.claims
ADD COLUMN IF NOT EXISTS override_by_user_id VARCHAR(100),
ADD COLUMN IF NOT EXISTS override_reason TEXT,
ADD COLUMN IF NOT EXISTS override_at TIMESTAMPTZ;

-- Index for regional queries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_region
    ON claims.claims(region) WHERE region IS NOT NULL;

-- Index for assigned adjustor queue
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_assigned_adjustor
    ON claims.claims(assigned_adjustor_id)
    WHERE status IN ('UNDER_ADJUDICATION', 'APPROVED');

-- Composite index for claims queue filtering
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims_queue_filter
    ON claims.claims(status, region, fraud_flag, created_at DESC);

-- ══════════════════════════════════════════════════════════════════════════════
-- WORKFLOW SCHEMA ENHANCEMENTS
-- ══════════════════════════════════════════════════════════════════════════════

-- Add field office and service area information to surveyors
ALTER TABLE workflow.surveyors
ADD COLUMN IF NOT EXISTS field_office VARCHAR(100),
ADD COLUMN IF NOT EXISTS service_areas TEXT; -- JSON array of regions, e.g. '["EAST", "NORTHEAST"]'

-- Update existing surveyor data with field office locations (using real Keycloak IDs)
UPDATE workflow.surveyors 
SET field_office = 'Boston Office', service_areas = '["EAST", "NORTHEAST"]'
WHERE id = '20000000-0000-0000-0000-000000000001';

UPDATE workflow.surveyors 
SET field_office = 'San Francisco Office', service_areas = '["WEST", "SOUTHWEST"]'
WHERE id = '20000000-0000-0000-0000-000000000002';

-- Add adjustors table for delegation feature
CREATE TABLE IF NOT EXISTS workflow.adjustors (
    id      UUID        PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    email   VARCHAR(255) NOT NULL,
    phone   VARCHAR(20),
    region  VARCHAR(50),
    active  BOOLEAN     NOT NULL DEFAULT TRUE,
    field_office VARCHAR(100),
    service_areas TEXT
);

-- Seed adjustors using real Keycloak user IDs from eclaims-realm.json
INSERT INTO workflow.adjustors (id, name, email, phone, region, active, field_office, service_areas)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'Bob Adjustor',   'adjustor1@eclaims.test', '+918604403487', 'EAST', TRUE, 'Boston Office', '["EAST", "NORTHEAST"]'),
    ('30000000-0000-0000-0000-000000000002', 'Betty Adjustor', 'adjustor2@eclaims.test', '+918604403487', 'WEST', TRUE, 'San Francisco Office', '["WEST", "SOUTHWEST"]')
ON CONFLICT DO NOTHING;

-- Reassignment history tracking
CREATE TABLE IF NOT EXISTS workflow.reassignments (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id            UUID        NOT NULL,
    entity_type         VARCHAR(20) NOT NULL CHECK (entity_type IN ('SURVEYOR', 'ADJUSTOR')),
    from_user_id        VARCHAR(100),
    to_user_id          VARCHAR(100) NOT NULL,
    reassigned_by       VARCHAR(100) NOT NULL, -- case manager user ID
    reason              TEXT,
    reassigned_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_reassignments_claim_id
    ON workflow.reassignments(claim_id);

CREATE INDEX IF NOT EXISTS idx_reassignments_to_user
    ON workflow.reassignments(to_user_id);

-- ══════════════════════════════════════════════════════════════════════════════
-- REPORTING SCHEMA ENHANCEMENTS
-- ══════════════════════════════════════════════════════════════════════════════

-- Regional KPI snapshots for regional manager reports
CREATE TABLE IF NOT EXISTS reporting.regional_kpi_snapshots (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    region                      VARCHAR(50) NOT NULL,
    total_claims                BIGINT      NOT NULL DEFAULT 0,
    submitted_today             BIGINT      NOT NULL DEFAULT 0,
    pending_assignment          BIGINT      NOT NULL DEFAULT 0,
    under_survey                BIGINT      NOT NULL DEFAULT 0,
    under_adjudication          BIGINT      NOT NULL DEFAULT 0,
    approved_this_month         BIGINT      NOT NULL DEFAULT 0,
    rejected_this_month         BIGINT      NOT NULL DEFAULT 0,
    settled_this_month          BIGINT      NOT NULL DEFAULT 0,
    total_settled_amount        DECIMAL(15,2) NOT NULL DEFAULT 0,
    avg_processing_time_hours   DECIMAL(10,2) NOT NULL DEFAULT 0,
    fraud_flagged               BIGINT      NOT NULL DEFAULT 0,
    generated_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(region, generated_at)
);

CREATE INDEX IF NOT EXISTS idx_regional_kpi_region_date
    ON reporting.regional_kpi_snapshots(region, generated_at DESC);

-- Claims processing time metrics (for detailed analysis)
CREATE TABLE IF NOT EXISTS reporting.processing_time_metrics (
    id                          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id                    UUID        NOT NULL,
    region                      VARCHAR(50),
    submitted_at                TIMESTAMPTZ NOT NULL,
    assigned_at                 TIMESTAMPTZ,
    survey_completed_at         TIMESTAMPTZ,
    adjudication_completed_at   TIMESTAMPTZ,
    payment_initiated_at        TIMESTAMPTZ,
    settled_at                  TIMESTAMPTZ,
    total_hours                 DECIMAL(10,2),
    assignment_hours            DECIMAL(10,2),
    survey_hours                DECIMAL(10,2),
    adjudication_hours          DECIMAL(10,2),
    payment_hours               DECIMAL(10,2),
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_processing_metrics_claim
    ON reporting.processing_time_metrics(claim_id);

CREATE INDEX IF NOT EXISTS idx_processing_metrics_region
    ON reporting.processing_time_metrics(region, submitted_at DESC);

-- Geography-based claims aggregation
CREATE TABLE IF NOT EXISTS reporting.claims_by_geography (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    region          VARCHAR(50) NOT NULL,
    city            VARCHAR(100),
    state           VARCHAR(50),
    zip_code        VARCHAR(10),
    claim_count     BIGINT      NOT NULL DEFAULT 0,
    total_amount    DECIMAL(15,2) NOT NULL DEFAULT 0,
    period_start    DATE        NOT NULL,
    period_end      DATE        NOT NULL,
    generated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(region, city, state, period_start)
);

CREATE INDEX IF NOT EXISTS idx_claims_geography_region
    ON reporting.claims_by_geography(region, period_start DESC);

-- ══════════════════════════════════════════════════════════════════════════════
-- SEED INITIAL REGIONAL KPI DATA
-- ══════════════════════════════════════════════════════════════════════════════

INSERT INTO reporting.regional_kpi_snapshots
    (region, total_claims, submitted_today, pending_assignment, fraud_flagged, generated_at)
VALUES
    ('EAST', 0, 0, 0, 0, NOW()),
    ('WEST', 0, 0, 0, 0, NOW()),
    ('NORTH', 0, 0, 0, 0, NOW()),
    ('SOUTH', 0, 0, 0, 0, NOW()),
    ('CENTRAL', 0, 0, 0, 0, NOW())
ON CONFLICT DO NOTHING;

-- ══════════════════════════════════════════════════════════════════════════════
-- COMMENTS FOR DOCUMENTATION
-- ══════════════════════════════════════════════════════════════════════════════

COMMENT ON COLUMN claims.claims.region IS 'Extracted from incident location or assigned surveyor region for reporting';
COMMENT ON COLUMN claims.claims.override_by_user_id IS 'Case manager who overrode the original adjudication decision';
COMMENT ON COLUMN claims.claims.override_reason IS 'Reason provided by case manager for overriding the decision';
COMMENT ON TABLE workflow.reassignments IS 'Tracks surveyor/adjustor reassignments by case managers';
COMMENT ON TABLE reporting.regional_kpi_snapshots IS 'Pre-aggregated KPI metrics per region for regional manager dashboards';
COMMENT ON TABLE reporting.processing_time_metrics IS 'Detailed processing time breakdown per claim for analytics';
COMMENT ON TABLE reporting.claims_by_geography IS 'Aggregated claim counts and amounts by geographic location';
