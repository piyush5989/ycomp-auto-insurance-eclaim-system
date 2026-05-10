package com.yclaims.app.audit;

/**
 * Audit row returned to the internal portal - matches {@code AuditViewPage} shape.
 */
public record AuditEventResponse(
        String eventId,
        String correlationId,
        String userId,
        String userRole,
        String action,
        String entityType,
        String entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        String reason,
        String userAgent,
        String timestamp
) {}
