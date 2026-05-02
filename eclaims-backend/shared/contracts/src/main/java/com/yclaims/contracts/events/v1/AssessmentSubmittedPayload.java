package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event payload when a surveyor submits damage assessment for a claim.
 * This triggers adjustor notification and adjudication workflow.
 */
public record AssessmentSubmittedPayload(
        UUID claimId,
        UUID surveyorId,
        BigDecimal assessedAmount,
        String damageNotes,
        List<UUID> documentIds,  // Uploaded damage photos/videos
        Instant submittedAt
) {}
