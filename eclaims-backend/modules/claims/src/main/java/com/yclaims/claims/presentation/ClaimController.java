package com.yclaims.claims.presentation;

import com.yclaims.claims.application.ClaimApplicationService;
import com.yclaims.claims.application.command.SubmitClaimCommand;
import com.yclaims.claims.application.command.UpdateClaimStatusCommand;
import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.presentation.dto.*;
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

    @GetMapping
    @PreAuthorize("hasAnyRole('SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR','REGIONAL_MGR','TOP_MANAGEMENT')")
    @Operation(summary = "Query claims with advanced filters and pagination")
    public ResponseEntity<ApiResponse<ClaimsPageResponse>> queryClaims(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) Boolean fraudFlag,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        
        ClaimStatus statusEnum = status != null ? ClaimStatus.valueOf(status) : null;
        ClaimsPageResponse response = claimService.queryClaimsWithFilters(
                statusEnum, region, fraudFlag, assignedTo, page, size, sortBy, sortOrder);
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
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

    /**
     * Soft duplicate check — call before submitting a new claim.
     * Returns similar active claims within a ±30-day window; never blocks submission.
     */
    @PostMapping("/check-duplicates")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Check for potentially duplicate claims (soft warning only)")
    public ResponseEntity<ApiResponse<List<PotentialDuplicateResponse>>> checkDuplicates(
            @Valid @RequestBody DuplicateCheckRequest request) {
        List<PotentialDuplicateResponse> duplicates = claimService.checkPotentialDuplicates(
                UserContextHolder.currentUserId(),
                request.vehicleRegistration(),
                request.incidentDate());
        return ResponseEntity.ok(ApiResponse.success(duplicates, correlationId()));
    }

    /**
     * Customer edits incident description / location while claim is still SUBMITTED.
     * Returns 400 if the claim has already been assigned or progressed further.
     */
    @PatchMapping("/{claimId}/incident-details")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Update incident description and location (SUBMITTED state only)")
    public ResponseEntity<ApiResponse<ClaimResponse>> updateIncidentDetails(
            @PathVariable UUID claimId,
            @Valid @RequestBody UpdateIncidentDetailsRequest request) {
        ClaimResponse response = claimService.updateIncidentDetails(
                claimId, request.incidentLocation(), request.description(),
                UserContextHolder.currentUserId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/{claimId}/endorsements")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER','AUDITOR')")
    @Operation(summary = "List all endorsements/notes for a claim")
    public ResponseEntity<ApiResponse<List<ClaimEndorsementResponse>>> getEndorsements(
            @PathVariable UUID claimId) {
        return ResponseEntity.ok(ApiResponse.success(
                claimService.getEndorsements(claimId), correlationId()));
    }

    @PostMapping("/{claimId}/endorsements")
    @PreAuthorize("hasAnyRole('CUSTOMER','SURVEYOR','ADJUSTOR','CASE_MANAGER')")
    @Operation(summary = "Add a note/endorsement to a claim")
    public ResponseEntity<ApiResponse<ClaimEndorsementResponse>> addEndorsement(
            @PathVariable UUID claimId,
            @Valid @RequestBody AddEndorsementRequest request) {
        ClaimEndorsementResponse response = claimService.addEndorsement(
                claimId, request.note(),
                UserContextHolder.currentUserId(), "CUSTOMER_NOTE");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId()));
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

    @PostMapping("/{claimId}/reassign-surveyor")
    @PreAuthorize("hasRole('CASE_MANAGER')")
    @Operation(summary = "Reassign claim to a different surveyor (case manager only)")
    public ResponseEntity<ApiResponse<ClaimResponse>> reassignSurveyor(
            @PathVariable UUID claimId,
            @Valid @RequestBody ReassignRequest request) {
        ClaimResponse response = claimService.reassignSurveyor(
                claimId, request.newUserId(), request.reason(),
                UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @PostMapping("/{claimId}/reassign-adjustor")
    @PreAuthorize("hasRole('CASE_MANAGER')")
    @Operation(summary = "Reassign claim to a different adjustor (case manager only)")
    public ResponseEntity<ApiResponse<ClaimResponse>> reassignAdjustor(
            @PathVariable UUID claimId,
            @Valid @RequestBody ReassignRequest request) {
        ClaimResponse response = claimService.reassignAdjustor(
                claimId, request.newUserId(), request.reason(),
                UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @PostMapping("/{claimId}/override")
    @PreAuthorize("hasRole('CASE_MANAGER')")
    @Operation(summary = "Override adjudication decision (case manager only)")
    public ResponseEntity<ApiResponse<ClaimResponse>> overrideDecision(
            @PathVariable UUID claimId,
            @Valid @RequestBody OverrideDecisionRequest request) {
        ClaimResponse response = claimService.overrideDecision(
                claimId, request.newAmount(), request.reason(),
                UserContextHolder.currentUserId(), correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
