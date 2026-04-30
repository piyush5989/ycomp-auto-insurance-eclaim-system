package com.yclaims.workshops.application;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.RepairStatusUpdatedPayload;
import com.yclaims.kernel.exception.NotFoundException;
import com.yclaims.workshops.infrastructure.persistence.*;
import com.yclaims.workshops.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkshopApplicationService {

    private final WorkshopJpaRepository workshopRepository;
    private final WorkOrderJpaRepository workOrderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Workshop/provider search — filtered by providerType and location (city or zip).
     * Cache disabled temporarily due to Redis serialization issues.
     * TODO: Re-enable with proper JSON serializer configuration.
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
                                                 String note, String correlationId) {
        WorkOrderEntity entity = workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new NotFoundException("WorkOrder", workOrderId.toString()));

        entity.setRepairStatus(status);
        entity.setUpdatedAt(Instant.now());
        workOrderRepository.save(entity);

        WorkshopEntity workshop = workshopRepository.findById(entity.getWorkshopId()).orElse(null);
        publishRepairStatusEvent(entity, workshop, note, correlationId);

        return toWorkOrderResponse(entity, workshop);
    }

    @Transactional(readOnly = true)
    public WorkOrderResponse getWorkOrderByClaimId(UUID claimId, String correlationId) {
        WorkOrderEntity entity = workOrderRepository
                .findFirstByClaimIdOrderByCreatedAtDesc(claimId)
                .orElseThrow(() -> new NotFoundException("WorkOrder", "claimId=" + claimId));
        WorkshopEntity workshop = workshopRepository.findById(entity.getWorkshopId()).orElse(null);
        return toWorkOrderResponse(entity, workshop);
    }

    /**
     * Customer selects a workshop for a claim.
     *
     * Note: The current workshops module doesn't persist this selection yet (no table/entity).
     * This method exists to support the API contract and can be extended later to publish an
     * event or persist in a dedicated selection table.
     */
    @Transactional
    public void selectWorkshopForClaim(UUID claimId, UUID workshopId, String customerId, String correlationId) {
        log.info("[{}] Customer {} selected workshop {} for claim {}",
                correlationId, customerId, workshopId, claimId);
    }

    /**
     * Customer confirms vehicle drop-off at workshop.
     *
     * Note: The current workshops module doesn't persist drop-off details yet (no table/entity).
     * Returns a generated dropOffId to support the API contract and enable future persistence.
     */
    @Transactional
    public UUID confirmVehicleDropOff(UUID claimId, VehicleDropOffRequest request,
                                      String customerId, String correlationId) {
        UUID dropOffId = UUID.randomUUID();
        log.info("[{}] Vehicle drop-off confirmed. dropOffId={} claimId={} customerId={} notes={} mileage={}",
                correlationId, dropOffId, claimId, customerId, request.dropOffNotes(), request.mileage());
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
