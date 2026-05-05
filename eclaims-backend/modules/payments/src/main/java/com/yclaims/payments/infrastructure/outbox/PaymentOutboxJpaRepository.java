package com.yclaims.payments.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxJpaRepository extends JpaRepository<PaymentOutboxEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM PaymentOutboxEntity o
            WHERE o.published = false
            ORDER BY o.createdAt ASC
            LIMIT 50
            """)
    List<PaymentOutboxEntity> findUnpublished();
}
