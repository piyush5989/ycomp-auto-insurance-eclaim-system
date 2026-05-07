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
    @PreAuthorize("@authz.isAllowed('report', 'kpi')")
    @Operation(summary = "Get claims KPI summary — pre-aggregated, cached snapshot")
    public ResponseEntity<ApiResponse<ClaimsKpiResponse>> getKpiSummary(
            @RequestParam(defaultValue = "global") String region) {
        ClaimsKpiResponse response = reportingService.getKpiSummary(region, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/fraud-ageing")
    @PreAuthorize("@authz.isAllowed('report', 'fraud-ageing')")
    @Operation(summary = "Fraud ageing report — claims flagged for fraud by age bucket")
    public ResponseEntity<ApiResponse<List<FraudAgeingResponse>>> getFraudAgeing() {
        List<FraudAgeingResponse> response = reportingService.getFraudAgeing(correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/regional")
    @PreAuthorize("@authz.isAllowed('report', 'kpi-regional')")
    @Operation(summary = "Get regional KPI summary for a specific region")
    public ResponseEntity<ApiResponse<RegionalKpiResponse>> getRegionalKpi(
            @RequestParam String region) {
        RegionalKpiResponse response = reportingService.getRegionalKpi(region, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/regional/all")
    @PreAuthorize("@authz.isAllowed('report', 'all-regions')")
    @Operation(summary = "Get KPI comparison across all regions for top management")
    public ResponseEntity<ApiResponse<List<RegionalKpiResponse>>> getAllRegionalKpis() {
        List<RegionalKpiResponse> response = reportingService.getAllRegionalKpis(correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    /**
     * Case Manager personal report — scoped to the authenticated user's claims portfolio.
     * Only ROLE_CASE_MANAGER can access this endpoint.
     * The user ID is extracted from the JWT subject — no user-supplied parameter accepted.
     */
    @GetMapping("/my-claims")
    @PreAuthorize("hasRole('ROLE_CASE_MANAGER')")
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
