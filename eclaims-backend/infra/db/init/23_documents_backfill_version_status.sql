-- Backfill documents.documents version/status after partial Hibernate ddl-auto:update or legacy rows.
-- Hibernate may add nullable columns then fail on SET NOT NULL, leaving NULLs and noisy startup warnings.
-- Safe to run repeatedly (idempotent).

ALTER TABLE documents.documents ADD COLUMN IF NOT EXISTS version INTEGER;
ALTER TABLE documents.documents ADD COLUMN IF NOT EXISTS parent_id UUID;
ALTER TABLE documents.documents ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE documents.documents ADD COLUMN IF NOT EXISTS checksum_sha256 VARCHAR(64);

UPDATE documents.documents SET version = 1 WHERE version IS NULL;

UPDATE documents.documents
SET status = 'ACTIVE'
WHERE status IS NULL OR LENGTH(TRIM(status)) = 0;

ALTER TABLE documents.documents ALTER COLUMN version SET DEFAULT 1;
ALTER TABLE documents.documents ALTER COLUMN version SET NOT NULL;

ALTER TABLE documents.documents ALTER COLUMN status SET DEFAULT 'ACTIVE';
ALTER TABLE documents.documents ALTER COLUMN status SET NOT NULL;

ALTER TABLE documents.documents DROP COLUMN IF EXISTS archived;

CREATE INDEX IF NOT EXISTS idx_documents_status
    ON documents.documents(status);

CREATE INDEX IF NOT EXISTS idx_documents_parent_id
    ON documents.documents(parent_id);
