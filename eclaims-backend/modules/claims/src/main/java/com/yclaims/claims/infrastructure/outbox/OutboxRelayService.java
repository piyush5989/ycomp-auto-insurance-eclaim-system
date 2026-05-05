package com.yclaims.claims.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Outbox relay service — forwards committed outbox rows to Kafka.
 *
 * Why it exists:
 *   The OutboxDomainEventPublisher writes events to claims.outbox_events in the SAME
 *   DB transaction as the domain save. This relay reads those committed rows and sends
 *   them to Kafka. If Kafka is down, the row stays unpublished and retries next tick.
 *   If the JVM crashes mid-relay, the row is still unpublished (not yet marked) so it
 *   retries on the next startup. No events are ever silently lost.
 *
 * Deserialization:
 *   We read the stored JSON as Map<String,Object> — Jackson produces the same JSON
 *   when re-serialized by KafkaTemplate, so consumers receive byte-for-byte identical
 *   messages to what the original publisher would have sent.
 *
 * Failure handling:
 *   Any Kafka error throws, rolls back the @Transactional, and the row stays unpublished.
 *   The next scheduler tick retries it automatically.
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

        log.debug("Outbox relay: processing {} pending event(s)", pending.size());

        for (OutboxEventEntity row : pending) {
            try {
                // Deserialize to Map — preserves the full JSON structure and re-serializes
                // identically when KafkaTemplate.send() uses JsonSerializer.
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(row.getPayload(), Map.class);

                // 5-second timeout: if Kafka is unavailable, fail fast and retry next tick
                kafkaTemplate.send(row.getTopic(), row.getAggregateId(), payload)
                        .get(5, TimeUnit.SECONDS);

                // markPublished via native UPDATE — avoids re-loading the full entity
                outboxRepository.markPublished(row.getId());

                log.debug("Outbox relay: published [{}] type={} topic={}",
                        row.getId(), row.getEventType(), row.getTopic());

            } catch (Exception ex) {
                log.error("Outbox relay: failed for event [{}] type={} — will retry next tick: {}",
                        row.getId(), row.getEventType(), ex.getMessage());
                // Throw causes @Transactional rollback — markPublished() calls above are undone,
                // so failed rows remain unpublished and are retried in the next scheduled run.
                throw new RuntimeException("Outbox relay failed", ex);
            }
        }
    }
}
