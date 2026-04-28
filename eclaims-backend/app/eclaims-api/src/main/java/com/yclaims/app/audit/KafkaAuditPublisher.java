package com.yclaims.app.audit;

import com.yclaims.kernel.audit.AuditEvent;
import com.yclaims.kernel.audit.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Implements AuditPublisher port using Kafka.
 * Publishes to 'audit-events' topic — append-only, 7-year retention.
 * Every module uses this via the AuditPublisher interface — no Kafka dependency in domain/application.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaAuditPublisher implements AuditPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publish(AuditEvent event) {
        kafkaTemplate.send("audit-events", event.entityId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish audit event [{}] action={}: {}",
                                event.eventId(), event.action(), ex.getMessage());
                    }
                });
    }
}
