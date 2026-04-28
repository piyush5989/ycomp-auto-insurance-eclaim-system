package com.yclaims.claims.infrastructure.event;

import com.yclaims.claims.domain.port.out.DomainEventPublisher;
import com.yclaims.contracts.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Implements DomainEventPublisher port using Spring Kafka.
 * Publishes DomainEvent<T> envelopes to Redpanda (Kafka-compatible).
 *
 * The domain and application layers have no Kafka dependency —
 * they only know the DomainEventPublisher interface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaClaimEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public <T> void publish(String topic, DomainEvent<T> event) {
        log.debug("Publishing event [{}] type={} to topic={}",
                event.eventId(), event.eventType(), topic);

        kafkaTemplate.send(topic, event.aggregateId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event [{}] to topic {}: {}",
                                event.eventId(), topic, ex.getMessage());
                    } else {
                        log.debug("Event [{}] published to topic {} partition {} offset {}",
                                event.eventId(), topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
