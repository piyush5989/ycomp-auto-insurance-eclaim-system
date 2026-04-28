package com.yclaims.claims.domain.event;

import com.yclaims.claims.domain.model.ClaimId;
import com.yclaims.claims.domain.model.ClaimStatus;
import java.time.Instant;

public record ClaimStatusChangedEvent(
        ClaimId claimId,
        ClaimStatus previousStatus,
        ClaimStatus newStatus,
        String changedByUserId,
        String correlationId,
        Instant occurredAt
) {
    public ClaimStatusChangedEvent(ClaimId claimId, ClaimStatus previousStatus,
                                   ClaimStatus newStatus, String changedByUserId,
                                   String correlationId) {
        this(claimId, previousStatus, newStatus, changedByUserId, correlationId, Instant.now());
    }
}
