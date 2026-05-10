package com.yclaims.reporting.infrastructure.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recomputes the reporting read-model snapshots from live OLTP data once per minute.
 *
 * <p>Production design (per the 23_reporting_kpi_seed.sql header) is for a Kafka
 * {@code ClaimEventReportConsumer} to update these snapshots reactively on every
 * domain event. For the POC we approximate that with a simple time-driven job
 * that runs the same aggregation SQL the seed migration uses, so dashboards
 * reflect real claim activity without requiring the Kafka consumer to be wired.
 *
 * <p>Behavior:
 * <ul>
 *   <li>Every 60s: append a fresh snapshot row per region. Existing rows stay -
 *       repository {@code DISTINCT ON (region) ORDER BY generated_at DESC} picks
 *       the newest, so callers always see the latest aggregation.</li>
 *   <li>The five canonical regions (EAST/WEST/NORTH/SOUTH/CENTRAL) are cross-joined
 *       so a region with zero real claims still produces a row of zeros. This keeps
 *       the dashboard layout stable.</li>
 *   <li>After insert, the {@code report} Spring cache is cleared so the next
 *       request reflects the new numbers without waiting for the 15-minute TTL.</li>
 *   <li>Rows older than 6 hours are pruned to keep the table bounded.</li>
 * </ul>
 *
 * <p>This is intentionally a thin scheduled job, not a CDC pipeline. Replacing it
 * with a Kafka consumer is a drop-in change - the SQL stays the same, only the
 * trigger differs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaimKpiSnapshotRefreshJob {

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long INITIAL_DELAY_MS = 15_000L;

    private final JdbcTemplate jdbcTemplate;
    private final CacheManager cacheManager;

    @Scheduled(fixedDelay = ONE_MINUTE_MS, initialDelay = INITIAL_DELAY_MS)
    @Transactional
    public void refreshSnapshots() {
        long start = System.currentTimeMillis();
        try {
            int globalRows = refreshGlobalSnapshot();
            int regionalRows = refreshRegionalSnapshots();
            pruneOldSnapshots();
            evictReportCache();
            log.info("KPI snapshot refresh complete: global={}, regional={}, took={}ms",
                    globalRows, regionalRows, System.currentTimeMillis() - start);
        } catch (RuntimeException ex) {
            log.warn("KPI snapshot refresh failed: {} - dashboards will keep last successful snapshot",
                    ex.getMessage());
        }
    }

    private int refreshGlobalSnapshot() {
        return jdbcTemplate.update("""
                INSERT INTO reporting.claim_kpi_snapshots (
                    region, total_claims, submitted_today, pending_assignment,
                    under_survey, under_adjudication, approved_this_month,
                    rejected_this_month, settled_this_month, total_settled_amount,
                    average_cycle_hours, fraud_flagged, generated_at
                )
                SELECT
                    'global',
                    COUNT(*),
                    COUNT(*) FILTER (WHERE date_trunc('day', created_at) = date_trunc('day', NOW())),
                    COUNT(*) FILTER (WHERE status = 'SUBMITTED'),
                    COUNT(*) FILTER (WHERE status = 'UNDER_SURVEY'),
                    COUNT(*) FILTER (WHERE status = 'UNDER_ADJUDICATION'),
                    COUNT(*) FILTER (WHERE status = 'APPROVED'
                        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
                    COUNT(*) FILTER (WHERE status = 'REJECTED'
                        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
                    COUNT(*) FILTER (WHERE status = 'SETTLED'
                        AND date_trunc('month', updated_at) = date_trunc('month', NOW())),
                    COALESCE(SUM(approved_amount) FILTER (WHERE status = 'SETTLED'), 0),
                    COALESCE(AVG(EXTRACT(EPOCH FROM (updated_at - created_at)) / 3600)
                        FILTER (WHERE status IN ('SETTLED', 'REJECTED')), 0),
                    COUNT(*) FILTER (WHERE fraud_flag = true),
                    NOW()
                FROM claims.claims
                """);
    }

    private int refreshRegionalSnapshots() {
        return jdbcTemplate.update("""
                INSERT INTO reporting.regional_kpi_snapshots (
                    region, total_claims, submitted_today, pending_assignment,
                    under_survey, under_adjudication, approved_this_month,
                    rejected_this_month, settled_this_month, total_settled_amount,
                    avg_processing_time_hours, fraud_flagged, generated_at
                )
                SELECT
                    r.region,
                    COUNT(c.id),
                    COUNT(c.id) FILTER (WHERE date_trunc('day', c.created_at) = date_trunc('day', NOW())),
                    COUNT(c.id) FILTER (WHERE c.status = 'SUBMITTED'),
                    COUNT(c.id) FILTER (WHERE c.status = 'UNDER_SURVEY'),
                    COUNT(c.id) FILTER (WHERE c.status = 'UNDER_ADJUDICATION'),
                    COUNT(c.id) FILTER (WHERE c.status = 'APPROVED'
                        AND date_trunc('month', c.updated_at) = date_trunc('month', NOW())),
                    COUNT(c.id) FILTER (WHERE c.status = 'REJECTED'
                        AND date_trunc('month', c.updated_at) = date_trunc('month', NOW())),
                    COUNT(c.id) FILTER (WHERE c.status = 'SETTLED'
                        AND date_trunc('month', c.updated_at) = date_trunc('month', NOW())),
                    COALESCE(SUM(c.approved_amount) FILTER (WHERE c.status = 'SETTLED'), 0),
                    COALESCE(AVG(EXTRACT(EPOCH FROM (c.updated_at - c.created_at)) / 3600)
                        FILTER (WHERE c.status IN ('SETTLED', 'REJECTED')), 0),
                    COUNT(c.id) FILTER (WHERE c.fraud_flag = true),
                    NOW()
                FROM (VALUES ('EAST'),('WEST'),('NORTH'),('SOUTH'),('CENTRAL')) AS r(region)
                LEFT JOIN claims.claims c ON UPPER(c.region) = r.region
                GROUP BY r.region
                """);
    }

    private void pruneOldSnapshots() {
        jdbcTemplate.update("""
                DELETE FROM reporting.claim_kpi_snapshots
                 WHERE generated_at < NOW() - INTERVAL '6 hours'
                """);
        jdbcTemplate.update("""
                DELETE FROM reporting.regional_kpi_snapshots
                 WHERE generated_at < NOW() - INTERVAL '6 hours'
                """);
    }

    private void evictReportCache() {
        Cache reportCache = cacheManager.getCache("report");
        if (reportCache == null) {
            return;
        }
        try {
            reportCache.clear();
        } catch (RuntimeException ex) {
            log.debug("Could not clear 'report' cache after refresh: {}", ex.getMessage());
        }
    }
}
