# Q&A Discussion Script: Category 4 - Database Architecture

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Why PostgreSQL?

**Spoken Answer:**
"We chose PostgreSQL as our core relational database because insurance claims processing requires **rock-solid ACID transaction semantics, complex relational integrity, and advanced JSON/text query capabilities**.

An insurance claim is fundamentally relational: a claim is tied to a policy, involves multiple vehicles, produces multiple surveyor assessment items, links to third-party repair work orders, and requires line-item deductible calculations. Maintaining foreign key constraints and financial precision is non-negotiable.

Furthermore, PostgreSQL offers enterprise-grade capabilities:
- Native `JSONB` data types allow us to store semi-structured third-party workshop estimate line items and police incident reports with GIN index indexing.
- Declarative Table Partitioning by year and region allows us to scale historical claim data effortlessly.
- Rich ecosystem support, enterprise ANSI-SQL compliance, and zero licensing fees make it the gold standard for financial and insurance core engines."

**Technical Bullets:**
- ACID Guarantees: Strict consistency for financial settlement and claim liability state.
- Extensibility: JSONB for polymorphic external estimates, PostGIS for surveyor zip code spatial queries.
- Enterprise Features: Declarative partitioning, write-ahead log (WAL) streaming for CDC, robust indexing (B-Tree, BRIN, GIN).

---

### Q2: Why Aurora instead of standard RDS PostgreSQL?

**Spoken Answer:**
"While standard RDS PostgreSQL is good, **Amazon Aurora PostgreSQL provides three architectural game-changers** essential for our 200M policyholder scale:

1. Distributed Shared Storage Architecture: Aurora separates compute from storage. Its storage volume is distributed across 3 Availability Zones with 6-way replication. Storage auto-scales from 10GB up to 128TB automatically without downtime or disk re-provisioning.
2. Near-Zero Read Replica Lag: In standard RDS, read replicas rely on logical or WAL replication over the network, often suffering seconds of lag under write load. In Aurora, all compute nodes share the exact same underlying storage tier. Replica lag is typically **under 20 milliseconds**, making read replicas truly viable for near-real-time customer status reads.
3. Rapid Crash Recovery and Fast Failover: Aurora crashes recover in seconds because it plays back redo logs on distributed storage in parallel, and automated failover to a replica takes under 30 seconds compared to several minutes on standard RDS."

**Technical Bullets:**
- Replication: Shared distributed storage across 3 AZs (6 copies of data; 4 of 6 write quorum, 3 of 6 read quorum).
- Replication Lag: Typically <20ms (vs hundreds of milliseconds or seconds on RDS under heavy write load).
- Scaling: Up to 15 read replicas with independent auto-scaling.
- Global Database: Cross-region replication latency <1 second for disaster recovery.

---

### Q3: Why not DynamoDB for claims?

**Spoken Answer:**
"DynamoDB is an exceptional key-value and document store for predictable, single-key access patterns (like user sessions or IoT telemetry). However, **it is poorly suited as the primary system of record for insurance claims**.

Here is why:
1. Unpredictable Access Patterns: Insurance claims demand multi-dimensional queries. Adjusters filter by `status = 'SURVEYED' AND region = 'MIDWEST' AND damage_estimate > 5000 ORDER BY created_at ASC`. In DynamoDB, doing this requires defining dozens of Global Secondary Indexes (GSIs), which are expensive, write-amplified, and eventually consistent.
2. Financial Precision and Schema Constraints: In auto insurance, referential integrity (ensuring a payment cannot exist without an approved assessment, and an assessment cannot exist without an active policy) is enforced at the database layer. DynamoDB has no foreign keys, requiring fragile application-level validation.
3. Query Flexibility: Regulatory audits frequently require ad-hoc SQL joins across claims, adjusters, and payments that DynamoDB simply cannot execute without full table scans."

**Technical Bullets:**
- Access Pattern Mismatch: Ad-hoc multi-column filtering and sorting vs strict key-value lookup.
- Cost of GSIs: Provisioning multiple GSIs for search facets drastically increases write costs.
- ACID Across Aggregates: Lack of native multi-table constraints increases application complexity.

---

### Q4: What data belongs in DynamoDB?

