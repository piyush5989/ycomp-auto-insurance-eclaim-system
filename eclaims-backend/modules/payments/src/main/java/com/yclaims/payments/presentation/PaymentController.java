package com.yclaims.payments.presentation;

import com.yclaims.kernel.security.UserContextHolder;
import com.yclaims.kernel.web.ApiResponse;
import com.yclaims.payments.application.PaymentApplicationService;
import com.yclaims.payments.presentation.dto.InitiatePaymentRequest;
import com.yclaims.payments.presentation.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Payment API — idempotency enforced via Idempotency-Key header backed by Redis.
 * Duplicate payment requests with the same key return the cached result (HTTP 200).
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment initiation and settlement")
public class PaymentController {

    private final PaymentApplicationService paymentService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
        summary = "Initiate a payment for an approved claim",
        description = "Idempotent — supply an Idempotency-Key header. Duplicate requests return the cached result."
    )
    public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest request) {

        PaymentResponse response = paymentService.initiatePayment(
                idempotencyKey, request, UserContextHolder.currentUserId(), correlationId());

        // If idempotency hit (existing payment) return 200; new payment returns 201
        boolean isNew = response.isNewlyCreated();
        return ResponseEntity.status(isNew ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER','CASE_MANAGER','AUDITOR')")
    @Operation(summary = "Get payment status by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable("paymentId") UUID paymentId) {
        PaymentResponse response = paymentService.getPayment(paymentId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
