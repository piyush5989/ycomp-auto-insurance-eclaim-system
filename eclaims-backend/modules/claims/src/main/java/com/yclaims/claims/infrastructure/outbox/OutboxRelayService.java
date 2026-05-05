package com.yclaims.claims.infrastructure.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.contracts.events.DomainEvent;

import java.util.List;

/**
 * Outbox relay service for the claims module.
 *
 * Polls claims.outbox_events every 500ms for unpublished rows.
 * For each row: publish to Kafka → mark as published.
 *
 * SKIP LOCKED on the repo query means concurrent relay instances (e.g. rolling deploy
 * with two pods briefly alive) skip rows being processed elsewhere — no double-publish.
 *
 * Failure handling:
 *   - If kafkaTemplate.send() throws: the @Transactional rolls back — row stays unpublished, retried next tick.
 *   - Kafka consumers are idempotent (Redis SETNX on eventId) so any rare duplicate is harmless.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventJpaRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${eclaims.outbox.relay-interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEventEntity> pending = outboxRepository.findUnpublished();
        if (pending.isEmpty()) return;

        log.debug("Outbox relay: processing {} pending events", pending.size());

        for (OutboxEventEntity row : pending) {
            try {
                DomainEvent<?> event = objectMapper.readValue(row.getPayload(), DomainEvent.class);
                kafkaTemplate.send(row.getTopic(), row.getAggregateId(), event).get();
                row.markPublished();
                outboxRepository.save(row);
                log.debug("Outbox relay: published [{}] type={} topic={}",
                        row.getId(), row.getEventType(), row.getTopic());
            } catch (Exception ex) {
                log.error("Outbox relay: failed to publish event [{}] type={} — will retry: {}",
                        row.getId(), row.getEventType(), ex.getMessage());
                throw new RuntimeException("Outbox relay failed — rolling back to retry", ex);
            }
        }
    }
}
