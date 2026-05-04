package com.yclaims.workshops.presentation.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkOrderStatusHistoryResponse(
        UUID id,
        String status,
        String note,
        LocalDate estimatedCompletionDate,
        Instant changedAt
) {}
