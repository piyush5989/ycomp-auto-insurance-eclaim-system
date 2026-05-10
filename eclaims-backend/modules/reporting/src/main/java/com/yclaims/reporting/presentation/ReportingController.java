package com.yclaims.reporting.presentation;

import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.reporting.application.ReportingApplicationService;
import com.yclaims.reporting.presentation.dto.CaseManagerReportResponse;
import com.yclaims.reporting.presentation.dto.ClaimsKpiResponse;
import com.yclaims.reporting.presentation.dto.FraudAgeingResponse;
import com.yclaims.reporting.presentation.dto.RegionalKpiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Pre-aggregated report endpoints; read-model snapshots materialised by Kafka consumers. */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reporting", description = "KPI reports, regional reports, fraud ageing")
public class ReportingController {

    private final ReportingApplicationService reportingService;

    @GetMapping("/kpi")
    @PreAuthorize("hasAnyRole('CASE_MANAGER', 'REGIONAL_MGR', 'TOP_MANAGEMENT', 'AUDITOR')")
    @Operation(summary = "Get claims KPI summary — pre-aggregated, cached snapshot")
    public ResponseEntity<ApiResponse<ClaimsKpiResponse>> getKpiSummary(
            @RequestParam(defaultValue = "global") String region) {
        ClaimsKpiResponse response = reportingService.getKpiSummary(region, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/fraud-ageing")
    @PreAuthorize("hasAnyRole('REGIONAL_MGR', 'TOP_MANAGEMENT', 'AUDITOR')")
    @Operation(summary = "Fraud ageing report — claims flagged for fraud by age bucket")
    public ResponseEntity<ApiResponse<List<FraudAgeingResponse>>> getFraudAgeing() {
        List<FraudAgeingResponse> response = reportingService.getFraudAgeing(correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/regional")
    @PreAuthorize("hasAnyRole('REGIONAL_MGR', 'TOP_MANAGEMENT', 'AUDITOR')")
    @Operation(summary = "Get regional KPI summary for a specific region")
    public ResponseEntity<ApiResponse<RegionalKpiResponse>> getRegionalKpi(
            @RequestParam String region) {
        RegionalKpiResponse response = reportingService.getRegionalKpi(region, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/regional/all")
    @PreAuthorize("hasRole('TOP_MANAGEMENT')")
    @Operation(summary = "Get KPI comparison across all regions for top management")
    public ResponseEntity<ApiResponse<List<RegionalKpiResponse>>> getAllRegionalKpis() {
        List<RegionalKpiResponse> response = reportingService.getAllRegionalKpis(correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/my-claims")
    @PreAuthorize("hasRole('CASE_MANAGER')")
    @Operation(summary = "Case Manager personal claims report — metrics for claims they have handled")
    public ResponseEntity<ApiResponse<CaseManagerReportResponse>> getMyCaseManagerReport(
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        CaseManagerReportResponse response = reportingService.getCaseManagerReport(userId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
