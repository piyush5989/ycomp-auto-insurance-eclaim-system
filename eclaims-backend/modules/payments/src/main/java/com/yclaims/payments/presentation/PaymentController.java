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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    @PreAuthorize("@authz.isAllowed('payment', 'initiate')")
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
    @PreAuthorize("@authz.isAllowed('payment', 'read')")
    @Operation(summary = "Get payment status by ID")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID paymentId) {
        PaymentResponse response = paymentService.getPayment(paymentId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId()));
    }

    @GetMapping("/calculate-bill/{claimId}")
    @PreAuthorize("@authz.isAllowed('payment', 'read')")
    @Operation(summary = "Calculate final bill amount for a claim",
               description = "Returns: (Workshop Final Cost - Approved Amount) + configured processing fee (eclaims.payments.processing-fee)")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> calculateFinalBill(@PathVariable UUID claimId) {
        java.math.BigDecimal amount = paymentService.calculateFinalBill(claimId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(amount, correlationId()));
    }

    @GetMapping("/{paymentId}/receipt")
    @PreAuthorize("@authz.isAllowed('payment', 'read')")
    @Operation(summary = "Generate payment receipt")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> generateReceipt(@PathVariable UUID paymentId) {
        java.util.Map<String, Object> receipt = paymentService.generateReceipt(paymentId, correlationId());
        return ResponseEntity.ok(ApiResponse.success(receipt, correlationId()));
    }

    @GetMapping("/claims/{claimId}/receipt.pdf")
    @PreAuthorize("@authz.isAllowed('payment', 'read') or @authz.isAllowed('workshop', 'work-order-read')")
    @Operation(summary = "Download settled claim payment receipt PDF")
    public ResponseEntity<byte[]> downloadReceiptPdfByClaim(@PathVariable UUID claimId) {
        byte[] pdf = paymentService.generateReceiptPdfByClaimId(claimId, correlationId());
        String filename = "receipt-" + claimId + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(pdf);
    }

    private String correlationId() {
        String id = MDC.get("correlationId");
        return id != null ? id : UUID.randomUUID().toString();
    }
}
