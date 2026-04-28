package com.yclaims.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AssignmentJpaRepository extends JpaRepository<AssignmentEntity, UUID> {

    @Query("SELECT COUNT(a) FROM AssignmentEntity a WHERE a.surveyorId = :surveyorId AND a.active = true")
    long countActiveBySurveyorId(@Param("surveyorId") UUID surveyorId);
}
