package com.yclaims.workshops.presentation.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record WorkshopListResponse(
        UUID workshopId,
        String name,
        String address,
        String city,
        String state,
        String zipCode,
        String phone,
        String email,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isPartner,
        boolean active,
        Double distanceKm  // Distance from customer location (if provided)
) {}
