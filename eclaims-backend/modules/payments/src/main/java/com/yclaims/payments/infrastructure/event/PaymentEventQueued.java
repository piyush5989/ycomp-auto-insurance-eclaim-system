package com.yclaims.payments.infrastructure.event;

import com.yclaims.contracts.events.DomainEvent;

/**
 * Spring ApplicationEvent carrier for a pending payment Kafka send.
 * Published inside a @Transactional method; dispatched to Kafka by
 * KafkaPaymentEventPublisher only AFTER the DB transaction commits.
 */
public record PaymentEventQueued(String topic, DomainEvent<?> event) {}
