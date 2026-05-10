package com.yclaims.rentals.presentation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReserveVehicleRequest(
        @NotNull(message = "Claim ID is required")
        UUID claimId,

        @NotNull(message = "Vehicle ID is required")
        UUID vehicleId,

        @NotNull(message = "Rental days is required")
        @Min(value = 1, message = "Rental duration must be at least 1 day")
        @Max(value = 30, message = "Rental duration cannot exceed 30 days")
        Integer rentalDays
) {}
