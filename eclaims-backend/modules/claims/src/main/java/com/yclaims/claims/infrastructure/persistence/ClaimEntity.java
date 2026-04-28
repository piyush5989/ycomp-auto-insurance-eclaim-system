package com.yclaims.claims.infrastructure.persistence;

import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.domain.model.ClaimType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the claims table — lives ONLY in the infrastructure layer.
 * Never exposed via API. The ClaimEntityMapper converts to/from domain Claim.
 *
 * Lombok rules (per coding standards):
 *   ✅ @Getter    — read access without boilerplate
 *   ✅ @NoArgsConstructor(PROTECTED) — JPA proxy requirement; not public
 *   ❌ No @Data, @AllArgsConstructor, @EqualsAndHashCode — breaks JPA proxies
 */
@Entity
@Table(
    name = "claims",
    schema = "claims",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_claim_natural_key",
            columnNames = {"policy_number", "incident_date", "vehicle_registration"}
        )
    },
    indexes = {
        @Index(name = "idx_claims_customer_id", columnList = "customer_id"),
        @Index(name = "idx_claims_status_date", columnList = "status, created_at"),
        @Index(name = "idx_claims_policy_number", columnList = "policy_number")
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClaimEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "policy_number", nullable = false, length = 20)
    private String policyNumber;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Column(name = "vehicle_registration", nullable = false, length = 20)
    private String vehicleRegistration;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 30)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ClaimStatus status;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location", length = 500)
    private String incidentLocation;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "police_report_filed")
    private boolean policeReportFiled;

    @Column(name = "police_report_number", length = 50)
    private String policeReportNumber;

    @Column(name = "assigned_surveyor_id", length = 100)
    private String assignedSurveyorId;

    @Column(name = "assigned_adjustor_id", length = 100)
    private String assignedAdjustorId;

    @Column(name = "assessed_amount", precision = 12, scale = 2)
    private BigDecimal assessedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "workshop_id", length = 100)
    private String workshopId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "fraud_flag")
    private boolean fraudFlag;

    @Column(name = "fraud_reason", length = 500)
    private String fraudReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Manual equals/hashCode based on id only — never use @EqualsAndHashCode on @Entity
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClaimEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    // Builder-style factory for persistence adapter
    public static ClaimEntity create(UUID id, String policyNumber, String customerId,
                                      String customerEmail, String vehicleRegistration,
                                      ClaimType claimType, ClaimStatus status,
                                      LocalDate incidentDate, String incidentLocation,
                                      String description, boolean policeReportFiled,
                                      String policeReportNumber) {
        ClaimEntity e = new ClaimEntity();
        e.id = id;
        e.policyNumber = policyNumber;
        e.customerId = customerId;
        e.customerEmail = customerEmail;
        e.vehicleRegistration = vehicleRegistration;
        e.claimType = claimType;
        e.status = status;
        e.incidentDate = incidentDate;
        e.incidentLocation = incidentLocation;
        e.description = description;
        e.policeReportFiled = policeReportFiled;
        e.policeReportNumber = policeReportNumber;
        return e;
    }

    public void updateFromDomain(ClaimStatus status, String assignedSurveyorId,
                                  String assignedAdjustorId, BigDecimal assessedAmount,
                                  BigDecimal approvedAmount, String workshopId,
                                  String rejectionReason, boolean fraudFlag, String fraudReason) {
        this.status = status;
        this.assignedSurveyorId = assignedSurveyorId;
        this.assignedAdjustorId = assignedAdjustorId;
        this.assessedAmount = assessedAmount;
        this.approvedAmount = approvedAmount;
        this.workshopId = workshopId;
        this.rejectionReason = rejectionReason;
        this.fraudFlag = fraudFlag;
        this.fraudReason = fraudReason;
    }
}
