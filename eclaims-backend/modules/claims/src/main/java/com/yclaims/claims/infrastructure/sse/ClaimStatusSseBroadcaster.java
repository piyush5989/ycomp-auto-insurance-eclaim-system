package com.yclaims.claims.infrastructure.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

/**
 * Registry of active SSE emitters, keyed by claimId.
 *
 * Thread-safe: ConcurrentHashMap + CopyOnWriteArrayList.
 * Emitters are registered by ClaimStatusSseController (one per browser tab)
 * and notified by ClaimStatusSseConsumer (Kafka listener on claim-events).
 *
 * Emitters auto-clean on completion/timeout/error — no leak.
 */
@Component
@Slf4j
public class ClaimStatusSseBroadcaster {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L; // 5 minutes

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * Register a new SSE connection for a specific claim.
     * The emitter is cleaned up automatically on completion, timeout, or error.
     */
    public SseEmitter register(UUID claimId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.computeIfAbsent(claimId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> remove(claimId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        log.debug("SSE: registered emitter for claimId={}, total for this claim={}",
                claimId, emitters.getOrDefault(claimId, List.of()).size());
        return emitter;
    }

    /**
     * Broadcast a status update to all subscribers of the given claimId.
     * Called by ClaimStatusSseConsumer on every claim.status.changed event.
     */
    public void broadcast(UUID claimId, SseStatusUpdate update) {
        List<SseEmitter> claimEmitters = emitters.get(claimId);
        if (claimEmitters == null || claimEmitters.isEmpty()) return;

        log.debug("SSE: broadcasting status update for claimId={} to {} subscriber(s)",
                claimId, claimEmitters.size());

        List<SseEmitter> dead = new CopyOnWriteArrayList<>();
        for (SseEmitter emitter : claimEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("claim-status")
                        .data(update));
            } catch (IOException | IllegalStateException ex) {
                dead.add(emitter);
                log.debug("SSE: emitter dead for claimId={}, removing", claimId);
            }
        }
        claimEmitters.removeAll(dead);
    }

    private void remove(UUID claimId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(claimId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(claimId);
        }
    }

    /** Payload sent to browser via SSE. */
    public record SseStatusUpdate(
            String claimId,
            String previousStatus,
            String newStatus,
            String changedByUserId,
            String reason,
            String occurredAt
    ) {}
}
