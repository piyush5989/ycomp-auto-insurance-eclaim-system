package com.yclaims.claims.application.command;

import com.yclaims.claims.domain.model.ClaimType;

import java.time.LocalDate;

/**
 * CQRS command for claim submission.
 * Carries all data needed to create a new claim.
 * Built from ClaimSubmissionRequest DTO in the presentation layer.
 */
public record SubmitClaimCommand(
        String policyNumber,
        String vehicleRegistration,
        LocalDate incidentDate,
        String incidentLocation,
        String description,
        ClaimType claimType,
        boolean policeReportFiled,
        String policeReportNumber,
        String correlationId,
        String requestingUserId
) {}
