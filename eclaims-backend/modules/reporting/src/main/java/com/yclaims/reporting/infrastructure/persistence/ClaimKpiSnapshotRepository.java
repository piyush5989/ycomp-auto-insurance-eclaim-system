package com.yclaims.reporting.infrastructure.persistence;

import com.yclaims.reporting.presentation.dto.ClaimsKpiResponse;
import com.yclaims.reporting.presentation.dto.FraudAgeingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Read model repository for the reporting module.
 * Queries the reporting.claim_kpi_snapshots table — pre-aggregated by Kafka consumer.
 * No writes from this class — it is a pure read model.
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
