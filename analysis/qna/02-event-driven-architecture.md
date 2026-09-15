# Q&A Discussion Script: Category 2 - Event-Driven Architecture

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Why Kafka instead of RabbitMQ?

**Spoken Answer:**
"We selected Apache Kafka (via Amazon MSK) over RabbitMQ because of three fundamental architectural requirements: durable replayability, horizontal partition scalability, and strict per-claim ordering.

RabbitMQ is a traditional message queue where messages are deleted from the broker as soon as a consumer acknowledges them. Kafka, by contrast, is a distributed immutable commit log. It retains events on disk for a configurable retention window (e.g., 7 days). If our Reporting Service or Notification Service goes down or introduces a regression, we can reset consumer group offsets and replay historical events from three days ago. With RabbitMQ, once a message is consumed, it is gone forever unless you build custom archiving.

Furthermore, Kafka handles massive write throughput by partitioning topics across brokers, allowing multiple consumer instances to read in parallel while guaranteeing strict chronological order per partition key."

**Technical Bullets:**
- Storage Model: Immutable append-only commit log vs Transient AMQP message queue.
- Replay Capability: Kafka allows offset rewind to replay past events; RabbitMQ lacks native replay.
- Throughput: Kafka handles 100K+ msg/sec with zero-copy sequential disk I/O; RabbitMQ degrades as queues grow large.
- Consumer Model: Pull-based consumer groups with client-tracked offsets vs broker-tracked push acknowledgments.

---

### Q2: Why Amazon MSK instead of EventBridge?

**Spoken Answer:**
"Amazon EventBridge is an excellent serverless event router for low-to-medium throughput SaaS integrations, but it is not suited as the core transaction backbone for eClaims.

First is throughput and latency: EventBridge has higher p99 latency (often 50-200ms) compared to Kafka's sub-10ms end-to-end publish-to-consume latency. 
Second is total event ordering: EventBridge does not guarantee strict FIFO ordering across related domain events. In auto insurance, strict chronological sequence is mandatory: `ClaimSubmitted` must be processed before `SurveyorAssigned`, which must be processed before `RepairCompleted`. 
Third is event replay and stream processing: EventBridge Archive has limited replay controls, whereas Kafka provides full stream processing semantics, consumer group offset resets, and integration with Kafka Streams / Flink for real-time fraud aggregation."

**Technical Bullets:**
- Latency: MSK <10ms latency vs EventBridge ~50-200ms.
- Ordering: MSK guarantees strict per-partition FIFO; EventBridge cannot guarantee FIFO ordering across event batches.
- Cost at Scale: EventBridge charges $1.00 per million events; at hundreds of millions of status updates, notifications, and audit records, MSK broker pricing is significantly more cost-effective.
- Replay: MSK supports deterministic offset rewind per consumer group.

---

### Q3: Why MSK instead of Confluent Cloud?

**Spoken Answer:**
"We chose Amazon MSK over Confluent Cloud primarily for network security compliance, AWS infrastructure co-location, and cloud billing consolidation.

Because YCompany processes insurance claims containing sensitive PII (driver licenses, police reports, and bank details), data sovereignty and network isolation are top priorities. Amazon MSK deploys directly inside our private AWS VPC subnets. All producer and consumer traffic traverses AWS PrivateLink and internal private VPC interfaces, never touching the public internet.

While Confluent Cloud offers richer managed tooling (like ksqlDB), it operates in Confluent-owned VPCs requiring complex VPC peering, cross-account Transit Gateways, and separate vendor procurement. Amazon MSK integrates seamlessly with AWS IAM auth, AWS KMS encryption, CloudWatch monitoring, and counts toward YCompany's existing enterprise AWS discount commitment."

**Technical Bullets:**
- Network Security: Direct private ENI attachment inside YCompany VPC subnets; no public egress.
- IAM Authentication: Native AWS IAM SASL/SCRAM authentication for producers and consumers.
- KMS Integration: Automated envelope encryption at rest using AWS KMS customer managed keys.
- Cost & Governance: Single AWS bill, eligible for AWS Enterprise Discount Program (EDP).

