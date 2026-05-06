-- Document Management Enhancements
-- Adds versioning, status lifecycle, SHA-256 integrity check, and an append-only audit log

-- ─── Versioning & Status columns ─────────────────────────────────────────────
ALTER TABLE documents.documents
    ADD COLUMN IF NOT EXISTS version         INTEGER     NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS parent_id       UUID        REFERENCES documents.documents(id),
    ADD COLUMN IF NOT EXISTS status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS checksum_sha256 VARCHAR(64);

-- Drop old boolean — superseded by status column
ALTER TABLE documents.documents DROP COLUMN IF EXISTS archived;

CREATE INDEX IF NOT EXISTS idx_documents_status
    ON documents.documents(status);

CREATE INDEX IF NOT EXISTS idx_documents_parent_id
    ON documents.documents(parent_id);

-- ─── Append-only Audit Log ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS documents.document_audit_log (
    id             UUID         PRIMARY KEY,
    document_id    UUID         NOT NULL,
    claim_id       UUID         NOT NULL,
    action         VARCHAR(30)  NOT NULL,   -- UPLOADED|VIEWED|DOWNLOADED|SUPERSEDED|ARCHIVED|VERSION_ADDED
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

CREATE INDEX IF NOT EXISTS idx_doc_audit_occurred_at
    ON documents.document_audit_log(occurred_at DESC);
