package com.yclaims.kernel.audit;

/**
 * Port: publish an audit event to the durable event bus.
 * Implemented in infrastructure layer (Kafka adapter).
 * Every module that performs a write operation must publish an AuditEvent.
 */
public interface AuditPublisher {

    void publish(AuditEvent event);
}
