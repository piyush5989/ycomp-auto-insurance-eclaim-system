package com.yclaims.notifications.application;

import com.yclaims.contracts.events.v1.ClaimAdjudicatedPayload;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates notification delivery across all three channels:
 *   - Email  : via Mailhog (dev, localhost:1025) / external SMTP (prod)
 *   - SMS    : via TwilioSmsAdapter when TWILIO_ENABLED=true, otherwise ConsoleSmsAdapter
 *   - In-app : persisted to notifications.customer_notifications, polled by NotificationController
 *
 * All methods are fire-and-forget — invoked from Kafka consumers, never from business transactions.
 * Notification failures are swallowed with a log (see EmailNotificationAdapter) so they never
 * roll back a business operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationApplicationService {

    private final EmailNotificationAdapter emailAdapter;
    private final SmsNotificationPort smsPort;
    private final CustomerNotificationJpaRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void sendClaimSubmittedNotification(ClaimCreatedPayload payload, String correlationId) {
        log.info("[{}] Claim submitted → email+SMS to {}", correlationId, payload.customerEmail());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Your Claim Has Been Submitted",
                buildClaimSubmittedBody(payload));

        sendSms(resolveCustomerPhone(payload.customerId()),
                "eClaims: Claim " + payload.claimId() + " received for policy "
                        + payload.policyNumber() + ". We'll keep you updated.");

        persistNotification(payload.customerId(), "CLAIM_SUBMITTED",
                "Claim Submitted",
                "Your claim for policy " + payload.policyNumber() + " has been received.",
                payload.claimId());
    }

    @Transactional
    public void sendStatusChangeNotification(ClaimStatusChangedPayload payload, String correlationId) {
        log.info("[{}] Status change → {} | email+SMS to {}", correlationId, payload.newStatus(), payload.customerEmail());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Claim Status Updated: " + payload.newStatus(),
                buildStatusChangeBody(payload));

        sendSms(resolveCustomerPhone(payload.customerId()),
                String.format("eClaims: Claim %s status updated to %s. Log in for details.",
                        payload.claimId(), payload.newStatus()));

        persistNotification(payload.customerId(), "CLAIM_STATUS_CHANGED",
                "Claim Status Updated",
                "Your claim status changed from " + payload.previousStatus()
                        + " to " + payload.newStatus() + ".",
                payload.claimId());
    }

    /**
     * Sends decision notifications to both the customer and the partner workshop.
     * workshopEmail is resolved at event-publish time via WorkshopEmailPort so we
     * never need a cross-module DB query here.
     */
    @Transactional
    public void sendClaimAdjudicatedNotification(ClaimAdjudicatedPayload payload, String correlationId) {
        log.info("[{}] Claim adjudicated → {} | email+SMS to customer {}", correlationId, payload.decision(), payload.customerEmail());

        // Customer email
        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Claim Decision: " + payload.decision(),
                buildAdjudicatedCustomerBody(payload));

        // Customer SMS
        String customerSms = "APPROVED".equals(payload.decision())
                ? String.format("eClaims: Claim %s APPROVED for %s. Check your portal.", payload.claimId(), payload.approvedAmount())
                : String.format("eClaims: Claim %s REJECTED. Reason: %s", payload.claimId(), payload.rejectionReason());
        sendSms(resolveCustomerPhone(payload.customerId() != null ? payload.customerId().toString() : null), customerSms);

        persistNotification(payload.customerId().toString(), "CLAIM_ADJUDICATED",
                "Claim " + payload.decision(),
                "APPROVED".equals(payload.decision())
                        ? "Your claim has been approved for " + payload.approvedAmount() + "."
                        : "Your claim has been rejected. Reason: " + payload.rejectionReason(),
                payload.claimId());

        // Workshop email (if the event carries the email address)
        if (payload.workshopEmail() != null && !payload.workshopEmail().isBlank()) {
            log.info("[{}] Adjudication email → workshop {}", correlationId, payload.workshopEmail());
            emailAdapter.sendEmail(
                    payload.workshopEmail(),
                    "eClaims — Repair Authorisation: " + payload.decision(),
                    buildAdjudicatedWorkshopBody(payload));
        }
    }

    @Transactional
    public void sendPaymentConfirmation(PaymentSettledPayload payload, String correlationId) {
        log.info("[{}] Payment confirmed → email+SMS to {}", correlationId, payload.customerEmail());

        emailAdapter.sendEmail(
                payload.customerEmail(),
                "eClaims — Payment Confirmation",
                buildPaymentConfirmationBody(payload));

        sendSms(resolveCustomerPhone(payload.customerId()),
                String.format("eClaims: Payment of %s %s for claim %s confirmed. Tx: %s",
                        payload.amount(), payload.currency(), payload.claimId(), payload.gatewayTransactionId()));

        persistNotification(payload.customerId(), "PAYMENT_CONFIRMED",
                "Payment Confirmed",
                "Your payment of " + payload.amount() + " " + payload.currency()
                        + " has been processed. Transaction: " + payload.gatewayTransactionId(),
                payload.claimId());
    }

    @Transactional
    public void sendRepairStatusNotification(RepairStatusUpdatedPayload payload, String correlationId) {
        log.info("[{}] Repair status → {} | email+SMS for claim {}", correlationId, payload.repairStatus(), payload.claimId());

        if (payload.customerEmail() != null) {
            emailAdapter.sendEmail(
                    payload.customerEmail(),
                    "eClaims — Repair Status Update: " + payload.repairStatus(),
                    buildRepairStatusBody(payload));
        }

        sendSms(resolveCustomerPhone(payload.customerId()),
                String.format("eClaims: Repair for claim %s → %s%s", payload.claimId(),
                        payload.repairStatus(),
                        payload.estimatedCompletionDate() != null
                                ? ". Est. completion: " + payload.estimatedCompletionDate() : ""));

        if (payload.customerId() != null) {
            persistNotification(payload.customerId(), "REPAIR_STATUS_UPDATED",
                    "Repair Update from " + payload.workshopName(),
                    "Repair status: " + payload.repairStatus()
                            + (payload.estimatedCompletionDate() != null
                                    ? ". Estimated completion: " + payload.estimatedCompletionDate() : "")
                            + (payload.statusNote() != null ? ". " + payload.statusNote() : ""),
                    payload.claimId());
        }
    }

    /**
     * Handles notification.requested events from AutoAssignmentService.
     * recipientEmail is now carried in the payload (n2) so staff receive email directly.
     * Phone lookup is Phase 2 — SMS falls through to ConsoleSmsAdapter when phone is absent.
     */
    @Transactional
    public void sendNotificationRequested(NotificationRequestedPayload payload, String correlationId) {
        log.info("[{}] notification.requested | recipient={} type={} channel={}",
                correlationId, payload.recipientId(), payload.notificationType(), payload.channel());

        if (payload.recipientEmail() != null && !payload.recipientEmail().isBlank()) {
            emailAdapter.sendEmail(payload.recipientEmail(), payload.subject(), buildStaffNotificationBody(payload));
        }

        String phone = payload.metadata() != null ? payload.metadata().get("recipientPhone") : null;
        if (phone == null || phone.isBlank()) {
            phone = resolveStaffPhone(payload.recipientType(), payload.recipientId());
        }
        sendSms(phone, payload.message());

        persistNotification(
                payload.recipientId(),
                payload.notificationType(),
                payload.subject(),
                payload.message(),
                payload.claimId()
        );
    }

    /**
     * Delegates to the active SmsNotificationPort implementation.
     * When phone is null/blank the adapter logs clearly ("No phone — skipping") rather than
     * attempting a send. Customer phone storage is tracked as a follow-on task.
     */
    private void sendSms(String phone, String message) {
        smsPort.send(phone, message);
    }

    private String resolveCustomerPhone(String customerId) {
        if (customerId == null || customerId.isBlank()) return null;
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT phone FROM customers.customer_profiles WHERE customer_id = ?",
                    String.class,
                    customerId
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveStaffPhone(String recipientType, String recipientId) {
        if (recipientType == null || recipientId == null) return null;
        try {
            if ("SURVEYOR".equalsIgnoreCase(recipientType)) {
                return jdbcTemplate.queryForObject(
                        "SELECT phone FROM workflow.surveyors WHERE id = ?::uuid",
                        String.class,
                        recipientId
                );
            }
            if ("ADJUSTOR".equalsIgnoreCase(recipientType)) {
                return jdbcTemplate.queryForObject(
                        "SELECT phone FROM workflow.adjustors WHERE id = ?::uuid",
                        String.class,
                        recipientId
                );
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void persistNotification(String recipientId, String type,
                                      String title, String message, UUID claimId) {
        if (recipientId == null) return;
        CustomerNotificationEntity entity = new CustomerNotificationEntity();
        entity.setCustomerId(recipientId);
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
                Track your claim status any time through the eClaims customer portal.

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

    private String buildAdjudicatedCustomerBody(ClaimAdjudicatedPayload p) {
        if ("APPROVED".equals(p.decision())) {
            return """
                    Dear Customer,

                    We are pleased to inform you that your claim has been APPROVED.

                    Claim ID:          %s
                    Decision:          APPROVED
                    Approved Amount:   %s

                    Your vehicle will now proceed with repairs. You will receive further
                    updates as the repair progresses.

                    Regards,
                    eClaims Team
                    """.formatted(p.claimId(), p.approvedAmount());
        }
        return """
                Dear Customer,

                After careful review, your claim has been REJECTED.

                Claim ID:   %s
                Decision:   REJECTED
                Reason:     %s

                If you believe this decision is incorrect, please contact our support team
                or raise an appeal through the eClaims customer portal.

                Regards,
                eClaims Team
                """.formatted(p.claimId(), p.rejectionReason());
    }

    private String buildAdjudicatedWorkshopBody(ClaimAdjudicatedPayload p) {
        if ("APPROVED".equals(p.decision())) {
            return """
                    Dear Workshop Partner,

                    Repair authorisation has been APPROVED for the following claim.

                    Claim ID:          %s
                    Approved Amount:   %s

                    You are authorised to proceed with repairs up to the approved amount.
                    Please upload the work order and repair updates through the workshop portal.

                    Regards,
                    eClaims Operations Team
                    """.formatted(p.claimId(), p.approvedAmount());
        }
        return """
                Dear Workshop Partner,

                Please be advised that claim %s has been REJECTED.

                Reason: %s

                Please contact the customer directly regarding next steps.

                Regards,
                eClaims Operations Team
                """.formatted(p.claimId(), p.rejectionReason());
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

    private String buildStaffNotificationBody(NotificationRequestedPayload p) {
        return """
                Dear %s,

                %s

                Claim ID: %s

                Please log in to the eClaims internal portal to view and action this claim.

                Regards,
                eClaims Operations
                """.formatted(p.recipientType(), p.message(), p.claimId());
    }
}
