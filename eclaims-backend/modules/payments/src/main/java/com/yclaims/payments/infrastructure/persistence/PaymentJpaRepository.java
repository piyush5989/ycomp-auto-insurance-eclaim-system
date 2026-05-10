package com.yclaims.payments.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findTopByClaimIdAndStatusOrderBySettledAtDesc(UUID claimId, String status);
}
