package com.yclaims.customers.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateBillingCycleRequest(
        @NotBlank
        @Pattern(regexp = "MONTHLY|QUARTERLY|ANNUALLY",
                 message = "billingCycle must be one of: MONTHLY, QUARTERLY, ANNUALLY")
        String billingCycle
) {}
