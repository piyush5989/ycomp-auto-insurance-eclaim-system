package com.yclaims.claims.presentation.dto;

import jakarta.validation.constraints.Size;

public record UpdateIncidentDetailsRequest(
        @Size(max = 500) String incidentLocation,
        @Size(max = 2000) String description
) {}
