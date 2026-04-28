package com.yclaims.claims.domain.port.out;

import com.yclaims.claims.domain.model.Claim;
import com.yclaims.claims.domain.model.ClaimId;
import com.yclaims.claims.domain.model.ClaimStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for Claim aggregate.
 * The domain defines this interface; infrastructure implements it.
 * This is the only allowed way for the domain to interact with persistence.
 */
public interface ClaimRepository {

    Claim save(Claim claim);

    Optional<Claim> findById(ClaimId id);

    /**
     * Natural key lookup for idempotent claim creation.
     * Prevents duplicate claims for the same incident.
     */
    Optional<Claim> findByNaturalKey(String policyNumber, LocalDate incidentDate, String vehicleRegistration);

    List<Claim> findByCustomerId(String customerId);

    List<Claim> findByCustomerIdAndStatusIn(String customerId, List<ClaimStatus> statuses);

    /**
     * Returns count of claims for a vehicle in the last N days — used by fraud detection.
     */
    int countRecentClaimsForVehicle(String vehicleRegistration, int days);

    /**
     * Paginated claim list for internal portal queue.
     */
    List<Claim> findByStatus(ClaimStatus status, int page, int size);
}
