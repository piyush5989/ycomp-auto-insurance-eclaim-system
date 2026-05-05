package com.yclaims.claims.infrastructure.sse;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.ClaimStatusChangedPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Dedicated Kafka consumer for SSE broadcasting.
 * Separate consumer group ("sse-service") so it gets its own offset — independent
 * of the notification-service and workflow-service consumer groups.
 *
 * Flow:
 *   claim.status.changed arrives on claim-events
 *   → this consumer calls ClaimStatusSseBroadcaster.broadcast()
 *   → all open SSE connections for that claimId receive an event immediately
 *   → browser updates status without polling
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimStatusSseConsumer {

    private final ClaimStatusSseBroadcaster broadcaster;

    @KafkaListener(
        topics = "claim-events",
        groupId = "sse-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onClaimEvent(DomainEvent<?> event) {
        if (!"claim.status.changed".equals(event.eventType())) return;
        if (!(event.payload() instanceof ClaimStatusChangedPayload payload)) return;

        log.debug("SSE consumer: claim {} status {} → {}",
                payload.claimId(), payload.previousStatus(), payload.newStatus());

        broadcaster.broadcast(payload.claimId(), new ClaimStatusSseBroadcaster.SseStatusUpdate(
                payload.claimId().toString(),
                payload.previousStatus(),
                payload.newStatus(),
                payload.changedByUserId(),
                payload.changeReason(),
                event.occurredAt().toString()
        ));
    }
}
