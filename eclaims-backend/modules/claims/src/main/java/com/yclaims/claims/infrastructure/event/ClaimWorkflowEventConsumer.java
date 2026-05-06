package com.yclaims.claims.infrastructure.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.claims.application.ClaimApplicationService;
import com.yclaims.claims.application.command.UpdateClaimStatusCommand;
import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.infrastructure.persistence.ClaimEntity;
import com.yclaims.claims.infrastructure.persistence.ClaimJpaRepository;
import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.AdjustorAssignedPayload;
import com.yclaims.contracts.events.v1.RentalSkippedPayload;
import com.yclaims.contracts.events.v1.RentalVehicleReservedPayload;
import com.yclaims.contracts.events.v1.SurveyorAssignedPayload;
import com.yclaims.contracts.events.v1.VehicleDroppedOffPayload;
import com.yclaims.contracts.events.v1.WorkshopSelectedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Claims-side consumer for workflow/workshop integration events.
 *
 * Enterprise pattern:
 * - Modules publish domain facts to Kafka
 * - The Claims module owns the claim aggregate and persists status transitions based on events
 * - Consumers are idempotent (Redis SETNX)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimWorkflowEventConsumer {

    private final ClaimApplicationService claimApplicationService;
    private final ClaimJpaRepository claimJpaRepository;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "claim-events",
            groupId = "claims-workflow-consumer",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handle(DomainEvent<?> event) {
        log.info("[{}] 📨 ClaimWorkflowEventConsumer received event: {} (type: {})", 
                event.correlationId(), event.eventId(), event.eventType());
        
        if (!deduplicate(event.eventId())) {
            log.debug("[{}] ⚠️  Event {} already processed (duplicate), skipping", event.correlationId(), event.eventId());
            return;
        }

        switch (event.eventType()) {
            case "workshop.selected" -> handleWorkshopSelected(event);
            case "vehicle.droppedoff" -> handleVehicleDroppedOff(event);
            case "surveyor.assigned" -> handleSurveyorAssigned(event);
            case "adjustor.assigned" -> handleAdjustorAssigned(event);
            case "rental.reserved" -> handleRentalReserved(event);
            case "rental.skipped" -> handleRentalSkipped(event);
            default -> log.debug("[{}] Ignoring event type: {}", event.correlationId(), event.eventType());
        }
    }

    private void handleWorkshopSelected(DomainEvent<?> event) {
        log.debug("[{}] handleWorkshopSelected called | payload type: {}", 
                event.correlationId(), event.payload().getClass().getName());
        
        WorkshopSelectedPayload payload = convertPayload(event.payload(), WorkshopSelectedPayload.class, event.correlationId());
        if (payload == null) {
            log.warn("[{}] workshop.selected payload conversion failed", event.correlationId());
            return;
        }

        Optional<ClaimEntity> maybe = claimJpaRepository.findById(payload.claimId());
        if (maybe.isEmpty()) {
            log.warn("[{}] workshop.selected for unknown claim {}", event.correlationId(), payload.claimId());
            return;
        }

        ClaimEntity entity = maybe.get();
        entity.updateFromDomain(
                ClaimStatus.WORKSHOP_SELECTED,
                entity.getAssignedSurveyorId(),
                entity.getAssignedAdjustorId(),
                entity.getAssessedAmount(),
                entity.getApprovedAmount(),
                payload.workshopId().toString(),
                entity.getRejectionReason(),
                entity.isFraudFlag(),
                entity.getFraudReason(),
                entity.getRegion(),
                entity.getOverrideByUserId(),
                entity.getOverrideReason(),
                entity.getOverrideAt(),
                entity.getRentalReservationId(),
                entity.getRentalStatus()
        );
        claimJpaRepository.save(entity);

        log.info("[{}] 📝 Claim {} updated from workshop.selected | status → WORKSHOP_SELECTED | workshopId={}",
                event.correlationId(), payload.claimId(), payload.workshopId());
    }

    private void handleVehicleDroppedOff(DomainEvent<?> event) {
        log.debug("[{}] handleVehicleDroppedOff called | payload type: {}", 
                event.correlationId(), event.payload().getClass().getName());
        
        VehicleDroppedOffPayload payload = convertPayload(event.payload(), VehicleDroppedOffPayload.class, event.correlationId());
        if (payload == null) {
            log.warn("[{}] vehicle.droppedoff payload conversion failed", event.correlationId());
            return;
        }

        Optional<ClaimEntity> maybe = claimJpaRepository.findById(payload.claimId());
        if (maybe.isEmpty()) {
            log.warn("[{}] vehicle.droppedoff for unknown claim {}", event.correlationId(), payload.claimId());
            return;
        }

        ClaimEntity entity = maybe.get();
        entity.updateFromDomain(
                ClaimStatus.VEHICLE_AT_WORKSHOP,
                entity.getAssignedSurveyorId(),
                entity.getAssignedAdjustorId(),
                entity.getAssessedAmount(),
                entity.getApprovedAmount(),
                entity.getWorkshopId(),
                entity.getRejectionReason(),
                entity.isFraudFlag(),
                entity.getFraudReason(),
                entity.getRegion(),
                entity.getOverrideByUserId(),
                entity.getOverrideReason(),
                entity.getOverrideAt(),
                entity.getRentalReservationId(),
                entity.getRentalStatus()
        );
        claimJpaRepository.save(entity);

        log.info("[{}] 📝 Claim {} updated from vehicle.droppedoff | status → VEHICLE_AT_WORKSHOP",
                event.correlationId(), payload.claimId());
    }

    private void handleSurveyorAssigned(DomainEvent<?> event) {
        SurveyorAssignedPayload payload = convertPayload(event.payload(), SurveyorAssignedPayload.class, event.correlationId());
        if (payload == null) {
            log.warn("[{}] surveyor.assigned payload conversion failed", event.correlationId());
            return;
        }
        // Route through the domain model — Claim.assignSurveyor() enforces the state machine
        // and registers domain events (ClaimAssignedEvent, ClaimStatusChangedEvent).
        claimApplicationService.updateClaimStatus(new UpdateClaimStatusCommand(
                payload.claimId(), ClaimStatus.ASSIGNED,
                payload.surveyorId().toString(), null, null, null,
                event.correlationId()
        ));
        log.info("[{}] Claim {} surveyor assigned via domain model | surveyorId={}",
                event.correlationId(), payload.claimId(), payload.surveyorId());
    }

    private void handleAdjustorAssigned(DomainEvent<?> event) {
        AdjustorAssignedPayload payload = convertPayload(event.payload(), AdjustorAssignedPayload.class, event.correlationId());
        if (payload == null) {
            log.warn("[{}] adjustor.assigned payload conversion failed", event.correlationId());
            return;
        }
        // Only sets assignedAdjustorId — status stays SURVEYED.
        // The adjustor explicitly begins adjudication via the UI (SURVEYED → UNDER_ADJUDICATION).
        claimApplicationService.assignAdjudicator(
                payload.claimId(), payload.adjustorId().toString(), event.correlationId());
        log.info("[{}] Claim {} adjustor assigned | adjustorId={}",
                event.correlationId(), payload.claimId(), payload.adjustorId());
    }

    private void handleRentalReserved(DomainEvent<?> event) {
        log.debug("[{}] handleRentalReserved called | payload type: {}", 
                event.correlationId(), event.payload().getClass().getName());
        
        RentalVehicleReservedPayload payload = convertPayload(event.payload(), RentalVehicleReservedPayload.class, event.correlationId());
        if (payload == null) {
            log.warn("[{}] rental.reserved payload conversion failed", event.correlationId());
            return;
        }

        Optional<ClaimEntity> maybe = claimJpaRepository.findById(payload.claimId());
        if (maybe.isEmpty()) {
            log.warn("[{}] rental.reserved for unknown claim {}", event.correlationId(), payload.claimId());
            return;
        }

        ClaimEntity entity = maybe.get();
        entity.updateFromDomain(
                entity.getStatus(),
                entity.getAssignedSurveyorId(),
                entity.getAssignedAdjustorId(),
                entity.getAssessedAmount(),
                entity.getApprovedAmount(),
                entity.getWorkshopId(),
                entity.getRejectionReason(),
                entity.isFraudFlag(),
                entity.getFraudReason(),
                entity.getRegion(),
                entity.getOverrideByUserId(),
                entity.getOverrideReason(),
                entity.getOverrideAt(),
                payload.reservationId(),
                "RESERVED"
        );
        claimJpaRepository.save(entity);

        log.info("[{}] 🚙 Claim {} updated from rental.reserved | rentalReservationId={} | rentalStatus=RESERVED",
                event.correlationId(), payload.claimId(), payload.reservationId());
    }

    private void handleRentalSkipped(DomainEvent<?> event) {
        log.debug("[{}] handleRentalSkipped called | payload type: {}", 
                event.correlationId(), event.payload().getClass().getName());
        
        RentalSkippedPayload payload = convertPayload(event.payload(), RentalSkippedPayload.class, event.correlationId());
        if (payload == null) {
            log.warn("[{}] rental.skipped payload conversion failed", event.correlationId());
            return;
        }

        Optional<ClaimEntity> maybe = claimJpaRepository.findById(payload.claimId());
        if (maybe.isEmpty()) {
            log.warn("[{}] rental.skipped for unknown claim {}", event.correlationId(), payload.claimId());
            return;
        }

        ClaimEntity entity = maybe.get();
        entity.updateFromDomain(
                entity.getStatus(),
                entity.getAssignedSurveyorId(),
                entity.getAssignedAdjustorId(),
                entity.getAssessedAmount(),
                entity.getApprovedAmount(),
                entity.getWorkshopId(),
                entity.getRejectionReason(),
                entity.isFraudFlag(),
                entity.getFraudReason(),
                entity.getRegion(),
                entity.getOverrideByUserId(),
                entity.getOverrideReason(),
                entity.getOverrideAt(),
                null,
                "SKIPPED"
        );
        claimJpaRepository.save(entity);

        log.info("[{}] 🚙 Claim {} updated from rental.skipped | rentalStatus=SKIPPED | reason={}",
                event.correlationId(), payload.claimId(), payload.reason());
    }

    private boolean deduplicate(String eventId) {
        String key = "kafka:processed:claims:" + eventId;
        Boolean isNew = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofHours(24));
        return Boolean.TRUE.equals(isNew);
    }

    /**
     * Helper method to convert payload from LinkedHashMap (Kafka deserialization) to proper type.
     * This is needed because Jackson deserializes the generic DomainEvent<T> payload as LinkedHashMap
     * when type information is not preserved.
     */
    private <T> T convertPayload(Object payload, Class<T> targetClass, String correlationId) {
        try {
            return objectMapper.convertValue(payload, targetClass);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Failed to convert payload to {} | payload type: {} | error: {}", 
                    correlationId, targetClass.getSimpleName(), payload.getClass().getName(), e.getMessage());
            return null;
        }
    }
}

