package com.yclaims.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AssignmentJpaRepository extends JpaRepository<AssignmentEntity, UUID> {

    @Query("SELECT COUNT(a) FROM AssignmentEntity a WHERE a.surveyorId = :surveyorId AND a.active = true")
    long countActiveBySurveyorId(@Param("surveyorId") UUID surveyorId);

    /**
     * Batch variant — fetches active assignment counts for multiple surveyors in one query.
     * Returns List of Object[]{UUID surveyorId, Long count}.
     */
    @Query("SELECT a.surveyorId, COUNT(a) FROM AssignmentEntity a " +
           "WHERE a.surveyorId IN :ids AND a.active = true GROUP BY a.surveyorId")
    List<Object[]> countActiveBySurveyorIds(@Param("ids") Collection<UUID> ids);
}
