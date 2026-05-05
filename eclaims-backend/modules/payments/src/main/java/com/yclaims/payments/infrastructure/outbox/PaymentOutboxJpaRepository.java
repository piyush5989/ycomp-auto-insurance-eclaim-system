package com.yclaims.payments.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PaymentOutboxJpaRepository extends JpaRepository<PaymentOutboxEntity, UUID> {

    @Query(value = """
            SELECT * FROM payments.outbox_events
            WHERE published = false
            ORDER BY created_at ASC
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<PaymentOutboxEntity> findUnpublished();

    @Modifying
    @Query(value = "UPDATE payments.outbox_events SET published = true, published_at = NOW() WHERE id = :id",
            nativeQuery = true)
    void markPublished(UUID id);
}
