package com.yclaims.notifications.infrastructure.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Base64;

/**
 * Production SMS adapter using Twilio's REST API.
 * Active only when eclaims.sms.twilio.enabled=true — set TWILIO_ENABLED=true plus the
 * three credential env-vars to enable real SMS delivery.
 *
 * Dev / CI: leave TWILIO_ENABLED unset (defaults false) and ConsoleSmsAdapter activates instead.
 *
 * Twilio pricing: ~$0.0079 per outbound SMS (US numbers, May 2026).
 */
@Component
@ConditionalOnProperty(name = "eclaims.sms.twilio.enabled", havingValue = "true")
@Slf4j
public class TwilioSmsAdapter implements SmsNotificationPort {

    private static final String TWILIO_API_BASE = "https://api.twilio.com/2010-04-01/Accounts";

    @Value("${eclaims.sms.twilio.account-sid}")
    private String accountSid;

    @Value("${eclaims.sms.twilio.auth-token}")
    private String authToken;

    @Value("${eclaims.sms.twilio.from-number}")
    private String fromNumber;

    @Override
    public void send(String to, String message) {
        if (to == null || to.isBlank()) {
            log.info("[SMS-TWILIO] No phone number available — skipping. Message: {}", truncate(message));
            return;
        }
        try {
            String credentials = Base64.getEncoder()
                    .encodeToString((accountSid + ":" + authToken).getBytes());

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("To", to);
            form.add("From", fromNumber);
            form.add("Body", message);

            RestClient.create()
                    .post()
                    .uri(TWILIO_API_BASE + "/{sid}/Messages.json", accountSid)
                    .header("Authorization", "Basic " + credentials)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();

            log.info("[SMS-TWILIO] Sent to {} | Message: {}", to, truncate(message));
        } catch (Exception e) {
            // SMS failure must never fail the business operation
            log.error("[SMS-TWILIO] Failed to send to {}: {}", to, e.getMessage());
        }
    }

    private static String truncate(String s) {
        return s != null && s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }
}
