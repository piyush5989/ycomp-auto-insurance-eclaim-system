package com.yclaims.claims.application;

import com.yclaims.claims.application.command.SubmitClaimCommand;
import com.yclaims.claims.application.command.UpdateClaimStatusCommand;
import com.yclaims.claims.domain.exception.ClaimNotFoundException;
import com.yclaims.claims.domain.exception.PolicyValidationException;
import com.yclaims.claims.domain.model.*;
import com.yclaims.claims.domain.port.out.ClaimRepository;
import com.yclaims.claims.domain.port.out.DomainEventPublisher;
import com.yclaims.claims.domain.port.out.PolicyServicePort;
import com.yclaims.claims.domain.service.FraudDetectionService;
import com.yclaims.claims.presentation.dto.ClaimResponse;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Claim Application Service — owns all use case orchestration for the claims module.
 *
 * Responsibilities:
 *   - Validates inbound commands
 *   - Coordinates domain model, repositories, and ports
 *   - Owns transaction boundaries (@Transactional)
 *   - Publishes domain events AFTER successful persistence
 *   - Returns DTOs (never domain objects) to the presentation layer
 */
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

    /**
     * Submit a new claim — idempotent via natural key deduplication.
     * Sync: validates policy, persists claim, returns claim ID.
     * Async: publishes claim.created event for notifications + reporting.
     */
    @Transactional
    public ClaimResponse submitClaim(SubmitClaimCommand cmd) {
        log.info("[{}] Submitting claim for policy {} vehicle {}",
                cmd.correlationId(), cmd.policyNumber(), cmd.vehicleRegistration());

        // Idempotency check — return existing if already submitted
        var existing = claimRepository.findByNaturalKey(
                cmd.policyNumber(), cmd.incidentDate(), cmd.vehicleRegistration());
        if (existing.isPresent()) {
            log.info("[{}] Duplicate claim detected — returning existing {}", cmd.correlationId(), existing.get().getId());
            return claimDtoMapper.toResponse(existing.get());
        }

        // Sync: validate policy (must succeed before claim is created)
        PolicyServicePort.PolicyValidationResult policy =
                policyServicePort.validate(cmd.policyNumber(), cmd.vehicleRegistration());
        if (!policy.valid()) {
            throw new PolicyValidationException(cmd.policyNumber(), policy.failureReason());
        }

        // Create domain aggregate
        AccidentDetails accidentDetails = new AccidentDetails(
                cmd.incidentDate(),
                cmd.incidentLocation(),
                cmd.description(),
                cmd.policeReportFiled(),
                cmd.policeReportNumber()
        );

        Claim claim = Claim.submit(
                cmd.policyNumber(),
                policy.customerId(),
                policy.customerEmail(),
                cmd.vehicleRegistration(),
                cmd.claimType(),
                accidentDetails
        );

        // Fraud check
        int recentClaims = claimRepository.countRecentClaimsForVehicle(cmd.vehicleRegistration(), 90);
        var fraudCtx = new FraudDetectionService.FraudContext(recentClaims, null);
        var fraudResult = fraudDetectionService.evaluate(claim, fraudCtx);
        if (fraudResult.fraudFlag()) {
            claim.flagFraud(fraudResult.reason());
            log.warn("[{}] Fraud flag raised for claim {}: {}", cmd.correlationId(), claim.getId(), fraudResult.reason());
        }

        // Persist
        Claim saved = claimRepository.save(claim);

        // Async: publish domain events after successful save
        publishClaimCreatedEvent(saved, cmd.correlationId());

        // Audit
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

    @Transactional
    public ClaimResponse updateClaimStatus(UpdateClaimStatusCommand cmd) {
        Claim claim = claimRepository.findById(ClaimId.of(cmd.claimId()))
                .orElseThrow(() -> new ClaimNotFoundException(cmd.claimId().toString()));

        ClaimStatus previous = claim.getStatus();

        switch (cmd.targetStatus()) {
            case ASSIGNED -> claim.assignSurveyor(cmd.performedByUserId(), cmd.correlationId());
            case UNDER_SURVEY -> claim.beginSurvey();
            case SURVEYED -> claim.completeSurvey(cmd.amount(), cmd.performedByUserId());
            case UNDER_ADJUDICATION -> claim.beginAdjudication();
            case APPROVED -> claim.approve(cmd.amount(), cmd.workshopId(), cmd.correlationId());
            case REJECTED -> claim.reject(cmd.reason(), cmd.correlationId());
            case WITHDRAWN -> claim.withdraw(cmd.correlationId());
            default -> throw new IllegalArgumentException("Status transition not supported: " + cmd.targetStatus());
        }

        Claim saved = claimRepository.save(claim);

        // Publish status change event
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
    }
}
