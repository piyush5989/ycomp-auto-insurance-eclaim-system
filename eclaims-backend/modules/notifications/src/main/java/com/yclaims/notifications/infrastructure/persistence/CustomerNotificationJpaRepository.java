package com.yclaims.notifications.infrastructure.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CustomerNotificationJpaRepository
        extends JpaRepository<CustomerNotificationEntity, UUID> {

    List<CustomerNotificationEntity> findByCustomerIdOrderByCreatedAtDesc(
            String customerId, Pageable pageable);

    List<CustomerNotificationEntity> findByCustomerIdAndReadFalseOrderByCreatedAtDesc(
            String customerId, Pageable pageable);

    long countByCustomerIdAndReadFalse(String customerId);

    @Modifying
    @Query("UPDATE CustomerNotificationEntity n SET n.read = true WHERE n.id = :id AND n.customerId = :customerId")
    int markAsRead(UUID id, String customerId);
}
