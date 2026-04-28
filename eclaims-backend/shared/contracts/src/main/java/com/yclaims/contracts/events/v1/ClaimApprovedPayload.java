package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payload for 'claim.approved' domain event.
 * Consumers: payment-module (initiate settlement), notification-module, workshop-module.
 */
public record ClaimApprovedPayload(
        UUID claimId,
        String policyNumber,
        String customerId,
        String customerEmail,
        BigDecimal approvedAmount,
        String approvedByUserId,
        String workshopId,
        String approvalNotes
) {}
