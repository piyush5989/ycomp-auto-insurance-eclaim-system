package com.yclaims.claims.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddEndorsementRequest(
        @NotBlank(message = "Note is required")
        @Size(min = 5, max = 2000, message = "Note must be between 5 and 2000 characters")
        String note
) {}
