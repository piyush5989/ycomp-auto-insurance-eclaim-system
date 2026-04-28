-- Documents Schema

CREATE TABLE IF NOT EXISTS documents.documents (
    id                  UUID            PRIMARY KEY,
    claim_id            UUID            NOT NULL,   -- FK to claims.claims — cross-schema reference allowed at DB level
    document_type       VARCHAR(40)     NOT NULL,
    filename            VARCHAR(255)    NOT NULL,
    content_type        VARCHAR(100),
    file_size_bytes     BIGINT,
    storage_key         VARCHAR(500)    NOT NULL,   -- S3 key or local FS path
    uploaded_by_user_id VARCHAR(100)    NOT NULL,
    uploaded_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    archived            BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_documents_claim_id
    ON documents.documents(claim_id);

CREATE INDEX IF NOT EXISTS idx_documents_type
    ON documents.documents(document_type);
