package com.yclaims.claims.presentation;

import com.yclaims.claims.application.ClaimApplicationService;
import com.yclaims.claims.application.command.SubmitClaimCommand;
import com.yclaims.claims.application.command.UpdateClaimStatusCommand;
import com.yclaims.claims.presentation.dto.ClaimResponse;
import com.yclaims.claims.presentation.dto.ClaimStatusUpdateRequest;
import com.yclaims.claims.presentation.dto.ClaimSubmissionRequest;
import com.yclaims.kernel.security.UserContextHolder;
import com.yclaims.kernel.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for the Claims bounded context.
 * Endpoints versioned under /api/v1/claims.
 *
 * Request flow:  HTTP JSON → DTO → Command → ApplicationService → Domain → Repository
 * Response flow: Domain → DTO → ApiResponse envelope → HTTP JSON
 *
 * JPA entities never appear here. Domain objects never leave this module.
 */
@RestController
@RequestMapping("/api/v1/claims")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Claims", description = "eClaims lifecycle management API")
public class ClaimController {

    private final ClaimApplicationService claimService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Submit a new insurance claim",
               description = "Idempotent — duplicate submissions for the same policy/incident/vehicle return the existing claim")
    public ResponseEntity<ApiResponse<ClaimResponse>> submitClaim(
            @Valid @RequestBody ClaimSubmissionRequest request) {

        String userId = UserContextHolder.currentUserId();
        SubmitClaimCommand cmd = new SubmitClaimCommand(
                request.policyNumber(),
                request.vehicleRegistration(),
                request.incidentDate(),
                request.incidentLocation(),
                request.description(),
                request.claimType(),
                request.policeReportFiled(),
                request.policeReportNumber(),
                correlationId(),
                userId
        );

        ClaimResponse response = claimService.submitClaim(cmd);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/{claimId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR','TOP_MANAGEMENT')")
    @Operation(summary = "Get claim details by ID")
    public ResponseEntity<ApiResponse<ClaimResponse>> getClaim(@PathVariable UUID claimId) {
        ClaimResponse response = claimService.getClaimById(claimId, UserContextHolder.currentUserId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/my-claims")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "List all claims for the authenticated customer")
    public ResponseEntity<ApiResponse<List<ClaimResponse>>> getMyClaims() {
        String customerId = UserContextHolder.currentUserId();
        List<ClaimResponse> claims = claimService.getClaimsByCustomer(customerId);
        return ResponseEntity.ok(ApiResponse.success(claims, correlationId()));
    }

    @PatchMapping("/{claimId}/status")
    @PreAuthorize("hasAnyRole('SURVEYOR','ADJUSTOR','CASE_MANAGER')")
    @Operation(summary = "Update claim status — triggers state machine transition")
    public ResponseEntity<ApiResponse<ClaimResponse>> updateStatus(
            @PathVariable UUID claimId,
            @Valid @RequestBody ClaimStatusUpdateRequest request) {

        UpdateClaimStatusCommand cmd = new UpdateClaimStatusCommand(
                claimId,
                request.targetStatus(),
                UserContextHolder.currentUserId(),
                request.amount(),
                request.reason(),
                request.workshopId(),
                correlationId()
        );

        ClaimResponse response = claimService.updateClaimStatus(cmd);
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @DeleteMapping("/{claimId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Withdraw a claim (customer-initiated)")
    public ResponseEntity<ApiResponse<ClaimResponse>> withdrawClaim(@PathVariable UUID claimId) {
        UpdateClaimStatusCommand cmd = new UpdateClaimStatusCommand(
                claimId,
                com.yclaims.claims.domain.model.ClaimStatus.WITHDRAWN,
                UserContextHolder.currentUserId(),
                null, "Customer withdrawal", null,
                correlationId()
        );
        ClaimResponse response = claimService.updateClaimStatus(cmd);
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
