package com.yclaims.contracts.events.v1;

import java.util.UUID;

/**
 * Payload for 'surveyor.assigned' event.
 * Published by workflow-module, consumed by claims-module (to persist assignee + status),
 * and notifications/reporting modules.
 */
public record SurveyorAssignedPayload(
        UUID claimId,
        UUID surveyorId,
        String surveyorName,
        UUID workshopId,
        String workshopZipCode,
        String assignmentTrigger // e.g. "VEHICLE_DROPPED_OFF"
) {}

