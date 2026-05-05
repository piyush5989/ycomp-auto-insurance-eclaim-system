package com.yclaims.claims.presentation;

import com.yclaims.claims.infrastructure.sse.ClaimStatusSseBroadcaster;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Server-Sent Events (SSE) endpoint for real-time claim status updates.
 *
 * Replaces the frontend's TanStack Query polling (30s interval) with an instant push.
 * Each browser tab opens one persistent HTTP connection; when a claim.status.changed
 * event arrives on Kafka, ClaimStatusSseConsumer broadcasts to all matching emitters.
 *
 * Usage (frontend EventSource):
 *   const es = new EventSource('/api/v1/claims/{id}/events');
 *   es.addEventListener('claim-status', e => { ... JSON.parse(e.data) ... });
 *
 * Security: same RBAC as the claim GET endpoint — customer can only subscribe to their own claims.
 * Connection lifecycle: 5-minute timeout with auto-reconnect on the client side.
 */
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Claims", description = "eClaims lifecycle management API")
public class ClaimStatusSseController {

    private final ClaimStatusSseBroadcaster broadcaster;

    @GetMapping(value = "/{claimId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR','TOP_MANAGEMENT')")
    @Operation(
        summary = "Subscribe to real-time claim status events (SSE)",
        description = "Opens a persistent SSE connection. Sends a 'claim-status' event on every state transition."
    )
    public SseEmitter subscribeToClaimEvents(@PathVariable UUID claimId) {
        log.info("SSE: new subscriber for claimId={}", claimId);
        return broadcaster.register(claimId);
    }
}
