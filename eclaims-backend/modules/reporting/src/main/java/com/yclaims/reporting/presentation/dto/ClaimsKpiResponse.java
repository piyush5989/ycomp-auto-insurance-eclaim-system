package com.yclaims.reporting.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Claims KPI summary used by the global / case-manager KPI dashboard.
 *
 * Mirrors {@link RegionalKpiResponse} - the {@code @NoArgsConstructor}
 * + {@code @Setter} pair is required so Jackson can re-hydrate the
 * cached JSON on a Redis cache hit.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
