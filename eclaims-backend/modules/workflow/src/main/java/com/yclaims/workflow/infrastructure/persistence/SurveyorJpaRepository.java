package com.yclaims.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SurveyorJpaRepository extends JpaRepository<SurveyorEntity, UUID> {
    List<SurveyorEntity> findByActiveTrue();
    java.util.Optional<SurveyorEntity> findByEmail(String email);
}
