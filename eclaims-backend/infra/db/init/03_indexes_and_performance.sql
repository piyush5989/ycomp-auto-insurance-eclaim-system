-- eClaims Simple Data Constraints
-- Basic constraints without complex IF NOT EXISTS logic

-- ===== PERFORMANCE OPTIMIZATION INDEXES =====
-- Compound indexes for common query patterns
CREATE INDEX IF NOT EXISTS idx_claims_status_priority_date
    ON claims.claims(status, priority_level, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_claims_region_status
    ON claims.claims(region, status) WHERE region IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_documents_claim_status_type
    ON documents.documents(claim_id, status, document_type);

CREATE INDEX IF NOT EXISTS idx_payments_status_created
    ON payments.payments(status, created_at);

CREATE INDEX IF NOT EXISTS idx_workshops_active_rating
    ON workshops.workshops(active, rating DESC) WHERE active = TRUE;

-- ===== CLEANUP AND MAINTENANCE =====
-- Index for notification cleanup (old read notifications)
CREATE INDEX IF NOT EXISTS idx_notifications_cleanup
    ON notifications.customer_notifications(is_read, created_at) WHERE is_read = TRUE;

-- Index for document audit log cleanup
CREATE INDEX IF NOT EXISTS idx_doc_audit_occurred_at
    ON documents.document_audit_log(occurred_at DESC);