package com.yclaims.contracts.api;

/**
 * Shared claim status enum — used in event payloads and shared API contracts.
 * Mirrors ClaimStatus in the claims domain model.
 *
 * State machine (enforced by ClaimStateMachine):
 * DRAFT → SUBMITTED → ASSIGNED → UNDER_SURVEY → SURVEYED → UNDER_ADJUDICATION
 *       → APPROVED / REJECTED
 * APPROVED → PAYMENT_INITIATED → SETTLED → ARCHIVED
 * Any active state → WITHDRAWN (customer-initiated)
 */
public enum ClaimStatusDto {
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
    ARCHIVED
}
