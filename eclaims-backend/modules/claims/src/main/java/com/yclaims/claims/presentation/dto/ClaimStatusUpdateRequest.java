package com.yclaims.claims.presentation.dto;

import com.yclaims.claims.domain.model.ClaimStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ClaimStatusUpdateRequest(

        @NotNull(message = "Target status is required")
        ClaimStatus targetStatus,

        BigDecimal amount,

        String reason,

        String workshopId

) {}
