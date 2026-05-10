package com.yclaims.claims.domain;

import com.yclaims.claims.domain.exception.InvalidClaimStateException;
import com.yclaims.claims.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Pure domain unit tests — no Spring context, no database, no mocks.
 * Tests the ClaimStateMachine rules encoded in the Claim aggregate.
 */
class ClaimStateMachineTest {

    @Test
    void newClaimShouldBeInSubmittedStatus() {
        Claim claim = buildClaim();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    void shouldTransitionFromSubmittedToAssigned() {
        Claim claim = buildClaim();
        claim.assignSurveyor("surveyor-1", "corr-123");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ASSIGNED);
        assertThat(claim.getAssignedSurveyorId()).isEqualTo("surveyor-1");
    }

    @Test
    void shouldTransitionThroughFullHappyPath() {
        Claim claim = buildClaim();

        claim.assignSurveyor("surveyor-1", "corr-1");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ASSIGNED);

        claim.beginSurvey();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_SURVEY);

        claim.completeSurvey(new BigDecimal("15000.00"));
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SURVEYED);

        claim.assignAdjudicator("adjustor-1");
        claim.beginAdjudication();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_ADJUDICATION);

        claim.approve(new BigDecimal("14500.00"), "workshop-1", "corr-2");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claim.getApprovedAmount()).isEqualByComparingTo("14500.00");

        claim.markPaymentInitiated();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.PAYMENT_INITIATED);

        claim.settle();
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(claim.getStatus().isTerminal()).isTrue();
    }

    @Test
    void shouldRejectClaim() {
        Claim claim = buildClaim();
        claim.assignSurveyor("s1", "c1");
        claim.beginSurvey();
        claim.completeSurvey(new BigDecimal("5000"));
        claim.assignAdjudicator("a1");
        claim.beginAdjudication();
        claim.reject("Fraudulent claim", "c2");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.getStatus().isTerminal()).isTrue();
    }

    @Test
    void shouldNotAllowInvalidTransition() {
        Claim claim = buildClaim();
        // Cannot skip SUBMITTED → directly to UNDER_SURVEY
        assertThatThrownBy(claim::beginSurvey)
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("SUBMITTED");
    }

    @Test
    void shouldAllowWithdrawalFromAnyActiveState() {
        Claim claim = buildClaim();
        claim.withdraw("corr-withdraw");
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.WITHDRAWN);
    }

    @Test
    void shouldNotAllowWithdrawalFromSettledClaim() {
        Claim claim = buildClaim();
        claim.assignSurveyor("s1", "c1");
        claim.beginSurvey();
        claim.completeSurvey(new BigDecimal("5000"));
        claim.assignAdjudicator("a1");
        claim.beginAdjudication();
        claim.approve(new BigDecimal("4500"), "w1", "c2");
        claim.markPaymentInitiated();
        claim.settle();

        assertThatThrownBy(() -> claim.withdraw("corr"))
                .isInstanceOf(InvalidClaimStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void shouldRegisterDomainEventOnSubmit() {
        Claim claim = buildClaim();
        assertThat(claim.getDomainEvents()).hasSize(1);
    }

    @Test
    void approvedAmountMustBePositive() {
        Claim claim = buildClaim();
        claim.assignSurveyor("s1", "c1");
        claim.beginSurvey();
        claim.completeSurvey(new BigDecimal("5000"));
        claim.assignAdjudicator("a1");
        claim.beginAdjudication();

        assertThatThrownBy(() -> claim.approve(BigDecimal.ZERO, "w1", "c"))
                .isInstanceOf(InvalidClaimStateException.class);
    }

    private Claim buildClaim() {
        return Claim.submit(
                "POL-00000001",
                "customer-1",
                "customer@test.com",
                "TN01-1234",
                ClaimType.COLLISION,
                new AccidentDetails(
                        LocalDate.now().minusDays(1),
                        "Highway 101, Exit 42",
                        "Rear-end collision at traffic light",
                        true,
                        "PD-2024-001"
                )
        );
    }
}
