package com.yclaims.payments.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards queued payment domain events to Kafka only after the DB transaction commits.
 *
 * Pattern mirrors KafkaClaimEventPublisher — same @TransactionalEventListener(AFTER_COMMIT)
 * guarantee: Kafka receives the event only when the payment row is already durable in the DB.
 *
 * PaymentApplicationService publishes PaymentEventQueued via ApplicationEventPublisher;
 * Spring routes it here post-commit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void forwardToKafka(PaymentEventQueued queued) {
        log.debug("Post-commit: forwarding payment event [{}] to topic={}",
                queued.event().eventId(), queued.topic());

        kafkaTemplate.send(queued.topic(), queued.event().aggregateId(), queued.event())
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka send failed for payment event [{}] topic={}: {}",
                                queued.event().eventId(), queued.topic(), ex.getMessage());
                    } else {
                        log.debug("Payment event [{}] delivered to topic={} partition={} offset={}",
                                queued.event().eventId(), queued.topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
