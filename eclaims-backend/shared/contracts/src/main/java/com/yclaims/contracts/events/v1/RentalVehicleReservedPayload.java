package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event payload when customer reserves a rental vehicle.
 */
public record RentalVehicleReservedPayload(
        UUID claimId,
        UUID reservationId,
        UUID vehicleId,
        UUID providerId,
        String customerId,
        String vehicleType,
        String vehicleMake,
        String vehicleModel,
        BigDecimal dailyRate,
        Instant reservationStart,
        Instant reservationEnd
) {}