**Spoken Answer:**
"While DynamoDB is not our primary claims ledger, it is the ideal choice for **high-throughput, predictable key-value and append-only workloads** within our ecosystem:

1. Customer Notification Preferences: Keyed on `customerId` -> `{ emailEnabled: true, smsPhone: "+1...", language: "en" }`. Simple, ultra-fast sub-5ms lookup.
2. Temporary File Upload Tickets: Short-lived metadata for pre-signed S3 upload handshakes keyed on `uploadTicketId` with native TTL expiration to clean up abandoned uploads automatically.
3. Real-Time WebSocket Connection State: Keyed on `connectionId` to track active client socket connections across our API Gateway fleet.
4. Fast Feature Flags and System Configurations: Low-latency global key-value reads."

**Technical Bullets:**
- Use Case: Key-value lookups with known partition keys.
- Native TTL: Automatic data eviction without cron cleanup jobs (e.g., upload tokens, websocket connections).
- Scale: Single-digit millisecond latency at any throughput scale.

---

### Q5: Why Redshift?

**Spoken Answer:**
"Amazon Redshift is our **dedicated Columnar Online Analytical Processing (OLAP) Data Warehouse**.

Insurance companies live on analytics: loss ratio calculations, claims aging matrices, regional fraud trends, and actuarial reserve forecasting. These queries scan tens of millions of historical rows and calculate aggregate sums and averages (e.g., `SUM(payout_amount) GROUP BY vehicle_make, geography, year`).

Row-oriented relational databases like PostgreSQL must read every single column of every row from disk into memory to calculate a column average. Redshift stores data column-by-column, uses massively parallel processing (MPP), and compresses data aggressively. A report query that takes 45 seconds and locks tables in PostgreSQL executes in 600 milliseconds on Redshift without consuming a single cycle of transactional database compute."

**Technical Bullets:**
- Storage: Columnar storage with massive data compression (ZSTD/AZ64).
- Architecture: MPP (Massively Parallel Processing) architecture across compute slices.
- Workload Isolation: Keeps heavy BI and C-suite reporting completely separate from customer transactional OLTP.

---

### Q6: Why shouldn't reporting queries run against Aurora?

**Spoken Answer:**
"Running heavy reporting queries against our primary Aurora OLTP database - even against read replicas - introduces **severe operational risks to customer experience**:

1. Buffer Pool Pollution: When an executive runs a monthly regional report scanning 5 million claim rows, PostgreSQL sweeps gigabytes of cold historical data into its shared buffer memory. This pushes out the hot, active claim records that adjusters and mobile users are actively modifying, causing subsequent customer API queries to hit slow physical disk.
2. Replica Lag Spikes: Long-running analytical queries hold read snapshots, preventing vacuuming and delaying the application of write replication streams, which causes replica lag to spike from 20ms to several seconds.
3. Compute Contention: Massive CPU consumption during aggregation slows down concurrent API status lookups."

**Technical Bullets:**
- Memory Impact: Large table scans evict hot cache pages from the PostgreSQL shared buffers pool.
- MVCC Degradation: Long-running read transactions block table vacuuming and inflate dead tuple bloat.
- Architectural Separation: Strictly follow CQRS - OLTP for transactional writes/reads; Redshift/QuickSight for analytical aggregations.

---

### Q7: Why Redis?

**Spoken Answer:**
"We utilize Amazon ElastiCache Redis as an **in-memory distributed cache and operational data grid** to achieve sub-millisecond response times and protect our database tier.

Redis serves three primary functions in our platform:
1. High-Speed Read Caching: Storing validated policy coverage snapshots, partner repair shop directories by zip code, and pre-calculated regional KPI summaries.
2. Distributed Concurrency Control: Providing distributed locks (via Redlock) to prevent cache stampedes and coordinate singleton workers.
3. Ephemeral Consumer Deduplication: Acting as our first line of defense for event deduplication using atomic `SETNX` operations."

**Technical Bullets:**
- Latency: Sub-millisecond reads (<1ms) directly from memory.
- Data Structures: Hashes for policy objects, Sets/Sorted Sets for zip-code proximity and leaderboards.
- High Availability: Multi-AZ Redis cluster with automated failover and replica nodes.

