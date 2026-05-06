package com.yclaims.claims.domain.model;

import com.yclaims.kernel.domain.AggregateRoot;
import com.yclaims.claims.domain.event.ClaimSubmittedEvent;
import com.yclaims.claims.domain.event.ClaimStatusChangedEvent;
import com.yclaims.claims.domain.event.ClaimAssignedEvent;
import com.yclaims.claims.domain.exception.InvalidClaimStateException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Claim aggregate root.
 * All business rules and state transitions are enforced here.
 * Never expose this directly via API — use ClaimResponse DTO.
 */
public class Claim extends AggregateRoot {

    private final ClaimId id;
    private final String policyNumber;
    private final String customerId;
    private final String customerEmail;
    private final String customerPhone;   // E.164 — sourced from PolicyServicePort
    private final String vehicleRegistration;
    private final ClaimType claimType;
    private final AccidentDetails accidentDetails;

    private ClaimStatus status;
    private String assignedSurveyorId;
    private String assignedAdjustorId;
    private BigDecimal assessedAmount;
    private BigDecimal approvedAmount;
    private String workshopId;
    private String rejectionReason;
    private boolean fraudFlag;
    private String fraudReason;

    private final Instant createdAt;
    private Instant updatedAt;

    private Claim(ClaimId id,
                  String policyNumber,
                  String customerId,
                  String customerEmail,
                  String customerPhone,
                  String vehicleRegistration,
                  ClaimType claimType,
                  AccidentDetails accidentDetails) {
        this.id = id;
        this.policyNumber = policyNumber;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
        this.vehicleRegistration = vehicleRegistration;
        this.claimType = claimType;
        this.accidentDetails = accidentDetails;
        this.status = ClaimStatus.SUBMITTED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * Factory method — the only way to create a new Claim.
     * Registers ClaimSubmittedEvent so the application layer can publish it.
     */
    public static Claim submit(String policyNumber,
                               String customerId,
                               String customerEmail,
                               String customerPhone,
                               String vehicleRegistration,
                               ClaimType claimType,
                               AccidentDetails accidentDetails) {
        Claim claim = new Claim(
                ClaimId.generate(),
                policyNumber,
                customerId,
                customerEmail,
                customerPhone,
                vehicleRegistration,
                claimType,
                accidentDetails
        );
        claim.registerEvent(new ClaimSubmittedEvent(claim.id, policyNumber, customerId, customerEmail));
        return claim;
    }

    /**
     * Reconstitute from persistence — no domain events registered.
     */
    public static Claim reconstitute(ClaimId id,
                                     String policyNumber,
                                     String customerId,
                                     String customerEmail,
                                     String customerPhone,
                                     String vehicleRegistration,
                                     ClaimType claimType,
                                     AccidentDetails accidentDetails,
                                     ClaimStatus status,
                                     String assignedSurveyorId,
                                     String assignedAdjustorId,
                                     BigDecimal assessedAmount,
                                     BigDecimal approvedAmount,
                                     String workshopId,
                                     String rejectionReason,
                                     boolean fraudFlag,
                                     String fraudReason,
                                     Instant createdAt,
                                     Instant updatedAt) {
        Claim claim = new Claim(id, policyNumber, customerId, customerEmail,
                customerPhone, vehicleRegistration, claimType, accidentDetails);
        claim.status = status;
        claim.assignedSurveyorId = assignedSurveyorId;
        claim.assignedAdjustorId = assignedAdjustorId;
        claim.assessedAmount = assessedAmount;
        claim.approvedAmount = approvedAmount;
        claim.workshopId = workshopId;
        claim.rejectionReason = rejectionReason;
        claim.fraudFlag = fraudFlag;
        claim.fraudReason = fraudReason;
        // Override timestamps from DB
        return claim;
    }

    public void assignSurveyor(String surveyorId, String correlationId) {
        requireStatus(ClaimStatus.SUBMITTED, "assign surveyor");
        this.assignedSurveyorId = surveyorId;
        this.status = ClaimStatus.ASSIGNED;
        this.updatedAt = Instant.now();
        registerEvent(new ClaimAssignedEvent(id, surveyorId, correlationId));
        registerEvent(new ClaimStatusChangedEvent(id, ClaimStatus.SUBMITTED, ClaimStatus.ASSIGNED, surveyorId, correlationId));
    }

    public void beginSurvey() {
        requireStatus(ClaimStatus.ASSIGNED, "begin survey");
        this.status = ClaimStatus.UNDER_SURVEY;
        this.updatedAt = Instant.now();
    }

    public void completeSurvey(BigDecimal assessedAmount, String adjustorId) {
        requireStatus(ClaimStatus.UNDER_SURVEY, "complete survey");
        this.assessedAmount = assessedAmount;
        this.assignedAdjustorId = adjustorId;
        this.status = ClaimStatus.SURVEYED;
        this.updatedAt = Instant.now();
    }

    public void beginAdjudication() {
        requireStatus(ClaimStatus.SURVEYED, "begin adjudication");
        this.status = ClaimStatus.UNDER_ADJUDICATION;
        this.updatedAt = Instant.now();
    }

    public void approve(BigDecimal approvedAmount, String workshopId, String correlationId) {
        requireStatus(ClaimStatus.UNDER_ADJUDICATION, "approve");
        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidClaimStateException(id, "Approved amount must be positive");
        }
        this.approvedAmount = approvedAmount;
        this.workshopId = workshopId;
        this.status = ClaimStatus.APPROVED;
        this.updatedAt = Instant.now();
        registerEvent(new ClaimStatusChangedEvent(id, ClaimStatus.UNDER_ADJUDICATION, ClaimStatus.APPROVED, "adjustor", correlationId));
    }

