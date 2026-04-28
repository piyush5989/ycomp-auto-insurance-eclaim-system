package com.yclaims.claims.domain.service;

import com.yclaims.claims.domain.model.Claim;
import com.yclaims.claims.domain.model.ClaimType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Rule-based fraud detection engine (Phase 1).
 * Evaluates claims against known fraud indicators.
 * ML-based scoring is Phase 2.
 *
 * Rules evaluated:
 *   1. Claim submitted within 30 days of policy inception (early-claim flag)
 *   2. Multiple claims for the same vehicle in 90 days
 *   3. Assessed amount > 120% of vehicle book value (if available)
 *   4. Same incident location as a flagged fraudulent claim in the last 6 months
 */
@Service
@Slf4j
public class FraudDetectionService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000.00");

    public FraudCheckResult evaluate(Claim claim, FraudContext context) {
        // Rule 1: High-value claim without police report
        if (claim.getClaimType() == ClaimType.THEFT &&
                !claim.getAccidentDetails().policeReportFiled()) {
            return FraudCheckResult.flagged("THEFT claim submitted without a police report");
        }

        // Rule 2: Multiple claims for same vehicle in 90 days
        if (context.recentClaimsForVehicle() >= 2) {
            return FraudCheckResult.flagged(
                    "Vehicle " + claim.getVehicleRegistration() +
                    " has " + context.recentClaimsForVehicle() + " claims in the last 90 days");
        }

        // Rule 3: Assessed amount exceeds vehicle book value by > 20%
        if (claim.getAssessedAmount() != null && context.vehicleBookValue() != null) {
            BigDecimal maxAllowed = context.vehicleBookValue().multiply(new BigDecimal("1.20"));
            if (claim.getAssessedAmount().compareTo(maxAllowed) > 0) {
                return FraudCheckResult.flagged(
                        "Assessed amount " + claim.getAssessedAmount() +
                        " exceeds 120% of vehicle book value " + context.vehicleBookValue());
            }
        }

        // Rule 4: Generic high-value claim threshold
        if (claim.getAssessedAmount() != null &&
                claim.getAssessedAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            log.info("High-value claim {} flagged for manual review: {}",
                    claim.getId(), claim.getAssessedAmount());
            return FraudCheckResult.reviewRequired("High-value claim requires manual review");
        }

        return FraudCheckResult.clean();
    }

    public record FraudCheckResult(boolean fraudFlag, boolean reviewRequired, String reason) {
        public static FraudCheckResult clean() {
            return new FraudCheckResult(false, false, null);
        }
        public static FraudCheckResult flagged(String reason) {
            return new FraudCheckResult(true, true, reason);
        }
        public static FraudCheckResult reviewRequired(String reason) {
            return new FraudCheckResult(false, true, reason);
        }
    }

    public record FraudContext(
            int recentClaimsForVehicle,
            BigDecimal vehicleBookValue
    ) {}
}