---

### Q8: What exactly are you caching?

**Spoken Answer:**
"We are highly selective about what we cache. We cache data that is **frequently read, expensive to compute, and tolerant of bounded staleness**:

1. Policy Coverage Snapshots (TTL: 15 minutes): Lookups against the external legacy Policy Administration System (PMS) are slow. We cache the customer's policy details upon initial login.
2. Certified Repair Workshop Directory (TTL: 1 hour): Lists of verified body shops and rental agencies organized by 3-digit and 5-digit zip codes.
3. Executive KPI Dashboard Aggregates (TTL: 5 minutes): Regional claim counts, triage queue totals, and average cycle times.
4. Active User Permissions & Role Mappings (TTL: 5 minutes): Keycloak authorization tokens and role assignments.
5. In-flight Event Deduplication Keys (TTL: 24 hours): Message UUIDs to ensure idempotent processing."

**Technical Bullets:**
- Policy Data: Key `policy:{policyNumber}`, TTL 15m.
- Workshop Directory: Key `workshops:zip:{zipPrefix}`, TTL 60m.
- Dashboard Summaries: Key `kpi:region:{regionId}`, TTL 5m.
- Deduplication: Key `event_dedup:{eventId}`, TTL 24h.

---

### Q9: How do you invalidate cached data?

**Spoken Answer:**
"We implement a hybrid caching strategy combining **Time-To-Live (TTL) expiration with Event-Driven Cache Invalidation (Cache-Aside Pattern)**:

1. Default Safety Net (TTL): Every cached key is assigned an explicit expiration time (e.g., 5m, 15m, 1h). Even in the event of an application crash or bug, stale data naturally purges.
2. Event-Driven Active Invalidation: When a state change occurs, the microservice publishing the event triggers an invalidation:
   - When a workshop updates its address or capacity, Workshop Service publishes `WorkshopUpdated`. The caching worker immediately executes `redisTemplate.delete('workshops:zip:' + zip)`.
   - When a customer updates their contact correspondence address, the profile service evicts `user:profile:{userId}`.
3. Write-Through for Critical Idempotency: Payment status updates write directly to both the database and the cache simultaneously."

**Technical Bullets:**
- Strategy: Cache-Aside (Lazy Loading) + Event-Driven Eviction.
- Resilience: Jittered TTLs (`base_ttl + random_jitter`) prevent synchronized mass expiration.
- Invalidation Channel: Dedicated Kafka consumer or Redis Pub/Sub for cross-cluster cache eviction.

---

### Q10: Why MemoryDB for payment idempotency?

**Spoken Answer:**
"Many architects mistakenly use standard Redis for payment idempotency keys, which is a major financial risk. Standard ElastiCache Redis is an in-memory cache that uses asynchronous replication; if a primary node crashes before replicating to its replica, recently written keys can be lost.

**Amazon MemoryDB for Redis is a Redis-compatible, durable, in-memory database built with a Multi-AZ transactional write-ahead log (WAL).**

When our Payment Service executes `SET payment_key:{idempotency_key} SUCCESS NX`, MemoryDB does not acknowledge the write until it is durably committed to the distributed multi-AZ transaction log across multiple AZs. 
This guarantees **zero data loss** and strict ACID durability with microsecond read latencies. If a customer hits 'Pay Deductible' twice during a cloud hardware failover, MemoryDB guarantees we will never lose the idempotency record and will never double-charge the policyholder."

**Technical Bullets:**
- Durability: Multi-AZ distributed transaction log (unlike standard Redis asynchronous replication).
- Compliance: Suitable for PCI-DSS financial transaction state tracking.
- Compatibility: 100% Redis API compatible with strict linearizable consistency for reads and writes.

---

### Q11: Why can't Aurora itself store idempotency keys?

**Spoken Answer:**
"Aurora *can* store idempotency keys, and we actually use a relational unique constraint as our secondary backstop. However, using Aurora alone as the primary high-throughput idempotency gatekeeper creates two major problems:

