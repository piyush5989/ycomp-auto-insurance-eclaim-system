package com.yclaims.app.onboarding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Customer self-registration request.
 * Policy details are used to verify the caller is an existing policyholder
 * before provisioning their Keycloak identity.
 */
public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^POL-\\d{8}$", message = "policyNumber must be in format POL-XXXXXXXX")
        String policyNumber,

        @NotBlank @Size(max = 20)
        String vehicleRegistration,

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, max = 100,
                message = "password must be at least 8 characters")
        String password
) {}
