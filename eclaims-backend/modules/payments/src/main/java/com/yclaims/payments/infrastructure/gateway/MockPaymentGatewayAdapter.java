package com.yclaims.payments.infrastructure.gateway;

import com.yclaims.payments.domain.port.out.PaymentGatewayPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * POC payment gateway: auto-approves all payments.
 * Demonstrates the PaymentGatewayPort interface.
 * Production replacement: StripePaymentGatewayAdapter — zero domain code change.
 */
@Component
@Profile({"local", "test", "default"})
@Slf4j
public class MockPaymentGatewayAdapter implements PaymentGatewayPort {

    @Override
    public PaymentGatewayResult initiatePayment(UUID paymentId, String customerId,
                                                 BigDecimal amount, String currency,
                                                 String description) {
        String txId = "MOCK-TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[PAYMENT-MOCK] Payment {} initiated: {} {} → txId={}", paymentId, amount, currency, txId);
        return PaymentGatewayResult.success(txId);
    }

    @Override
    public PaymentGatewayResult confirmPayment(String gatewayPaymentId) {
        log.info("[PAYMENT-MOCK] Payment {} confirmed", gatewayPaymentId);
        return PaymentGatewayResult.success(gatewayPaymentId);
    }
}