1. Connection Contention and Row Locking: Under high concurrency (like storm surges), having thousands of incoming requests execute `INSERT INTO idempotency_keys` creates severe row lock contention, index leaf splits, and connection pool saturation on the primary database writer.
2. Latency Overhead: An Aurora relational transaction requires a full database round-trip (10-30ms) including buffer management and WAL flushing. MemoryDB checks and claims keys atomically in **sub-millisecond memory time (<1ms)**.

By placing MemoryDB in front of Aurora, 100% of duplicate requests are rejected at the memory tier in under 1ms before they ever consume a database connection or execute an expensive SQL query."

**Technical Bullets:**
- Latency: MemoryDB <1ms vs Aurora SQL transaction 15-30ms.
- Throughput: MemoryDB handles 100K+ ops/sec per node without locking relational database rows.
- Two-Tier Protection: MemoryDB provides fast distributed rejection; Aurora unique constraint provides final safety.

---

### Q12: Do your microservices have independent databases?

**Spoken Answer:**
"In our target production architecture, our microservices have **strictly decoupled data ownership**. However, we apply an architectural pattern called **Schema-Per-Service on a shared multi-tenant Aurora cluster** for our initial production release, evolving toward physical database instances as scale dictates.

Logical independence is complete and absolute:
- Claims Service owns the `claims` schema.
- Workshop Service owns the `workshops` schema.
- Payment Service owns the `payments` schema.
- Document Service owns the `documents` schema.

Each microservice has its own dedicated database user credentials. The `claims_user` has zero permissions to query or read tables in the `workshops` schema. No service can cross schema boundaries via SQL joins. All cross-domain data exchange must occur via REST APIs or Kafka events."

**Technical Bullets:**
- Pattern: Schema-Per-Service (Logical database-per-service).
- Access Control: PostgreSQL RBAC (`GRANT` / `REVOKE`) enforces isolation; no cross-schema SQL grants.
- Cost Efficiency: Avoids paying for 8 separate idle multi-AZ database clusters while maintaining 100% data boundary hygiene.

---

### Q13: Your database design appears to have schemas in one PostgreSQL instance. Is that really database-per-service?

**Spoken Answer:**
"Yes, it is the classic **Logical Database-Per-Service** pattern, and from an architectural and DDD standpoint, it strictly enforces all data encapsulation requirements.

The core rule of Database-per-Service is **encapsulation of data ownership**: no other service may directly read or write your private tables. In our setup, this is enforced by PostgreSQL user permissions and security policies. The Claims Service application credentials physically cannot execute a query against `workshops.work_orders`.

Running separate logical schemas on a shared Aurora cluster is standard practice for early-stage enterprise architectures. It provides:
1. Drastic infrastructure cost savings (one high-spec Aurora cluster instead of 8 under-utilized clusters costing $15,000/month).
2. Centralized backup, point-in-time recovery, and multi-region disaster recovery management.
3. A trivial migration path: because there are zero cross-schema foreign keys or joins, moving any schema (e.g., `payments`) to its own physical Aurora cluster later is a simple pg_dump/restore or AWS DMS migration with zero application code changes."

**Technical Bullets:**
- Governance: Zero cross-schema foreign keys, zero cross-schema joins.
- Cost: Single 3-node Aurora cluster vs 8 separate clusters saves ~70% infrastructure cost.
- Portability: Clean boundaries ensure easy physical extraction whenever a single service's IOPS demand it.

---

### Q14: How would you evolve toward stronger service data ownership?

**Spoken Answer:**
"We follow a clear, three-phase evolutionary roadmap:

Phase 1 (Current Target): **Logical Isolation (Schema-per-service)** on a shared Aurora cluster with strict PostgreSQL user grants and separate Liquibase migration pipelines per service.

Phase 2 (Selective Physical Extraction): As specific services experience outsized write volumes - specifically the `Claims Service` and `Payment Service` - we extract those schemas into dedicated Amazon Aurora clusters using AWS Database Migration Service (AWS DMS) with CDC. This gives them dedicated CPU, dedicated buffer pools, and independent scaling limits.

Phase 3 (Polyglot Persistence): Moving specialized workloads to purpose-built data engines: e.g., moving surveyor spatial coverage to a PostGIS-optimized instance, moving full-text claims search to Amazon OpenSearch, and archiving old audit records to Amazon S3 Glacier."

