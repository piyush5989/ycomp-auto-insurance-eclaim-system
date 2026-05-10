package com.yclaims.claims.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request DTO for case manager to override an adjudication decision.
 */
public record OverrideDecisionRequest(
        @NotNull(message = "New approved amount is required")
        BigDecimal newAmount,
        
        @NotBlank(message = "Override reason is required")
        String reason
) {}
