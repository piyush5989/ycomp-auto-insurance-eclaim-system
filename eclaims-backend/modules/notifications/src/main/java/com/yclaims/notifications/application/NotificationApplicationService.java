package com.yclaims.notifications.application;

import com.yclaims.contracts.events.v1.ClaimCreatedPayload;
import com.yclaims.contracts.events.v1.ClaimStatusChangedPayload;
import com.yclaims.contracts.events.v1.NotificationRequestedPayload;
import com.yclaims.contracts.events.v1.PaymentSettledPayload;
import com.yclaims.contracts.events.v1.RepairStatusUpdatedPayload;
import com.yclaims.notifications.infrastructure.email.EmailNotificationAdapter;
import com.yclaims.notifications.infrastructure.persistence.CustomerNotificationEntity;
import com.yclaims.notifications.infrastructure.persistence.CustomerNotificationJpaRepository;
import com.yclaims.notifications.infrastructure.sms.SmsNotificationPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates notification delivery:
 *   - Email via Mailhog (dev) / SMTP (prod)
 *   - SMS (stub — Phase 2: requires phone in customer profile)
 *   - In-app: persisted to notifications.customer_notifications, fetched via NotificationController
 *
 * All methods are fire-and-forget (called from Kafka consumers — async from business operations).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationApplicationService {

    private final EmailNotificationAdapter emailAdapter;
    private final SmsNotificationPort smsPort;
    private final CustomerNotificationJpaRepository notificationRepository;

    @Transactional
    public void sendClaimSubmittedNotification(ClaimCreatedPayload payload, String correlationId) {
        log.info("[{}] Sending claim submitted notification to {}", correlationId, payload.customerEmail());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Your Claim Has Been Submitted",
                buildClaimSubmittedBody(payload));

        smsPort.send(null, "eClaims: Claim " + payload.claimId() + " received. We'll update you shortly.");

        persistNotification(payload.customerId(), "CLAIM_SUBMITTED",
                "Claim Submitted",
                "Your claim for policy " + payload.policyNumber() + " has been received.",
                payload.claimId());
    }

    @Transactional
    public void sendStatusChangeNotification(ClaimStatusChangedPayload payload, String correlationId) {
        log.info("[{}] Sending status change notification: {} → {}",
                correlationId, payload.previousStatus(), payload.newStatus());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Claim Status Updated: " + payload.newStatus(),
                buildStatusChangeBody(payload));

        persistNotification(payload.customerId(), "CLAIM_STATUS_CHANGED",
                "Claim Status Updated",
                "Your claim status changed from " + payload.previousStatus()
                        + " to " + payload.newStatus() + ".",
                payload.claimId());
    }

    @Transactional
    public void sendPaymentConfirmation(PaymentSettledPayload payload, String correlationId) {
        log.info("[{}] Sending payment confirmation to {}", correlationId, payload.customerEmail());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Payment Confirmation",
                buildPaymentConfirmationBody(payload));

        persistNotification(payload.customerId(), "PAYMENT_CONFIRMED",
                "Payment Confirmed",
                "Your payment of " + payload.amount() + " " + payload.currency()
                        + " has been processed. Transaction: " + payload.gatewayTransactionId(),
                payload.claimId());
    }

    /**
     * Handles generic notification.requested events from the workflow module.
     * Persists an in-app notification for the recipient (surveyor, adjustor, etc.).
     * Email/SMS channels require a recipient email lookup from IAM (Phase 2).
     */
    @Transactional
    public void sendNotificationRequested(NotificationRequestedPayload payload, String correlationId) {
        log.info("[{}] notification.requested | recipient={} type={} channel={}",
                correlationId, payload.recipientId(), payload.notificationType(), payload.channel());
        persistNotification(
                payload.recipientId(),
                payload.notificationType(),
                payload.subject(),
                payload.message(),
                payload.claimId()
        );
    }

    @Transactional
    public void sendRepairStatusNotification(RepairStatusUpdatedPayload payload, String correlationId) {
        log.info("[{}] Sending repair status notification for claimId={} status={}",
                correlationId, payload.claimId(), payload.repairStatus());

        if (payload.customerEmail() != null) {
            emailAdapter.sendEmail(
                    payload.customerEmail(),
                    "eClaims — Repair Status Update: " + payload.repairStatus(),
                    buildRepairStatusBody(payload));
        }

        if (payload.customerId() != null) {
            persistNotification(payload.customerId(), "REPAIR_STATUS_UPDATED",
                    "Repair Update from " + payload.workshopName(),
                    "Repair status: " + payload.repairStatus()
                            + (payload.estimatedCompletionDate() != null
                                    ? ". Estimated completion: " + payload.estimatedCompletionDate()
                                    : "")
                            + (payload.statusNote() != null ? ". " + payload.statusNote() : ""),
                    payload.claimId());
        }
    }

    private void persistNotification(String customerId, String type,
                                      String title, String message, UUID claimId) {
        if (customerId == null) return;
        CustomerNotificationEntity entity = new CustomerNotificationEntity();
        entity.setCustomerId(customerId);
        entity.setType(type);
        entity.setTitle(title);
        entity.setMessage(message);
        entity.setClaimId(claimId);
        entity.setRead(false);
        notificationRepository.save(entity);
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

    private String buildRepairStatusBody(RepairStatusUpdatedPayload p) {
        return """
                Dear Customer,

                There is an update on your vehicle repair.

                Claim ID:               %s
                Workshop:               %s
                Repair Status:          %s
                Estimated Completion:   %s
                Note:                   %s

                Please log in to the eClaims portal for full repair progress details.

                Regards,
                eClaims Team
                """.formatted(p.claimId(), p.workshopName(), p.repairStatus(),
                              p.estimatedCompletionDate() != null ? p.estimatedCompletionDate() : "TBD",
                              p.statusNote() != null ? p.statusNote() : "-");
    }
}
