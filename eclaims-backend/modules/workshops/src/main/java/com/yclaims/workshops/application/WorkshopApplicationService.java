package com.yclaims.workshops.application;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.RepairStatusUpdatedPayload;
import com.yclaims.contracts.events.v1.VehicleDroppedOffPayload;
import com.yclaims.documents.application.DocumentApplicationService;
import com.yclaims.kernel.exception.NotFoundException;
import com.yclaims.kernel.security.UserContextHolder;
import com.yclaims.workshops.infrastructure.persistence.*;
import com.yclaims.workshops.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopApplicationService {

    private final WorkshopJpaRepository workshopRepository;
    private final WorkOrderJpaRepository workOrderRepository;
    private final WorkOrderStatusHistoryRepository statusHistoryRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentApplicationService documentService;

    @Transactional(readOnly = true)
    public WorkshopResponse getWorkshopById(UUID workshopId) {
        WorkshopEntity entity = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new NotFoundException("Workshop", workshopId.toString()));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getMyClaimsForWorkshop(String keycloakUserId) {
        WorkshopEntity workshop = workshopRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new NotFoundException("Workshop", "keycloakUserId=" + keycloakUserId));
        return jdbcTemplate.queryForList(
                """
                SELECT id AS claim_id,
                       status,
                       vehicle_registration,
                       policy_number,
                       incident_date,
                       incident_location,
                       description,
                       assessed_amount,
                       approved_amount,
                       rejection_reason,
                       fraud_flag,
                       created_at,
                       updated_at
                FROM claims.claims
                WHERE workshop_id = ?
                ORDER BY updated_at DESC
                """,
                workshop.getId().toString());
    }

    @Transactional(readOnly = true)
    public WorkshopResponse getMyWorkshopProfile(String keycloakUserId) {
        WorkshopEntity entity = workshopRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new NotFoundException("Workshop", "keycloakUserId=" + keycloakUserId));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderResponse> getMyWorkOrders(String keycloakUserId) {
        WorkshopEntity entity = workshopRepository.findByKeycloakUserId(keycloakUserId)
                .orElseThrow(() -> new NotFoundException("Workshop", "keycloakUserId=" + keycloakUserId));
        return workOrderRepository.findByWorkshopIdOrderByCreatedAtDesc(entity.getId())
                .stream().map(wo -> toWorkOrderResponse(wo, entity)).toList();
    }

    /**
     * Workshop/provider search — filtered by providerType and location (city or zip).
     * TODO: enable cache with proper JSON serializer configuration.
     */
    // @Cacheable(value = "workshop",
    //            key = "(#providerType != null ? #providerType : 'ALL') + ':' + (#zip != null ? 'zip:' + #zip : #location != null ? 'city:' + #location : 'all')",
    //            unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<WorkshopResponse> searchWorkshops(String location, String zip,
                                                   String providerType, String correlationId) {
        List<WorkshopEntity> workshops;

        if (zip != null && !zip.isBlank()) {
            workshops = providerType != null && !providerType.isBlank()
                    ? workshopRepository.findByZipCodeAndProviderTypeAndActiveTrue(zip.trim(), providerType)
                    : workshopRepository.findByZipCodeAndActiveTrue(zip.trim());
        } else if (location != null && !location.isBlank()) {
            workshops = providerType != null && !providerType.isBlank()
                    ? workshopRepository.findByCityContainingIgnoreCaseAndProviderTypeAndActiveTrue(location, providerType)
                    : workshopRepository.findByCityContainingIgnoreCaseAndActiveTrue(location);
        } else if (providerType != null && !providerType.isBlank()) {
            workshops = workshopRepository.findByProviderTypeAndActiveTrue(providerType);
        } else {
            workshops = workshopRepository.findByActiveTrue();
        }

        return workshops.stream().map(this::toResponse).toList();
    }

    @Transactional
    public UUID uploadWorkOrderMedia(UUID workOrderId, String documentType,
                                     MultipartFile file, String userId, String correlationId) {
        WorkOrderEntity workOrder = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new NotFoundException("WorkOrder", workOrderId.toString()));
        
        try {
            var response = documentService.uploadDocument(
                    workOrder.getClaimId(), documentType, file, userId, correlationId);
            log.info("[{}] Workshop media uploaded | WorkOrder: {} | ClaimId: {} | DocumentId: {} | Type: {}",
                    correlationId, workOrderId, workOrder.getClaimId(), response.getDocumentId(), documentType);
            return response.getDocumentId();
        } catch (Exception e) {
            log.error("[{}] Failed to upload workshop media for work order {}", correlationId, workOrderId, e);
            throw new RuntimeException("Failed to upload media: " + e.getMessage(), e);
        }
    }

    @Transactional
    public WorkOrderResponse submitWorkOrder(WorkOrderRequest request, String correlationId) {
        WorkOrderEntity entity = new WorkOrderEntity();
        entity.setId(UUID.randomUUID());
        entity.setClaimId(request.claimId());
        entity.setWorkshopId(request.workshopId());
        entity.setEstimatedCost(request.estimatedCost());
        entity.setEstimatedCompletionDate(request.estimatedCompletionDate());
        entity.setWorkDescription(request.workDescription());
        entity.setRepairStatus("PENDING");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        workOrderRepository.save(entity);
        log.info("[{}] Work order {} created for claim {}", correlationId, entity.getId(), request.claimId());
        return toWorkOrderResponse(entity, null);
    }

    @Transactional
    public WorkOrderResponse updateRepairStatus(UUID workOrderId, String status,
                                                 String note, java.math.BigDecimal finalCost,
                                                 java.time.LocalDate estimatedCompletionDate,
                                                 String correlationId) {
        WorkOrderEntity entity = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new NotFoundException("WorkOrder", workOrderId.toString()));

        entity.setRepairStatus(status);
        if (finalCost != null) entity.setFinalCost(finalCost);
        if (estimatedCompletionDate != null) entity.setEstimatedCompletionDate(estimatedCompletionDate);
        entity.setUpdatedAt(Instant.now());
        workOrderRepository.save(entity);

        // Save status change to history for customer tracking (FR9)
        WorkOrderStatusHistoryEntity historyEntry = new WorkOrderStatusHistoryEntity();
        historyEntry.setId(UUID.randomUUID());
        historyEntry.setWorkOrderId(workOrderId);
        historyEntry.setStatus(status);
        historyEntry.setNote(note);
        historyEntry.setEstimatedCompletionDate(estimatedCompletionDate);
        historyEntry.setChangedByUserId(UserContextHolder.currentUserId());
        historyEntry.setChangedAt(Instant.now());
        statusHistoryRepository.save(historyEntry);

        WorkshopEntity workshop = workshopRepository.findById(entity.getWorkshopId()).orElse(null);
        publishRepairStatusEvent(entity, workshop, note, correlationId);

        return toWorkOrderResponse(entity, workshop);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrderByClaimId(UUID claimId, String correlationId) {
        return workOrderRepository
                .findFirstByClaimIdOrderByCreatedAtDesc(claimId)
                .map(entity -> {
                    WorkshopEntity workshop = workshopRepository.findById(entity.getWorkshopId()).orElse(null);
                    return toWorkOrderResponse(entity, workshop);
                })
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderStatusHistoryResponse> getWorkOrderStatusHistory(UUID workOrderId) {
        return statusHistoryRepository.findByWorkOrderIdOrderByChangedAtAsc(workOrderId).stream()
                .map(entity -> new WorkOrderStatusHistoryResponse(
                        entity.getId(),
                        entity.getStatus(),
                        entity.getNote(),
                        entity.getEstimatedCompletionDate(),
                        entity.getChangedAt()
                ))
                .toList();
    }

    /**
     * Customer selects a workshop for a claim.
     * Updates claim status to WORKSHOP_SELECTED and stores workshopId on the claim.
     */
    @Transactional
    public void selectWorkshopForClaim(UUID claimId, UUID workshopId, String customerId, String correlationId) {
        // Validate workshop exists
        WorkshopEntity workshop = workshopRepository.findById(workshopId)
                .orElseThrow(() -> new NotFoundException("Workshop", workshopId.toString()));

        // Update claim status to WORKSHOP_SELECTED and record workshopId (cross-schema write in modular monolith)
        int updated = jdbcTemplate.update(
                "UPDATE claims.claims SET status = 'WORKSHOP_SELECTED', workshop_id = ?, updated_at = NOW() WHERE id = ? AND status = 'SUBMITTED'",
                workshopId.toString(), claimId);

        if (updated == 0) {
            // Claim may already be past SUBMITTED — check current status
            String currentStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM claims.claims WHERE id = ?", String.class, claimId);
            log.warn("[{}] Workshop selection: claim {} not updated (current status: {}). Requires SUBMITTED.",
                    correlationId, claimId, currentStatus);
            throw new IllegalStateException(
                    "Cannot select workshop: claim is not in SUBMITTED status (current: " + currentStatus + ")");
        }

        log.info("[{}] Workshop selected | claim={} | workshop={} ({}) | status -> WORKSHOP_SELECTED",
                correlationId, claimId, workshopId, workshop.getName());

        // Publish workshop.selected event
        record WorkshopSelectedPayload(UUID claimId, UUID workshopId, String workshopName,
                String workshopZipCode, String workshopState, boolean isPartnerWorkshop) {}
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "workshop.selected",
                correlationId, null,
                claimId.toString(), "Claim",
                "v1", Instant.now(),
                new WorkshopSelectedPayload(claimId, workshopId, workshop.getName(),
                        workshop.getZipCode(), null, true));
        kafkaTemplate.send("claim-events", claimId.toString(), event);
    }

    /**
     * Customer confirms vehicle drop-off at workshop.
     * Updates claim status to VEHICLE_AT_WORKSHOP — this TRIGGERS surveyor auto-assignment.
     */
    @Transactional
    public UUID confirmVehicleDropOff(UUID claimId, VehicleDropOffRequest request,
                                      String customerId, String correlationId) {
        UUID dropOffId = UUID.randomUUID();

        // Update claim status to VEHICLE_AT_WORKSHOP (cross-schema write in modular monolith)
        int updated = jdbcTemplate.update(
                "UPDATE claims.claims SET status = 'VEHICLE_AT_WORKSHOP', updated_at = NOW() WHERE id = ? AND status = 'WORKSHOP_SELECTED'",
                claimId);

        if (updated == 0) {
            String currentStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM claims.claims WHERE id = ?", String.class, claimId);
            log.warn("[{}] Drop-off: claim {} not updated (current status: {}). Requires WORKSHOP_SELECTED.",
                    correlationId, claimId, currentStatus);
            throw new IllegalStateException(
                    "Cannot confirm drop-off: claim is not in WORKSHOP_SELECTED status (current: " + currentStatus + ")");
        }

        // Fetch workshop info for the event payload
        String workshopIdRaw = jdbcTemplate.queryForObject(
                "SELECT workshop_id FROM claims.claims WHERE id = ?", String.class, claimId);
        UUID workshopId = workshopIdRaw != null ? UUID.fromString(workshopIdRaw) : null;

        String workshopName = "Unknown Workshop";
        String workshopZip = null;
        if (workshopId != null) {
            try {
                var row = jdbcTemplate.queryForMap(
                        "SELECT name, zip_code FROM workshops.workshops WHERE id = ?", workshopId);
                workshopName = (String) row.get("name");
                workshopZip = (String) row.get("zip_code");
            } catch (Exception e) {
                log.warn("[{}] Could not fetch workshop details for drop-off event", correlationId);
            }
        }

        log.info("[{}] Vehicle drop-off confirmed | claim={} | workshop={} | dropOffId={} | mileage={} | fuel={} | status -> VEHICLE_AT_WORKSHOP",
                correlationId, claimId, workshopName, dropOffId,
                request.mileage(), request.fuelLevel());

        // AutoAssignmentService listens to vehicle.droppedoff to trigger surveyor assignment
        var dropOffPayload = new VehicleDroppedOffPayload(
                claimId,
                workshopId,
                workshopName,
                workshopZip,        // workshopZipCode
                null,               // workshopState
                null,               // workshopLatitude
                null,               // workshopLongitude
                dropOffId,
                Instant.now(),      // droppedOffAt
                request.dropOffNotes(),
                request.mileage(),
                request.fuelLevel(),
                request.photosUploaded(),
                null,               // confirmedBy
                null,               // customerId
                null,               // policyNumber
                null                // vehicleRegistration
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "vehicle.droppedoff",
                correlationId, null,
                claimId.toString(), "Claim",
                "v1", Instant.now(),
                dropOffPayload);
        kafkaTemplate.send("claim-events", claimId.toString(), event);

        return dropOffId;
    }

    private void publishRepairStatusEvent(WorkOrderEntity order, WorkshopEntity workshop,
                                           String note, String correlationId) {
        // Enrich event with customer data from claims table (cross-schema read — same DB in modular monolith)
        String customerId = null;
        String customerEmail = null;
        try {
            var row = jdbcTemplate.queryForMap(
                    "SELECT customer_id, customer_email FROM claims.claims WHERE id = ?",
                    order.getClaimId());
            customerId    = (String) row.get("customer_id");
            customerEmail = (String) row.get("customer_email");
        } catch (Exception e) {
            log.warn("[{}] Could not enrich repair event with customer data for claimId={}",
                    correlationId, order.getClaimId());
        }

        var payload = new RepairStatusUpdatedPayload(
                order.getId(), order.getClaimId(),
                customerId, customerEmail,
                order.getWorkshopId().toString(),
                workshop != null ? workshop.getName() : "Unknown Workshop",
                order.getRepairStatus(),
                order.getEstimatedCompletionDate(),
                note
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "repair.status.updated",
                correlationId, null,
                order.getClaimId().toString(), "WorkOrder",
                "v1", Instant.now(), payload
        );
        kafkaTemplate.send("repair-events", order.getClaimId().toString(), event);
    }

    private WorkshopResponse toResponse(WorkshopEntity e) {
        return WorkshopResponse.builder()
                .id(e.getId()).name(e.getName()).address(e.getAddress())
                .city(e.getCity()).zipCode(e.getZipCode()).phone(e.getPhone())
                .email(e.getEmail()).rating(e.getRating()).active(e.isActive())
                .providerType(e.getProviderType())
                .build();
    }

    private WorkOrderResponse toWorkOrderResponse(WorkOrderEntity e, WorkshopEntity w) {
        return WorkOrderResponse.builder()
                .workOrderId(e.getId()).claimId(e.getClaimId()).workshopId(e.getWorkshopId())
                .workshopName(w != null ? w.getName() : null)
                .estimatedCost(e.getEstimatedCost()).finalCost(e.getFinalCost())
                .repairStatus(e.getRepairStatus())
                .estimatedCompletionDate(e.getEstimatedCompletionDate())
                .workDescription(e.getWorkDescription())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }
}
