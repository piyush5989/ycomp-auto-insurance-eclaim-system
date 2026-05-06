package com.yclaims.claims.infrastructure.retention;

import com.yclaims.claims.domain.model.ClaimStatus;
import com.yclaims.claims.infrastructure.persistence.ClaimEntity;
import com.yclaims.claims.infrastructure.persistence.ClaimJpaRepository;
import com.yclaims.kernel.audit.AuditEvent;
import com.yclaims.kernel.audit.AuditPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CCPA/GDPR right-to-erasure handler.
 *
 * Rule: Anonymises PII on SETTLED or ARCHIVED claims for a given customer.
 * Rule: Active claims (not yet settled) CANNOT be anonymised — regulatory hold.
 *       This is documented here explicitly per GDPR compliance requirement.
 *
 * The claim record is retained (7-year regulatory requirement),
 * but the person behind it is anonymised by replacing PII with ANONYMISED_{uuid}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimDataRetentionService {

    private final ClaimJpaRepository claimJpaRepository;
    private final AuditPublisher auditPublisher;

    @Transactional
    public AnonymisationResult anonymiseCustomerData(String customerId, String requestedByUserId) {
        log.info("GDPR anonymisation request for customerId={} by={}", customerId, requestedByUserId);

        List<ClaimEntity> activeClaims = claimJpaRepository
                .findByCustomerIdAndStatusIn(customerId,
                        List.of(ClaimStatus.DRAFT, ClaimStatus.SUBMITTED, ClaimStatus.ASSIGNED,
                                ClaimStatus.UNDER_SURVEY, ClaimStatus.SURVEYED,
                                ClaimStatus.UNDER_ADJUDICATION, ClaimStatus.APPROVED,
                                ClaimStatus.PAYMENT_INITIATED, ClaimStatus.PAYMENT_PROCESSED));

        if (!activeClaims.isEmpty()) {
            log.warn("Cannot anonymise customer {} — {} active claim(s) in progress", customerId, activeClaims.size());
            return AnonymisationResult.blocked(
                    "Customer has " + activeClaims.size() + " active claim(s). " +
                    "Anonymisation is only permitted after all claims are settled or archived.");
        }

        List<ClaimEntity> settledClaims = claimJpaRepository
                .findByCustomerIdAndStatusIn(customerId,
                        List.of(ClaimStatus.SETTLED, ClaimStatus.ARCHIVED,
                                ClaimStatus.REJECTED, ClaimStatus.WITHDRAWN));

        if (settledClaims.isEmpty()) {
            return AnonymisationResult.noDataFound();
        }

        String anonymisedToken = "ANONYMISED_" + UUID.randomUUID().toString().substring(0, 8);

        settledClaims.forEach(claim -> {
            anonymiseClaim(claim, anonymisedToken);
            auditPublisher.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    "gdpr-erasure",
                    requestedByUserId,
                    "SYSTEM",
                    "PII_ANONYMISED",
                    "Claim",
                    claim.getId().toString(),
                    null, anonymisedToken,
                    null, null, null,
                    Instant.now()
            ));
        });

        log.info("Anonymised {} claims for customer {}", settledClaims.size(), customerId);
        return AnonymisationResult.success(settledClaims.size());
    }

    private void anonymiseClaim(ClaimEntity claim, String token) {
        // Direct UPDATE replaces PII columns without touching the domain model or its
        // state machine — GDPR erasure is an infrastructure concern, not a business operation.
        claimJpaRepository.anonymisePii(claim.getId(), token);
    }

    public record AnonymisationResult(boolean success, boolean blocked, String message, int anonymisedCount) {
        public static AnonymisationResult success(int count) {
            return new AnonymisationResult(true, false, "Anonymised " + count + " claim records", count);
        }
        public static AnonymisationResult blocked(String reason) {
            return new AnonymisationResult(false, true, reason, 0);
        }
        public static AnonymisationResult noDataFound() {
            return new AnonymisationResult(true, false, "No settled claims found for this customer", 0);
        }
    }
}
