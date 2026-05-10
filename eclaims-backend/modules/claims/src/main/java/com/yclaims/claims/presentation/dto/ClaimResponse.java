package com.yclaims.claims.presentation.dto;

import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.domain.model.ClaimType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Outbound DTO for claim data. JPA entities are never returned from API.
 * Fields safe for API exposure — no PII masking needed here (handled at CustomerProfileResponse level).
 */
@Getter
@Builder
public class ClaimResponse {

    private UUID claimId;
    private String policyNumber;
    private String customerId;
    private String vehicleRegistration;
    private ClaimType claimType;
    private ClaimStatus status;

    // Accident details
    private LocalDate incidentDate;
    private String incidentLocation;
    private String description;
    private boolean policeReportFiled;

    // Assignment
    private String assignedSurveyorId;
    private String assignedAdjustorId;

    // Financial
    private BigDecimal estimatedAmount;
    private BigDecimal assessedAmount;
    private BigDecimal approvedAmount;

    // Workshop
    private String workshopId;

    // Decision
    private String rejectionReason;

    // Fraud
    private boolean fraudFlag;

    // Rental vehicle
    private UUID rentalReservationId;
    private String rentalStatus; // NOT_SELECTED, RESERVED, SKIPPED

    // Timestamps
    private Instant surveyCompletedAt;
    private Instant adjudicatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
