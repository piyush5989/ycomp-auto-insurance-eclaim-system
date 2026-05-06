package com.yclaims.contracts.events.v1;

import java.util.Map;
import java.util.UUID;

/**
 * Event payload when a notification needs to be sent.
 * recipientEmail is populated for staff recipients (surveyor, adjustor) so the
 * notification module can send email without a cross-module DB lookup.
 */
public record NotificationRequestedPayload(
        UUID claimId,
        String recipientId,
        String recipientEmail,  // nullable — present for surveyor/adjustor; enables direct email delivery
        String recipientType,   // CUSTOMER, SURVEYOR, ADJUSTOR, WORKSHOP, CASE_MANAGER
        String notificationType,  // CLAIM_SUBMITTED, WORKSHOP_SELECTED, SURVEYOR_ASSIGNED, etc.
        String channel,           // EMAIL, SMS, IN_APP
        String subject,
        String message,
        Map<String, String> metadata
) {}
