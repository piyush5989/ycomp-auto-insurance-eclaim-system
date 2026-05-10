package com.yclaims.notifications.infrastructure.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Dev / CI SMS fallback — active when eclaims.sms.twilio.enabled is false or unset.
 * Logs the full message body so developers can verify SMS content in the console.
 * Switch to TwilioSmsAdapter in production by setting TWILIO_ENABLED=true.
 */
@Component
@ConditionalOnProperty(name = "eclaims.sms.twilio.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
public class ConsoleSmsAdapter implements SmsNotificationPort {
    @Override
    public void send(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.info("[SMS-CONSOLE] (no phone on record) Would send: {}", message);
        } else {
            log.info("[SMS-CONSOLE] To: {} | Message: {}", phoneNumber, message);
        }
    }
}
