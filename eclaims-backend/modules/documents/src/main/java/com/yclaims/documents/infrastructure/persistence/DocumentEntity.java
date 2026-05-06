package com.yclaims.documents.infrastructure.persistence;

import com.yclaims.documents.domain.model.DocumentStatus;
import com.yclaims.documents.domain.model.DocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "documents",
    schema = "documents",
    indexes = {
        @Index(name = "idx_documents_claim_id", columnList = "claim_id"),
        @Index(name = "idx_documents_status",   columnList = "status"),
        @Index(name = "idx_documents_parent_id", columnList = "parent_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
public class DocumentEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "claim_id", nullable = false, columnDefinition = "uuid")
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private long fileSizeBytes;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Column(name = "uploaded_by_user_id", nullable = false, length = 100)
    private String uploadedByUserId;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** Version counter — starts at 1, increments each time a new version is uploaded. */
    @Column(name = "version", nullable = false)
    private int version = 1;

    /** Points to the document this one supersedes; null for the first upload. */
    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    /** SHA-256 hex digest of the stored bytes — used for integrity verification. */
    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DocumentEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
