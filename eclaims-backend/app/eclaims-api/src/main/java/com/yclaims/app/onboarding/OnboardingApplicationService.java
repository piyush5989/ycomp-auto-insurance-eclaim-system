package com.yclaims.app.onboarding;

import com.yclaims.app.onboarding.dto.RegisterRequest;
import com.yclaims.app.onboarding.dto.RegisterResponse;
import com.yclaims.claims.domain.port.out.PolicyServicePort;
import com.yclaims.claims.domain.port.out.PolicyServicePort.PolicyValidationResult;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates customer self-registration using policy details as identity verification.
 *
 * Flow:
 *  1. Validate policyNumber + vehicleRegistration via PolicyServicePort (same stub/prod adapter as claims)
 *  2. If valid, create a Keycloak user with the 'customer' realm role + customerId attribute
 *  3. Return confirmation — customer can now log in via the normal Keycloak flow
 *
 * Enterprise note: Replace Keycloak Admin calls with CIAM provisioning API (Okta/Auth0/ForgeRock).
 * PolicyServicePort validation remains unchanged regardless of CIAM choice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OnboardingApplicationService {

    private final PolicyServicePort policyServicePort;
    private final Keycloak keycloakAdminClient;
    private final String keycloakTargetRealm;

    public RegisterResponse register(RegisterRequest request, String correlationId) {
        log.info("[{}] Customer registration attempt for policy {}", correlationId, request.policyNumber());

        PolicyValidationResult policy = policyServicePort.validate(
                request.policyNumber(), request.vehicleRegistration());

        if (!policy.valid()) {
            log.warn("[{}] Policy validation failed: {}", correlationId, policy.failureReason());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Policy validation failed: " + policy.failureReason());
        }

        String username = request.email().toLowerCase();

        try {
            UserRepresentation user = buildUserRepresentation(
                    username, request.email(), request.password(),
                    policy.customerName(), policy.customerId());

            Response response = keycloakAdminClient
                    .realm(keycloakTargetRealm)
                    .users()
                    .create(user);

            if (response.getStatus() == 409) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "An account with this email already exists.");
            }
            if (response.getStatus() != 201) {
                log.error("[{}] Keycloak user creation returned HTTP {}", correlationId, response.getStatus());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Account creation failed. Please try again.");
            }

            // Assign customer realm role
            String userId = extractCreatedUserId(response);
            assignCustomerRole(userId);

            log.info("[{}] Customer account created: username={} customerId={}",
                    correlationId, username, policy.customerId());

            return new RegisterResponse(
                    "Account created successfully. You can now sign in.",
                    username,
                    policy.customerId());

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("[{}] Unexpected error during registration", correlationId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Account creation failed. Please contact support.");
        }
    }

    private UserRepresentation buildUserRepresentation(String username, String email,
                                                         String password, String fullName,
                                                         String customerId) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(false);

        String[] nameParts = fullName != null ? fullName.split(" ", 2) : new String[]{"", ""};

        UserRepresentation user = new UserRepresentation();
        user.setUsername(username);
        user.setEmail(email);
        user.setFirstName(nameParts[0]);
        user.setLastName(nameParts.length > 1 ? nameParts[1] : "");
        user.setEnabled(true);
        user.setEmailVerified(true);
        user.setCredentials(List.of(credential));
        user.setAttributes(Map.of("customerId", List.of(customerId)));
        user.setRealmRoles(List.of("customer"));
        return user;
    }

    private void assignCustomerRole(String userId) {
        var customerRole = keycloakAdminClient
                .realm(keycloakTargetRealm)
                .roles()
                .get("customer")
                .toRepresentation();

        keycloakAdminClient
                .realm(keycloakTargetRealm)
                .users()
                .get(userId)
                .roles()
                .realmLevel()
                .add(List.of(customerRole));
    }

    private String extractCreatedUserId(Response response) {
        String location = response.getHeaderString("Location");
        if (location != null && location.contains("/")) {
            return location.substring(location.lastIndexOf('/') + 1);
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Could not determine created user ID from Keycloak response.");
    }
}
