package com.yclaims.claims.presentation.mapper;

import com.yclaims.claims.domain.model.Claim;
import com.yclaims.claims.presentation.dto.ClaimResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper: Claim domain model ↔ ClaimResponse DTO.
 * This is the ONLY permitted crossing point between domain and API layers.
 */
@Mapper(componentModel = "spring")
public interface ClaimDtoMapper {

    @Mapping(source = "id.value", target = "claimId")
    @Mapping(source = "accidentDetails.incidentDate", target = "incidentDate")
    @Mapping(source = "accidentDetails.incidentLocation", target = "incidentLocation")
    @Mapping(source = "accidentDetails.description", target = "description")
    @Mapping(source = "accidentDetails.policeReportFiled", target = "policeReportFiled")
    ClaimResponse toResponse(Claim claim);
}
