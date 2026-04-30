package com.yclaims.workshops.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record VehicleDropOffRequest(
        String dropOffNotes,
        Integer mileage,
        String fuelLevel,  // FULL, THREE_QUARTERS, HALF, QUARTER, EMPTY
        String vehicleCondition,
        boolean photosUploaded,
        Instant expectedPickupAt
) {}
