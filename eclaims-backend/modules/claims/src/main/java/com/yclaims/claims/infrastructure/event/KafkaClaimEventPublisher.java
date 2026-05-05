package com.yclaims.claims.infrastructure.event;

import com.yclaims.claims.domain.port.out.DomainEventPublisher;
import com.yclaims.contracts.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Implements DomainEventPublisher by binding Kafka delivery to the transaction lifecycle.
 *
 * How it works:
 *   1. publish() is called INSIDE the @Transactional application service method.
 *      It queues a Spring ApplicationEvent — no Kafka I/O happens yet.
 *   2. Spring fires @TransactionalEventListener(AFTER_COMMIT) once the DB commit succeeds.
 *      Only then is the message sent to Kafka — the event is guaranteed to reflect
 *      data that is already durable in the DB.
 *   3. If the DB transaction rolls back, the ApplicationEvent is discarded automatically
 *      by Spring — no phantom Kafka messages.
 *
 * This gives us the same safety guarantee as the Transactional Outbox without the
 * extra DB table, relay scheduler, or native SQL — ideal for a POC.
 *
 * Trade-off vs Outbox: if the JVM crashes between commit and Kafka send, the event is
 * lost. For the POC this is acceptable; for production, the Outbox pattern is preferred.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaClaimEventPublisher implements DomainEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ApplicationEventPublisher springEventPublisher;

    /**
     * Queues the domain event for post-commit Kafka delivery.
     * Called inside @Transactional — the actual send is deferred to AFTER_COMMIT.
     */
    @Override
    public <T> void publish(String topic, DomainEvent<T> event) {
        log.debug("Queuing event [{}] type={} topic={} for post-commit delivery",
                event.eventId(), event.eventType(), topic);
        springEventPublisher.publishEvent(new KafkaDispatch(topic, event));
    }

    /**
     * Fires after the DB transaction commits successfully.
     * Sends the queued event to Kafka asynchronously (fire-and-forget with error logging).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void forwardToKafka(KafkaDispatch dispatch) {
        log.debug("Post-commit: forwarding event [{}] to topic={}",
                dispatch.event().eventId(), dispatch.topic());

        kafkaTemplate.send(dispatch.topic(), dispatch.event().aggregateId(), dispatch.event())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka send failed for event [{}] topic={}: {}",
                                dispatch.event().eventId(), dispatch.topic(), ex.getMessage());
                    } else {
                        log.debug("Event [{}] delivered to topic={} partition={} offset={}",
                                dispatch.event().eventId(), dispatch.topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Carrier for a pending Kafka send — passed through Spring's transactional event bus.
     * Scoped to this package; nothing outside should construct or depend on this directly.
     */
    public record KafkaDispatch(String topic, DomainEvent<?> event) {}
}
