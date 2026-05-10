-- Claims: replace natural-key dedupe with idempotency key
--
-- Why:
-- - Natural key (policy_number, incident_date, vehicle_registration) blocks valid repeated submissions
--   and causes "stale" data by returning the first claim.
-- - Proper idempotency should be explicit (client-generated key or request key), not inferred from business fields.

-- Drop natural key constraint (if present)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uq_claim_natural_key'
          AND conrelid = 'claims.claims'::regclass
    ) THEN
        ALTER TABLE claims.claims DROP CONSTRAINT uq_claim_natural_key;
    END IF;
END
$$;

-- Drop natural key index (if present)
DROP INDEX IF EXISTS claims.idx_claims_natural_key;

-- Add idempotency key column (nullable for backfill / existing rows)
ALTER TABLE claims.claims
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(120);

-- Unique index on idempotency_key (only when key is present)
CREATE UNIQUE INDEX IF NOT EXISTS uq_claims_idempotency_key
    ON claims.claims (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

