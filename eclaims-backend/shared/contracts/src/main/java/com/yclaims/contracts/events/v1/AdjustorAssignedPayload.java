package com.yclaims.contracts.events.v1;

import java.util.UUID;

/**
 * Payload for 'adjustor.assigned' event.
 * Published by workflow-module, consumed by claims-module (to persist assignee + status),
 * and notifications/reporting modules.
 */
public record AdjustorAssignedPayload(
        UUID claimId,
        UUID adjustorId,
        String adjustorName,
        String assignmentTrigger // e.g. "SURVEY_COMPLETED"
) {}

