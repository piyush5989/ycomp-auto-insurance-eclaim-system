package com.yclaims.payments.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.contracts.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Transactional Outbox publisher for the payments module.
 * Replaces the direct KafkaTemplate injection in PaymentApplicationService.
 *
 * Writing to outbox_events and PaymentEntity.save() happen in the same @Transactional —
 * eliminating the lost-event gap that existed when Kafka was called directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxPublisher {

    private final PaymentOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public <T> void publish(String topic, DomainEvent<T> event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            PaymentOutboxEntity entry = PaymentOutboxEntity.create(
                    event.aggregateId(),
                    event.aggregateType(),
                    event.eventType(),
                    topic,
                    payload
            );
            outboxRepository.save(entry);
            log.debug("Payment outbox: queued event [{}] type={}", event.eventId(), event.eventType());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise payment event for outbox: " + event.eventId(), e);
        }
    }
}
