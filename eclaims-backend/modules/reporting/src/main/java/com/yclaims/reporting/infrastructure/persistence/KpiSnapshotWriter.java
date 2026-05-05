package com.yclaims.reporting.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Write side of the reporting read model.
 * All methods are idempotency-safe (called from an already-deduplicated Kafka consumer).
 *
 * Strategy: UPSERT on (region) — insert a row if none exists, then UPDATE in place.
 * The snapshot is a rolling aggregate — it accumulates forever (total_claims, total_settled_amount)
 * while daily/monthly counters reset via a nightly batch job (future improvement).
 *
 * Cache eviction: after each write, evict the "report" cache so the next
 * ReportingApplicationService.getKpiSummary() call sees fresh data.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class KpiSnapshotWriter {

    private final JdbcTemplate jdbcTemplate;

    private static final String UPSERT_REGION_ROW = """
            INSERT INTO reporting.claim_kpi_snapshots
                (region, total_claims, submitted_today, pending_assignment,
                 under_survey, under_adjudication, approved_this_month,
                 rejected_this_month, settled_this_month, total_settled_amount,
                 average_cycle_hours, fraud_flagged, generated_at)
            VALUES (?, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, NOW())
            ON CONFLICT DO NOTHING
            """;

    @Transactional
    @CacheEvict(value = "report", key = "'kpi:' + #region")
    public void incrementTotalClaims(String region) {
        ensureRegionRow(region);
        jdbcTemplate.update("""
                UPDATE reporting.claim_kpi_snapshots
                SET total_claims = total_claims + 1, generated_at = ?
                WHERE region = ?
                """, Instant.now(), region);
    }

    @Transactional
    @CacheEvict(value = "report", key = "'kpi:' + #region")
    public void incrementSubmittedToday(String region) {
        ensureRegionRow(region);
        jdbcTemplate.update("""
                UPDATE reporting.claim_kpi_snapshots
                SET submitted_today = submitted_today + 1, generated_at = ?
                WHERE region = ?
                """, Instant.now(), region);
    }

    @Transactional
    @CacheEvict(value = "report", key = "'kpi:' + #region")
    public void incrementPendingAssignment(String region) {
        ensureRegionRow(region);
        jdbcTemplate.update("""
                UPDATE reporting.claim_kpi_snapshots
                SET pending_assignment = pending_assignment + 1, generated_at = ?
                WHERE region = ?
                """, Instant.now(), region);
    }

    /**
     * Adjusts counter columns based on the claim status transition.
     * Handles the movement of a claim from one "bucket" to another.
     */
    @Transactional
    @CacheEvict(value = "report", key = "'kpi:' + #region")
    public void handleStatusTransition(String region, String previousStatus, String newStatus) {
        ensureRegionRow(region);

        // Decrement the counter for the bucket the claim is leaving
        switch (previousStatus) {
            case "SUBMITTED" -> decrement(region, "pending_assignment");
            case "ASSIGNED", "UNDER_SURVEY" -> decrement(region, "under_survey");
            case "SURVEYED", "UNDER_ADJUDICATION" -> decrement(region, "under_adjudication");
            default -> { /* no tracked bucket */ }
        }

        // Increment the counter for the bucket the claim is entering
        switch (newStatus) {
            case "ASSIGNED" -> decrement(region, "pending_assignment"); // claim was assigned, no longer pending
            case "UNDER_SURVEY" -> increment(region, "under_survey");
            case "UNDER_ADJUDICATION" -> increment(region, "under_adjudication");
            case "APPROVED" -> increment(region, "approved_this_month");
            case "REJECTED" -> increment(region, "rejected_this_month");
            case "SETTLED" -> increment(region, "settled_this_month");
            default -> { /* WITHDRAWN, ARCHIVED — no specific KPI bucket */ }
        }

        jdbcTemplate.update("""
                UPDATE reporting.claim_kpi_snapshots
                SET generated_at = ? WHERE region = ?
                """, Instant.now(), region);
    }

    @Transactional
    @CacheEvict(value = "report", key = "'kpi:' + #region")
    public void recordSettlement(String region, BigDecimal amount) {
        ensureRegionRow(region);
        jdbcTemplate.update("""
                UPDATE reporting.claim_kpi_snapshots
                SET total_settled_amount = total_settled_amount + ?,
                    generated_at = ?
                WHERE region = ?
                """, amount, Instant.now(), region);
    }

    private void ensureRegionRow(String region) {
        jdbcTemplate.update(UPSERT_REGION_ROW, region);
    }

    private void increment(String region, String column) {
        jdbcTemplate.update(
                "UPDATE reporting.claim_kpi_snapshots SET " + column + " = " + column + " + 1 WHERE region = ?",
                region);
    }

    private void decrement(String region, String column) {
        jdbcTemplate.update(
                "UPDATE reporting.claim_kpi_snapshots SET " + column + " = GREATEST(" + column + " - 1, 0) WHERE region = ?",
                region);
    }
}
