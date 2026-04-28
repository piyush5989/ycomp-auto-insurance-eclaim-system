package com.yclaims.reporting.application;

import com.yclaims.reporting.infrastructure.persistence.ClaimKpiSnapshotRepository;
import com.yclaims.reporting.presentation.dto.ClaimsKpiResponse;
import com.yclaims.reporting.presentation.dto.FraudAgeingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reporting service — serves pre-aggregated read model snapshots.
 * KPI data is materialised by Kafka consumer (ClaimEventReportConsumer) into reporting schema.
 * Reports are NEVER generated on-demand — always served from cache or read model.
 *
 * Cache: report:kpi:{region}:{yyyyMM} — TTL 15 minutes
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

        // Serve from pre-aggregated read model
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
    public List<FraudAgeingResponse> getFraudAgeing(String correlationId) {
        log.debug("[{}] Fetching fraud ageing report", correlationId);
        return kpiRepository.getFraudAgeingReport();
    }
}
