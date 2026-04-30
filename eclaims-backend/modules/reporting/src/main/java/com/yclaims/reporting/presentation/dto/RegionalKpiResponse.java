package com.yclaims.reporting.presentation.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Regional KPI summary for regional manager dashboards
 */
@Builder
public record RegionalKpiResponse(
        String region,
        long totalClaims,
        long submittedToday,
        long pendingAssignment,
        long underSurvey,
        long underAdjudication,
        long approvedThisMonth,
        long rejectedThisMonth,
        long settledThisMonth,
        BigDecimal totalSettledAmount,
        BigDecimal avgProcessingTimeHours,
        long fraudFlagged,
        Instant generatedAt
) {}
