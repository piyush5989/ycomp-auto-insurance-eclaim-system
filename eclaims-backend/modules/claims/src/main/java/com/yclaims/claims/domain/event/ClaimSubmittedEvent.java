package com.yclaims.claims.domain.event;

import com.yclaims.claims.domain.model.ClaimId;
import java.time.Instant;

public record ClaimSubmittedEvent(
        ClaimId claimId,
        String policyNumber,
        String customerId,
        String customerEmail,
        Instant occurredAt
) {
    public ClaimSubmittedEvent(ClaimId claimId, String policyNumber, String customerId, String customerEmail) {
        this(claimId, policyNumber, customerId, customerEmail, Instant.now());
    }
}
