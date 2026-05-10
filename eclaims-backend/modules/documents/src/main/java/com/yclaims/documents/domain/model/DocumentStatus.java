package com.yclaims.documents.domain.model;

public enum DocumentStatus {
    /** Visible to all authorised parties — the current active version. */
    ACTIVE,
    /** Replaced by a newer version; kept for audit lineage but hidden from default listings. */
    SUPERSEDED,
    /** Claim closed; binary moved to cold storage tier. */
    ARCHIVED,
    /** Legal or regulatory hold — blocks automated archival/purge. */
    LEGAL_HOLD
}
