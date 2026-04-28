package com.yclaims.claims.infrastructure.persistence;

import com.yclaims.claims.domain.model.Claim;
import com.yclaims.claims.domain.model.ClaimId;
import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.domain.port.out.ClaimRepository;
import com.yclaims.claims.infrastructure.persistence.mapper.ClaimEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
        return jpaRepository.countRecentClaimsForVehicle(vehicleRegistration, days);
    }

    @Override
    public List<Claim> findByStatus(ClaimStatus status, int page, int size) {
        return jpaRepository.findByStatus(status, PageRequest.of(page, size)).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
