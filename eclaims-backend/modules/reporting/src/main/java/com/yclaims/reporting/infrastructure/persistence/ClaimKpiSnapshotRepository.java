package com.yclaims.reporting.infrastructure.persistence;

import com.yclaims.reporting.presentation.dto.CaseManagerReportResponse;
import com.yclaims.reporting.presentation.dto.ClaimsKpiResponse;
import com.yclaims.reporting.presentation.dto.FraudAgeingResponse;
import com.yclaims.reporting.presentation.dto.RegionalKpiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read model repository for the reporting module.
 * All queries are read-only — data is materialised by Kafka consumers or DB seed migrations.
 * Three read models served here:
 *   1. claim_kpi_snapshots        — global/region aggregates (Case Manager + KPI dashboard)
 *   2. regional_kpi_snapshots     — per-region aggregates (Regional Manager dashboard)
 *   3. Live claims queries         — fraud ageing, case manager personal report
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ClaimKpiSnapshotRepository {

    private final JdbcTemplate jdbcTemplate;

    public Optional<ClaimsKpiResponse> getLatestSnapshot(String region) {
        try {
            ClaimsKpiResponse snapshot = jdbcTemplate.queryForObject(
                    """
                    SELECT region, total_claims, submitted_today, pending_assignment,
                           under_survey, under_adjudication, approved_this_month,
                           rejected_this_month, settled_this_month, total_settled_amount,
                           average_cycle_hours, fraud_flagged, generated_at
                    FROM reporting.claim_kpi_snapshots
                    WHERE region = ?
                    ORDER BY generated_at DESC
                    LIMIT 1
                    """,
                    (rs, rn) -> ClaimsKpiResponse.builder()
                            .region(rs.getString("region"))
                            .totalClaims(rs.getLong("total_claims"))
                            .submittedToday(rs.getLong("submitted_today"))
                            .pendingAssignment(rs.getLong("pending_assignment"))
                            .underSurvey(rs.getLong("under_survey"))
                            .underAdjudication(rs.getLong("under_adjudication"))
                            .approvedThisMonth(rs.getLong("approved_this_month"))
                            .rejectedThisMonth(rs.getLong("rejected_this_month"))
                            .settledThisMonth(rs.getLong("settled_this_month"))
                            .totalSettledAmount(rs.getBigDecimal("total_settled_amount"))
                            .averageClaimCycleHours(rs.getDouble("average_cycle_hours"))
                            .fraudFlagged(rs.getLong("fraud_flagged"))
                            .generatedAt(rs.getTimestamp("generated_at").toInstant())
                            .build(),
                    region);
            return Optional.ofNullable(snapshot);
        } catch (Exception e) {
            log.debug("No KPI snapshot found for region {}: {}", region, e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<RegionalKpiResponse> getLatestRegionalSnapshot(String region) {
        try {
            RegionalKpiResponse snapshot = jdbcTemplate.queryForObject(
                    """
                    SELECT region, total_claims, submitted_today, pending_assignment,
                           under_survey, under_adjudication, approved_this_month,
                           rejected_this_month, settled_this_month, total_settled_amount,
                           avg_processing_time_hours, fraud_flagged, generated_at
                    FROM reporting.regional_kpi_snapshots
                    WHERE region = ?
                    ORDER BY generated_at DESC
                    LIMIT 1
                    """,
                    (rs, rn) -> RegionalKpiResponse.builder()
                            .region(rs.getString("region"))
                            .totalClaims(rs.getLong("total_claims"))
                            .submittedToday(rs.getLong("submitted_today"))
                            .pendingAssignment(rs.getLong("pending_assignment"))
                            .underSurvey(rs.getLong("under_survey"))
                            .underAdjudication(rs.getLong("under_adjudication"))
                            .approvedThisMonth(rs.getLong("approved_this_month"))
                            .rejectedThisMonth(rs.getLong("rejected_this_month"))
                            .settledThisMonth(rs.getLong("settled_this_month"))
                            .totalSettledAmount(rs.getBigDecimal("total_settled_amount"))
                            .avgProcessingTimeHours(rs.getBigDecimal("avg_processing_time_hours"))
                            .fraudFlagged(rs.getLong("fraud_flagged"))
                            .generatedAt(rs.getTimestamp("generated_at").toInstant())
                            .build(),
                    region);
            return Optional.ofNullable(snapshot);
        } catch (Exception e) {
            log.debug("No regional KPI snapshot found for region {}: {}", region, e.getMessage());
            return Optional.empty();
        }
    }

    public List<RegionalKpiResponse> getAllRegionalSnapshots() {
        return jdbcTemplate.query(
                """
                SELECT DISTINCT ON (region)
                    region, total_claims, submitted_today, pending_assignment,
                    under_survey, under_adjudication, approved_this_month,
                    rejected_this_month, settled_this_month, total_settled_amount,
                    avg_processing_time_hours, fraud_flagged, generated_at
                FROM reporting.regional_kpi_snapshots
                ORDER BY region, generated_at DESC
                """,
                (rs, rn) -> RegionalKpiResponse.builder()
                        .region(rs.getString("region"))
                        .totalClaims(rs.getLong("total_claims"))
                        .submittedToday(rs.getLong("submitted_today"))
                        .pendingAssignment(rs.getLong("pending_assignment"))
                        .underSurvey(rs.getLong("under_survey"))
                        .underAdjudication(rs.getLong("under_adjudication"))
                        .approvedThisMonth(rs.getLong("approved_this_month"))
                        .rejectedThisMonth(rs.getLong("rejected_this_month"))
                        .settledThisMonth(rs.getLong("settled_this_month"))
                        .totalSettledAmount(rs.getBigDecimal("total_settled_amount"))
                        .avgProcessingTimeHours(rs.getBigDecimal("avg_processing_time_hours"))
                        .fraudFlagged(rs.getLong("fraud_flagged"))
                        .generatedAt(rs.getTimestamp("generated_at").toInstant())
                        .build());
    }

    /** Live query — shows claims where this case manager acted as adjustor or overrode a decision. */
    public CaseManagerReportResponse getCaseManagerReport(String caseManagerUserId) {
        String sql = """
                SELECT
                    COUNT(*)                                                           AS total_received,
                    COUNT(*) FILTER (WHERE status = 'SETTLED')                        AS total_settled,
                    COUNT(*) FILTER (WHERE status = 'REJECTED')                       AS total_rejected,
                    COUNT(*) FILTER (WHERE status IN ('SUBMITTED','ASSIGNED',
                        'UNDER_SURVEY','UNDER_ADJUDICATION','APPROVED'))               AS in_progress,
                    COUNT(*) FILTER (WHERE fraud_flag = true)                         AS fraud_flagged,
                    COALESCE(SUM(approved_amount) FILTER (WHERE status = 'SETTLED'), 0) AS total_paid_out,
                    COALESCE(AVG(
                        EXTRACT(EPOCH FROM (updated_at - created_at)) / 3600
                    ) FILTER (WHERE status IN ('SETTLED','REJECTED')), 0)             AS avg_processing_hours,
                    COUNT(*) FILTER (
                        WHERE date_trunc('month', created_at) = date_trunc('month', NOW())
                    )                                                                  AS submitted_this_month
                FROM claims.claims
                WHERE override_by_user_id = ?
                   OR assigned_adjustor_id = ?
                """;

        return jdbcTemplate.queryForObject(sql,
                (rs, rn) -> CaseManagerReportResponse.builder()
                        .caseManagerId(caseManagerUserId)
                        .totalReceived(rs.getLong("total_received"))
                        .totalSettled(rs.getLong("total_settled"))
                        .totalRejected(rs.getLong("total_rejected"))
                        .inProgress(rs.getLong("in_progress"))
                        .fraudFlagged(rs.getLong("fraud_flagged"))
                        .totalPaidOut(rs.getBigDecimal("total_paid_out"))
                        .avgProcessingHours(rs.getBigDecimal("avg_processing_hours"))
                        .submittedThisMonth(rs.getLong("submitted_this_month"))
                        .generatedAt(Instant.now())
                        .build(),
                caseManagerUserId, caseManagerUserId);
    }

    public List<FraudAgeingResponse> getFraudAgeingReport() {
        return jdbcTemplate.query(
                """
                SELECT c.id as claim_id, c.policy_number, c.fraud_reason,
                       EXTRACT(EPOCH FROM (NOW() - c.created_at)) / 3600 as age_hours,
                       c.assessed_amount, c.status
                FROM claims.claims c
                WHERE c.fraud_flag = true
                  AND c.status NOT IN ('SETTLED', 'ARCHIVED', 'REJECTED')
                ORDER BY c.created_at ASC
                """,
                (rs, rn) -> {
                    long ageHours = rs.getLong("age_hours");
                    String bucket = ageHours < 1 ? "< 1hr"
                            : ageHours < 24 ? "1-24hrs"
                            : ageHours < 168 ? "1-7days" : "> 7days";
                    return FraudAgeingResponse.builder()
                            .claimId(rs.getObject("claim_id", java.util.UUID.class))
                            .policyNumber(rs.getString("policy_number"))
                            .fraudReason(rs.getString("fraud_reason"))
                            .ageInHours(ageHours)
                            .ageingBucket(bucket)
                            .assessedAmount(rs.getBigDecimal("assessed_amount"))
                            .currentStatus(rs.getString("status"))
                            .build();
                });
    }
}
