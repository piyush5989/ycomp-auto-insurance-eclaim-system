package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event payload when customer selects a workshop for vehicle repairs.
 * Triggers surveyor auto-assignment based on workshop location.
 */
public record WorkshopSelectedPayload(
        UUID claimId,
        UUID workshopId,
        String workshopName,
        String workshopZipCode,
        String workshopState,
        BigDecimal workshopLatitude,
        BigDecimal workshopLongitude,
        boolean isPartnerWorkshop,
        String customerId,
        String policyNumber,
        String vehicleRegistration
) {}
