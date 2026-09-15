package com.yclaims.contracts;

public final class KafkaTopics {

    public static final String CLAIM_EVENTS        = "claim-events";
    public static final String AUDIT_EVENTS        = "audit-events";
    public static final String PAYMENT_EVENTS      = "payment-events";
    public static final String REPAIR_EVENTS       = "repair-events";
    public static final String NOTIFICATION_EVENTS = "notification-events";

    private KafkaTopics() {}
}
