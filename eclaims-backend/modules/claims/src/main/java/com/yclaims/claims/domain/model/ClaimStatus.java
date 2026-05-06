package com.yclaims.claims.domain.model;

/**
 * Complete claim lifecycle state machine.
 * Valid transitions enforced by ClaimStateMachine — no direct field mutation allowed.
 *
 * DRAFT → SUBMITTED → ASSIGNED → UNDER_SURVEY → SURVEYED → UNDER_ADJUDICATION
 *       → APPROVED / REJECTED
 * APPROVED → PAYMENT_INITIATED → PAYMENT_PROCESSED → SETTLED → ARCHIVED
 * (PAYMENT_PROCESSED may be persisted by payment flows; settle accepts it.)
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
    /** Payment gateway / settlement pipeline has completed; claim not yet marked SETTLED. */
    PAYMENT_PROCESSED,
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
