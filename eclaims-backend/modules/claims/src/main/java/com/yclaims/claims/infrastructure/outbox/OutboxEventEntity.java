package com.yclaims.claims.infrastructure.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for the claims.outbox_events table.
 * Written atomically with the domain aggregate save in the same @Transactional boundary.
 * Read and forwarded to Kafka by OutboxRelayService.
 */
@Entity
@Table(name = "outbox_events", schema = "claims")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 100)
    private String topic;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    public static OutboxEventEntity create(String aggregateId, String aggregateType,
                                           String eventType, String topic, String payload) {
        OutboxEventEntity e = new OutboxEventEntity();
        e.id = UUID.randomUUID();
        e.aggregateId = aggregateId;
        e.aggregateType = aggregateType;
        e.eventType = eventType;
        e.topic = topic;
        e.payload = payload;
        e.createdAt = Instant.now();
        e.published = false;
        return e;
    }

    // Publishing is marked via OutboxEventJpaRepository.markPublished(id) — native UPDATE.
    // No setter here — prevents accidental in-memory mutation bypassing the DB update.
}
