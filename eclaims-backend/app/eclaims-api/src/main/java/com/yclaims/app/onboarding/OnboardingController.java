package com.yclaims.app.onboarding;

import com.yclaims.app.onboarding.dto.RegisterRequest;
import com.yclaims.app.onboarding.dto.RegisterResponse;
import com.yclaims.kernel.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public customer onboarding endpoint.
 * No authentication required — this is the entry point for new customer registration.
 *
 * Security:
 *  - Policy validation acts as identity proof (you must own the policy to register)
 *  - Rate limiting and bot protection should be applied at the API Gateway layer in production
 *  - HTTPS is mandatory in production (enforced at load balancer / ingress)
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Onboarding", description = "Customer self-registration using policy details")
public class OnboardingController {

    private final OnboardingApplicationService onboardingService;

    @PostMapping("/register")
    @Operation(
        summary = "Register a new customer account using policy details",
        description = "Validates policyNumber + vehicleRegistration against the Policy Management System, " +
                      "then provisions a Keycloak identity with the 'customer' role. " +
                      "The customer can sign in immediately after successful registration."
    )
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        String cid = correlationId();
        log.info("[{}] POST /api/v1/onboarding/register  policy={} email={}",
                cid, request.policyNumber(), request.email());
        RegisterResponse response = onboardingService.register(request, cid);
        log.info("[{}] Registration successful: username={}", cid, response.username());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, cid));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
