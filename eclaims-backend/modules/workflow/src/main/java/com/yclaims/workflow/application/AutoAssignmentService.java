package com.yclaims.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.AdjustorAssignedPayload;
import com.yclaims.contracts.events.v1.NotificationRequestedPayload;
import com.yclaims.contracts.events.v1.SurveyorAssignedPayload;
import com.yclaims.workflow.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Auto-assignment service — listens to workflow trigger events and assigns the best available
 * surveyor (on vehicle drop-off) or adjustor (on survey completion) using load-balanced selection.
 *
 * Assignment algorithm (Phase 1 — rule-based):
 *   1. For surveyors: filter by ZIP coverage (DB-driven), then pick lowest workload
 *   2. For adjustors: load-balance across all active adjustors
 *   3. If no candidate available, publish claim.escalated event (Case Manager notified)
 *
 * Status transitions are driven by domain events — this service publishes surveyor.assigned /
 * adjustor.assigned events that the claims module's ClaimWorkflowEventConsumer consumes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoAssignmentService {

    private final AssignmentJpaRepository assignmentRepository;
    private final SurveyorJpaRepository surveyorRepository;
    private final AdjustorJpaRepository adjustorRepository;
    private final SurveyorZipCoverageJpaRepository coverageRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "claim-events",
        groupId = "workflow-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleClaimEvents(DomainEvent<?> event) {
        log.info("[{}] 📨 AutoAssignmentService received event: {} (type: {})", 
                event.correlationId(), event.eventId(), event.eventType());
        
        if (!deduplicate(event.eventId())) {
            log.debug("[{}] ⚠️  Event {} already processed (duplicate), skipping", event.correlationId(), event.eventId());
            return;
        }

        // Handle vehicle drop-off → Assign surveyor
        if ("vehicle.droppedoff".equals(event.eventType())) {
            com.yclaims.contracts.events.v1.VehicleDroppedOffPayload payload = 
                    convertPayload(event.payload(), com.yclaims.contracts.events.v1.VehicleDroppedOffPayload.class, event.correlationId());
            if (payload != null) {
                log.info("[{}] 🚗 Vehicle dropped off for claim {} - NOW triggering surveyor auto-assignment for workshop inspection",
                        event.correlationId(), payload.claimId());
                autoAssignBasedOnDropOff(payload, event.correlationId());
            } else {
                log.warn("[{}] vehicle.droppedoff payload conversion failed", event.correlationId());
            }
        }

        // Handle survey completed → Assign adjustor
        if ("claim.status.changed".equals(event.eventType())) {
            com.yclaims.contracts.events.v1.ClaimStatusChangedPayload payload = 
                    convertPayload(event.payload(), com.yclaims.contracts.events.v1.ClaimStatusChangedPayload.class, event.correlationId());
            if (payload != null && "SURVEYED".equals(payload.newStatus())) {
                log.info("[{}] 📋 Survey completed for claim {} - NOW triggering adjustor auto-assignment",
                        event.correlationId(), payload.claimId());
                autoAssignAdjustor(payload.claimId(), event.correlationId());
            }
        }
    }


    /**
     * Auto-assign surveyor based on vehicle drop-off at workshop (enterprise approach).
     * TRIGGERED ONLY AFTER vehicle is physically dropped off at workshop.
     * Surveyor visits workshop location where vehicle is, not incident location.
     * 
     * Assignment algorithm:
     * 1. Extract workshop location from drop-off event
     * 2. Find surveyors covering workshop ZIP code
     * 3. Filter by active, within capacity
     * 4. Rank by workload (load balancing)
     * 5. Select surveyor with lowest score
     */
    private void autoAssignBasedOnDropOff(com.yclaims.contracts.events.v1.VehicleDroppedOffPayload payload, 
                                           String correlationId) {
        UUID claimId = payload.claimId();
        UUID workshopId = payload.workshopId();
        String workshopZip = payload.workshopZipCode();
        String workshopState = payload.workshopState();
        String workshopName = payload.workshopName();
        
        log.info("[{}] 🔍 Finding surveyors covering workshop ZIP: {}, State: {} (vehicle is NOW at workshop '{}', ready for inspection)", 
                correlationId, workshopZip, workshopState, workshopName);
        
        // Phase 1: Resolve which region covers this workshop ZIP, then find matching surveyors.
        // Full ZIP5 is tried first; fall back to ZIP3 prefix; then global fallback.
        List<SurveyorEntity> candidatesInZip = resolveSurveyorsByZip(workshopZip, correlationId);

        if (candidatesInZip.isEmpty()) {
            log.error("[{}] ❌ No surveyors available at all — escalating to case managers",
                    correlationId);
            publishEscalationEventForDropOff(claimId, workshopId, workshopZip, correlationId);
            return;
        }
        
        log.info("[{}] ✓ Found {} candidate surveyor(s) for workshop ZIP {}",
                correlationId, candidatesInZip.size(), workshopZip);

        // Phase 2: Batch-fetch all workload counts in a single query, then pick lowest.
        Set<UUID> candidateIds = candidatesInZip.stream().map(SurveyorEntity::getId).collect(Collectors.toSet());
        Map<UUID, Long> workloads = batchSurveyorWorkloads(candidateIds);

        Optional<SurveyorEntity> assignee = candidatesInZip.stream()
                .min(Comparator.comparingLong(s -> workloads.getOrDefault(s.getId(), 0L)));
        
        if (assignee.isEmpty()) {
            log.error("[{}] ❌ Failed to select surveyor from candidates — escalating", correlationId);
            publishEscalationEventForDropOff(claimId, workshopId, workshopZip, correlationId);
            return;
        }
        
        // Phase 3: Create assignment record in workflow schema
        long currentLoad = workloads.getOrDefault(assignee.get().getId(), 0L);
        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setClaimId(claimId);
        assignment.setSurveyorId(assignee.get().getId());
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        assignment.setCorrelationId(correlationId);
        assignmentRepository.save(assignment);

        log.info("[{}] ✅ SURVEYOR AUTO-ASSIGNED | Claim: {} | Surveyor: {} ({}) | ZIP: {} | Workload: {} active",
                correlationId, claimId, assignee.get().getId(), assignee.get().getName(),
                workshopZip, currentLoad);

        // Phase 4: Publish events — ClaimWorkflowEventConsumer in the claims module
        // will receive surveyor.assigned and update the claim via the domain model.
        publishSurveyorAssignedEventForDropOff(claimId, workshopId, workshopZip, assignee.get(), correlationId);
        publishNotificationForSurveyorDropOff(claimId, workshopId, workshopZip, assignee.get(), correlationId);
    }
    
    /**
     * Resolves active surveyors covering the given workshop ZIP code.
     * Queries the surveyor_zip_coverage table: ZIP5 → ZIP3 → global fallback.
     */
    private List<SurveyorEntity> resolveSurveyorsByZip(String workshopZip, String correlationId) {
        if (workshopZip != null && workshopZip.length() >= 5) {
            String zip5 = workshopZip.substring(0, 5);
            String region = coverageRepository.findRegionByZipPrefix(zip5).orElse(null);
            if (region == null && workshopZip.length() >= 3) {
                String zip3 = workshopZip.substring(0, 3);
                region = coverageRepository.findRegionByZipPrefix(zip3).orElse(null);
                if (region != null) {
                    log.debug("[{}] ZIP5 {} not found; ZIP3 {} matched region {}", correlationId, zip5, zip3, region);
                }
            }
            if (region != null) {
                final String matched = region;
                List<SurveyorEntity> byRegion = surveyorRepository.findByActiveTrue().stream()
                        .filter(s -> matched.equalsIgnoreCase(s.getRegion()))
                        .toList();
                if (!byRegion.isEmpty()) return byRegion;
                log.warn("[{}] Region {} matched ZIP {} but no active surveyors — global fallback", correlationId, region, workshopZip);
            } else {
                log.warn("[{}] No coverage record for ZIP {} — global fallback", correlationId, workshopZip);
            }
        }
        return surveyorRepository.findByActiveTrue();
    }

    /**
     * Batch-fetches active assignment counts for a set of surveyor IDs in a single query.
     * Avoids the N+1 problem of calling countActiveBySurveyorId() inside a comparator.
     */
    private Map<UUID, Long> batchSurveyorWorkloads(Set<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return assignmentRepository.countActiveBySurveyorIds(ids)
                .stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> (Long) row[1]
                ));
    }

    private void publishSurveyorAssignedEventForDropOff(UUID claimId, UUID workshopId, String workshopZip,
                                                        SurveyorEntity surveyor, String correlationId) {
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "surveyor.assigned",
                correlationId, null,
                claimId.toString(), "Claim",
                "v1", Instant.now(),
                new SurveyorAssignedPayload(claimId, surveyor.getId(), surveyor.getName(),
                        workshopId, workshopZip, "VEHICLE_DROPPED_OFF")
        );
        log.info("[{}] 📤 Publishing surveyor.assigned event for claim {} (trigger: vehicle drop-off confirmed)", 
                correlationId, claimId);
        kafkaTemplate.send("claim-events", claimId.toString(), event);
    }

    private void publishNotificationForSurveyorDropOff(UUID claimId, UUID workshopId, String workshopZip,
                                                        SurveyorEntity surveyor, String correlationId) {
        var notification = new NotificationRequestedPayload(
                claimId, surveyor.getId().toString(), "SURVEYOR", "SURVEYOR_ASSIGNED", "IN_APP",
                "New Survey Assignment - Vehicle Ready",
                String.format("You have been assigned to survey claim %s. Vehicle is at workshop (ZIP: %s) and ready for inspection.", claimId, workshopZip),
                null
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "notification.requested", correlationId, null,
                claimId.toString(), "Notification", "v1", Instant.now(), notification
        );
        log.info("[{}] Publishing notification for surveyor {}", correlationId, surveyor.getName());
        kafkaTemplate.send("notification-events", surveyor.getId().toString(), event);
    }

    private void publishEscalationEventForDropOff(UUID claimId, UUID workshopId, String workshopZip, 
                                                   String correlationId) {
        record EscalationPayload(UUID claimId, String reason, String workshopZip) {}
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "claim.escalated",
                correlationId, null,
                claimId.toString(), "Claim",
                "v1", Instant.now(),
                new EscalationPayload(claimId, 
                        "No available surveyor covering workshop location after vehicle drop-off", 
                        workshopZip)
        );
        log.warn("[{}] ⚠️  Publishing escalation event for claim {} - No surveyor coverage (vehicle dropped off)", 
                correlationId, claimId);
        kafkaTemplate.send("claim-events", claimId.toString(), event);
    }
    

    /**
     * Public method to manually trigger adjustor assignment for a claim.
     * Used for retry scenarios (e.g., when adjustors were unavailable initially).
     */
    @Transactional
    public void manuallyAssignAdjustor(UUID claimId, String correlationId) {
        log.info("[{}] 🔄 Manual adjustor assignment requested for claim {}", correlationId, claimId);
        autoAssignAdjustor(claimId, correlationId);
    }

    /**
     * Auto-assign adjustor after survey is completed.
     * Uses simple load balancing - assigns to adjustor with lowest active workload.
     */
    private void autoAssignAdjustor(UUID claimId, String correlationId) {
        log.info("[{}] 🔍 Finding available adjustor for claim {}", correlationId, claimId);

        // Get all active adjustors
        List<AdjustorEntity> adjustors = adjustorRepository.findByActiveTrue();

        if (adjustors.isEmpty()) {
            log.error("[{}] ❌ No active adjustors available - escalating to case managers", correlationId);
            publishAdjustorEscalationEvent(claimId, correlationId);
            return;
        }

        log.info("[{}] ✓ Found {} active adjustor(s)", correlationId, adjustors.size());

        // Batch-fetch workloads in one query, then pick lowest — avoids N+1 inside comparator.
        Map<UUID, Long> adjustorWorkloads = batchAdjustorWorkloads(adjustors);

        Optional<AdjustorEntity> assignee = adjustors.stream()
                .min(Comparator.comparingLong(a -> adjustorWorkloads.getOrDefault(a.getId(), 0L)));

        if (assignee.isEmpty()) {
            log.error("[{}] ❌ Failed to select adjustor - escalating", correlationId);
            publishAdjustorEscalationEvent(claimId, correlationId);
            return;
        }

        AdjustorEntity selectedAdjustor = assignee.get();
        long currentLoad = adjustorWorkloads.getOrDefault(selectedAdjustor.getId(), 0L);

        log.info("[{}] ✅ ADJUSTOR AUTO-ASSIGNED | Claim: {} | Adjustor: {} ({}) | Workload: {} active",
                correlationId, claimId, selectedAdjustor.getId(), selectedAdjustor.getName(), currentLoad);

        // Publish events — ClaimWorkflowEventConsumer will persist the adjustor ID via the domain model.
        publishAdjustorAssignedEvent(claimId, assignee.get(), correlationId);
        publishNotificationForAdjustor(claimId, assignee.get(), correlationId);
    }

    /**
     * Batch-fetches UNDER_ADJUDICATION claim counts for all given adjustors in a single cross-schema
     * read query. Adjustors are tracked on claims.claims, not in workflow.assignments.
     */
    private Map<UUID, Long> batchAdjustorWorkloads(List<AdjustorEntity> adjustors) {
        if (adjustors.isEmpty()) return Map.of();
        String placeholders = adjustors.stream().map(a -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT assigned_adjustor_id, COUNT(*) FROM claims.claims " +
                "WHERE assigned_adjustor_id IN (" + placeholders + ") " +
                "AND status = 'UNDER_ADJUDICATION' GROUP BY assigned_adjustor_id";
        Object[] params = adjustors.stream().map(a -> a.getId().toString()).toArray();
        Map<UUID, Long> result = new HashMap<>();
        try {
            jdbcTemplate.query(
                con -> {
                    var ps = con.prepareStatement(sql);
                    for (int i = 0; i < params.length; i++) {
                        ps.setString(i + 1, (String) params[i]);
                    }
                    return ps;
                },
                rs -> {
                    result.put(UUID.fromString(rs.getString(1)), rs.getLong(2));
                }
            );
        } catch (Exception e) {
            log.warn("Failed to batch-count adjustor workloads: {}", e.getMessage());
        }
        return result;
    }

    private void publishAdjustorAssignedEvent(UUID claimId, AdjustorEntity adjustor, String correlationId) {
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "adjustor.assigned",
                correlationId, null,
                claimId.toString(), "Claim",
                "v1", Instant.now(),
                new AdjustorAssignedPayload(claimId, adjustor.getId(), adjustor.getName(), "SURVEY_COMPLETED")
        );
        log.info("[{}] 📤 Publishing adjustor.assigned event for claim {} (trigger: survey completed)",
                correlationId, claimId);
        kafkaTemplate.send("claim-events", claimId.toString(), event);
    }

    private void publishNotificationForAdjustor(UUID claimId, AdjustorEntity adjustor, String correlationId) {
        var notification = new NotificationRequestedPayload(
                claimId, adjustor.getId().toString(), "ADJUSTOR", "ADJUSTOR_ASSIGNED", "IN_APP",
                "New Claim for Adjudication",
                String.format("Survey has been completed for claim %s. Please review and adjudicate.", claimId),
                null
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "notification.requested", correlationId, null,
                claimId.toString(), "Notification", "v1", Instant.now(), notification
        );
        log.info("[{}] Publishing notification for adjustor {}", correlationId, adjustor.getName());
        kafkaTemplate.send("notification-events", adjustor.getId().toString(), event);
    }

    private void publishAdjustorEscalationEvent(UUID claimId, String correlationId) {
        record EscalationPayload(UUID claimId, String reason) {}
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "claim.escalated",
                correlationId, null,
                claimId.toString(), "Claim",
                "v1", Instant.now(),
                new EscalationPayload(claimId, "No available adjustor for adjudication")
        );
        log.warn("[{}] ⚠️  Publishing escalation event for claim {} - No adjustor available",
                correlationId, claimId);
        kafkaTemplate.send("claim-events", claimId.toString(), event);
    }

    private boolean deduplicate(String eventId) {
        String key = "kafka:processed:workflow:" + eventId;
        Boolean isNew = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofHours(24));
        return Boolean.TRUE.equals(isNew);
    }

    /**
     * Helper method to convert payload from LinkedHashMap (Kafka deserialization) to proper type.
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
