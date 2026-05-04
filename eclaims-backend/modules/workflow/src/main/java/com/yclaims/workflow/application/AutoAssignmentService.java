package com.yclaims.workflow.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.contracts.events.v1.AdjustorAssignedPayload;
import com.yclaims.contracts.events.v1.SurveyorAssignedPayload;
import com.yclaims.workflow.infrastructure.persistence.*;
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
    private final AdjustorJpaRepository adjustorRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
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

    private void autoAssign(ClaimCreatedPayload payload, String correlationId) {
        // Region is not part of the v1 claim.created contract payload.
        // For now, fall back to global load-balancing across all active surveyors.
        String region = null;
        
        // First, try to find surveyors in the claim's region
        List<SurveyorEntity> availableInRegion = surveyorRepository.findByActiveTrue().stream()
                .filter(s -> region != null && region.equalsIgnoreCase(s.getRegion()))
                .toList();

        List<SurveyorEntity> candidates = availableInRegion.isEmpty() 
                ? surveyorRepository.findByActiveTrue()  // Fallback to all surveyors if no regional match
                : availableInRegion;

        if (availableInRegion.isEmpty() && region != null) {
            log.warn("[{}] No surveyors found in region {} for claim {} — falling back to all surveyors",
                    correlationId, region, payload.claimId());
        }

        Optional<SurveyorEntity> assignee = candidates.stream()
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

        log.info("[{}] Claim {} auto-assigned to surveyor {} in region {}",
                correlationId, payload.claimId(), assignee.get().getId(), assignee.get().getRegion());

        // Publish claim.assigned event so the claims module updates status
        publishClaimAssignedEvent(payload, assignee.get(), correlationId);
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
        
        // Phase 1: Find surveyors covering the workshop ZIP code
        List<SurveyorEntity> candidatesInZip = surveyorRepository.findByActiveTrue().stream()
                .filter(s -> coversZipCode(s, workshopZip))
                .toList();
        
        if (candidatesInZip.isEmpty()) {
            // Fallback: Try ZIP3 (first 3 digits) for broader coverage
            String zip3 = workshopZip != null && workshopZip.length() >= 3 ? workshopZip.substring(0, 3) : null;
            log.warn("[{}] ⚠️  No surveyors found for ZIP5 {} — trying ZIP3 fallback: {}", 
                    correlationId, workshopZip, zip3);
            
            candidatesInZip = surveyorRepository.findByActiveTrue().stream()
                    .filter(s -> zip3 != null && coversZip3(s, zip3))
                    .toList();
        }
        
        if (candidatesInZip.isEmpty()) {
            // Final fallback: any active surveyor (ensures assignment always succeeds in demo)
            log.warn("[{}] ⚠️  No ZIP coverage match — using global fallback across all active surveyors",
                    correlationId);
            candidatesInZip = surveyorRepository.findByActiveTrue();
        }

        if (candidatesInZip.isEmpty()) {
            log.error("[{}] ❌ No surveyors available at all — escalating to case managers",
                    correlationId);
            publishEscalationEventForDropOff(claimId, workshopId, workshopZip, correlationId);
            return;
        }
        
        log.info("[{}] ✓ Found {} candidate surveyor(s) for workshop ZIP {}", 
                correlationId, candidatesInZip.size(), workshopZip);
        
        // Phase 2: Rank by workload (load balancing)
        Optional<SurveyorEntity> assignee = candidatesInZip.stream()
                .min((a, b) -> {
                    long aLoad = assignmentRepository.countActiveBySurveyorId(a.getId());
                    long bLoad = assignmentRepository.countActiveBySurveyorId(b.getId());
                    log.debug("[{}]   Surveyor {} (ID: {}) workload: {} active assignments", 
                            correlationId, a.getName(), a.getId(), aLoad);
                    log.debug("[{}]   Surveyor {} (ID: {}) workload: {} active assignments", 
                            correlationId, b.getName(), b.getId(), bLoad);
                    return Long.compare(aLoad, bLoad);
                });
        
        if (assignee.isEmpty()) {
            log.error("[{}] ❌ Failed to select surveyor from candidates — escalating", correlationId);
            publishEscalationEventForDropOff(claimId, workshopId, workshopZip, correlationId);
            return;
        }
        
        // Phase 3: Create assignment
        long currentLoad = assignmentRepository.countActiveBySurveyorId(assignee.get().getId());
        AssignmentEntity assignment = new AssignmentEntity();
        assignment.setId(UUID.randomUUID());
        assignment.setClaimId(claimId);
        assignment.setSurveyorId(assignee.get().getId());
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        assignment.setCorrelationId(correlationId);
        assignmentRepository.save(assignment);
        
        log.info("[{}] ✅ SURVEYOR AUTO-ASSIGNED (after vehicle drop-off) | Claim: {} | Surveyor: {} ({}) | Workshop ZIP: {} | Current workload: {} active assignments | Selection reason: Lowest workload in coverage area",
                correlationId, 
                claimId, 
                assignee.get().getId(), 
                assignee.get().getName(),
                workshopZip,
                currentLoad);
        
        // Phase 4: Update claim status to ASSIGNED in DB (cross-schema write — same DB in modular monolith)
        jdbcTemplate.update(
                "UPDATE claims.claims SET status = 'ASSIGNED', assigned_surveyor_id = ?, updated_at = NOW() WHERE id = ?",
                assignee.get().getId().toString(), claimId);
        log.info("[{}] 📝 Updated claim {} status → ASSIGNED (assignedSurveyorId: {})",
                correlationId, claimId, assignee.get().getId());

        // Phase 5: Publish events
        publishSurveyorAssignedEventForDropOff(claimId, workshopId, workshopZip, assignee.get(), correlationId);
        publishNotificationForSurveyorDropOff(claimId, workshopId, workshopZip, assignee.get(), correlationId);
    }
    
    /**
     * Check if surveyor covers a specific ZIP5 code.
     * In production, this would query surveyor_coverage table.
     * For now, simplified check based on region.
     */
    private boolean coversZipCode(SurveyorEntity surveyor, String zipCode) {
        if (zipCode == null || zipCode.length() < 5) return false;
        
        // Simplified: Boston area (021xx) for EAST surveyors, SF area (941xx) for WEST
        if (zipCode.startsWith("021") && "EAST".equals(surveyor.getRegion())) return true;
        if (zipCode.startsWith("941") && "WEST".equals(surveyor.getRegion())) return true;
        
        return false;
    }
    
    /**
     * Check if surveyor covers a ZIP3 area (broader coverage).
     */
    private boolean coversZip3(SurveyorEntity surveyor, String zip3) {
        if (zip3 == null || zip3.length() != 3) return false;
        
        // Simplified: 021 = Boston, 941 = San Francisco
        if ("021".equals(zip3) && "EAST".equals(surveyor.getRegion())) return true;
        if ("941".equals(zip3) && "WEST".equals(surveyor.getRegion())) return true;
        
        return false;
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
        record NotificationPayload(UUID claimId, String recipientId, String recipientType, 
                                    String notificationType, String channel, String subject, String message) {}
        var notification = new NotificationPayload(
                claimId,
                surveyor.getId().toString(),
                "SURVEYOR",
                "SURVEYOR_ASSIGNED",
                "IN_APP",
                "New Survey Assignment - Vehicle Ready",
                String.format("You have been assigned to survey claim %s. Vehicle is now at workshop (ZIP: %s) and ready for inspection.",
                        claimId, workshopZip)
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "notification.requested",
                correlationId, null,
                claimId.toString(), "Notification",
                "v1", Instant.now(),
                notification
        );
        log.info("[{}] 🔔 Publishing notification for surveyor {} - Vehicle ready for inspection", 
                correlationId, surveyor.getName());
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

        // Load balancing: find adjustor with lowest active claim count
        Optional<AdjustorEntity> assignee = adjustors.stream()
                .min((a1, a2) -> {
                    long load1 = countActiveClaimsForAdjustor(a1.getId());
                    long load2 = countActiveClaimsForAdjustor(a2.getId());
                    log.debug("[{}]   Adjustor {} workload: {} active claims", correlationId, a1.getName(), load1);
                    log.debug("[{}]   Adjustor {} workload: {} active claims", correlationId, a2.getName(), load2);
                    return Long.compare(load1, load2);
                });

        if (assignee.isEmpty()) {
            log.error("[{}] ❌ Failed to select adjustor - escalating", correlationId);
            publishAdjustorEscalationEvent(claimId, correlationId);
            return;
        }

        AdjustorEntity selectedAdjustor = assignee.get();
        long currentLoad = countActiveClaimsForAdjustor(selectedAdjustor.getId());

        log.info("[{}] ✅ ADJUSTOR AUTO-ASSIGNED (after survey completed) | Claim: {} | Adjustor: {} ({}) | Current workload: {} active claims",
                correlationId,
                claimId,
                selectedAdjustor.getId(),
                selectedAdjustor.getName(),
                currentLoad);

        // Update claim status to UNDER_ADJUDICATION and record the assigned adjustor.
        // Note: adjustors are NOT stored in the workflow.assignments table (which has a FK to surveyors).
        // Adjustor tracking is done via the assigned_adjustor_id column on the claims table.
        jdbcTemplate.update(
                "UPDATE claims.claims SET status = 'UNDER_ADJUDICATION', assigned_adjustor_id = ?, updated_at = NOW() WHERE id = ?",
                selectedAdjustor.getId().toString(), claimId);
        log.info("[{}] 📝 Updated claim {} status → UNDER_ADJUDICATION (assignedAdjustorId: {})",
                correlationId, claimId, selectedAdjustor.getId());

        // Publish events
        publishAdjustorAssignedEvent(claimId, assignee.get(), correlationId);
        publishNotificationForAdjustor(claimId, assignee.get(), correlationId);
    }

    /**
     * Count active claims assigned to an adjustor.
     * Adjustors are tracked on claims.claims table, not in workflow.assignments (which is for surveyors).
     */
    private long countActiveClaimsForAdjustor(UUID adjustorId) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM claims.claims WHERE assigned_adjustor_id = ? AND status IN ('UNDER_ADJUDICATION')",
                    Long.class, adjustorId.toString());
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to count active claims for adjustor {}: {}", adjustorId, e.getMessage());
            return 0L;
        }
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
        record NotificationPayload(UUID claimId, String recipientId, String recipientType,
                                    String notificationType, String channel, String subject, String message) {}
        var notification = new NotificationPayload(
                claimId,
                adjustor.getId().toString(),
                "ADJUSTOR",
                "ADJUSTOR_ASSIGNED",
                "IN_APP",
                "New Claim for Adjudication",
                String.format("Survey has been completed for claim %s. Please review and adjudicate.", claimId)
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "notification.requested",
                correlationId, null,
                claimId.toString(), "Notification",
                "v1", Instant.now(),
                notification
        );
        log.info("[{}] 🔔 Publishing notification for adjustor {} - Survey completed, ready for adjudication",
                correlationId, adjustor.getName());
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
