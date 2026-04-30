package com.yclaims.notifications.infrastructure.kafka;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.contracts.events.v1.ClaimStatusChangedPayload;
import com.yclaims.contracts.events.v1.PaymentSettledPayload;
import com.yclaims.contracts.events.v1.RepairStatusUpdatedPayload;
import com.yclaims.notifications.application.NotificationApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Kafka consumers for the notifications module.
 * Subscribes to claim-events, payment-events, and repair-events topics.
 *
 * Idempotent consumer: every event deduplicated via Redis SETNX on eventId.
 * Duplicate delivery (Kafka at-least-once) is silently skipped — no duplicate notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimEventConsumer {

    private final NotificationApplicationService notificationService;
    private final RedisTemplate<String, String> stringRedisTemplate;

    @KafkaListener(
        topics = "claim-events",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleClaimEvent(DomainEvent<?> event) {
        if (!deduplicate(event.eventId())) {
            log.debug("Skipping duplicate event [{}] type={}", event.eventId(), event.eventType());
            return;
        }

        log.info("Processing event [{}] type={} correlationId={}",
                event.eventId(), event.eventType(), event.correlationId());

        switch (event.eventType()) {
            case "claim.created" -> {
                if (event.payload() instanceof ClaimCreatedPayload payload) {
                    notificationService.sendClaimSubmittedNotification(payload, event.correlationId());
                }
            }
            case "claim.status.changed" -> {
                if (event.payload() instanceof ClaimStatusChangedPayload payload) {
                    notificationService.sendStatusChangeNotification(payload, event.correlationId());
                }
            }
            default -> log.debug("No notification handler for claim event type: {}", event.eventType());
        }
    }

    @KafkaListener(
        topics = "payment-events",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(DomainEvent<?> event) {
        if (!deduplicate(event.eventId())) return;

        if ("payment.settled".equals(event.eventType()) &&
                event.payload() instanceof PaymentSettledPayload payload) {
            notificationService.sendPaymentConfirmation(payload, event.correlationId());
        }
    }

    @KafkaListener(
        topics = "repair-events",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleRepairEvent(DomainEvent<?> event) {
        if (!deduplicate(event.eventId())) return;

        log.info("Processing repair event [{}] type={}", event.eventId(), event.eventType());

        if ("repair.status.updated".equals(event.eventType()) &&
                event.payload() instanceof RepairStatusUpdatedPayload payload) {
            notificationService.sendRepairStatusNotification(payload, event.correlationId());
        }
    }

    /**
     * SETNX — atomic set-if-not-exists. Returns true if this is the first processing.
     * Dedup key expires after 24 hours — sufficient for Kafka redelivery window.
     */
    private boolean deduplicate(String eventId) {
        String key = "kafka:processed:" + eventId;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(24));
        return Boolean.TRUE.equals(isNew);
    }
}
