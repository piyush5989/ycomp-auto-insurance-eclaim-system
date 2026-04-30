package com.yclaims.workshops.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkOrderJpaRepository extends JpaRepository<WorkOrderEntity, UUID> {
    java.util.Optional<WorkOrderEntity> findFirstByClaimIdOrderByCreatedAtDesc(UUID claimId);
}
