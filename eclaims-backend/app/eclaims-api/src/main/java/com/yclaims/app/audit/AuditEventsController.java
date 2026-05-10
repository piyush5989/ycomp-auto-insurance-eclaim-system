package com.yclaims.app.audit;

import com.yclaims.kernel.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit", description = "Immutable audit trail (audit.audit_log)")
public class AuditEventsController {

    private final AuditLogQueryService auditLogQueryService;

    @GetMapping("/events")
    @PreAuthorize("@authz.isAllowed('claim', 'list-all')")
    @Operation(summary = "List audit events for a claim")
    public ResponseEntity<ApiResponse<List<AuditEventResponse>>> listForClaim(
            @RequestParam("claimId") String claimId) {
        String cid = correlationId();
        UUID id;
        try {
            id = UUID.fromString(claimId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "claimId must be a UUID");
        }
        List<AuditEventResponse> rows = auditLogQueryService.findByClaimId(id);
        log.debug("[{}] audit events for claimId={} count={}", cid, id, rows.size());
        return ResponseEntity.ok(ApiResponse.success(rows, cid));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
