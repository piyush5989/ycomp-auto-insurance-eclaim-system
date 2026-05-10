-- One-off backfill: denormalise claims.region from the assigned surveyor's routing region.
-- Safe to re-run: only updates rows where region is NULL or blank.
-- Reporting already falls back via JOIN in ClaimKpiSnapshotRepository; this aligns claims.region for filters and exports.

UPDATE claims.claims c
SET region = TRIM(s.region)
FROM workflow.surveyors s
WHERE c.assigned_surveyor_id IS NOT NULL
  AND TRIM(c.assigned_surveyor_id) <> ''
  AND s.id::text = TRIM(c.assigned_surveyor_id)
  AND s.region IS NOT NULL
  AND TRIM(s.region) <> ''
  AND (c.region IS NULL OR TRIM(c.region) = '');
