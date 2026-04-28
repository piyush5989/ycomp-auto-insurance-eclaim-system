package com.yclaims.claims.domain.event;

import com.yclaims.claims.domain.model.ClaimId;
import java.time.Instant;

public record ClaimAssignedEvent(
        ClaimId claimId,
        String surveyorId,
        String correlationId,
        Instant occurredAt
) {
    public ClaimAssignedEvent(ClaimId claimId, String surveyorId, String correlationId) {
        this(claimId, surveyorId, correlationId, Instant.now());
    }
}
