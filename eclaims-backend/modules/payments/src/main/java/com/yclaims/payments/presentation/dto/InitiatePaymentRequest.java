package com.yclaims.payments.presentation.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record InitiatePaymentRequest(

        @NotNull(message = "Claim ID is required")
        UUID claimId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        @NotBlank(message = "Currency is required")
        String currency,

        String bankAccountNumber,

        String bankRoutingNumber

) {}
