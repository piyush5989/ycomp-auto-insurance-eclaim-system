package com.yclaims.claims.presentation.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record DuplicateCheckRequest(
        @NotBlank String policyNumber,
        @NotBlank String vehicleRegistration,
        @NotNull @PastOrPresent LocalDate incidentDate
) {}
