package com.yclaims.claims.infrastructure.persistence;

import com.yclaims.claims.domain.model.Claim;
import com.yclaims.claims.domain.model.ClaimId;
import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.domain.port.out.ClaimRepository;
import com.yclaims.claims.infrastructure.persistence.mapper.ClaimEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Implements the ClaimRepository port using Spring Data JPA.
 * The domain has zero knowledge of JPA — it only knows the ClaimRepository interface.
 */
@Component
@RequiredArgsConstructor
public class ClaimPersistenceAdapter implements ClaimRepository {

    private final ClaimJpaRepository jpaRepository;
    private final ClaimEntityMapper mapper;

    @Override
    public Claim save(Claim claim) {
        ClaimEntity entity = mapper.toEntity(claim);
        ClaimEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Claim> findById(ClaimId id) {
        return jpaRepository.findById(id.getValue()).map(mapper::toDomain);
    }

    @Override
    public Optional<Claim> findByNaturalKey(String policyNumber, LocalDate incidentDate,
                                             String vehicleRegistration) {
        return jpaRepository
                .findByPolicyNumberAndIncidentDateAndVehicleRegistration(
                        policyNumber, incidentDate, vehicleRegistration)
                .map(mapper::toDomain);
    }

    @Override
    public List<Claim> findByCustomerId(String customerId) {
        return jpaRepository.findByCustomerId(customerId).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Claim> findByCustomerIdAndStatusIn(String customerId, List<ClaimStatus> statuses) {
        return jpaRepository.findByCustomerIdAndStatusIn(customerId, statuses).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public int countRecentClaimsForVehicle(String vehicleRegistration, int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return jpaRepository.countRecentClaimsForVehicle(vehicleRegistration, since);
    }

    @Override
    public List<Claim> findPotentialDuplicates(String customerId, String vehicleRegistration,
                                                LocalDate from, LocalDate to) {
        return jpaRepository.findPotentialDuplicates(customerId, vehicleRegistration, from, to)
                .stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Claim> findByStatus(ClaimStatus status, int page, int size) {
        return jpaRepository.findByStatus(status, PageRequest.of(page, size)).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Claim> findByAssignedSurveyorId(String surveyorId, List<ClaimStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return jpaRepository.findByAssignedSurveyorId(surveyorId).stream()
                    .map(mapper::toDomain)
                    .toList();
        }
        return jpaRepository.findByAssignedSurveyorIdAndStatusIn(surveyorId, statuses).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Claim> findByAssignedAdjustorId(String adjustorId, List<ClaimStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return jpaRepository.findByAssignedAdjustorId(adjustorId).stream()
                    .map(mapper::toDomain)
                    .toList();
        }
        return jpaRepository.findByAssignedAdjustorIdAndStatusIn(adjustorId, statuses).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public ClaimsPage findByFilters(ClaimStatus status, String region, Boolean fraudFlag,
                                    String assignedTo, int page, int size, String sortBy, String sortOrder) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = sortBy != null ? sortBy : "createdAt";
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortField));
        
        List<ClaimEntity> entities = jpaRepository.findByFilters(status, region, fraudFlag, assignedTo, pageRequest);
        long totalElements = jpaRepository.countByFilters(status, region, fraudFlag, assignedTo);
        int totalPages = (int) Math.ceil((double) totalElements / size);
        
        List<Claim> claims = entities.stream()
                .map(mapper::toDomain)
                .toList();
        
        return new ClaimsPage(claims, totalElements, totalPages, page, size);
    }
}
