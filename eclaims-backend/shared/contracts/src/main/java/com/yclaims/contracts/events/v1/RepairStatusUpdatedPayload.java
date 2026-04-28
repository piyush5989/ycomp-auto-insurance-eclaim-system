package com.yclaims.contracts.events.v1;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Payload for 'repair.status.updated' domain event.
 * Consumers: notification-module (alert customer of repair progress), reporting-module.
 */
public record RepairStatusUpdatedPayload(
        UUID workOrderId,
        UUID claimId,
        String customerId,
        String customerEmail,
        String workshopId,
        String workshopName,
        String repairStatus,
        LocalDate estimatedCompletionDate,
        String statusNote
) {}
