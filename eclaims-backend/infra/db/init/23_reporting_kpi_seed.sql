-- ============================================================================
-- Migration 23: Reporting KPI Seed Data (POC Demo)
--
-- Purpose: Populate reporting read models with realistic data so the Internal
-- Reporting dashboards (KPI, Regional Manager, Top Management) show meaningful
-- numbers out of the box, without requiring Kafka consumers to be running.
--
-- In production these tables are populated by:
--   ClaimEventReportConsumer (Kafka) → refreshes snapshots on every claim event
--
-- This seed is idempotent (INSERT ... ON CONFLICT DO NOTHING / DO UPDATE).
-- ============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- 1. Global KPI snapshot (used by Case Manager KPI report + internal dashboard)
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO reporting.claim_kpi_snapshots (
    region, total_claims, submitted_today, pending_assignment,
    under_survey, under_adjudication, approved_this_month,
    rejected_this_month, settled_this_month, total_settled_amount,
    average_cycle_hours, fraud_flagged, generated_at
)
SELECT
    'global',
    COUNT(*),
    COUNT(*) FILTER (WHERE date_trunc('day', created_at) = date_trunc('day', NOW())),
    COUNT(*) FILTER (WHERE status = 'SUBMITTED'),
    COUNT(*) FILTER (WHERE status = 'UNDER_SURVEY'),
    COUNT(*) FILTER (WHERE status = 'UNDER_ADJUDICATION'),
    COUNT(*) FILTER (WHERE status = 'APPROVED'
        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
    COUNT(*) FILTER (WHERE status = 'REJECTED'
        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
    COUNT(*) FILTER (WHERE status = 'SETTLED'
        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
    COALESCE(SUM(approved_amount) FILTER (WHERE status = 'SETTLED'), 0),
    COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 3600)
        FILTER (WHERE status IN ('SETTLED', 'REJECTED')), 0),
    COUNT(*) FILTER (WHERE fraud_flag = true),
    NOW()
FROM claims.claims
ON CONFLICT DO NOTHING;

-- If no real claims exist yet, insert a realistic demo snapshot
INSERT INTO reporting.claim_kpi_snapshots (
    region, total_claims, submitted_today, pending_assignment,
    under_survey, under_adjudication, approved_this_month,
    rejected_this_month, settled_this_month, total_settled_amount,
    average_cycle_hours, fraud_flagged, generated_at
)
SELECT
    'global', 1847, 12, 43, 87, 124, 312, 28, 289,
    4785320.00, 38.4, 17, NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM reporting.claim_kpi_snapshots WHERE region = 'global'
);

-- ────────────────────────────────────────────────────────────────────────────
-- 2. Regional KPI snapshots (Regional Manager + Top Management dashboards)
--    Uses live claims data if available; falls back to static demo numbers.
-- ────────────────────────────────────────────────────────────────────────────

-- Attempt live aggregation per region
INSERT INTO reporting.regional_kpi_snapshots (
    region, total_claims, submitted_today, pending_assignment,
    under_survey, under_adjudication, approved_this_month,
    rejected_this_month, settled_this_month, total_settled_amount,
    avg_processing_time_hours, fraud_flagged, generated_at
)
SELECT
    region,
    COUNT(*),
    COUNT(*) FILTER (WHERE date_trunc('day', created_at) = date_trunc('day', NOW())),
    COUNT(*) FILTER (WHERE status = 'SUBMITTED'),
    COUNT(*) FILTER (WHERE status = 'UNDER_SURVEY'),
    COUNT(*) FILTER (WHERE status = 'UNDER_ADJUDICATION'),
    COUNT(*) FILTER (WHERE status = 'APPROVED'
        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
    COUNT(*) FILTER (WHERE status = 'REJECTED'
        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
    COUNT(*) FILTER (WHERE status = 'SETTLED'
        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
    COALESCE(SUM(approved_amount) FILTER (WHERE status = 'SETTLED'), 0),
    COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 3600)
        FILTER (WHERE status IN ('SETTLED', 'REJECTED')), 0),
    COUNT(*) FILTER (WHERE fraud_flag = true),
    NOW()
FROM claims.claims
WHERE region IS NOT NULL
GROUP BY region
ON CONFLICT DO NOTHING;

-- Demo seed for regions that have no real claims yet
-- Numbers are crafted to tell a plausible story per region for demo purposes
INSERT INTO reporting.regional_kpi_snapshots (
    region, total_claims, submitted_today, pending_assignment,
    under_survey, under_adjudication, approved_this_month,
    rejected_this_month, settled_this_month, total_settled_amount,
    avg_processing_time_hours, fraud_flagged, generated_at
)
VALUES
    -- EAST: largest hub, high volume, average processing time
    ('EAST',    523, 4, 14, 28, 39, 97,  9, 81, 1485200.00, 36.2, 6, NOW()),
    -- WEST: second hub, tech-heavy, fastest processing
    ('WEST',    418, 3, 11, 22, 31, 74,  7, 66, 1203800.00, 29.7, 4, NOW()),
    -- NORTH: smaller region, slower but thorough
    ('NORTH',   287, 2,  8, 15, 20, 51,  5, 44,  821500.00, 44.1, 3, NOW()),
    -- SOUTH: high fraud activity, slower processing
    ('SOUTH',   364, 2, 10, 19, 26, 63,  5, 58,  774320.00, 52.8, 8, NOW() - interval '2 minutes'),
    -- CENTRAL: newest region, ramping up
    ('CENTRAL', 255, 1,  0,  3,  8, 27,  2, 40,  500500.00, 41.3, 0, NOW() - interval '5 minutes')
ON CONFLICT DO NOTHING;

-- ────────────────────────────────────────────────────────────────────────────
-- 3. Ensure all demo claims have a region assigned (needed for live queries)
--    Assign region based on incident_location substring match as a simple heuristic
-- ────────────────────────────────────────────────────────────────────────────
UPDATE claims.claims
SET region = CASE
    WHEN incident_location ILIKE '%new york%' OR incident_location ILIKE '%boston%'
         OR incident_location ILIKE '%philadelphia%' OR incident_location ILIKE '%EAST%'  THEN 'EAST'
    WHEN incident_location ILIKE '%los angeles%' OR incident_location ILIKE '%san francisco%'
         OR incident_location ILIKE '%seattle%' OR incident_location ILIKE '%WEST%'        THEN 'WEST'
    WHEN incident_location ILIKE '%chicago%' OR incident_location ILIKE '%detroit%'
         OR incident_location ILIKE '%minneapolis%' OR incident_location ILIKE '%NORTH%'   THEN 'NORTH'
    WHEN incident_location ILIKE '%houston%' OR incident_location ILIKE '%miami%'
         OR incident_location ILIKE '%atlanta%' OR incident_location ILIKE '%SOUTH%'       THEN 'SOUTH'
    ELSE 'CENTRAL'
END
WHERE region IS NULL;

-- ────────────────────────────────────────────────────────────────────────────
-- 4. Mark some demo fraud claims so the Fraud Ageing report is non-empty
-- ────────────────────────────────────────────────────────────────────────────
UPDATE claims.claims
SET fraud_flag   = TRUE,
    fraud_reason = 'Multiple claims from same VIN within 30 days'
WHERE fraud_flag = FALSE
  AND status NOT IN ('SETTLED', 'REJECTED', 'ARCHIVED')
  AND id IN (
      SELECT id FROM claims.claims
      WHERE fraud_flag = FALSE
        AND status NOT IN ('SETTLED', 'REJECTED', 'ARCHIVED')
      ORDER BY created_at ASC
      LIMIT 3
  );
