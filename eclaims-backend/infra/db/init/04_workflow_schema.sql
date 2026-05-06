-- Workflow Schema

CREATE TABLE IF NOT EXISTS workflow.surveyors (
    id      UUID        PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    email   VARCHAR(255) NOT NULL,
    phone   VARCHAR(20),
    region  VARCHAR(50),
    active  BOOLEAN     NOT NULL DEFAULT TRUE
);

-- Seed surveyors using real Keycloak user IDs from eclaims-realm.json
-- These IDs are explicitly set in Keycloak to ensure consistency
INSERT INTO workflow.surveyors (id, name, email, phone, region, active)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'Alice Surveyor',   'surveyor1@eclaims.test', '+918604403487', 'EAST', TRUE),
    ('20000000-0000-0000-0000-000000000002', 'Steve Surveyor',   'surveyor2@eclaims.test', '+918604403487', 'WEST', TRUE)
ON CONFLICT DO NOTHING;

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
