package com.yclaims.workshops.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkshopJpaRepository extends JpaRepository<WorkshopEntity, UUID> {
    List<WorkshopEntity> findByActiveTrue();
    List<WorkshopEntity> findByCityContainingIgnoreCaseAndActiveTrue(String city);
}
