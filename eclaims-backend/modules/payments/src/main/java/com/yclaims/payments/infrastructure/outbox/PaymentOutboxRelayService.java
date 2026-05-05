package com.yclaims.payments.infrastructure.outbox;

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
 * Outbox relay service for the payments module.
 * Same pattern as OutboxRelayService in the claims module.
 * See that class for the full design rationale.
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
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(row.getPayload(), Map.class);

                kafkaTemplate.send(row.getTopic(), row.getAggregateId(), payload)
                        .get(5, TimeUnit.SECONDS);

                outboxRepository.markPublished(row.getId());

                log.debug("Payment outbox relay: published [{}] type={}", row.getId(), row.getEventType());

            } catch (Exception ex) {
                log.error("Payment outbox relay: failed for event [{}] — will retry: {}",
                        row.getId(), ex.getMessage());
                throw new RuntimeException("Payment outbox relay failed", ex);
            }
        }
    }
}
