package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event payload when an adjustor adjudicates a claim (approves or rejects).
 * This triggers customer and workshop notifications.
 */
public record ClaimAdjudicatedPayload(
        UUID claimId,
        UUID adjustorId,
        String decision,  // "APPROVED" or "REJECTED"
        BigDecimal approvedAmount,  // null if rejected
        String rejectionReason,  // null if approved
        UUID customerId,
        String customerEmail,
        UUID workshopId,
        String workshopName,
        Instant adjudicatedAt
) {}
