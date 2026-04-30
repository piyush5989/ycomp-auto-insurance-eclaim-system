package com.yclaims.claims.infrastructure.persistence;

import com.yclaims.claims.domain.model.ClaimStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for ClaimEntity.
 * Only accessed by ClaimPersistenceAdapter — never directly by domain or application layers.
 */
public interface ClaimJpaRepository extends JpaRepository<ClaimEntity, UUID> {

    /**
     * Natural key lookup for idempotent claim creation.
     * Maps to unique constraint: uq_claim_natural_key (policy_number, incident_date, vehicle_registration).
     */
    Optional<ClaimEntity> findByPolicyNumberAndIncidentDateAndVehicleRegistration(
            String policyNumber, LocalDate incidentDate, String vehicleRegistration);

    List<ClaimEntity> findByCustomerId(String customerId);

    List<ClaimEntity> findByCustomerIdAndStatusIn(String customerId, List<ClaimStatus> statuses);

    List<ClaimEntity> findByStatus(ClaimStatus status, Pageable pageable);

    /**
     * Count of claims for a vehicle in recent N days — used by fraud detection rule.
     * Avoids N+1 by using a single COUNT query.
     */
    @Query("SELECT COUNT(c) FROM ClaimEntity c " +
           "WHERE c.vehicleRegistration = :reg " +
           "AND c.createdAt >= :since")
    int countRecentClaimsForVehicle(@Param("reg") String vehicleRegistration, @Param("since") Instant since);

    /**
     * Soft-duplicate check: active claims for same customer + vehicle within an incident date window.
     * Returns max 5 to keep the warning UI compact.
     */
    @Query("SELECT c FROM ClaimEntity c " +
           "WHERE c.customerId = :customerId " +
           "AND c.vehicleRegistration = :vehicleReg " +
           "AND c.incidentDate BETWEEN :from AND :to " +
           "AND c.status NOT IN ('WITHDRAWN', 'ARCHIVED', 'REJECTED')" +
           "ORDER BY c.incidentDate DESC")
    List<ClaimEntity> findPotentialDuplicates(
            @Param("customerId") String customerId,
            @Param("vehicleReg") String vehicleRegistration,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /**
     * Find claims assigned to a specific surveyor with optional status filter.
     * Used by surveyor portal to show "My Assignments".
     */
    List<ClaimEntity> findByAssignedSurveyorIdAndStatusIn(String assignedSurveyorId, List<ClaimStatus> statuses);

    List<ClaimEntity> findByAssignedSurveyorId(String assignedSurveyorId);

    /**
     * Find claims assigned to a specific adjustor with optional status filter.
     * Used by adjustor portal to show "My Cases".
     */
    List<ClaimEntity> findByAssignedAdjustorIdAndStatusIn(String assignedAdjustorId, List<ClaimStatus> statuses);

    List<ClaimEntity> findByAssignedAdjustorId(String assignedAdjustorId);

    /**
     * Advanced filtering with pagination for internal portal claims queue.
     * Supports filtering by status, region, fraud flag, and assigned user.
     */
    @Query("SELECT c FROM ClaimEntity c " +
           "WHERE (:status IS NULL OR c.status = :status) " +
           "AND (:region IS NULL OR c.region = :region) " +
           "AND (:fraudFlag IS NULL OR c.fraudFlag = :fraudFlag) " +
           "AND (:assignedTo IS NULL OR c.assignedSurveyorId = :assignedTo OR c.assignedAdjustorId = :assignedTo) " +
           "ORDER BY c.createdAt DESC")
    List<ClaimEntity> findByFilters(
            @Param("status") ClaimStatus status,
            @Param("region") String region,
            @Param("fraudFlag") Boolean fraudFlag,
            @Param("assignedTo") String assignedTo,
            Pageable pageable);

    /**
     * Count for pagination - matches findByFilters criteria
     */
    @Query("SELECT COUNT(c) FROM ClaimEntity c " +
           "WHERE (:status IS NULL OR c.status = :status) " +
           "AND (:region IS NULL OR c.region = :region) " +
           "AND (:fraudFlag IS NULL OR c.fraudFlag = :fraudFlag) " +
           "AND (:assignedTo IS NULL OR c.assignedSurveyorId = :assignedTo OR c.assignedAdjustorId = :assignedTo)")
    long countByFilters(
            @Param("status") ClaimStatus status,
            @Param("region") String region,
            @Param("fraudFlag") Boolean fraudFlag,
            @Param("assignedTo") String assignedTo);
}
