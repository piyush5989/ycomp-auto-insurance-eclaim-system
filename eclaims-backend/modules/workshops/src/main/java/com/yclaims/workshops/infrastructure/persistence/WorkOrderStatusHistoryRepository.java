package com.yclaims.workshops.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkOrderStatusHistoryRepository extends JpaRepository<WorkOrderStatusHistoryEntity, UUID> {
    List<WorkOrderStatusHistoryEntity> findByWorkOrderIdOrderByChangedAtAsc(UUID workOrderId);
}