---

### Q4: What topics would exist?

**Spoken Answer:**
"We organize topics cleanly around core business domains and lifecycle event classifications:

1. `claim-events`: Core lifecycle state changes (`ClaimCreated`, `ClaimAssigned`, `ClaimApproved`, `ClaimSettled`).
2. `workflow-events`: Workflow task allocations and SLA timer escalations (`SurveyorAssigned`, `EscalationTriggered`).
3. `repair-events`: Workshop progress updates (`VehicleDroppedOff`, `EstimateSubmitted`, `RepairCompleted`).
4. `payment-events`: Financial transaction outcomes (`PaymentInitiated`, `PaymentSettled`, `DisbursementFailed`).
5. `document-events`: File ingestion and OCR status (`DocumentUploaded`, `TextractCompleted`, `DocumentQuarantined`).
6. `notification-events`: Fan-out dispatch queues (`SendSMS`, `SendEmail`, `PushMobileAlert`).
7. `audit-events`: Immutable compliance tracking records destined for S3 WORM storage.
8. `dlq-events`: Dead Letter Queue for poison pill payloads and exhausted retries across all domains."

**Technical Bullets:**
- Domain Alignment: 1 primary topic per bounded context aggregate.
- Retention: 7 days for business events (`claim-events`), 14 days for `audit-events`, 3 days for `notification-events`.
- Cleanup Policy: `delete` for standard queues; `compact` for reference state topics.

---

### Q5: How would you choose the number of partitions?

**Spoken Answer:**
"We calculate the number of partitions using the formula: **Number of Partitions = Max(Target Throughput / Producer Throughput, Target Throughput / Consumer Throughput)**, balanced against consumer concurrency requirements.

In our peak capacity model, our platform handles 250 write TPS across all services, with storm surges reaching bursts of up to 1,000 events/second during catastrophic weather events. A single Spring Boot consumer container can comfortably process ~40-50 business events per second (including database writes and validation). 

To support 1,000 events/sec without lag, we need at least 20-25 parallel consumer tasks. Since a single Kafka partition can only be read by one consumer instance in a group at any time, we provision **32 partitions** for our primary topics (`claim-events`, `repair-events`). 

32 partitions evenly distributes load across our 6-broker MSK cluster (roughly 5-6 leader partitions per broker), gives us headroom to scale consumer worker tasks from 4 baseline instances up to 32 instances during surges, and aligns with powers of 2 for clean hashing."

**Technical Bullets:**
- Cluster Sizing: 6 brokers (3 AZs, 2 brokers per AZ) with `replication.factor = 3`.
- Partition Count: 32 partitions per core topic.
- Scaling Room: Allows auto-scaling consumer groups from 4 pods up to 32 pods without partition rebalancing bottlenecks.

---

### Q6: What would your Kafka partition key be for claim events?

**Spoken Answer:**
"Our Kafka partition key for all claim-related topics is the **`claimId` (UUID or business identifier like `CLM-2026-004812`)**.

Kafka uses the formula `MurmurHash2(partitionKey) % numPartitions` to determine the destination partition. By using `claimId` as the partition key, we guarantee that every single event related to that specific claim - from FNOL creation to surveyor assignment, repair estimate, adjuster approval, and payment settlement - lands on the exact same Kafka partition."

**Technical Bullets:**
- Key: `claimId.toString()`.
- Hashing: Default Kafka MurmurHash2 partitioner.
- Guarantee: Strict FIFO ordering for all events belonging to that specific claim.

---

### Q7: Would claimId be a good partition key? Why?

**Spoken Answer:**
"Yes, `claimId` is the optimal partition key for this platform, and here is why:

First, it has **high cardinality**. Over a year, we process millions of distinct claims. High cardinality guarantees a completely uniform distribution of messages across all 32 partitions, preventing 'hot partition' skew. 

