package com.yclaims.workflow.application;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.workflow.infrastructure.persistence.AssignmentEntity;
import com.yclaims.workflow.infrastructure.persistence.AssignmentJpaRepository;
import com.yclaims.workflow.infrastructure.persistence.SurveyorEntity;
import com.yclaims.workflow.infrastructure.persistence.SurveyorJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Auto-assignment service — consumes 'claim.created' events and assigns the best available
 * surveyor based on region and current workload.
 *
 * Assignment algorithm (Phase 1 — rule-based):
 *   1. Find active surveyors in the claim's region
 *   2. Select the one with the fewest current active assignments (load balancing)
 *   3. If no surveyor available, publish escalation event (Case Manager notified)
 *
 * Phase 2: ML-based assignment (skills matching, proximity, historical performance).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoAssignmentService {

    private final AssignmentJpaRepository assignmentRepository;
    private final SurveyorJpaRepository surveyorRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;

    @KafkaListener(
        topics = "claim-events",
        groupId = "workflow-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleClaimCreated(DomainEvent<?> event) {
        if (!"claim.created".equals(event.eventType())) return;
        if (!deduplicate(event.eventId())) return;

        if (event.payload() instanceof ClaimCreatedPayload payload) {
            log.info("[{}] Auto-assigning surveyor for claim {}",
                    event.correlationId(), payload.claimId());
            autoAssign(payload, event.correlationId());
        }
    }

    private void autoAssign(ClaimCreatedPayload payload, String correlationId) {
        List<SurveyorEntity> available = surveyorRepository.findByActiveTrue();

        Optional<SurveyorEntity> assignee = available.stream()
                .min((a, b) -> {
                    long aLoad = assignmentRepository.countActiveBySurveyorId(a.getId());
                    long bLoad = assignmentRepository.countActiveBySurveyorId(b.getId());
                    return Long.compare(aLoad, bLoad);
                });

        if (assignee.isEmpty()) {
            log.warn("[{}] No available surveyors for claim {} — escalating to case managers",
                    correlationId, payload.claimId());
            publishEscalationEvent(payload, correlationId);
            return;
        }

        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setClaimId(payload.claimId());
        assignment.setSurveyorId(assignee.get().getId());
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        assignment.setCorrelationId(correlationId);
        assignmentRepository.save(assignment);

        log.info("[{}] Claim {} auto-assigned to surveyor {}",
                correlationId, payload.claimId(), assignee.get().getId());

        // Publish claim.assigned event so the claims module updates status
        publishClaimAssignedEvent(payload, assignee.get(), correlationId);
    }

    private void publishClaimAssignedEvent(ClaimCreatedPayload payload,
                                            SurveyorEntity surveyor, String correlationId) {
        record ClaimAssignedPayload(UUID claimId, UUID surveyorId, String surveyorName) {}
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "claim.assigned",
                correlationId, null,
                payload.claimId().toString(), "Claim",
                "v1", Instant.now(),
                new ClaimAssignedPayload(payload.claimId(), surveyor.getId(), surveyor.getName())
        );
        kafkaTemplate.send("claim-events", payload.claimId().toString(), event);
    }

    private void publishEscalationEvent(ClaimCreatedPayload payload, String correlationId) {
        record EscalationPayload(UUID claimId, String reason) {}
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "claim.escalated",
                correlationId, null,
                payload.claimId().toString(), "Claim",
                "v1", Instant.now(),
                new EscalationPayload(payload.claimId(), "No available surveyor in region")
        );
        kafkaTemplate.send("claim-events", payload.claimId().toString(), event);
    }

    private boolean deduplicate(String eventId) {
        String key = "kafka:processed:workflow:" + eventId;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(24));
        return Boolean.TRUE.equals(isNew);
    }
}
