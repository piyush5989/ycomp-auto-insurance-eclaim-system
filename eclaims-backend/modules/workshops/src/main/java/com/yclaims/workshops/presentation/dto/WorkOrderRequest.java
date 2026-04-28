package com.yclaims.workshops.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderRequest(
        @NotNull UUID claimId,
        @NotNull UUID workshopId,
        @NotNull @DecimalMin("0.01") BigDecimal estimatedCost,
        LocalDate estimatedCompletionDate,
        String workDescription
) {}