If we made the mistake of partitioning by `region` (e.g., East, Midwest, West), a major hailstorm in Texas would overwhelm the Central partition broker with 80% of all traffic while other brokers sat idle.

Second, `claimId` matches the boundary of our **Domain Aggregate Root**. In business logic, events for Claim A have zero dependency on Claim B. But events for Claim A *must* be processed in exact order. Partitioning by `claimId` satisfies both business ordering constraints and physical load balancing."

**Technical Bullets:**
- Cardinality: Millions of unique keys prevents hotspotting.
- Anti-Pattern: Partitioning by `region` or `status` causes extreme broker load skew.
- Domain Fit: Matches the Claim aggregate root consistency boundary.

---

### Q8: How do you preserve ordering of events belonging to one claim?

**Spoken Answer:**
"We preserve ordering through a three-part configuration:

1. Partition Keying: Every event for a claim uses `claimId` as the key, ensuring they are serialized to the exact same partition log.
2. Producer In-Flight Settings: We set `max.in.flight.requests.per.connection = 5` with `enable.idempotence = true` in our Spring Kafka producer. In modern Kafka (v2.4+), the idempotent producer assigns internal sequence numbers to message batches, ensuring that even if network retries occur, the broker writes batches to disk in exact original sequence without reordering or duplication.
3. Single Consumer Threading: Within a consumer group, a partition is assigned to exactly one consumer thread. The consumer processes events sequentially before advancing the partition offset."

**Technical Bullets:**
- Producer Config: `enable.idempotence=true`, `acks=all`, `max.in.flight.requests.per.connection=5`.
- Broker Guarantee: Strict FIFO per partition log segment.
- Consumer Guarantee: Single-threaded consumption per assigned partition.

---

### Q9: What happens if a consumer fails after processing an event but before committing the offset?

**Spoken Answer:**
"When a consumer crashes after executing business logic (like writing to the database) but before sending its offset commit to Kafka, a **duplicate event delivery** will occur upon recovery.

Here is what happens:
1. The consumer process dies or loses its heartbeat.
2. Kafka detects the timeout and triggers a consumer group rebalance.
3. The uncommitted partition is reassigned to another surviving consumer instance.
4. The new consumer begins reading from the last *committed* offset, meaning it receives the exact same event a second time.

Because this is standard distributed systems behavior, our architecture makes every consumer **idempotent**. 
When the new consumer receives the duplicate event, it checks Redis using `SETNX` on `eventId` or hits a unique constraint in PostgreSQL. It detects that the event was already processed, skips the business execution, commits the offset to Kafka, and moves forward safely."

**Technical Bullets:**
- Failure Mode: At-least-once delivery semantics cause replay of uncommitted messages.
- Offset Management: `enable.auto.commit = false`; manual offset commit after local transaction commit.
- Mitigation: Atomic idempotency table or Redis check prevents double-processing.

---

### Q10: How do you prevent duplicate notifications?

**Spoken Answer:**
"Duplicate notifications infuriate customers. If a driver receives five identical SMS messages saying 'Your claim is approved', they call customer support in panic.

We prevent duplicate notifications at the Notification Service consumer using a two-tier deduplication check:
1. In-Memory Distributed Cache Check: Before calling Twilio or Amazon SES, the notification worker executes an atomic Redis command: `SET notification_lock:{claimId}:{eventType}:{recipient} "SENT" NX EX 86400`.
   If the key already exists, Redis returns false, and the worker drops the duplicate message immediately.
2. Authoritative Database Deduplication: In the Notification Service PostgreSQL database, we maintain a `notification_logs` table with a composite unique constraint: `UNIQUE (event_id, channel, recipient)`. 
Even during network partitions where Redis is momentarily unavailable, the database unique constraint rejects duplicate inserts and halts downstream email/SMS API calls."

**Technical Bullets:**
- Redis Lock: `SETNX` with 24-hour TTL keyed on `eventId + channel`.
- DB Constraint: `UNIQUE(event_id, channel, recipient)`.
- Third-Party Idempotency: Pass the `eventId` to Twilio/SendGrid idempotency headers if supported.

