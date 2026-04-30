package com.yclaims.claims.presentation.mapper;

import com.yclaims.claims.domain.model.AccidentDetails;
import com.yclaims.claims.domain.model.Claim;
import com.yclaims.claims.domain.model.ClaimId;
import com.yclaims.claims.presentation.dto.ClaimResponse;
import java.time.LocalDate;
import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-01T01:42:02+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.6 (OpenLogic)"
)
@Component
public class ClaimDtoMapperImpl implements ClaimDtoMapper {

    @Override
    public ClaimResponse toResponse(Claim claim) {
        if ( claim == null ) {
            return null;
        }

        ClaimResponse.ClaimResponseBuilder claimResponse = ClaimResponse.builder();

        claimResponse.claimId( claimIdValue( claim ) );
        claimResponse.incidentDate( claimAccidentDetailsIncidentDate( claim ) );
        claimResponse.incidentLocation( claimAccidentDetailsIncidentLocation( claim ) );
        claimResponse.description( claimAccidentDetailsDescription( claim ) );
        claimResponse.policeReportFiled( claimAccidentDetailsPoliceReportFiled( claim ) );
        claimResponse.policyNumber( claim.getPolicyNumber() );
        claimResponse.customerId( claim.getCustomerId() );
        claimResponse.vehicleRegistration( claim.getVehicleRegistration() );
        claimResponse.claimType( claim.getClaimType() );
        claimResponse.status( claim.getStatus() );
        claimResponse.assignedSurveyorId( claim.getAssignedSurveyorId() );
        claimResponse.assignedAdjustorId( claim.getAssignedAdjustorId() );
        claimResponse.assessedAmount( claim.getAssessedAmount() );
        claimResponse.approvedAmount( claim.getApprovedAmount() );
        claimResponse.workshopId( claim.getWorkshopId() );
        claimResponse.rejectionReason( claim.getRejectionReason() );
        claimResponse.fraudFlag( claim.isFraudFlag() );
        claimResponse.createdAt( claim.getCreatedAt() );
        claimResponse.updatedAt( claim.getUpdatedAt() );

        return claimResponse.build();
    }

    private UUID claimIdValue(Claim claim) {
        if ( claim == null ) {
            return null;
        }
        ClaimId id = claim.getId();
        if ( id == null ) {
            return null;
        }
        UUID value = id.getValue();
        if ( value == null ) {
            return null;
        }
        return value;
    }

    private LocalDate claimAccidentDetailsIncidentDate(Claim claim) {
        if ( claim == null ) {
            return null;
        }
        AccidentDetails accidentDetails = claim.getAccidentDetails();
        if ( accidentDetails == null ) {
            return null;
        }
        LocalDate incidentDate = accidentDetails.incidentDate();
        if ( incidentDate == null ) {
            return null;
        }
        return incidentDate;
    }

    private String claimAccidentDetailsIncidentLocation(Claim claim) {
        if ( claim == null ) {
            return null;
        }
        AccidentDetails accidentDetails = claim.getAccidentDetails();
        if ( accidentDetails == null ) {
            return null;
        }
        String incidentLocation = accidentDetails.incidentLocation();
        if ( incidentLocation == null ) {
            return null;
        }
        return incidentLocation;
    }

    private String claimAccidentDetailsDescription(Claim claim) {
        if ( claim == null ) {
            return null;
        }
        AccidentDetails accidentDetails = claim.getAccidentDetails();
        if ( accidentDetails == null ) {
            return null;
        }
        String description = accidentDetails.description();
        if ( description == null ) {
            return null;
        }
        return description;
    }

    private boolean claimAccidentDetailsPoliceReportFiled(Claim claim) {
        if ( claim == null ) {
            return false;
        }
        AccidentDetails accidentDetails = claim.getAccidentDetails();
        if ( accidentDetails == null ) {
            return false;
        }
        boolean policeReportFiled = accidentDetails.policeReportFiled();
        return policeReportFiled;
    }
}
