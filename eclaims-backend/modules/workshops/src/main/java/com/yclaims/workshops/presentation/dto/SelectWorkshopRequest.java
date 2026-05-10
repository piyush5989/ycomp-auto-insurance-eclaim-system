package com.yclaims.workshops.presentation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SelectWorkshopRequest(
        @NotNull(message = "Workshop ID is required")
        UUID workshopId
) {}
