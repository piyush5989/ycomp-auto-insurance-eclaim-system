package com.yclaims.reporting.presentation;

import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.reporting.application.ReportingApplicationService;
import com.yclaims.reporting.presentation.dto.ClaimsKpiResponse;
import com.yclaims.reporting.presentation.dto.FraudAgeingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Reporting API — pre-aggregated read model.
 * Reports are never generated on-demand; they are materialised by Kafka consumers.
 * Complex reports: async batch — cached snapshots served here.
 * Target: simple KPI p95 < 2000ms.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "KPI reports, regional reports, fraud ageing")
public class ReportingController {

    private final ReportingApplicationService reportingService;

    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('REGIONAL_MGR','TOP_MANAGEMENT','AUDITOR')")
    @Operation(summary = "Get claims KPI summary — pre-aggregated, cached snapshot")
    public ResponseEntity<ApiResponse<ClaimsKpiResponse>> getKpiSummary(
            @RequestParam(defaultValue = "global") String region) {
        ClaimsKpiResponse response = reportingService.getKpiSummary(region, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/fraud-ageing")
    @PreAuthorize("hasAnyRole('AUDITOR','TOP_MANAGEMENT')")
    @Operation(summary = "Fraud ageing report — claims flagged for fraud by age bucket")
    public ResponseEntity<ApiResponse<List<FraudAgeingResponse>>> getFraudAgeing() {
        List<FraudAgeingResponse> response = reportingService.getFraudAgeing(correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
