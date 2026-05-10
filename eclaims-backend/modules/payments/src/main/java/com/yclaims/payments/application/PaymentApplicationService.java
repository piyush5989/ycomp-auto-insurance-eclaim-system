package com.yclaims.payments.application;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.PaymentSettledPayload;
import com.yclaims.kernel.exception.NotFoundException;
import com.yclaims.payments.domain.port.out.PaymentGatewayPort;
import com.yclaims.payments.infrastructure.persistence.PaymentEntity;
import com.yclaims.payments.infrastructure.persistence.PaymentJpaRepository;
import com.yclaims.payments.presentation.dto.BillPreviewResponse;
import com.yclaims.payments.presentation.dto.InitiatePaymentRequest;
import com.yclaims.payments.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment orchestration with Redis idempotency key store.
 * Duplicate payment requests within 24h return the cached result — zero side effects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentApplicationService {

    @Value("${eclaims.payments.processing-fee:10.00}")
    private BigDecimal processingFeeAmount;

    private final PaymentGatewayPort gatewayPort;
    private final PaymentJpaRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate jdbcTemplate;

    private static final String APPROVED_AND_LATEST_FINAL_COST_SQL = """
            SELECT c.approved_amount,
                   (SELECT wo.final_cost FROM workshops.work_orders wo
                    WHERE wo.claim_id = c.id
                    ORDER BY wo.created_at DESC NULLS LAST
                    LIMIT 1) AS final_cost
            FROM claims.claims c
            WHERE c.id = ?::uuid
            """;

    @Transactional(readOnly = true)
    public BillPreviewResponse previewBill(UUID claimId, String correlationId) {
        Map<String, Object> claimData = jdbcTemplate.queryForMap(APPROVED_AND_LATEST_FINAL_COST_SQL, claimId.toString());
        BigDecimal approvedAmount = (BigDecimal) claimData.get("approved_amount");
        BigDecimal finalCost = (BigDecimal) claimData.get("final_cost");

        if (approvedAmount == null) {
            throw new IllegalStateException("Claim must be approved before payment calculation");
        }

        BigDecimal processingFee = processingFeeAmount;
        BigDecimal workshopDifference = BigDecimal.ZERO;
        if (finalCost != null && finalCost.compareTo(approvedAmount) > 0) {
            workshopDifference = finalCost.subtract(approvedAmount);
        }
        BigDecimal totalDue = workshopDifference.add(processingFee);

        log.info("[{}] Bill preview | Claim: {} | Approved: {} | Workshop final: {} | Fee: {} | Total due: {}",
                correlationId, claimId, approvedAmount, finalCost, processingFee, totalDue);

        return new BillPreviewResponse(approvedAmount, finalCost, processingFee, totalDue);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateFinalBill(UUID claimId, String correlationId) {
        return previewBill(claimId, correlationId).totalDue();
    }

    @Transactional
    public PaymentResponse initiatePayment(String idempotencyKey,
                                            InitiatePaymentRequest request,
                                            String userId,
                                            String correlationId) {
        // Redis idempotency check — SETNX pattern
        String cacheKey = "idempotency:" + idempotencyKey;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof PaymentResponse existing) {
            log.info("[{}] Idempotency hit for key {} — returning cached payment {}",
                    correlationId, idempotencyKey, existing.getPaymentId());
            return existing;
        }

        // Calculate the actual final bill amount (override request amount)
        BigDecimal finalBillAmount = calculateFinalBill(request.claimId(), correlationId);
        
        UUID paymentId = UUID.randomUUID();
        PaymentGatewayPort.PaymentGatewayResult result = gatewayPort.initiatePayment(
                paymentId, userId, finalBillAmount, request.currency(),
                "Claim settlement: " + request.claimId());

        PaymentEntity entity = new PaymentEntity();
        entity.setId(paymentId);
        entity.setClaimId(request.claimId());
        entity.setCustomerId(userId);
        entity.setAmount(finalBillAmount);  // Use calculated amount, not request amount
        entity.setCurrency(request.currency());
        entity.setStatus(result.success() ? "SETTLED" : "FAILED");
        entity.setGatewayTransactionId(result.gatewayTransactionId());
        entity.setCreatedAt(Instant.now());
        if (result.success()) {
            entity.setSettledAt(Instant.now());
        }

        paymentRepository.save(entity);

        PaymentResponse response = toResponse(entity, true);

        // Store in Redis for 24h idempotency window
        redisTemplate.opsForValue().set(cacheKey, response, Duration.ofHours(24));

        // Publish payment settled event
        if (result.success()) {
            publishPaymentSettledEvent(entity, correlationId);
            // Update claim status to indicate payment is complete
            updateClaimStatusAfterPayment(request.claimId(), correlationId);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId, String correlationId) {
        PaymentEntity entity = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment", paymentId.toString()));
        return toResponse(entity, false);
    }

    private void publishPaymentSettledEvent(PaymentEntity entity, String correlationId) {
        var payload = new PaymentSettledPayload(
                entity.getId(), entity.getClaimId(), entity.getCustomerId(),
                null, // customerEmail not stored on payment — reporting joins via claims
                entity.getAmount(), entity.getCurrency(),
                entity.getGatewayTransactionId(), entity.getSettledAt()
        );
        var event = new DomainEvent<>(
                UUID.randomUUID().toString(), "payment.settled",
                correlationId, null,
                entity.getId().toString(), "Payment",
                "v1", Instant.now(), payload
        );
        kafkaTemplate.send("payment-events", entity.getId().toString(), event);
    }

    private void updateClaimStatusAfterPayment(UUID claimId, String correlationId) {
        try {
            jdbcTemplate.update(
                    "UPDATE claims.claims SET status = 'PAYMENT_PROCESSED' WHERE id = ?::uuid",
                    claimId.toString());
            log.info("[{}] Updated claim {} status to PAYMENT_PROCESSED after successful payment", 
                    correlationId, claimId);
        } catch (Exception e) {
            log.error("[{}] Failed to update claim status after payment for claim {}", 
                    correlationId, claimId, e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> generateReceipt(UUID paymentId, String correlationId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment", paymentId.toString()));
        Map<String, Object> receipt = buildReceiptData(payment);

        log.info("[{}] Generated receipt for payment {}", correlationId, paymentId);
        return receipt;
    }

    @Transactional(readOnly = true)
    public byte[] generateReceiptPdfByClaimId(UUID claimId, String correlationId) {
        PaymentEntity payment = paymentRepository
                .findTopByClaimIdAndStatusOrderBySettledAtDesc(claimId, "SETTLED")
                .orElseThrow(() -> new NotFoundException("SettledPaymentForClaim", claimId.toString()));

        Map<String, Object> receipt = buildReceiptData(payment);
        byte[] pdfBytes = buildReceiptPdf(receipt);
        log.info("[{}] Generated receipt PDF for claim {} payment {}", correlationId, claimId, payment.getId());
        return pdfBytes;
    }

    private Map<String, Object> buildReceiptData(PaymentEntity payment) {
        Map<String, Object> claimDetails = jdbcTemplate.queryForMap(
                """
                SELECT c.policy_number, c.vehicle_registration, c.incident_date,
                       c.approved_amount, w.name as workshop_name, w.phone as workshop_phone,
                       (SELECT wo.final_cost FROM workshops.work_orders wo
                        WHERE wo.claim_id = c.id
                        ORDER BY wo.created_at DESC NULLS LAST
                        LIMIT 1) AS final_cost
                FROM claims.claims c
                LEFT JOIN workshops.workshops w ON c.workshop_id::uuid = w.id
                WHERE c.id = ?::uuid
                """, payment.getClaimId().toString());

        Map<String, Object> receipt = new HashMap<>();
        receipt.put("receiptId", payment.getId().toString());
        receipt.put("claimId", payment.getClaimId().toString());
        receipt.put("policyNumber", claimDetails.get("policy_number"));
        receipt.put("vehicleRegistration", claimDetails.get("vehicle_registration"));
        receipt.put("incidentDate", claimDetails.get("incident_date"));
        receipt.put("approvedAmount", claimDetails.get("approved_amount"));
        receipt.put("workshopFinalCost", claimDetails.get("final_cost"));
        receipt.put("workshopName", claimDetails.get("workshop_name"));
        receipt.put("workshopPhone", claimDetails.get("workshop_phone"));
        receipt.put("processingFee", processingFeeAmount);
        receipt.put("totalPaid", payment.getAmount());
        receipt.put("currency", payment.getCurrency());
        receipt.put("paymentDate", payment.getSettledAt());
        receipt.put("transactionId", payment.getGatewayTransactionId());
        return receipt;
    }

    private byte[] buildReceiptPdf(Map<String, Object> receipt) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
                .withZone(ZoneId.systemDefault());
        List<String> lines = new ArrayList<>();
        lines.add("eClaims Payment Receipt");
        lines.add("");
        lines.add("Receipt ID: " + value(receipt.get("receiptId")));
        lines.add("Claim ID: " + value(receipt.get("claimId")));
        lines.add("Policy Number: " + value(receipt.get("policyNumber")));
        lines.add("Vehicle: " + value(receipt.get("vehicleRegistration")));
        lines.add("Workshop: " + value(receipt.get("workshopName")));
        lines.add("Workshop Phone: " + value(receipt.get("workshopPhone")));
        lines.add("");
        lines.add("Approved Amount: " + value(receipt.get("approvedAmount")));
        lines.add("Workshop Final Cost: " + value(receipt.get("workshopFinalCost")));
        lines.add("Processing Fee: " + value(receipt.get("processingFee")));
        lines.add("Total Paid: " + value(receipt.get("totalPaid")) + " " + value(receipt.get("currency")));
        lines.add("");
        lines.add("Payment Date: " + formatInstant(receipt.get("paymentDate"), formatter));
        lines.add("Transaction ID: " + value(receipt.get("transactionId")));

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.newLineAtOffset(50, 780);
                stream.setLeading(18f);
                stream.setFont(PDType1Font.HELVETICA_BOLD, 16);
                stream.showText(lines.get(0));
                stream.newLine();
                stream.newLine();
                stream.setFont(PDType1Font.HELVETICA, 11);
                for (int i = 1; i < lines.size(); i++) {
                    stream.showText(lines.get(i));
                    stream.newLine();
                }
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate receipt PDF", e);
        }
    }

    private String value(Object v) {
        return v == null ? "-" : String.valueOf(v);
    }

    private String formatInstant(Object v, DateTimeFormatter formatter) {
        if (v instanceof Instant instant) {
            return formatter.format(instant);
        }
        return value(v);
    }

    private PaymentResponse toResponse(PaymentEntity entity, boolean isNew) {
        return PaymentResponse.builder()
                .paymentId(entity.getId())
                .claimId(entity.getClaimId())
                .customerId(entity.getCustomerId())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
                .gatewayTransactionId(entity.getGatewayTransactionId())
                .createdAt(entity.getCreatedAt())
                .settledAt(entity.getSettledAt())
                .newlyCreated(isNew)
                .build();
    }
}
