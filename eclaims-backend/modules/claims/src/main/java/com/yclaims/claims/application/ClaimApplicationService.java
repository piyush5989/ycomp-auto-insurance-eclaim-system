package com.yclaims.claims.application;

import com.yclaims.claims.application.command.SubmitClaimCommand;
import com.yclaims.claims.application.command.UpdateClaimStatusCommand;
import com.yclaims.claims.domain.exception.ClaimNotFoundException;
import com.yclaims.claims.domain.exception.PolicyValidationException;
import com.yclaims.claims.domain.model.*;
import com.yclaims.claims.domain.port.out.ClaimRepository;
import com.yclaims.claims.domain.port.out.DomainEventPublisher;
import com.yclaims.claims.domain.port.out.PolicyServicePort;
import com.yclaims.claims.domain.port.out.WorkshopEmailPort;
import com.yclaims.claims.domain.service.FraudDetectionService;
import com.yclaims.claims.infrastructure.persistence.ClaimEndorsementEntity;
import com.yclaims.claims.infrastructure.persistence.ClaimEndorsementJpaRepository;
import com.yclaims.claims.presentation.dto.*;
import com.yclaims.claims.presentation.mapper.ClaimDtoMapper;
import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.contracts.events.v1.ClaimStatusChangedPayload;
import com.yclaims.kernel.audit.AuditEvent;
import com.yclaims.kernel.audit.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Orchestrates all claims use cases; owns transaction boundaries and event publishing. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimApplicationService {

    private final ClaimRepository claimRepository;
    private final PolicyServicePort policyServicePort;
    private final DomainEventPublisher eventPublisher;
    private final AuditPublisher auditPublisher;
    private final FraudDetectionService fraudDetectionService;
    private final ClaimDtoMapper claimDtoMapper;
    private final ClaimEndorsementJpaRepository endorsementRepository;
    private final WorkshopEmailPort workshopEmailPort;

    @Transactional
    public ClaimResponse submitClaim(SubmitClaimCommand cmd) {
        log.info("[{}] Submitting claim for policy {} vehicle {}",
                cmd.correlationId(), cmd.policyNumber(), cmd.vehicleRegistration());

        PolicyServicePort.PolicyValidationResult policy =
                policyServicePort.validate(cmd.policyNumber(), cmd.vehicleRegistration());
        if (!policy.valid()) {
            throw new PolicyValidationException(cmd.policyNumber(), policy.failureReason());
        }

        AccidentDetails accidentDetails = new AccidentDetails(
                cmd.incidentDate(),
                cmd.incidentLocation(),
                cmd.description(),
                cmd.policeReportFiled(),
                cmd.policeReportNumber()
        );

        // Use the authenticated user's Keycloak sub as the claim owner.
        // In the POC the policy stub returns a synthetic customerId; the real
        // identity authority is always the logged-in user's JWT subject.
        String claimOwner = cmd.requestingUserId();
        String customerEmail = policy.customerEmail() != null ? policy.customerEmail()
                : cmd.requestingUserId() + "@eclaims.local";

        Claim claim = Claim.submit(
                cmd.policyNumber(),
                claimOwner,
                customerEmail,
                cmd.vehicleRegistration(),
                cmd.claimType(),
                accidentDetails
        );

        int recentClaims = claimRepository.countRecentClaimsForVehicle(cmd.vehicleRegistration(), 90);
        var fraudCtx = new FraudDetectionService.FraudContext(recentClaims, null);
        var fraudResult = fraudDetectionService.evaluate(claim, fraudCtx);
        if (fraudResult.fraudFlag()) {
            claim.flagFraud(fraudResult.reason());
            log.warn("[{}] Fraud flag raised for claim {}: {}", cmd.correlationId(), claim.getId(), fraudResult.reason());
        }

        Claim saved = claimRepository.save(claim);
        publishClaimCreatedEvent(saved, cmd.correlationId());
        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(), cmd.correlationId(),
                cmd.requestingUserId(), "ROLE_CUSTOMER",
                "CLAIM_SUBMITTED", "Claim", saved.getId().toString(),
                null, saved.getStatus().name(),
                null, null, null, Instant.now()
        ));

        log.info("[{}] Claim {} created successfully", cmd.correlationId(), saved.getId());
        return claimDtoMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ClaimResponse getClaimById(UUID claimId, String requestingUserId) {
        Claim claim = claimRepository.findById(ClaimId.of(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId.toString()));
        return claimDtoMapper.toResponse(claim);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> getClaimsByCustomer(String customerId) {
        return claimRepository.findByCustomerId(customerId).stream()
                .map(claimDtoMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClaimsPageResponse queryClaimsWithFilters(ClaimStatus status, String region, Boolean fraudFlag,
                                                      String assignedTo, int page, int size, 
                                                      String sortBy, String sortOrder) {
        ClaimRepository.ClaimsPage claimsPage = claimRepository.findByFilters(
                status, region, fraudFlag, assignedTo, page, size, sortBy, sortOrder);
        
        List<ClaimResponse> claimResponses = claimsPage.content().stream()
                .map(claimDtoMapper::toResponse)
                .toList();
        
        return ClaimsPageResponse.builder()
                .data(claimResponses)
                .totalElements(claimsPage.totalElements())
                .totalPages(claimsPage.totalPages())
                .currentPage(claimsPage.currentPage())
                .pageSize(claimsPage.pageSize())
                .build();
    }

    /**
     * Soft duplicate detection: returns active claims for the same customer + vehicle
     * within ±30 days of the requested incident date.
     * Never blocks submission — only informs the UI to show a warning dialog.
     */
    @Transactional(readOnly = true)
    public List<PotentialDuplicateResponse> checkPotentialDuplicates(
            String customerId, String vehicleRegistration, LocalDate incidentDate) {

        LocalDate from = incidentDate.minusDays(30);
        LocalDate to   = incidentDate.plusDays(30);

        return claimRepository.findPotentialDuplicates(customerId, vehicleRegistration, from, to)
                .stream()
                .map(c -> PotentialDuplicateResponse.builder()
                        .claimId(c.getId().getValue())
                        .policyNumber(c.getPolicyNumber())
                        .vehicleRegistration(c.getVehicleRegistration())
                        .claimType(c.getClaimType())
                        .status(c.getStatus())
                        .incidentDate(c.getAccidentDetails().incidentDate())
                        .incidentLocation(c.getAccidentDetails().incidentLocation())
                        .createdAt(c.getCreatedAt())
                        .build())
                .toList();
    }

    /**
     * Edits incident description/location while claim is still SUBMITTED.
     * Once the claim has been assigned to a surveyor the customer must use endorsements.
     */
    @Transactional
    public ClaimResponse updateIncidentDetails(UUID claimId, String incidentLocation,
                                               String description, String requestingUserId) {
        Claim claim = claimRepository.findById(ClaimId.of(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId.toString()));

        claim.updateIncidentDetails(incidentLocation, description);
        Claim saved = claimRepository.save(claim);

        log.info("Claim {} incident details updated by {}", claimId, requestingUserId);
        return claimDtoMapper.toResponse(saved);
    }

    /**
     * Adds a customer or system note to a claim.
     * Used when the claim is beyond SUBMITTED and fields can no longer be edited directly.
     */
    @Transactional
    public ClaimEndorsementResponse addEndorsement(UUID claimId, String note,
                                                    String addedBy, String endorsementType) {
        if (!claimRepository.findById(ClaimId.of(claimId)).isPresent()) {
            throw new ClaimNotFoundException(claimId.toString());
        }
        ClaimEndorsementEntity entity = ClaimEndorsementEntity.create(claimId, note, addedBy, endorsementType);
        ClaimEndorsementEntity saved = endorsementRepository.save(entity);
        log.info("Endorsement {} added to claim {} by {}", saved.getId(), claimId, addedBy);
        return toEndorsementResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ClaimEndorsementResponse> getEndorsements(UUID claimId) {
        return endorsementRepository.findByClaimIdOrderByCreatedAtAsc(claimId)
                .stream().map(this::toEndorsementResponse).toList();
    }

    private ClaimEndorsementResponse toEndorsementResponse(ClaimEndorsementEntity e) {
        return ClaimEndorsementResponse.builder()
                .endorsementId(e.getId())
                .claimId(e.getClaimId())
                .note(e.getNote())
                .addedBy(e.getAddedBy())
                .endorsementType(e.getEndorsementType())
                .createdAt(e.getCreatedAt())
                .build();
    }

    @Transactional
    public ClaimResponse updateClaimStatus(UpdateClaimStatusCommand cmd) {
        Claim claim = claimRepository.findById(ClaimId.of(cmd.claimId()))
                .orElseThrow(() -> new ClaimNotFoundException(cmd.claimId().toString()));

        ClaimStatus previous = claim.getStatus();

        switch (cmd.targetStatus()) {
            case ASSIGNED -> claim.assignSurveyor(cmd.performedByUserId(), cmd.correlationId());
            case UNDER_SURVEY -> claim.beginSurvey();
            case SURVEYED -> claim.completeSurvey(cmd.amount());
            case UNDER_ADJUDICATION -> claim.beginAdjudication();
            case APPROVED -> claim.approve(cmd.amount(), cmd.workshopId(), cmd.correlationId());
            case REJECTED -> claim.reject(cmd.reason(), cmd.correlationId());
            case WITHDRAWN -> claim.withdraw(cmd.correlationId());
            default -> throw new IllegalArgumentException("Status transition not supported: " + cmd.targetStatus());
        }

        Claim saved = claimRepository.save(claim);

        if (cmd.targetStatus() == ClaimStatus.SURVEYED
                && cmd.reason() != null && !cmd.reason().isBlank()) {
            addEndorsement(cmd.claimId(), cmd.reason(), cmd.performedByUserId(), "SURVEYOR_NOTE");
        }
        if ((cmd.targetStatus() == ClaimStatus.APPROVED || cmd.targetStatus() == ClaimStatus.REJECTED)
                && cmd.reason() != null && !cmd.reason().isBlank()) {
            addEndorsement(cmd.claimId(), cmd.reason(), cmd.performedByUserId(), "ADJUDICATOR_NOTE");
        }

        publishStatusChangedEvent(saved, previous, cmd);

        return claimDtoMapper.toResponse(saved);
    }

    private void publishClaimCreatedEvent(Claim claim, String correlationId) {
        var payload = new ClaimCreatedPayload(
                claim.getId().getValue(),
                claim.getPolicyNumber(),
                claim.getCustomerId(),
                claim.getCustomerEmail(),
                claim.getVehicleRegistration(),
                claim.getAccidentDetails().incidentDate(),
                claim.getClaimType().name(),
                claim.getStatus().name()
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(),
                "claim.created",
                correlationId,
                null,
                claim.getId().toString(),
                "Claim",
                "v1",
                Instant.now(),
                payload
        );
        eventPublisher.publish("claim-events", event);
    }

    private void publishStatusChangedEvent(Claim claim, ClaimStatus previous, UpdateClaimStatusCommand cmd) {
        var payload = new ClaimStatusChangedPayload(
                claim.getId().getValue(),
                claim.getPolicyNumber(),
                claim.getCustomerId(),
                claim.getCustomerEmail(),
                previous.name(),
                claim.getStatus().name(),
                cmd.performedByUserId(),
                cmd.reason()
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(),
                "claim.status.changed",
                cmd.correlationId(),
                null,
                claim.getId().toString(),
                "Claim",
                "v1",
                Instant.now(),
                payload
        );
        eventPublisher.publish("claim-events", event);

        if (claim.getStatus() == ClaimStatus.SURVEYED && previous != ClaimStatus.SURVEYED) {
            publishAssessmentSubmittedEvent(claim, cmd);
        }
        if ((claim.getStatus() == ClaimStatus.APPROVED || claim.getStatus() == ClaimStatus.REJECTED) 
                && (previous != ClaimStatus.APPROVED && previous != ClaimStatus.REJECTED)) {
            publishClaimAdjudicatedEvent(claim, cmd);
        }
    }

    private void publishAssessmentSubmittedEvent(Claim claim, UpdateClaimStatusCommand cmd) {
        var payload = new com.yclaims.contracts.events.v1.AssessmentSubmittedPayload(
                claim.getId().getValue(),
                UUID.fromString(claim.getAssignedSurveyorId()),
                claim.getAssessedAmount(),
                cmd.reason(),  // damage notes
                java.util.Collections.emptyList(),  // documentIds - frontend uploads separately
                Instant.now()
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(),
                "assessment.submitted",
                cmd.correlationId(),
                null,
                claim.getId().toString(),
                "Claim",
                "v1",
                Instant.now(),
                payload
        );
        eventPublisher.publish("claim-events", event);
        log.info("[{}] Assessment submitted | claim={} surveyor={} amount={}",
                cmd.correlationId(), claim.getId().getValue(), claim.getAssignedSurveyorId(), claim.getAssessedAmount());
    }

    private void publishClaimAdjudicatedEvent(Claim claim, UpdateClaimStatusCommand cmd) {
        UUID workshopId = claim.getWorkshopId() != null ? UUID.fromString(claim.getWorkshopId()) : null;
        String workshopEmail = workshopId != null
                ? workshopEmailPort.findEmailByWorkshopId(workshopId).orElse(null)
                : null;
        var payload = new com.yclaims.contracts.events.v1.ClaimAdjudicatedPayload(
                claim.getId().getValue(),
                UUID.fromString(claim.getAssignedAdjustorId()),
                claim.getStatus().name(),  // APPROVED or REJECTED
                claim.getApprovedAmount(),
                claim.getRejectionReason(),
                UUID.fromString(claim.getCustomerId()),
                claim.getCustomerEmail(),
                workshopId,
                null,  // workshopName — not needed for notification
                workshopEmail,
                Instant.now()
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(),
                "claim.adjudicated",
                cmd.correlationId(),
                null,
                claim.getId().toString(),
                "Claim",
                "v1",
                Instant.now(),
                payload
        );
        eventPublisher.publish("claim-events", event);
        log.info("[{}] Claim adjudicated | claim={} decision={} adjustor={}",
                cmd.correlationId(), claim.getId().getValue(), claim.getStatus(), claim.getAssignedAdjustorId());
    }

    @Transactional
    public ClaimResponse reassignSurveyor(UUID claimId, String newSurveyorId, String reason,
                                          String reassignedBy, String correlationId) {
        Claim claim = claimRepository.findById(ClaimId.of(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId.toString()));

        String previousSurveyorId = claim.getAssignedSurveyorId();
        claim.reassignSurveyor(newSurveyorId, reassignedBy, reason);
        Claim saved = claimRepository.save(claim);

        String endorsementNote = String.format("Surveyor reassigned from %s to %s. Reason: %s",
                previousSurveyorId != null ? previousSurveyorId : "unassigned",
                newSurveyorId, reason);
        addEndorsement(claimId, endorsementNote, reassignedBy, "REASSIGNMENT");

        log.info("[{}] Claim {} surveyor reassigned from {} to {} by {}",
                correlationId, claimId, previousSurveyorId, newSurveyorId, reassignedBy);

        return claimDtoMapper.toResponse(saved);
    }

    @Transactional
    public ClaimResponse reassignAdjustor(UUID claimId, String newAdjustorId, String reason,
                                          String reassignedBy, String correlationId) {
        Claim claim = claimRepository.findById(ClaimId.of(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId.toString()));

        String previousAdjustorId = claim.getAssignedAdjustorId();
        claim.reassignAdjustor(newAdjustorId, reassignedBy, reason);
        Claim saved = claimRepository.save(claim);

        String endorsementNote = String.format("Adjustor reassigned from %s to %s. Reason: %s",
                previousAdjustorId != null ? previousAdjustorId : "unassigned",
                newAdjustorId, reason);
        addEndorsement(claimId, endorsementNote, reassignedBy, "REASSIGNMENT");

        log.info("[{}] Claim {} adjustor reassigned from {} to {} by {}",
                correlationId, claimId, previousAdjustorId, newAdjustorId, reassignedBy);

        return claimDtoMapper.toResponse(saved);
    }

    /**
     * Called by the workflow module (via ClaimWorkflowEventConsumer) when AutoAssignmentService
     * picks an adjustor after survey completion. Sets the adjustorId without changing status —
     * the adjustor begins adjudication explicitly through the UI.
     */
    @Transactional
    public void assignAdjudicator(UUID claimId, String adjustorId, String correlationId) {
        Claim claim = claimRepository.findById(ClaimId.of(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId.toString()));
        claim.assignAdjudicator(adjustorId);
        claimRepository.save(claim);
        log.info("[{}] Adjustor {} assigned to claim {}", correlationId, adjustorId, claimId);
    }

    @Transactional
    public ClaimResponse overrideDecision(UUID claimId, BigDecimal newAmount, String reason,
                                          String overrideBy, String correlationId) {
        Claim claim = claimRepository.findById(ClaimId.of(claimId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId.toString()));

        BigDecimal previousAmount = claim.getApprovedAmount();
        claim.markOverridden(overrideBy, reason, newAmount);
        Claim saved = claimRepository.save(claim);

        String endorsementNote = String.format("Decision overridden by case manager. Previous amount: %s, New amount: %s. Reason: %s",
                previousAmount != null ? previousAmount.toString() : "none",
                newAmount.toString(), reason);
        addEndorsement(claimId, endorsementNote, overrideBy, "OVERRIDE");

        auditPublisher.publish(new AuditEvent(
                UUID.randomUUID().toString(), correlationId,
                overrideBy, "ROLE_CASE_MANAGER",
                "CLAIM_OVERRIDDEN", "Claim", claimId.toString(),
                previousAmount != null ? previousAmount.toString() : null,
                newAmount.toString(),
                null, reason, null, Instant.now()
        ));

        log.warn("[{}] Claim {} decision overridden by {} - Previous: {}, New: {}, Reason: {}",
                correlationId, claimId, overrideBy, previousAmount, newAmount, reason);

        return claimDtoMapper.toResponse(saved);
    }

}
