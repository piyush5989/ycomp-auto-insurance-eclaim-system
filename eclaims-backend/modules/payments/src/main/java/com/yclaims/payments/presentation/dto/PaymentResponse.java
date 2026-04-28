package com.yclaims.payments.presentation.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class PaymentResponse {
    private UUID paymentId;
    private UUID claimId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String gatewayTransactionId;
    private Instant createdAt;
    private Instant settledAt;

    @JsonIgnore
    private boolean newlyCreated;
}
