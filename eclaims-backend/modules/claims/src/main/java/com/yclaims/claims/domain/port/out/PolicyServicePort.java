package com.yclaims.claims.domain.port.out;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Port for validating policies against the external Policy Management System (PMS).
 * POC implementation: returns seeded test policies.
 * Production implementation: REST call to PMS via PolicyServiceRestAdapter.
 */
public interface PolicyServicePort {

    /**
     * Validates that the policy exists, is active, and covers the vehicle.
     * Throws PolicyValidationException if validation fails.
     */
    PolicyValidationResult validate(String policyNumber, String vehicleRegistration);

    record PolicyValidationResult(
            boolean valid,
            String customerId,
            String customerName,
            String customerEmail,
            LocalDate policyStartDate,
            LocalDate policyEndDate,
            String coverageType,
            String failureReason
    ) {
        public static PolicyValidationResult valid(String customerId, String customerName,
                                                    String customerEmail, LocalDate start,
                                                    LocalDate end, String coverage) {
            return new PolicyValidationResult(true, customerId, customerName, customerEmail,
                    start, end, coverage, null);
        }

        public static PolicyValidationResult invalid(String reason) {
            return new PolicyValidationResult(false, null, null, null, null, null, null, reason);
        }
    }
}
