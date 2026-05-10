package com.yclaims.reporting.presentation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Regional KPI summary for the regional manager and top management dashboards.
 *
 * Notes on the Lombok annotation set:
 *
 * - Intentionally a regular class (not a Java record) because this DTO is
 *   stored in the Redis-backed {@code report} cache via
 *   {@code GenericJackson2JsonRedisSerializer} with Jackson default-typing
 *   set to {@code NON_FINAL}. Records are implicitly {@code final}, so
 *   Jackson would skip writing {@code @class} metadata at the root and the
 *   value would not round-trip on the next cache hit.
 *
 * - {@code @NoArgsConstructor} + {@code @Setter} are needed by Jackson to
 *   instantiate the object during deserialization (cache read). Without
 *   them Jackson reports "no Creators, like default constructor, exist".
 *
 * - {@code @AllArgsConstructor} keeps the Lombok builder happy.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionalKpiResponse {
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
    private BigDecimal avgProcessingTimeHours;
    private long fraudFlagged;
    private Instant generatedAt;
}
