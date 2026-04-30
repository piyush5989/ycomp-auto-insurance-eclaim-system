package com.yclaims.claims.presentation.dto;

import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.domain.model.ClaimType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight summary returned in duplicate-check response.
 * Contains only what the customer needs to identify the claim — not full details.
 */
@Getter
@Builder
public class PotentialDuplicateResponse {
    private UUID claimId;
    private String policyNumber;
    private String vehicleRegistration;
    private ClaimType claimType;
    private ClaimStatus status;
    private LocalDate incidentDate;
    private String incidentLocation;
    private Instant createdAt;
}