    public void reject(String reason, String correlationId) {
        requireStatus(ClaimStatus.UNDER_ADJUDICATION, "reject");
        this.rejectionReason = reason;
        this.status = ClaimStatus.REJECTED;
        this.updatedAt = Instant.now();
        registerEvent(new ClaimStatusChangedEvent(id, ClaimStatus.UNDER_ADJUDICATION, ClaimStatus.REJECTED, "adjustor", correlationId));
    }

    public void markPaymentInitiated() {
        requireStatus(ClaimStatus.APPROVED, "initiate payment");
        this.status = ClaimStatus.PAYMENT_INITIATED;
        this.updatedAt = Instant.now();
    }

    public void markPaymentProcessed() {
        requireStatus(ClaimStatus.PAYMENT_INITIATED, "mark payment processed");
        this.status = ClaimStatus.PAYMENT_PROCESSED;
        this.updatedAt = Instant.now();
    }

    public void settle() {
        if (status != ClaimStatus.PAYMENT_INITIATED && status != ClaimStatus.PAYMENT_PROCESSED) {
            throw new InvalidClaimStateException(id,
                    "Cannot settle when claim is in status " + status
                            + ". Expected: PAYMENT_INITIATED or PAYMENT_PROCESSED");
        }
        this.status = ClaimStatus.SETTLED;
        this.updatedAt = Instant.now();
    }

    public void withdraw(String correlationId) {
        if (status.isTerminal()) {
            throw new InvalidClaimStateException(id, "Cannot withdraw a terminal claim in status: " + status);
        }
        ClaimStatus previous = this.status;
        this.status = ClaimStatus.WITHDRAWN;
        this.updatedAt = Instant.now();
        registerEvent(new ClaimStatusChangedEvent(id, previous, ClaimStatus.WITHDRAWN, customerId, correlationId));
    }

    public void flagFraud(String reason) {
        this.fraudFlag = true;
        this.fraudReason = reason;
        this.updatedAt = Instant.now();
    }

    private void requireStatus(ClaimStatus expected, String operation) {
        if (this.status != expected) {
            throw new InvalidClaimStateException(id,
                    "Cannot " + operation + " when claim is in status " + this.status +
                    ". Expected: " + expected);
        }
    }

    // Getters (Lombok @Getter not used on Aggregate — explicit control)
    public ClaimId getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public String getCustomerId() { return customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public String getVehicleRegistration() { return vehicleRegistration; }
    public ClaimType getClaimType() { return claimType; }
    public AccidentDetails getAccidentDetails() { return accidentDetails; }
    public ClaimStatus getStatus() { return status; }
    public String getAssignedSurveyorId() { return assignedSurveyorId; }
    public String getAssignedAdjustorId() { return assignedAdjustorId; }
    public BigDecimal getAssessedAmount() { return assessedAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public String getWorkshopId() { return workshopId; }
    public String getRejectionReason() { return rejectionReason; }
    public boolean isFraudFlag() { return fraudFlag; }
    public String getFraudReason() { return fraudReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
