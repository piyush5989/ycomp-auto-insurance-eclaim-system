package com.yclaims.notifications.infrastructure.sms;

/**
 * Port for SMS notifications.
 * POC implementation: logs to console.
 * Production implementation: TwilioSmsAdapter behind this port.
 */
public interface SmsNotificationPort {
    void send(String phoneNumber, String message);
}
