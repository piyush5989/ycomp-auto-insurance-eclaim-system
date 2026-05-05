package com.yclaims.claims.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for the claims outbox.
 *
 * Uses a native PostgreSQL query with FOR UPDATE SKIP LOCKED:
 *   - FOR UPDATE: pessimistic row lock prevents concurrent relay instances processing the same row.
 *   - SKIP LOCKED: rows already locked by another transaction are bypassed instead of blocking —
 *     safe for rolling deploys where two relay pods briefly coexist.
 *
 * Note: JPQL does not support LIMIT or SKIP LOCKED — native SQL is required here.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
            SELECT * FROM claims.outbox_events
            WHERE published = false
            ORDER BY created_at ASC
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    List<OutboxEventEntity> findUnpublished();

    @Modifying
    @Query(value = "UPDATE claims.outbox_events SET published = true, published_at = NOW() WHERE id = :id",
            nativeQuery = true)
    void markPublished(UUID id);
}