---

### Q11: How do you implement retries?

**Spoken Answer:**
"We implement a non-blocking **Retry Topic Pattern** using Spring Kafka, rather than blocking the main consumer thread with local sleep loops.

If a consumer encounters a transient failure (such as a database connection timeout or third-party API 503):
1. The message is published to a retry topic: `claim-events-retry-5m` with a retry count header incremented.
2. The consumer immediately commits the offset on the primary `claim-events` topic and moves to the next message. This prevents a single failing message from blocking the entire partition for other claims.
3. A dedicated retry consumer reads `claim-events-retry-5m` with an exponential backoff policy: 3 retries spaced at 1 minute, 5 minutes, and 15 minutes.
4. If all retries fail, the message is routed to the Dead Letter Queue (`claim-events-dlq`) for engineering inspection."

**Technical Bullets:**
- Pattern: Non-blocking Retry Topics (`<topic>-retry-1`, `<topic>-retry-2`, `<topic>-dlq`).
- Backoff: Exponential with jitter (initial interval 1000ms, multiplier 2.0, max 3 attempts).
- Avoidance: Never use `Thread.sleep()` inside Kafka listener; it triggers consumer group rebalances.

---

### Q12: When would you send something to a DLQ?

**Spoken Answer:**
"We send messages to the Dead Letter Queue (DLQ) under two distinct conditions:

1. Unrecoverable Poison Pill Payloads: 
If a message cannot be deserialized, has a corrupted JSON/Avro schema, or violates core business invariants (e.g., negative repair cost or invalid UUID format), retrying it will never succeed. It is immediately routed to the DLQ on the first attempt with an error description header.

2. Exhausted Retry Attempts:
If a message experiences transient infrastructure failures (e.g., Stripe API down or external workshop webhook unreachable) and fails across all configured exponential retry attempts, it is moved to the DLQ.

Every DLQ arrival emits a high-priority CloudWatch alarm to PagerDuty. Engineers can inspect the payload in the DLQ UI, fix the underlying issue, and execute an automated re-drive script."

**Technical Bullets:**
- Poison Pill: `DeserializationException`, Schema mismatch, validation failure -> Immediate DLQ.
- Exhausted Retries: Max attempts reached -> Routed to `<topic>-dlq`.
- Alerting: CloudWatch metric filter on `dlq-events` publish count > 0 triggers PagerDuty.

---

### Q13: How would you replay historical events?

**Spoken Answer:**
"Replaying historical events in Kafka is a routine operational procedure achieved by resetting the consumer group's offset.

Suppose we deploy a new fraud scoring model in our Fraud Service, or our Reporting Service had a bug and needs to recalculate metrics for the past 72 hours.
1. We stop the target consumer group (`reporting-service-group`).
2. Using the Kafka CLI or MSK admin API, we execute an offset reset command:
   `kafka-consumer-groups.sh --bootstrap-server <msk-endpoint> --group reporting-service-group --topic claim-events --reset-offsets --to-datetime 2026-09-12T00:00:00.000 --execute`.
3. We restart the Reporting Service consumer tasks.
4. The service begins reading from three days ago at full network speed, reprocessing events and repopulating its reporting tables.

Because other consumer groups track their own offsets independently, this replay operation has **zero impact** on live claims processing or customer notifications."

**Technical Bullets:**
- Mechanism: Consumer group offset manipulation (`--to-offset`, `--to-datetime`, or `--to-earliest`).
- Consumer Group Isolation: Each microservice has a distinct `group.id`; resetting one does not affect others.
- Prerequisite: Topics must be configured with adequate retention (`log.retention.hours = 168` for 7 days).

---

### Q14: How do you handle schema evolution?

**Spoken Answer:**
"In an event-driven architecture, services evolve at different speeds. If the Claims Service adds a new field `weatherConditions` to `ClaimCreatedEvent`, older consumers must not crash.

We handle schema evolution using **Apache Avro serialized with an AWS Glue Schema Registry (or Confluent Schema Registry)** and enforce **FULL Compatibility** (both backward and forward compatible).

