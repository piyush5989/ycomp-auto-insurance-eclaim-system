package com.yclaims.rentals.presentation.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Builder
public record ReservationResponse(
        UUID reservationId,
        UUID claimId,
        UUID vehicleId,
        BigDecimal dailyRate,
        BigDecimal totalCost,
        Integer rentalDays,
        Instant reservationStart,
        Instant reservationEnd,
        String status
) {}
