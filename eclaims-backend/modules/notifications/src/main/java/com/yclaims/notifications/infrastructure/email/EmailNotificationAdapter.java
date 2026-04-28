package com.yclaims.notifications.infrastructure.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email notification adapter using Spring Mail.
 * Sends to Mailhog SMTP in local dev (localhost:1025).
 * Open localhost:8025 to see all notification emails arrive in real time.
 *
 * Production: points to SES/SendGrid — same code, different SMTP config.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationAdapter {

    private final JavaMailSender mailSender;

    public void sendEmail(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            log.warn("Skipping email — no recipient address");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@eclaims.io");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("Email sent to {} | Subject: {}", to, subject);
        } catch (Exception e) {
            // Notification failure must never fail the business operation
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
