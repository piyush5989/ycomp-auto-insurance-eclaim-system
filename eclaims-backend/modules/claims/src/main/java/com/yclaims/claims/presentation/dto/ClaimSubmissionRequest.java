package com.yclaims.claims.presentation.dto;

import com.yclaims.claims.domain.model.ClaimType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Inbound DTO for claim submission. JPA entities are never used in API layer.
 * All fields validated before reaching the service.
 */
public record ClaimSubmissionRequest(

        @NotBlank(message = "Policy number is required")
        @Pattern(regexp = "^[A-Z]{3}-\\d{8}$", message = "Invalid policy number format. Expected: ABC-12345678")
        String policyNumber,

        @NotBlank(message = "Vehicle registration is required")
        @Size(max = 20, message = "Vehicle registration must not exceed 20 characters")
        String vehicleRegistration,

        @NotNull(message = "Incident date is required")
        @PastOrPresent(message = "Incident date cannot be in the future")
        LocalDate incidentDate,

        @Size(max = 500, message = "Incident location must not exceed 500 characters")
        String incidentLocation,

        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        @NotNull(message = "Claim type is required")
        ClaimType claimType,

        boolean policeReportFiled,

        @Size(max = 50, message = "Police report number must not exceed 50 characters")
        String policeReportNumber

) {}
