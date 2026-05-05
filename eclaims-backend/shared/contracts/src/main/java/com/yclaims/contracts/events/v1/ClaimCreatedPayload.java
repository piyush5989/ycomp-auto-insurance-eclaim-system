package com.yclaims.contracts.events.v1;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for 'claim.created' domain event.
 * Consumers: notification-module (send submission email), reporting-module (materialise read model).
 */
public record ClaimCreatedPayload(
        UUID claimId,
        String policyNumber,
        String customerId,
        String customerEmail,
        String customerPhone,       // E.164 — used for SMS notifications
        String vehicleRegistration,
        LocalDate incidentDate,
        String claimType,
        String status
) {}
