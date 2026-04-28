package com.yclaims.contracts.events;

import java.time.Instant;

/**
 * Standard envelope for ALL Kafka messages in the eClaims system.
 * Every module publishes this — no raw payloads go on the bus.
 *
 * Key fields for operations:
 *   eventId       → consumer deduplication (SETNX in Redis)
 *   correlationId → distributed tracing across module boundaries
 *   causationId   → the eventId that triggered this event (event chain debugging)
 *   version       → schema evolution without breaking consumers ("v1", "v2")
 */
public record DomainEvent<T>(
        String eventId,         // UUID — primary key for idempotent consumer dedup
        String eventType,       // e.g. "claim.created", "payment.settled"
        String correlationId,   // traces the full user request across modules
        String causationId,     // eventId of the triggering event (null for user-initiated)
        String aggregateId,     // ID of the aggregate (e.g. claimId, paymentId)
        String aggregateType,   // e.g. "Claim", "Payment"
        String version,         // "v1" — bump to "v2" for additive schema changes only
        Instant occurredAt,     // when the domain fact occurred (not processing time)
        T payload               // module-specific payload
) {}
