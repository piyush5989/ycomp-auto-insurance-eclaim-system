-- Reporting Schema — Read Model (pre-aggregated, materialised by Kafka consumers)
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

-- Seed initial KPI snapshot for demo
INSERT INTO reporting.claim_kpi_snapshots
    (region, total_claims, submitted_today, pending_assignment, fraud_flagged, generated_at)
VALUES
    ('global', 0, 0, 0, 0, NOW())
ON CONFLICT DO NOTHING;
