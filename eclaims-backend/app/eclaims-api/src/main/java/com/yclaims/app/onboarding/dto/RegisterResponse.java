package com.yclaims.app.onboarding.dto;

public record RegisterResponse(
        String message,
        String username,
        String customerId
) {}
