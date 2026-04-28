package com.yclaims.notifications.application;

import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.contracts.events.v1.ClaimStatusChangedPayload;
import com.yclaims.contracts.events.v1.PaymentSettledPayload;
import com.yclaims.notifications.infrastructure.email.EmailNotificationAdapter;
import com.yclaims.notifications.infrastructure.sms.SmsNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates notification delivery across channels: email (Mailhog) + SMS (stub).
 * All methods are fire-and-forget (called from Kafka consumers — async from business ops).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationApplicationService {

    private final EmailNotificationAdapter emailAdapter;
    private final SmsNotificationPort smsPort;

    public void sendClaimSubmittedNotification(ClaimCreatedPayload payload, String correlationId) {
        log.info("[{}] Sending claim submitted notification to {}", correlationId, payload.customerEmail());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Your Claim Has Been Submitted",
                buildClaimSubmittedBody(payload)
        );

        smsPort.send(null, // phone number not in payload — Phase 2
                "eClaims: Your claim " + payload.claimId() +
                " has been received. We'll update you shortly.");
    }

    public void sendStatusChangeNotification(ClaimStatusChangedPayload payload, String correlationId) {
        log.info("[{}] Sending status change notification: {} → {}",
                correlationId, payload.previousStatus(), payload.newStatus());

        String subject = "eClaims — Claim Status Updated: " + payload.newStatus();
        emailAdapter.sendEmail(
                payload.customerEmail(),
                subject,
                buildStatusChangeBody(payload)
        );
    }

    public void sendPaymentConfirmation(PaymentSettledPayload payload, String correlationId) {
        log.info("[{}] Sending payment confirmation to {}", correlationId, payload.customerEmail());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Payment Confirmation",
                buildPaymentConfirmationBody(payload)
        );
    }

    private String buildClaimSubmittedBody(ClaimCreatedPayload p) {
        return """
                Dear Customer,
                
                Your eClaims submission has been received successfully.
                
                Claim ID:           %s
                Policy Number:      %s
                Vehicle:            %s
                Incident Date:      %s
                Status:             %s
                
                Our team will review your claim and assign a surveyor shortly.
                You can track your claim status at any time through the eClaims customer portal.
                
                Regards,
                eClaims Team
                """.formatted(p.claimId(), p.policyNumber(), p.vehicleRegistration(),
                              p.incidentDate(), p.status());
    }

    private String buildStatusChangeBody(ClaimStatusChangedPayload p) {
        return """
                Dear Customer,
                
                Your claim status has been updated.
                
                Claim ID:         %s
                Previous Status:  %s
                New Status:       %s
                
                Please log in to the eClaims portal for more details.
                
                Regards,
                eClaims Team
                """.formatted(p.claimId(), p.previousStatus(), p.newStatus());
    }

    private String buildPaymentConfirmationBody(PaymentSettledPayload p) {
        return """
                Dear Customer,
                
                Your payment has been processed successfully.
                
                Claim ID:       %s
                Payment ID:     %s
                Amount:         %s %s
                Transaction ID: %s
                Settled At:     %s
                
                Thank you for using eClaims.
                
                Regards,
                eClaims Team
                """.formatted(p.claimId(), p.paymentId(), p.amount(), p.currency(),
                              p.gatewayTransactionId(), p.settledAt());
    }
}
