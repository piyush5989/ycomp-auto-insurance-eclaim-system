package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event payload when customer confirms vehicle drop-off at workshop.
 * THIS EVENT TRIGGERS surveyor auto-assignment.
 * Surveyor visits workshop where vehicle is now located.
 */
public record VehicleDroppedOffPayload(
        UUID claimId,
        UUID workshopId,
        String workshopName,
        String workshopZipCode,
        String workshopState,
        BigDecimal workshopLatitude,
        BigDecimal workshopLongitude,
        UUID dropOffId,
        Instant droppedOffAt,
        String dropOffNotes,
        Integer mileage,
        String fuelLevel,
        boolean photosUploaded,
        String confirmedBy,
        String customerId,
        String policyNumber,
        String vehicleRegistration
) {}
