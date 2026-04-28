package com.yclaims.claims.domain.model;

/**
 * Complete claim lifecycle state machine.
 * Valid transitions enforced by ClaimStateMachine — no direct field mutation allowed.
 *
 * DRAFT → SUBMITTED → ASSIGNED → UNDER_SURVEY → SURVEYED → UNDER_ADJUDICATION
 *       → APPROVED / REJECTED
 * APPROVED → PAYMENT_INITIATED → SETTLED → ARCHIVED
 * Any active (non-terminal) state → WITHDRAWN (customer-initiated)
 */
public enum ClaimStatus {
    DRAFT,
    SUBMITTED,
    ASSIGNED,
    UNDER_SURVEY,
    SURVEYED,
    UNDER_ADJUDICATION,
    APPROVED,
    REJECTED,
    PAYMENT_INITIATED,
    SETTLED,
    WITHDRAWN,
    ARCHIVED;

    public boolean isTerminal() {
        return this == SETTLED || this == REJECTED || this == WITHDRAWN || this == ARCHIVED;
    }

    public boolean isActive() {
        return !isTerminal();
    }
}
