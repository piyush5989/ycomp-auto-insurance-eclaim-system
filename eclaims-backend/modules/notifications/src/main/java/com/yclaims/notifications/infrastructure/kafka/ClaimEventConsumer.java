package com.yclaims.notifications.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.ClaimAdjudicatedPayload;
import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.contracts.events.v1.ClaimStatusChangedPayload;
import com.yclaims.contracts.events.v1.NotificationRequestedPayload;
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
    private final ObjectMapper objectMapper;

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
                try {
                    ClaimCreatedPayload payload =
                            objectMapper.convertValue(event.payload(), ClaimCreatedPayload.class);
                    notificationService.sendClaimSubmittedNotification(payload, event.correlationId());
                } catch (IllegalArgumentException e) {
                    log.error("Failed to deserialise claim.created payload [{}]: {}",
                            event.eventId(), e.getMessage());
                }
            }
            case "claim.status.changed" -> {
                try {
                    ClaimStatusChangedPayload payload =
                            objectMapper.convertValue(event.payload(), ClaimStatusChangedPayload.class);
                    notificationService.sendStatusChangeNotification(payload, event.correlationId());
                } catch (IllegalArgumentException e) {
                    log.error("Failed to deserialise claim.status.changed payload [{}]: {}",
                            event.eventId(), e.getMessage());
                }
            }
            case "claim.adjudicated" -> {
                try {
                    ClaimAdjudicatedPayload payload =
                            objectMapper.convertValue(event.payload(), ClaimAdjudicatedPayload.class);
                    notificationService.sendClaimAdjudicatedNotification(payload, event.correlationId());
                } catch (IllegalArgumentException e) {
                    log.error("Failed to deserialise claim.adjudicated payload [{}]: {}",
                            event.eventId(), e.getMessage());
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

        if ("payment.settled".equals(event.eventType())) {
            try {
                PaymentSettledPayload payload =
                        objectMapper.convertValue(event.payload(), PaymentSettledPayload.class);
                notificationService.sendPaymentConfirmation(payload, event.correlationId());
            } catch (IllegalArgumentException e) {
                log.error("Failed to deserialise payment.settled payload [{}]: {}",
                        event.eventId(), e.getMessage());
            }
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

        if ("repair.status.updated".equals(event.eventType())) {
            try {
                RepairStatusUpdatedPayload payload =
                        objectMapper.convertValue(event.payload(), RepairStatusUpdatedPayload.class);
                notificationService.sendRepairStatusNotification(payload, event.correlationId());
            } catch (IllegalArgumentException e) {
                log.error("Failed to deserialise repair.status.updated payload [{}]: {}",
                        event.eventId(), e.getMessage());
            }
        }
    }

    /**
     * Consumes notification.requested events published by AutoAssignmentService
     * (e.g. SURVEYOR_ASSIGNED, ADJUSTOR_ASSIGNED after vehicle drop-off / survey completion).
     * Previously this topic had no consumer — in-app notifications were silently dropped.
     */
    @KafkaListener(
        topics = "notification-events",
        groupId = "notification-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleNotificationEvent(DomainEvent<?> event) {
        if (!deduplicate(event.eventId())) return;

        log.info("Processing notification event [{}] type={}", event.eventId(), event.eventType());

        if ("notification.requested".equals(event.eventType())) {
            try {
                NotificationRequestedPayload payload =
                        objectMapper.convertValue(event.payload(), NotificationRequestedPayload.class);
                notificationService.sendNotificationRequested(payload, event.correlationId());
            } catch (IllegalArgumentException e) {
                log.error("Failed to deserialise notification.requested payload [{}]: {}",
                        event.eventId(), e.getMessage());
            }
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