Under FULL compatibility rules:
1. New fields must always have default values (e.g., `null` or a default string).
2. Existing fields can never be deleted; they can only be deprecated.
3. Field data types can never be changed.

Before any service can merge code into the CI/CD pipeline, the build step validates the new Avro schema against the Schema Registry. If the schema violates compatibility, the build fails before code ever deploys."

**Technical Bullets:**
- Serialization: Apache Avro with AWS Glue Schema Registry.
- Compatibility Mode: `FULL` compatibility (supports both backward and forward evolution).
- CI/CD Enforcement: Gradle/Maven Avro plugin fails the build on incompatible schema changes.

---

### Q15: Why does your DAR mention a Schema Registry?

**Spoken Answer:**
"Our Decision Analysis & Resolution (DAR) specifies a Schema Registry because binary JSON payloads without schema contracts are a leading cause of silent production outages in event-driven systems.

Without a Schema Registry, Kafka treats payloads as dumb byte arrays. If an engineer renames a field from `claimId` to `claim_id` in a producer, Kafka accepts the message without complaint. But downstream in Notification Service, the Jackson JSON parser returns `null`, customer alerts stop firing, and the bug goes undetected until customers complain.

A Schema Registry acts as a central governance authority. Producers register their schema version, and messages include a compact 4-byte schema ID header instead of repetitive JSON keys, reducing Kafka network bandwidth by up to 60% while guaranteeing that bad data is rejected at compile/publish time."

**Technical Bullets:**
- Contract Enforcement: Enforces type safety and structural validation across microservices.
- Wire Efficiency: Avro eliminates JSON string key overhead, reducing payload size by 50-60%.
- Governance: Serves as the single source of truth for enterprise event contracts.

---

### Q16: How do you avoid breaking old consumers?

**Spoken Answer:**
"We avoid breaking old consumers through strict **Backward Compatibility rules and schema evolution discipline**:

1. Never remove or rename existing fields in an event schema.
2. If adding a new field, always provide a default value (e.g., `null` or `""`). When an old consumer reads the new payload, its Avro deserializer simply ignores the unknown field or maps it to default.
3. If a major business concept changes fundamentally, we do not mutate the existing event schema. Instead, we create a new topic version (e.g., `claim-events-v2`). The producer dual-publishes to both v1 and v2 for a deprecation grace period until all consumer squads migrate, after which v1 is retired."

**Technical Bullets:**
- Deserialization: Avro reader schema maps fields based on schema IDs, ignoring newly added unmapped fields.
- Migration Path: Dual-publishing for breaking semantic changes (`claim-events-v1` and `claim-events-v2`).
- Contract Testing: Pact or Spring Cloud Contract tests run in consumer CI pipelines to verify compatibility against producer contracts.

---

### Q17: What happens if Kafka consumer lag increases dramatically?

**Spoken Answer:**
"When consumer lag spikes dramatically, it means messages are arriving faster than consumer workers can process them, or a downstream dependency (like PostgreSQL) is bottlenecked.

Here is our automated recovery sequence:
1. Detection: CloudWatch alarms monitor `ConsumerLag` on Amazon MSK. If lag exceeds 10,000 messages or age-of-oldest-message exceeds 60 seconds, an alert fires.
2. Horizontal Auto-Scaling: AWS ECS Target Tracking auto-scaling triggers, scaling the consumer service tasks up to the maximum partition count (up to 32 tasks for 32 partitions).
3. If lag continues to climb despite max container scaling, it indicates a downstream bottleneck (e.g., database lock contention). In this case, the consumer switches to a **Bulk Processing Mode**, grouping incoming events into JDBC batch operations (`saveAll()` with batch size 50) to minimize database round-trips.
4. As a last resort, non-critical consumers (like Dashboards/BI) can temporarily shed non-essential calculations to prioritize real-time customer status queues."

