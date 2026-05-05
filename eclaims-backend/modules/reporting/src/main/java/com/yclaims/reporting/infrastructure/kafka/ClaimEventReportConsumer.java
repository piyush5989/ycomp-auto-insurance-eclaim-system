package com.yclaims.reporting.infrastructure.kafka;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.contracts.events.v1.ClaimStatusChangedPayload;
import com.yclaims.contracts.events.v1.PaymentSettledPayload;
import com.yclaims.reporting.infrastructure.persistence.KpiSnapshotWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka consumer that materialises the reporting.claim_kpi_snapshots read model.
 *
 * This was the missing piece — ReportingApplicationService could READ the snapshots
 * but nothing was WRITING them from the event stream. This consumer closes that gap.
 *
 * Design:
 *   - Separate consumer group "reporting-service" — its own Kafka offset, independent lag
 *   - Idempotent via Redis SETNX on eventId (same pattern as notification-service)
 *   - Upserts KPI snapshot for the "global" region on every claim event
 *   - For production: extend to per-region snapshots using claim metadata
 *
 * Topics consumed:
 *   - claim-events  → updates claim count, status distribution, fraud flags
 *   - payment-events → updates total_settled_amount, settled_this_month
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimEventReportConsumer {

    private final KpiSnapshotWriter snapshotWriter;
    private final RedisTemplate<String, String> stringRedisTemplate;

    @KafkaListener(
        topics = "claim-events",
        groupId = "reporting-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onClaimEvent(DomainEvent<?> event) {
        if (!deduplicate(event.eventId())) {
            log.debug("Reporting: skipping duplicate event [{}]", event.eventId());
            return;
        }

        switch (event.eventType()) {
            case "claim.created" -> {
                if (event.payload() instanceof ClaimCreatedPayload payload) {
                    log.info("Reporting: incrementing total_claims for claim {}", payload.claimId());
                    snapshotWriter.incrementTotalClaims("global");
                    snapshotWriter.incrementSubmittedToday("global");
                    snapshotWriter.incrementPendingAssignment("global");
                }
            }
            case "claim.status.changed" -> {
                if (event.payload() instanceof ClaimStatusChangedPayload payload) {
                    log.debug("Reporting: handling status change {} → {} for claim {}",
                            payload.previousStatus(), payload.newStatus(), payload.claimId());
                    snapshotWriter.handleStatusTransition("global",
                            payload.previousStatus(), payload.newStatus());
                }
            }
            default -> { /* other claim events not relevant for KPI snapshots */ }
        }
    }

    @KafkaListener(
        topics = "payment-events",
        groupId = "reporting-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(DomainEvent<?> event) {
        if (!deduplicate(event.eventId())) return;

        if ("payment.settled".equals(event.eventType()) &&
                event.payload() instanceof PaymentSettledPayload payload) {
            log.info("Reporting: recording settlement of {} {} for claim {}",
                    payload.amount(), payload.currency(), payload.claimId());
            snapshotWriter.recordSettlement("global", payload.amount());
        }
    }

    private boolean deduplicate(String eventId) {
        String key = "kafka:processed:reporting:" + eventId;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(24));
        return Boolean.TRUE.equals(isNew);
    }
}
