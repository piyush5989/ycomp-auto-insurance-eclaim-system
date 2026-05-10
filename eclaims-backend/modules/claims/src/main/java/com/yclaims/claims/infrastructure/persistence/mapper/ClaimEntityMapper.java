package com.yclaims.claims.infrastructure.persistence.mapper;

import com.yclaims.claims.domain.model.*;
import com.yclaims.claims.infrastructure.persistence.ClaimEntity;
import org.springframework.stereotype.Component;

/**
 * Maps between ClaimEntity (JPA) and Claim (domain model).
 * Hand-written (not MapStruct) because the domain model uses a private constructor
 * and factory methods — MapStruct cannot generate these.
 *
 * This is the ONLY place where JPA entities and domain models meet.
 */
@Component
public class ClaimEntityMapper {

    public ClaimEntity toEntity(Claim claim) {
        ClaimEntity entity = ClaimEntity.create(
                claim.getId().getValue(),
                claim.getPolicyNumber(),
                claim.getCustomerId(),
                claim.getCustomerEmail(),
                claim.getVehicleRegistration(),
                null,
                claim.getClaimType(),
                claim.getStatus(),
                claim.getAccidentDetails().incidentDate(),
                claim.getAccidentDetails().incidentLocation(),
                claim.getAccidentDetails().description(),
                claim.getAccidentDetails().policeReportFiled(),
                claim.getAccidentDetails().policeReportNumber()
        );
        entity.updateFromDomain(
                claim.getStatus(),
                claim.getAssignedSurveyorId(),
                claim.getAssignedAdjustorId(),
                claim.getAssessedAmount(),
                claim.getApprovedAmount(),
                claim.getWorkshopId(),
                claim.getRejectionReason(),
                claim.isFraudFlag(),
                claim.getFraudReason(),
                claim.getRegion(),
                claim.getOverrideByUserId(),
                claim.getOverrideReason(),
                claim.getOverrideAt(),
                claim.getRentalReservationId(),
                claim.getRentalStatus()
        );
        return entity;
    }

    public Claim toDomain(ClaimEntity entity) {
        AccidentDetails accidentDetails = new AccidentDetails(
                entity.getIncidentDate(),
                entity.getIncidentLocation(),
                entity.getDescription(),
                entity.isPoliceReportFiled(),
                entity.getPoliceReportNumber()
        );

        return Claim.reconstitute(
                ClaimId.of(entity.getId()),
                entity.getPolicyNumber(),
                entity.getCustomerId(),
                entity.getCustomerEmail(),
                entity.getVehicleRegistration(),
                entity.getClaimType(),
                accidentDetails,
                entity.getStatus(),
                entity.getAssignedSurveyorId(),
                entity.getAssignedAdjustorId(),
                entity.getAssessedAmount(),
                entity.getApprovedAmount(),
                entity.getWorkshopId(),
                entity.getRejectionReason(),
                entity.isFraudFlag(),
                entity.getFraudReason(),
                entity.getRegion(),
                entity.getOverrideByUserId(),
                entity.getOverrideReason(),
                entity.getOverrideAt(),
                entity.getRentalReservationId(),
                entity.getRentalStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
