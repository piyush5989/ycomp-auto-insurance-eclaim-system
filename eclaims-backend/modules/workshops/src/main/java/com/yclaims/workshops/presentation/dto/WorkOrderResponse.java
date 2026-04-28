package com.yclaims.workshops.presentation.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Builder
public class WorkOrderResponse {
    private UUID workOrderId;
    private UUID claimId;
    private UUID workshopId;
    private String workshopName;
    private BigDecimal estimatedCost;
    private BigDecimal finalCost;
    private String repairStatus;
    private LocalDate estimatedCompletionDate;
    private String workDescription;
    private Instant createdAt;
    private Instant updatedAt;
}
