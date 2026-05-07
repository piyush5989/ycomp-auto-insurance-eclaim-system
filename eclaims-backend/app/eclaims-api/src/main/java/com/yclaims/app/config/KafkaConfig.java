package com.yclaims.app.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka topic definitions and consumer factory configuration.
 * Topics created on application startup via Spring's KafkaAdmin.
 *
 * Topic naming:      hyphen-case  (claim-events, payment-events, audit-events)
 * Event type naming: dot notation (claim.created, payment.settled, audit.event)
 *
 * audit-events: 7-year retention (compliance — configured at broker level)
 *
 * Error handling: failed messages are retried 3 times (1 s apart) then published
 * to a Dead Letter Topic (<topic>.DLT) via DeadLetterPublishingRecoverer.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:eclaims-default}")
    private String defaultGroupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    // Replication factor — override in prod profile (kafka.topics.replication-factor: 3)
    @Value("${kafka.topics.replication-factor:1}")
    private int replicationFactor;

    @Value("${kafka.topics.claim-events.partitions:3}")
    private int claimEventsPartitions;

    @Value("${kafka.topics.audit-events.partitions:3}")
    private int auditEventsPartitions;

    @Value("${kafka.topics.payment-events.partitions:2}")
    private int paymentEventsPartitions;

    @Value("${kafka.topics.repair-events.partitions:2}")
    private int repairEventsPartitions;

    @Value("${kafka.topics.notification-events.partitions:2}")
    private int notificationEventsPartitions;

    // -------------------------------------------------------------------------
    // Topic definitions
    // -------------------------------------------------------------------------

    @Bean
    public NewTopic claimEventsTopic() {
        return TopicBuilder.name("claim-events")
                .partitions(claimEventsPartitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic auditEventsTopic() {
        return TopicBuilder.name("audit-events")
                .partitions(auditEventsPartitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events")
                .partitions(paymentEventsPartitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic repairEventsTopic() {
        return TopicBuilder.name("repair-events")
                .partitions(repairEventsPartitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name("notification-events")
                .partitions(notificationEventsPartitions)
                .replicas(replicationFactor)
                .build();
    }

    // -------------------------------------------------------------------------
    // Consumer factories
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(
                "com.yclaims.contracts.events.DomainEvent"));
    }

    /**
     * Consumer factory for {@code audit-events}: payloads are
     * {@link com.yclaims.kernel.audit.AuditEvent}, not {@code DomainEvent}.
     *
     * TODO: wire a dedicated {@code @KafkaListener} with
     *       {@code containerFactory = "auditKafkaListenerContainerFactory"}
     *       so audit events are deserialized directly into AuditEvent.
     */
    @Bean
    public ConsumerFactory<String, Object> auditConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(baseConsumerProps(
                "com.yclaims.kernel.audit.AuditEvent"));
    }

    private Map<String, Object> baseConsumerProps(String valueDefaultType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.yclaims.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, valueDefaultType);
        return props;
    }

    // -------------------------------------------------------------------------
    // Listener container factories
    // -------------------------------------------------------------------------

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1000L, 3)
        ));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> auditKafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditConsumerFactory());
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1000L, 3)
        ));
        return factory;
    }

}
