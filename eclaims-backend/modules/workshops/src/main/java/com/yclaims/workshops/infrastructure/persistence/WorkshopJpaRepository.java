package com.yclaims.workshops.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkshopJpaRepository extends JpaRepository<WorkshopEntity, UUID> {
    List<WorkshopEntity> findByActiveTrue();
    List<WorkshopEntity> findByProviderTypeAndActiveTrue(String providerType);
    List<WorkshopEntity> findByCityContainingIgnoreCaseAndActiveTrue(String city);
    List<WorkshopEntity> findByCityContainingIgnoreCaseAndProviderTypeAndActiveTrue(String city, String providerType);
    List<WorkshopEntity> findByZipCodeAndActiveTrue(String zipCode);
    List<WorkshopEntity> findByZipCodeAndProviderTypeAndActiveTrue(String zipCode, String providerType);
}