**Technical Bullets:**
- Extraction Tool: AWS Database Migration Service (DMS) with continuous replication for zero-downtime cutover.
- Trigger: When a single service's write IOPS consume >40% of the shared cluster's IOPS capacity.
- DevOps: Each service already maintains independent Liquibase / Flyway migration directories in Git.

---

### Q15: Can Workshop Service directly query Claims tables?

**Spoken Answer:**
"**No, absolutely not.** That is an architectural violation that is strictly prohibited in our design and blocked at the database engine level.

If Workshop Service were allowed to run `SELECT * FROM claims.claims WHERE id = ?`, three severe problems emerge:
1. Tight Coupling: Any refactoring of the Claims database schema immediately breaks the Workshop Service.
2. Loss of Business Invariants: The Claims Service cannot enforce validation rules, status transitions, or audit logging if another service bypasses its application layer.
3. Database Security Breach: The Workshop database role would gain access to sensitive policyholder PII that third-party workshops have no legal right to see.

Instead, Workshop Service obtains claim context via **Event-Carried State Transfer**: when a claim is assigned to a shop, the `ClaimAssignedEvent` published to Kafka contains the necessary read-only snapshot (claimId, vehicleMake, damageDescription). For live queries, Workshop Service calls the Claims Service REST API."

**Technical Bullets:**
- Database Security: `REVOKE ALL ON SCHEMA claims FROM workshop_user;`.
- Integration Pattern: Asynchronous Event-Carried State Transfer + Synchronous REST API facade.
- Compliance: Prevents unauthorized exposure of customer PII to external repair shops.

---

### Q16: How does Reporting Service obtain information belonging to several services?

**Spoken Answer:**
"Reporting Service obtains cross-service data through **Asynchronous Event Ingestion and Read-Model Materialization (CQRS)**, never through distributed synchronous queries.

Here is the exact mechanism:
1. Event Consumption: The Reporting Service runs Kafka consumer workers listening to `claim-events`, `repair-events`, `workflow-events`, and `payment-events`.
2. Projection into Reporting Tables: As events arrive, the Reporting Service updates its own denormalized read-model tables in the `reporting` schema (e.g., `reporting.claims_summary`, `reporting.turnaround_metrics`).
3. Scheduled Batch Synchronization: For historical data reconciliation, an hourly AWS Glue ETL job extracts sanitized CDC records from each service's read replica and loads them into Amazon Redshift.
4. C-Suite Dashboards: Executive dashboards (Amazon QuickSight) query Redshift or the denormalized reporting tables, achieving sub-second response times without touching live operational databases."

**Technical Bullets:**
- Pattern: CQRS (Command Query Responsibility Segregation) with Materialized Projections.
- Real-Time Layer: Kafka consumers maintain streaming aggregation tables.
- Batch Layer: AWS Glue ETL streams snapshots to Amazon Redshift for long-term analytical trends.

---

### Q17: What happens when Claims DB succeeds but Workflow DB fails?

**Spoken Answer:**
"This scenario cannot cause data corruption or orphaned claims because **our transactional boundary is completely decoupled via the Transactional Outbox Pattern**.

Here is what occurs:
1. When a claim is created, the Claims Service writes the claim and writes an outbox event in the same atomic database transaction. The Claims DB transaction commits.
2. The event is published to Kafka.
3. The Workflow Service consumes the event to start the Camunda workflow. Suppose the Workflow database crashes at that exact moment.
4. The Workflow Service consumer fails to process the message and does NOT commit the Kafka offset.
5. Kafka's message remains buffered. The Workflow consumer retries with exponential backoff.
6. Once the Workflow database recovers, the consumer retries the message, successfully writes the workflow state, and commits the offset.
The two databases never participate in a shared, fragile lock; consistency is guaranteed through at-least-once event delivery and idempotent retry."

**Technical Bullets:**
- Decoupling: Zero distributed transactions; each database commits locally.
- In-Flight Buffer: Kafka brokers retain uncommitted events safely on disk.
- Self-Healing: Workflow service resumes consumption immediately upon DB recovery.

---

### Q18: How do you handle concurrent updates to a claim?

