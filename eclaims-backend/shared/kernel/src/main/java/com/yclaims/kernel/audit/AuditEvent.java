package com.yclaims.kernel.audit;

import java.time.Instant;

/**
 * Enhanced audit event record — published to the 'audit.event' Kafka topic by every module.
 * Immutable, append-only. Never read from cache.
 *
 * oldValue / newValue are JSON snapshots for change tracking (compliance + fraud investigation).
 * ipAddress / userAgent are captured for fraud analysis.
 * 7-year retention enforced at the Kafka topic level.
 */
public record AuditEvent(
        String eventId,           // UUID — primary key
        String correlationId,     // ties to the originating HTTP request
        String userId,            // who performed the action
        String userRole,          // role at time of action (denormalised — roles can change)
        String action,            // e.g. "CLAIM_APPROVED", "STATUS_CHANGED", "PAYMENT_INITIATED"
        String entityType,        // e.g. "Claim", "Payment", "Workshop"
        String entityId,          // e.g. the claimId
        String oldValue,          // JSON snapshot of previous state (null for CREATE)
        String newValue,          // JSON snapshot of new state
        String ipAddress,         // originating request IP (fraud investigation)
        String userAgent,         // browser/client type (fraud investigation)
        String sessionId,         // Keycloak session ID
        Instant occurredAt        // when the business action happened
) {}
