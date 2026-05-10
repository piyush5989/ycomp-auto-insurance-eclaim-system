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
     * Legacy lookup retained for adapter compatibility.
     * No longer used for hard deduplication — use checkPotentialDuplicates for soft warnings.
     */
    Optional<Claim> findByNaturalKey(String policyNumber, LocalDate incidentDate, String vehicleRegistration);

    /**
     * Finds active claims for the same customer + vehicle within a date window.
     * Used for soft duplicate detection: warns the customer, does NOT block submission.
     */
    List<Claim> findPotentialDuplicates(String customerId, String vehicleRegistration,
                                         LocalDate from, LocalDate to);

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

    /**
     * Find claims assigned to a specific surveyor with optional status filter.
     * Used by surveyor portal to show "My Assignments".
     */
    List<Claim> findByAssignedSurveyorId(String surveyorId, List<ClaimStatus> statuses);

    /**
     * Find claims assigned to a specific adjustor with optional status filter.
     * Used by adjustor portal to show "My Cases".
     */
    List<Claim> findByAssignedAdjustorId(String adjustorId, List<ClaimStatus> statuses);

    /**
     * Advanced filtering with pagination for internal portal claims queue.
     * Returns paginated list of claims matching the filter criteria.
     */
    ClaimsPage findByFilters(ClaimStatus status, String region, Boolean fraudFlag, 
                             String assignedTo, int page, int size, String sortBy, String sortOrder);

    /**
     * Container for paginated results
     */
    record ClaimsPage(List<Claim> content, long totalElements, int totalPages, int currentPage, int pageSize) {}
}
