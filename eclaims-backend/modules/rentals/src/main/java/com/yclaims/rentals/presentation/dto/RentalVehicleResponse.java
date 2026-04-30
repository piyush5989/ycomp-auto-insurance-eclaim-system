package com.yclaims.rentals.presentation.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record RentalVehicleResponse(
        UUID vehicleId,
        String vehicleType,
        String make,
        String model,
        Integer year,
        Integer seatingCapacity,
        String transmissionType,
        String fuelType,
        BigDecimal dailyRate,
        boolean available,
        UUID providerId,
        String providerName
) {}
