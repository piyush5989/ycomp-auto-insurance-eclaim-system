package com.yclaims.claims.infrastructure.integration;

import com.yclaims.claims.domain.port.out.PolicyServicePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * POC stub for the Policy Management System (PMS) integration.
 * Returns seeded test policies for known policy numbers.
 * Production replacement: PolicyServiceRestAdapter — REST call to PMS via the same port.
 *
 * Active on profiles: local, test
 * Production profile: replaces this with PolicyServiceRestAdapter
 */
@Component
@Profile({"local", "test", "default"})
@Slf4j
public class PolicyServiceStubAdapter implements PolicyServicePort {

    /** Seeded test policies matching the Keycloak test users. */
    private static final Map<String, PolicyValidationResult> SEED_POLICIES = Map.of(
            "POL-00000001", PolicyValidationResult.valid(
                    "customer1-uuid", "John Customer", "customer1@eclaims.test",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2026, 12, 31), "COMPREHENSIVE"),
            "POL-00000002", PolicyValidationResult.valid(
                    "customer2-uuid", "Jane Customer", "customer2@eclaims.test",
                    LocalDate.of(2025, 6, 1), LocalDate.of(2026, 5, 31), "COLLISION"),
            "POL-00000003", PolicyValidationResult.valid(
                    UUID.randomUUID().toString(), "Load Test User", "loadtest@eclaims.test",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2027, 12, 31), "COMPREHENSIVE")
    );

    @Override
    public PolicyValidationResult validate(String policyNumber, String vehicleRegistration) {
        log.debug("Policy validation (stub) for policy={} vehicle={}", policyNumber, vehicleRegistration);

        // Allow any policy matching the test pattern for load testing
        if (policyNumber.matches("^POL-\\d{8}$")) {
            if (SEED_POLICIES.containsKey(policyNumber)) {
                return SEED_POLICIES.get(policyNumber);
            }
            // Generic valid response for load test generated policy numbers
            return PolicyValidationResult.valid(
                    UUID.randomUUID().toString(), "Test Customer", "test@eclaims.test",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2027, 12, 31), "COMPREHENSIVE"
            );
        }

        return PolicyValidationResult.invalid("Policy not found: " + policyNumber);
    }
}
