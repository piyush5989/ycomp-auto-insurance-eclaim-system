package com.yclaims.reporting.presentation.dto;

import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class ClaimsKpiResponse {
    private String region;
    private long totalClaims;
    private long submittedToday;
    private long pendingAssignment;
    private long underSurvey;
    private long underAdjudication;
    private long approvedThisMonth;
    private long rejectedThisMonth;
    private long settledThisMonth;
    private BigDecimal totalSettledAmount;
    private double averageClaimCycleHours;
    private long fraudFlagged;
    private Instant generatedAt;
}
