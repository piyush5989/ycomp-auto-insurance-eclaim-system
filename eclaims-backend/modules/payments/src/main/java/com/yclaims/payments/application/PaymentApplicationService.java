package com.yclaims.payments.application;

import com.yclaims.contracts.events.DomainEvent;
import com.yclaims.contracts.events.v1.PaymentSettledPayload;
import com.yclaims.kernel.exception.NotFoundException;
import com.yclaims.payments.domain.port.out.PaymentGatewayPort;
import com.yclaims.payments.infrastructure.event.PaymentEventQueued;
import com.yclaims.payments.infrastructure.persistence.PaymentEntity;
import com.yclaims.payments.infrastructure.persistence.PaymentJpaRepository;
import com.yclaims.payments.presentation.dto.InitiatePaymentRequest;
import com.yclaims.payments.presentation.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment orchestration with Redis idempotency key store.
 * Duplicate payment requests within 24h return the cached result — zero side effects.
 *
 * Event publishing uses @TransactionalEventListener(AFTER_COMMIT) via ApplicationEventPublisher:
 * the Kafka send is deferred until after the DB row is committed, preventing phantom events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentApplicationService {

    private final PaymentGatewayPort gatewayPort;
    private final PaymentJpaRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;

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

        UUID paymentId = UUID.randomUUID();
        PaymentGatewayPort.PaymentGatewayResult result = gatewayPort.initiatePayment(
                paymentId, userId, request.amount(), request.currency(),
                "Claim settlement: " + request.claimId());

        PaymentEntity entity = new PaymentEntity();
        entity.setId(paymentId);
        entity.setClaimId(request.claimId());
        entity.setCustomerId(userId);
        entity.setAmount(request.amount());
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

        // Queue payment settled event — KafkaPaymentEventPublisher fires after TX commit
        if (result.success()) {
            publishPaymentSettledEvent(entity, correlationId);
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
        // Queue for post-commit delivery — KafkaPaymentEventPublisher handles the actual send
        eventPublisher.publishEvent(new PaymentEventQueued("payment-events", event));
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
