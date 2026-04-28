package com.yclaims.notifications.infrastructure.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * POC SMS stub: logs to console. Production replacement: TwilioSmsAdapter.
 */
@Component
@Slf4j
public class ConsoleSmsAdapter implements SmsNotificationPort {
    @Override
    public void send(String phoneNumber, String message) {
        log.info("[SMS-STUB] To: {} | Message: {}", phoneNumber != null ? phoneNumber : "N/A", message);
    }
}
