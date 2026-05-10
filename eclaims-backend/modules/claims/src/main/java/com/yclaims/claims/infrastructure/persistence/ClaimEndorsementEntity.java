package com.yclaims.claims.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores customer and system notes added to a claim after it has moved past SUBMITTED.
 * Think of these as an audit trail of "I want to add context to this claim but I can't edit it".
 */
@Entity
@Table(
    name = "claim_endorsements",
    schema = "claims",
    indexes = @Index(name = "idx_claim_endorsements_claim_id", columnList = "claim_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimEndorsementEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "claim_id", nullable = false, columnDefinition = "uuid")
    private UUID claimId;

    @Column(name = "note", nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "added_by", nullable = false, length = 100)
    private String addedBy;

    @Column(name = "endorsement_type", nullable = false, length = 50)
    private String endorsementType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public static ClaimEndorsementEntity create(UUID claimId, String note,
                                                 String addedBy, String endorsementType) {
        ClaimEndorsementEntity e = new ClaimEndorsementEntity();
        e.id = UUID.randomUUID();
        e.claimId = claimId;
        e.note = note;
        e.addedBy = addedBy;
        e.endorsementType = endorsementType;
        return e;
    }
}
