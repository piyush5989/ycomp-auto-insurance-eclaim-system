package com.yclaims.documents.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit record for every document access or lifecycle event.
 * Never updated or deleted — provides irrefutable evidence for compliance and no-repudiation.
 */
@Entity
@Table(
    name = "document_audit_log",
    schema = "documents",
    indexes = {
        @Index(name = "idx_doc_audit_document_id", columnList = "document_id"),
        @Index(name = "idx_doc_audit_claim_id",    columnList = "claim_id"),
        @Index(name = "idx_doc_audit_occurred_at", columnList = "occurred_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class DocumentAuditLogEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "document_id", nullable = false, columnDefinition = "uuid")
    private UUID documentId;

    @Column(name = "claim_id", nullable = false, columnDefinition = "uuid")
    private UUID claimId;

    /**
     * UPLOADED | VIEWED | DOWNLOADED | SUPERSEDED | ARCHIVED | VERSION_ADDED
     */
    @Column(name = "action", nullable = false, length = 30)
    private String action;

    @Column(name = "actor_user_id", nullable = false, length = 100)
    private String actorUserId;

    @Column(name = "actor_role", length = 100)
    private String actorRole;

    @Column(name = "actor_ip", length = 45)
    private String actorIp;

    @Column(name = "correlation_id", length = 36)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Extra context as key=value string (e.g. "filename=report.pdf;version=2"). */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
