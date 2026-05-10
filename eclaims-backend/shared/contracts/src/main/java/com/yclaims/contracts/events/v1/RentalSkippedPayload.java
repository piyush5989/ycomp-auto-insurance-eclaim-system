package com.yclaims.contracts.events.v1;

import java.util.UUID;

/**
 * Event payload when a customer skips the optional rental vehicle step.
 */
public record RentalSkippedPayload(
        UUID claimId,
        String customerId,
        String reason  // e.g., "CUSTOMER_DECLINED", "NOT_NEEDED"
) {
}
