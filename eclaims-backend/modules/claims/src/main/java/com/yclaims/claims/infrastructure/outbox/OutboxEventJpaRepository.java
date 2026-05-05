package com.yclaims.claims.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for the claims outbox.
 * SKIP_LOCKED ensures concurrent relay instances (e.g. rolling deploys) never double-publish.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /**
     * Fetch up to {@code limit} unpublished events ordered by creation time.
     * SKIP LOCKED: rows locked by another transaction are bypassed — safe for concurrent relay pods.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT o FROM OutboxEventEntity o
            WHERE o.published = false
            ORDER BY o.createdAt ASC
            LIMIT 50
            """)
    List<OutboxEventEntity> findUnpublished();
}
