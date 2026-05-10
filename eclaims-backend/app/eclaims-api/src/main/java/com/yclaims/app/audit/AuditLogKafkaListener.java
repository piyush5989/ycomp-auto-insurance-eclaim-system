package com.yclaims.app.audit;

import com.yclaims.kernel.audit.AuditEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Persists published audit events to {@code audit.audit_log} for internal portal queries.
 * Idempotent on {@code event_id} (unique constraint).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogKafkaListener {

    private final JdbcTemplate jdbcTemplate;

    @KafkaListener(
            topics = "audit-events",
            groupId = "eclaims-audit-persistence",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    public void persist(AuditEvent event) {
        if (event == null || event.eventId() == null) {
            log.warn("Skipping null audit event payload");
            return;
        }
        Instant at = event.occurredAt() != null ? event.occurredAt() : Instant.now();
        try {
            int n = jdbcTemplate.update(
                    """
                    INSERT INTO audit.audit_log (
                        event_id, correlation_id, user_id, user_role, action, entity_type, entity_id,
                        old_value, new_value, ip_address, user_agent, session_id, occurred_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (event_id) DO NOTHING
                    """,
                    event.eventId(),
                    event.correlationId(),
                    event.userId(),
                    event.userRole(),
                    event.action(),
                    event.entityType(),
                    event.entityId(),
                    event.oldValue(),
                    event.newValue(),
                    event.ipAddress(),
                    event.userAgent(),
                    event.sessionId(),
                    Timestamp.from(at));
            if (n > 0) {
                log.debug("Persisted audit event {} action={}", event.eventId(), event.action());
            }
        } catch (Exception e) {
            log.error("Failed to persist audit event {}: {}", event.eventId(), e.getMessage());
        }
    }
}
