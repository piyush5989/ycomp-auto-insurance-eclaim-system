package com.yclaims.payments.domain.port.out;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port for payment gateway integration.
 * POC implementation: MockPaymentGatewayAdapter (auto-approves all payments).
 * Production implementation: StripePaymentGatewayAdapter.
 *
 * The domain has zero knowledge of Stripe — only this interface.
 */
public interface PaymentGatewayPort {

    PaymentGatewayResult initiatePayment(UUID paymentId, String customerId,
                                          BigDecimal amount, String currency,
                                          String description);

    PaymentGatewayResult confirmPayment(String gatewayPaymentId);

    record PaymentGatewayResult(
            boolean success,
            String gatewayTransactionId,
            String status,
            String failureReason
    ) {
        public static PaymentGatewayResult success(String txId) {
            return new PaymentGatewayResult(true, txId, "COMPLETED", null);
        }
        public static PaymentGatewayResult failed(String reason) {
            return new PaymentGatewayResult(false, null, "FAILED", reason);
        }
    }
}
