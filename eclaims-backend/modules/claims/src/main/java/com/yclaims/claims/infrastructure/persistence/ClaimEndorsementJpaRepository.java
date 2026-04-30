package com.yclaims.claims.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClaimEndorsementJpaRepository extends JpaRepository<ClaimEndorsementEntity, UUID> {
    List<ClaimEndorsementEntity> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
}
