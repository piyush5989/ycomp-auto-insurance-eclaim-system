package com.yclaims.claims.infrastructure.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.claims.domain.port.out.DomainEventPublisher;
import com.yclaims.contracts.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Transactional Outbox implementation of DomainEventPublisher.
 *
 * Replaces KafkaClaimEventPublisher as the primary DomainEventPublisher adapter.
 * Writes events to the claims.outbox_events table — in the SAME @Transactional
 * as the domain aggregate save — guaranteeing exactly-once publish semantics:
 *
 *   Claim saved → Outbox row written → TX COMMITS
 *   OutboxRelayService → reads outbox → sends to Kafka → marks published
 *
 * If the JVM crashes between commit and Kafka send, the relay re-publishes on restart.
 * Consumers are idempotent (Redis SETNX on eventId) so duplicate delivery is harmless.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDomainEventPublisher implements DomainEventPublisher {

    private final OutboxEventJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public <T> void publish(String topic, DomainEvent<T> event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEventEntity outboxEntry = OutboxEventEntity.create(
                    event.aggregateId(),
                    event.aggregateType(),
                    event.eventType(),
                    topic,
                    payload
            );
            outboxRepository.save(outboxEntry);
            log.debug("Outbox: queued event [{}] type={} topic={}",
                    event.eventId(), event.eventType(), topic);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialise domain event for outbox: " + event.eventId(), e);
        }
    }
}
