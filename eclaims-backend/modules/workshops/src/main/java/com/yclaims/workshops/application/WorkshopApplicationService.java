package com.yclaims.workshops.application;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.RepairStatusUpdatedPayload;
import com.yclaims.workshops.infrastructure.persistence.*;
import com.yclaims.workshops.presentation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * Workshop search — cached by location key.
     * Cache key: workshop:nearby:{location}  TTL: 30 min (workshops change infrequently)
     * Target: p95 < 500ms
     */
    @Cacheable(value = "workshop", key = "#location != null ? #location : 'all'",
               unless = "#result.isEmpty()")
    @Transactional(readOnly = true)
    public List<WorkshopResponse> searchWorkshops(String location, String correlationId) {
        List<WorkshopEntity> workshops = location != null && !location.isBlank()
                ? workshopRepository.findByCityContainingIgnoreCaseAndActiveTrue(location)
                : workshopRepository.findByActiveTrue();

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
                .orElseThrow(() -> new com.yclaims.kernel.exception.NotFoundException("WorkOrder", workOrderId.toString()));

        entity.setRepairStatus(status);
        entity.setUpdatedAt(Instant.now());
        workOrderRepository.save(entity);

        // Publish repair status event
        WorkshopEntity workshop = workshopRepository.findById(entity.getWorkshopId()).orElse(null);
        publishRepairStatusEvent(entity, workshop, note, correlationId);

        return toWorkOrderResponse(entity, workshop);
    }

    private void publishRepairStatusEvent(WorkOrderEntity order, WorkshopEntity workshop,
                                           String note, String correlationId) {
        var payload = new RepairStatusUpdatedPayload(
                order.getId(), order.getClaimId(), null, null,
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
