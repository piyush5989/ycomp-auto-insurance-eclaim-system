package com.yclaims.reporting.presentation.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Case Manager personal claims report.
 * Shows metrics for claims the case manager has handled (overridden or directly adjudicated).
 */
@Getter
@Builder
public class CaseManagerReportResponse {
    private String caseManagerId;
    private long totalReceived;
    private long totalSettled;
    private long totalRejected;
    private long inProgress;
    private long fraudFlagged;
    private BigDecimal totalPaidOut;
    private BigDecimal avgProcessingHours;
    private long submittedThisMonth;
    private Instant generatedAt;
}
