package com.yclaims.claims.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request DTO for case manager to reassign a claim to a different surveyor or adjustor.
 */
public record ReassignRequest(
        @NotNull(message = "New user ID is required")
        String newUserId,
        
        @NotBlank(message = "Reason for reassignment is required")
        String reason
) {}
