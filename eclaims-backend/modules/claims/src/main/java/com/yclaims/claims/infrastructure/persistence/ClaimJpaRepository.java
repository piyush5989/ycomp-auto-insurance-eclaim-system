package com.yclaims.claims.infrastructure.persistence;

import com.yclaims.claims.domain.model.ClaimStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
     * GDPR right-to-erasure: replace all PII fields with an anonymisation token.
     * Uses a direct UPDATE rather than loading the domain model to avoid triggering
     * the state machine on an entity whose lifecycle is already closed.
     */
    @Modifying
    @Query("UPDATE ClaimEntity c SET " +
           "c.customerId = :token, " +
           "c.customerEmail = :token, " +
           "c.customerPhone = :token " +
           "WHERE c.id = :id")
    void anonymisePii(@Param("id") UUID id, @Param("token") String token);
}