**Technical Bullets:**
- Metric: `AWS/Kafka ConsumerLag` and Burrow / Prometheus lag exporter.
- Auto-scaling ceiling: Limited to 32 pods (1 pod per partition).
- DB Optimization: Shift from single-row inserts to JDBC multi-row batching (`rewriteBatchedStatements=true`).

---

### Q18: How do you monitor Kafka?

**Spoken Answer:**
"We monitor Amazon MSK across three layers: Broker health, Topic throughput, and Consumer group performance.

1. Broker Infrastructure Metrics (via CloudWatch & Prometheus MSK Open Monitoring):
- `CpuUtilization`: Must stay below 60%.
- `DiskSpaceUsedPercentage`: Sized with auto-expanding storage; alert at 75%.
- `UnderReplicatedPartitions`: Must be strictly ZERO. Any value > 0 indicates broker hardware or network failure.

2. Producer Metrics:
- `RecordSendRate` and `RecordQueueTimeMs`.
- `RecordErrorRate`: Must be zero.

3. Consumer Lag Metrics (The most critical business metric):
- `SumOffsetLag` and `MaxOffsetLag`: Alert if lag exceeds 5,000 messages.
- `EstimatedTimeLag`: How many seconds behind real-time the consumer is running.

All metrics feed into Datadog/CloudWatch operational dashboards with automated PagerDuty escalation policies."

**Technical Bullets:**
- Open Monitoring: Prometheus JMX Exporter enabled on MSK brokers.
- Key Alerts: `UnderReplicatedPartitions > 0` (P1 Critical), `ConsumerLag > 5000` (P2 High).
- Retention Monitoring: Ensure disk growth is predictable against 7-day retention curves.

---

### Q19: Does Kafka guarantee exactly-once processing?

**Spoken Answer:**
"Kafka provides an **Exactly-Once Semantics (EOS)** feature within a closed Kafka-to-Kafka stream processing topology (using `processing.guarantee = exactly_once_v2` in Kafka Streams).

However, in an end-to-end distributed system where microservices read from Kafka and write to external side-effects - like an Amazon Aurora relational database, an S3 bucket, or an external Stripe payment gateway - **Kafka alone CANNOT guarantee exactly-once processing end-to-end**.

If a consumer writes to PostgreSQL and then the network fails before Kafka records the offset commit, the message *will* be redelivered. Therefore, the honest architectural answer is: **Kafka guarantees at-least-once transport; exactly-once processing is achieved by combining Kafka with idempotent consumers at the application tier.**"

**Technical Bullets:**
- Scope: Kafka EOS only covers Kafka -> Kafka transactions (consume-transform-produce).
- End-to-End Reality: Edge side-effects (DB, SMS, Stripe) break broker-only EOS.
- Solution: At-least-once delivery + Idempotent consumer design = Effectively Exactly-Once.

---

### Q20: Would you actually require exactly-once semantics here?

**Spoken Answer:**
"No, we do not require heavyweight distributed exactly-once semantics across our entire pipeline. What we require is **effectively exactly-once business outcomes**.

For notifications: If an SMS event is delivered twice, our Redis deduplication lock drops the second alert. The customer experiences exactly-once notification.
For payments: If a deductible charge event is redelivered, our MemoryDB idempotency check detects the existing transaction reference and prevents a double charge. The customer experiences exactly-once billing.
For claim status transitions: Setting a claim state from `SURVEYED` to `ADJUDICATED` is naturally idempotent. Running `UPDATE claims SET status = 'ADJUDICATED'` twice produces the identical database state.

Enforcing distributed two-phase commits to achieve theoretical exactly-once processing would degrade our throughput by 70% and introduce extreme fragility. Effectively exactly-once via idempotent consumers gives us maximum performance and absolute financial correctness."

**Technical Bullets:**
- Business Requirement: Idempotent outcome, not distributed 2PC locks.
- Idempotency Techniques: Redis locks, database `ON CONFLICT DO NOTHING`, idempotent state machine transitions.
- Performance Advantage: 3x higher throughput compared to Kafka transactional producer overhead.
