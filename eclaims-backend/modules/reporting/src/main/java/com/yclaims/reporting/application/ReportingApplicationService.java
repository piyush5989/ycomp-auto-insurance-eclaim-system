package com.yclaims.reporting.application;

import com.yclaims.reporting.infrastructure.persistence.ClaimKpiSnapshotRepository;
import com.yclaims.reporting.presentation.dto.CaseManagerReportResponse;
import com.yclaims.reporting.presentation.dto.ClaimsKpiResponse;
import com.yclaims.reporting.presentation.dto.FraudAgeingResponse;
import com.yclaims.reporting.presentation.dto.RegionalKpiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Serves pre-aggregated KPI snapshots (global, regional) and a live case-manager report.
 * Snapshots are populated by the Kafka consumer; cache TTL keeps reads cheap.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingApplicationService {

    private final ClaimKpiSnapshotRepository kpiRepository;

    @Cacheable(value = "report", key = "'kpi:' + #region", unless = "#result == null")
    @Transactional(readOnly = true)
    public ClaimsKpiResponse getKpiSummary(String region, String correlationId) {
        log.debug("[{}] Fetching KPI snapshot for region={}", correlationId, region);
        return kpiRepository.getLatestSnapshot(region).orElseGet(() -> {
            log.warn("No KPI snapshot found for region {} — returning empty summary", region);
            return ClaimsKpiResponse.builder()
                    .region(region)
                    .totalClaims(0).submittedToday(0).pendingAssignment(0)
                    .fraudFlagged(0).totalSettledAmount(BigDecimal.ZERO)
                    .generatedAt(Instant.now())
                    .build();
        });
    }

    @Transactional(readOnly = true)
    public CaseManagerReportResponse getCaseManagerReport(String caseManagerUserId, String correlationId) {
        log.debug("[{}] Fetching case manager report for userId={}", correlationId, caseManagerUserId);
        return kpiRepository.getCaseManagerReport(caseManagerUserId);
    }

    @Cacheable(value = "report", key = "'regional:' + #region", unless = "#result == null")
    @Transactional(readOnly = true)
    public RegionalKpiResponse getRegionalKpi(String region, String correlationId) {
        log.debug("[{}] Fetching regional KPI for region={}", correlationId, region);
        return kpiRepository.getLatestRegionalSnapshot(region).orElseGet(() -> {
            log.warn("No regional snapshot found for region {} — returning empty", region);
            return RegionalKpiResponse.builder()
                    .region(region)
                    .totalClaims(0).submittedToday(0).pendingAssignment(0)
                    .underSurvey(0).underAdjudication(0)
                    .approvedThisMonth(0).rejectedThisMonth(0).settledThisMonth(0)
                    .totalSettledAmount(BigDecimal.ZERO)
                    .avgProcessingTimeHours(BigDecimal.ZERO)
                    .fraudFlagged(0)
                    .generatedAt(Instant.now())
                    .build();
        });
    }

    @Cacheable(value = "report", key = "'all-regions'", unless = "#result == null || #result.isEmpty()")
    @Transactional(readOnly = true)
    public List<RegionalKpiResponse> getAllRegionalKpis(String correlationId) {
        log.debug("[{}] Fetching all regional KPIs", correlationId);
        List<RegionalKpiResponse> snapshots = kpiRepository.getAllRegionalSnapshots();
        if (!snapshots.isEmpty()) {
            return snapshots;
        }
        // Fallback: return empty shells so the UI always has rows for all regions
        log.warn("[{}] No regional snapshots found — returning empty shells", correlationId);
        return List.of("EAST", "WEST", "NORTH", "SOUTH", "CENTRAL").stream()
                .map(region -> getRegionalKpi(region, correlationId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FraudAgeingResponse> getFraudAgeing(String correlationId) {
        log.debug("[{}] Fetching fraud ageing report", correlationId);
        return kpiRepository.getFraudAgeingReport();
    }
}
