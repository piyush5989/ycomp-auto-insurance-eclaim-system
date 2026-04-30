package com.yclaims.customers.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerProfileJpaRepository extends JpaRepository<CustomerProfileEntity, UUID> {
    Optional<CustomerProfileEntity> findByCustomerId(String customerId);
}
