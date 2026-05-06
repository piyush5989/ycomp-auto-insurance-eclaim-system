package com.yclaims.workflow.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SurveyorZipCoverageJpaRepository extends JpaRepository<SurveyorZipCoverageEntity, UUID> {

    @Query("SELECT c.region FROM SurveyorZipCoverageEntity c WHERE c.zipPrefix = :zipPrefix")
    Optional<String> findRegionByZipPrefix(@Param("zipPrefix") String zipPrefix);
}
