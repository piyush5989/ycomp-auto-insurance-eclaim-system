package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event payload when an adjustor adjudicates a claim (approves or rejects).
 * This triggers customer and workshop notifications.
 * workshopEmail is populated at publish time via WorkshopEmailPort so the
 * notification module can email the workshop without a cross-module DB query.
 */
public record ClaimAdjudicatedPayload(
        UUID claimId,
        UUID adjustorId,
        String decision,        // "APPROVED" or "REJECTED"
        BigDecimal approvedAmount,  // null if rejected
        String rejectionReason,     // null if approved
        UUID customerId,
        String customerEmail,
        UUID workshopId,
        String workshopName,
        String workshopEmail,   // nullable — looked up via WorkshopEmailPort at publish time
        Instant adjudicatedAt
) {}
