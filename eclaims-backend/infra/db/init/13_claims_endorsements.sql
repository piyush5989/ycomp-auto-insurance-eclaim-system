-- Claim Endorsements
-- Customer notes / amendments added when the claim is past SUBMITTED state.
-- Think: post-it notes on a paper claim folder — they don't change facts already recorded,
-- but add important context that the adjuster / surveyor sees alongside the claim.

CREATE TABLE IF NOT EXISTS claims.claim_endorsements (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id            UUID            NOT NULL REFERENCES claims.claims(id),
    note                TEXT            NOT NULL,
    added_by            VARCHAR(100)    NOT NULL,
    endorsement_type    VARCHAR(50)     NOT NULL DEFAULT 'CUSTOMER_NOTE',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_claim_endorsements_claim_id
    ON claims.claim_endorsements(claim_id);
