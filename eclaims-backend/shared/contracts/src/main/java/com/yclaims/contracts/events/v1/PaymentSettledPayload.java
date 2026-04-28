package com.yclaims.contracts.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload for 'payment.settled' domain event.
 * Consumers: notification-module (send payment confirmation), reporting-module.
 */
public record PaymentSettledPayload(
        UUID paymentId,
        UUID claimId,
        String customerId,
        String customerEmail,
        BigDecimal amount,
        String currency,
        String gatewayTransactionId,
        Instant settledAt
) {}
