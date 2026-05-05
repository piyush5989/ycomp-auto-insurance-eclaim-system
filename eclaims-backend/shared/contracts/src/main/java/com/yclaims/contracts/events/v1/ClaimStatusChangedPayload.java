package com.yclaims.contracts.events.v1;

import java.util.UUID;

/**
 * Payload for 'claim.status.changed' domain event.
 * Consumers: notification-module, reporting-module, workflow-module (for auto-assignment triggers).
 */
public record ClaimStatusChangedPayload(
        UUID claimId,
        String policyNumber,
        String customerId,
        String customerEmail,
        String customerPhone,       // E.164 — used for SMS notifications
        String previousStatus,
        String newStatus,
        String changedByUserId,
        String changeReason
) {}