**Spoken Answer:**
"In an insurance platform, concurrent updates are common: a customer might be uploading a police report from their phone at the exact moment a claims adjuster is updating the damage estimate from the web portal.

We handle concurrent updates using **Optimistic Concurrency Control (OCC) with Database Row Versioning**:

Every mutable entity (like `Claim`) has a `@Version private Long version;` column managed by JPA/Hibernate.
1. When User A and User B open the claim, both read `version = 5`.
2. User A submits an update. The SQL query executes:
   `UPDATE claims SET status = 'SURVEYED', version = 6 WHERE id = :id AND version = 5;`
3. The update succeeds, and the row version in the database becomes 6.
4. User B then submits their update with the stale version 5. The SQL update matches 0 rows.
5. Hibernate detects 0 updated rows and throws an `OptimisticLockException`.
6. Our Spring controller catches this and returns an **HTTP 409 Conflict** with an informative message: *'This claim was modified by another user. Please refresh and review latest updates.'*"

**Technical Bullets:**
- Mechanism: `@Version` column mapped to integer versioning in PostgreSQL.
- SQL Enforcement: Atomic `WHERE id = ? AND version = ?`.
- API Contract: HTTP 409 Conflict returned to client with latest entity payload.

---

### Q19: Optimistic versus pessimistic locking - which would you use?

**Spoken Answer:**
"We use **Optimistic Locking for 95% of the platform**, and reserve **Pessimistic Locking strictly for high-risk financial balance updates**.

Why Optimistic Locking for Claims and Workflows:
Insurance claim interactions involve human think-time. An adjuster might open a claim and spend 15 minutes reviewing damage photos. If we used pessimistic locking (`SELECT FOR UPDATE`), the adjuster's browser would hold an open database row lock for 15 minutes, blocking all customer status checks, automated Kafka workers, and manager reviews. Optimistic locking provides high throughput and never holds database locks during human idle time.

Where we use Pessimistic Locking:
In the **Payment Service**, when capturing deductible balances or updating a workshop payout ledger where two concurrent automated financial triggers could attempt to double-disburse funds. Here, a short millisecond-level pessimistic lock (`SELECT ... FOR UPDATE NOWAIT`) guarantees absolute balance isolation."

**Technical Bullets:**
- Optimistic: Best for human-driven, read-heavy, long-duration workflows (Claim adjudication, repair notes).
- Pessimistic: Best for high-contention, automated, sub-second financial writes (Payment ledger balance deductions).
- Golden Rule: Never hold pessimistic locks across network calls or human wait states.

---

### Q20: What indexes are most important?

**Spoken Answer:**
"We have designed targeted B-Tree, Composite, and Partial indexes aligned with our primary query access patterns:

1. Primary Keys: Standard B-Tree unique indexes on all entity UUIDs (`claim_id`, `work_order_id`, `payment_id`).
2. High-Frequency Lookups:
   - `claims.claims (policy_number)`: For instant customer claim history lookups.
   - `claims.claims (customer_id, created_at DESC)`: For rendering customer mobile dashboard timelines.
3. Queue Filtering Composite Indexes:
   - `claims.claims (status, assigned_surveyor_id)`: Powers the surveyor's active assessment worklist.
   - `claims.claims (status, region, created_at)`: Powers regional triage dashboards and SLA monitors.
4. Partial Indexes (Crucial for Performance):
   - `CREATE INDEX idx_open_claims ON claims.claims (region, created_at) WHERE status NOT IN ('SETTLED', 'REJECTED');`
   Only 5% of our 60 million claims are active at any time. A partial index indexes *only* open claims, keeping the index size tiny (a few megabytes) so it fits entirely in RAM and executes in under 2ms.
5. Partition-Key Indexes: Indexes locally aligned on each partitioned year table (`claims_2026`)."

**Technical Bullets:**
- Composite Index: `(status, region, created_at)` eliminates filesorts on triage queues.
- Partial Index: `WHERE status NOT IN ('SETTLED', 'CLOSED')` reduces index size by 90%.
- Foreign Keys: Indexed to prevent table locks during cascade operations.
- GIN Index: `USING GIN (additional_attributes)` for JSONB fields.
