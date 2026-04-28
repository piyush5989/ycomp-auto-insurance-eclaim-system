package com.yclaims.claims.domain.port.out;

import com.yclaims.contracts.events.DomainEvent;

/**
 * Port for publishing domain events to the event bus (Kafka).
 * Implemented by KafkaClaimEventPublisher in the infrastructure layer.
 */
public interface DomainEventPublisher {

    <T> void publish(String topic, DomainEvent<T> event);
}
