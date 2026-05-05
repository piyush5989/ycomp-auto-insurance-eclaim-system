package com.yclaims.payments.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yclaims.contracts.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relay service for the payments outbox.
 * Polls every 500ms (configurable), sends to Kafka, marks rows published.
 * Runs in a @Transactional: any Kafka failure rolls back the markPublished() so the row retries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOutboxRelayService {

    private final PaymentOutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${eclaims.outbox.relay-interval-ms:500}")
    @Transactional
    public void relay() {
        List<PaymentOutboxEntity> pending = outboxRepository.findUnpublished();
        if (pending.isEmpty()) return;

        for (PaymentOutboxEntity row : pending) {
            try {
                DomainEvent<?> event = objectMapper.readValue(row.getPayload(), DomainEvent.class);
                kafkaTemplate.send(row.getTopic(), row.getAggregateId(), event).get();
                row.markPublished();
                outboxRepository.save(row);
            } catch (Exception ex) {
                log.error("Payment outbox relay failed for event [{}] — will retry: {}",
                        row.getId(), ex.getMessage());
                throw new RuntimeException("Payment outbox relay failed", ex);
            }
        }
    }
}
