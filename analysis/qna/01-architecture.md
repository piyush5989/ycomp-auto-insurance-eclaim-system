# Q&A Discussion Script: Category 1 - Architecture

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Walk me through your eClaims architecture end-to-end in 3-5 minutes.

**Spoken Answer:**
"Let me walk you through the end-to-end request lifecycle from the edge to persistence.

When a customer or workshop user opens the app, their DNS resolves via Amazon Route 53 with latency-based Anycast routing. CloudFront terminates TLS 1.3 at the nearest edge PoP, caching our React single-page app and static assets. AWS WAF inspects every request at the perimeter, enforcing rate limits and OWASP Top 10 rule groups before traffic touches our Application Load Balancer.

At the gateway tier, Amazon API Gateway acts as the gatekeeper. It validates incoming JWT tokens. For 200 million policyholders, identity is managed by AWS Cognito for elastic consumer scale and MFA. For internal claims adjusters, case managers, and partner workshops, tokens come from our clustered Keycloak deployment, which integrates with enterprise Active Directory and provides fine-grained UMA 2.0 role permissions.

Once authenticated, requests enter our private application subnets hosting our microservices running on AWS ECS Fargate with Spring Boot 3.2 on Java 21. Java 21 Virtual Threads give us massive concurrency for I/O operations without thread pool starvation.

The core services are organized around bounded contexts: Claims Service handles FNOL intake and status state machines; Workflow Service integrates with Camunda 8 for business process orchestration and SLA escalations; Document Service issues pre-signed S3 URLs so multi-megabyte accident photos never traverse our application memory; Workshop Service tracks repair work orders; Payment Service integrates with Stripe for electronic deductibles and shop disbursements; and Notification Service delivers multi-channel SMS and email.

For state persistence, we use Amazon Aurora PostgreSQL Multi-AZ with dedicated read replicas for CQRS query separation. When a state change occurs, like claim approval, the service writes to PostgreSQL and emits an event via the Transactional Outbox pattern into Amazon MSK (managed Apache Kafka). Downstream consumers pick up these events asynchronously to trigger automated surveyor assignment, update customer WebSocket progress bars in real time, and stream tamper-proof audit records into S3 Object Lock storage with a 7-year WORM compliance guarantee.

In short: synchronous REST for immediate client-facing writes, asynchronous Kafka event streaming for downstream side-effects, and strict separation between write transactions and analytical reads."

**Technical Bullets:**
- Edge: Route 53 (Anycast, 60s failover) + CloudFront + WAF + ALB.
- Identity: Dual IdP - Cognito (200M policyholders) + Keycloak 24 (internal staff/partners).
- App Tier: Spring Boot 3.2, Java 21 Virtual Threads, AWS ECS Fargate tasks across 3 AZs.
- Orchestration: Camunda 8 SaaS / self-hosted engine for long-running BPMN workflows.
- Persistence: Amazon Aurora PostgreSQL (1 Primary Writer + 3 Read Replicas), ElastiCache Redis, S3 Object Lock.
- Messaging: Amazon MSK (Apache Kafka, 32 partitions per core topic, RF=3).

---

### Q2: Why did you choose microservices instead of a modular monolith?

**Spoken Answer:**
"We chose microservices for the target architecture because of three enterprise operational realities: independent team scaling, divergent deployment cycles, and heterogeneous runtime characteristics.

In this platform, the Claims Service has 99.99% availability requirements and handles bursty, customer-facing FNOL write loads during storm surges. Document Service, on the other hand, is heavy on network I/O and S3 pre-signed URL generation. Reporting Service runs complex analytical aggregations and batch exports. 

If all of these modules remain in a single modular monolith, an unoptimized reporting query or an out-of-memory error caused by PDF document generation crashes the entire container, bringing down FNOL intake for 200 million customers. Furthermore, our workshop integrations change on third-party partner release schedules, while claim adjudication logic changes on regulatory compliance cycles. Microservices allow independent CI/CD pipelines, independent auto-scaling boundaries, and fault isolation."

**Technical Bullets:**
- Fault Isolation: A crash in report generation or notification retries cannot kill claim submission.
- Independent Scaling: Scale Notification Service to 30 tasks during a blizzard while keeping Payment Service at 4 tasks.
- Team Topologies: 3-4 separate engineering squads work without merge locks or synchronized release trains.
- Tech Diversity: Document Service can leverage Python/Node OCR pipelines, while Claims core runs high-throughput Java.

---

### Q3: Your DAR says the POC is a modular monolith. Why not simply scale that horizontally?

**Spoken Answer:**
"Horizontal scaling of a modular monolith is exactly what we did for Phase 0 and Phase 1 to validate business fit with low operational overhead. But horizontal scaling of a monolith hits a hard ceiling when resource profiles diverge.

In a monolith, every single instance must run all modules. When our Document and Reporting modules consume 4GB of heap memory for batch processing, we must scale the *entire* 4GB container horizontally across twenty instances, even if only the FNOL intake endpoint is experiencing high traffic. That leads to severe cloud resource waste.

More critically, in a horizontally scaled monolith, every instance opens its own connection pool to the single shared PostgreSQL database. If you scale to 50 monolith instances, each configured with 20 HikariCP connections, you are pounding the primary database with 1,000 active connections. Microservices allow us to scale only the I/O-bound or CPU-bound services while keeping database connection pools lean and isolated."

**Technical Bullets:**
- Resource Inefficiency: Scaling a 4GB monolith image 20x costs 3x more than scaling a 512MB lightweight Claims task.
- Connection Starvation: 50 monolith nodes * 20 Hikari connections = 1,000 connections, exhausting Postgres max_connections.
- Blast Radius: One thread pool exhaustion event in monolith halts all 50 use cases across all 4 user groups.

---

### Q4: How did you identify your service boundaries / bounded contexts?

**Spoken Answer:**
"We applied Domain-Driven Design (DDD) strategic design principles, analyzing the ubiquitous language, business capability ownership, and lifecycle state changes across the insurance value chain.

We identified five core bounded contexts and three supporting contexts:
1. Claims Intake and Adjudication: Owns the Claim aggregate root, FNOL validation, and authoritative lifecycle state.
2. Workforce and Incident Management: Owns surveyor assignment, adjuster work queues, and managerial delegations.
3. Workshop and Repairs: Owns the vehicle work order, repair milestones, and body shop estimates.
4. Electronic Payments: Owns deductible billing calculation, Stripe payment tokens, and shop disbursement records.
5. Document Management: Owns evidence metadata, S3 pre-signed upload tickets, and compliance retention tags.
6. Notifications: Supporting context for multi-channel message dispatch.
7. Reporting and Analytics: Supporting context for read-only aggregation and KPI calculations.
8. Identity / Auth: Supporting context separating external customer credentials from enterprise internal RBAC.

Each boundary is defined by distinct aggregates, independent transaction lifecycles, and minimal cross-context synchronous coupling."

**Technical Bullets:**
- Method: Event Storming workshops + Context Mapping (Shared Kernel vs Customer/Supplier vs Anti-Corruption Layer).
- Key Test: Does a change in repair workshop rate structures require modifying the Claim aggregate? No, so Workshop is an independent bounded context.

---

### Q5: Why do you need eight services? Could this solution work with four or five?

**Spoken Answer:**
"Yes, this system could technically run on four or five services in Phase 1, and we explicitly designed our target architecture to support a pragmatic migration path.

If we consolidated to five services, we would combine:
1. Claims + Workforce into a single Claims Core service.
2. Workshop + Rental into a Partner Service.
3. Payment as a standalone service due to PCI-DSS compliance isolation.
4. Document as a standalone service due to S3 binary I/O patterns.
5. Notification + Reporting into an Async Operations service.

However, we target eight microservices because of compliance and organizational governance:
- Payment must be strictly isolated to minimize PCI-DSS audit scope.
- Document management must comply with strict state insurance WORM data retention rules.
- Workforce assignment contains complex regional business rules and Camunda workflows that iterate faster than core claims accounting.
Starting with cleanly decoupled modules in code allows us to run them as 4-5 deployment units initially and split into 8 when team size and traffic dictate."

**Technical Bullets:**
- 5-Service Consolidation: ClaimsCore, PartnerService, PaymentService (PCI), DocumentService, OperationsService.
- Why 8 is better at scale: PCI boundary isolation, WORM compliance isolation, independent team ownership per squad.

---

### Q6: Which services would you extract from the monolith first, and why?

**Spoken Answer:**
"We follow the Strangler Fig pattern, extracting services based on business value, blast radius, and resource intensity.

Our extraction order is:
1. Notification Service first: It is completely asynchronous, has zero incoming synchronous dependencies, and immediately removes slow external email/SMS API calls from the monolith.
2. Document Service second: It offloads heavy multipart photo uploads and S3 bandwidth from monolith heap memory to pre-signed S3 URLs.
3. Payment Service third: Extracting payments isolates PCI-DSS compliance boundaries and enables dedicated MemoryDB idempotency caching.
4. Workshop Service fourth: Decouples external third-party partner portals and body shop APIs from internal systems.
5. Claims and Workflow last: This is the transactional core holding the central state machine; extracting it last ensures all supporting asynchronous pipelines are already battle-tested."

**Technical Bullets:**
- Phase 1: Notification (zero incoming sync calls, high latency variability).
- Phase 2: Document (memory/bandwidth relief via S3 pre-signed URLs).
- Phase 3: Payment (PCI-DSS compliance scope reduction).
- Phase 4: Workshop & Repair (external partner integration boundary).
- Phase 5: Core Claims + Camunda Orchestration.

---

### Q7: What communication is synchronous versus asynchronous?

**Spoken Answer:**
"We adhere to a strict rule: **Synchronous for queries and client command handshakes; Asynchronous for all state transitions, notifications, and cross-service side-effects.**

Synchronous (REST over HTTPS / TLS 1.3):
- Customer submitting FNOL: The client needs an immediate HTTP 201 response with the generated Claim ID (`CLM-2026-XXXX`).
- Adjuster querying claim details or viewing damage photos.
- Payment gateway invocation (synchronous call to Stripe API with an idempotency key).
- Workshop searching vehicle parts availability.

Asynchronous (Apache Kafka / Amazon MSK):
- Auto-assigning a field surveyor after FNOL submission.
- Emitting notifications (SMS, Email, Push) to customer and workshop.
- Streaming immutable audit records to S3 compliance storage.
- Updating real-time UI dashboards via WebSocket workers.
- Feeding the OLAP data lake / Redshift for executive KPI reports."

**Technical Bullets:**
- Synchronous SLA: REST p99 < 180ms for primary writes (writing to local DB + local outbox).
- Asynchronous SLA: Kafka event propagation p99 < 500ms for downstream notification fan-out.

---

### Q8: Why do you need an event-driven architecture?

**Spoken Answer:**
"Without an event-driven architecture, a single FNOL submission would require the Claims Service to make five sequential synchronous HTTP calls: one to Workforce to assign a surveyor, one to Notification to text the driver, one to Document to register upload tickets, one to Fraud to score risk, and one to Audit.

If the telecom SMS provider hangs for three seconds, or if the fraud scoring service is restarting, the customer's mobile app freezes, eventually times out, and the user hits 'Submit' again. This creates duplicate claims and cascades failures across the platform.

With an Event-Driven Architecture, the Claims Service writes the claim to PostgreSQL, writes a `ClaimCreated` event to its local outbox table, and returns HTTP 201 in under 180 milliseconds. Kafka buffers the event, and independent consumer workers execute side-effects at their own pace. If the SMS vendor is down, Kafka retains the message until the notification worker can retry, without affecting the customer."

**Technical Bullets:**
- Decoupling: Producers do not know or care who consumes the event.
- Temporal Decoupling: Consumers can be offline or degraded without impacting producer availability.
- Backpressure Buffer: During peak storm surges, Kafka absorbs bursts up to 250 write TPS while downstream consumers process at a steady, sustainable rate.

---

### Q9: What happens if Kafka/MSK becomes unavailable?

**Spoken Answer:**
"Because we use the **Transactional Outbox Pattern**, Kafka downtime does NOT cause claim submissions or business transactions to fail.

Here is the exact failure path:
When a customer submits a claim, the Claims Service begins a local database transaction. It writes the claim record to `claims.claims` and simultaneously writes the event payload into `claims.outbox`. It then commits the database transaction and immediately returns HTTP 201 to the customer.

A separate CDC process (such as Debezium or an outbox publisher worker) polls or tails the PostgreSQL write-ahead log (WAL) to push events to Kafka. If Kafka is completely down:
1. The customer still gets a successful submission confirmation.
2. The event remains safely persisted on disk inside the PostgreSQL outbox table.
3. The outbox publisher pauses with exponential backoff and emits a PagerDuty alert.
4. Once Kafka recovers, the publisher resumes streaming from the outbox table in strict sequence with zero data loss."

**Technical Bullets:**
- Outbox Resilience: Core DB write and Outbox write occur in the same ACID transaction.
- Zero Loss: Outbox table retains un-published rows with `published = FALSE`.
- Recovery: Reconnection triggers bulk publish ordered by `created_at` timestamp.

---

### Q10: How do you prevent one service failure from cascading across the platform?

**Spoken Answer:**
"We enforce four layers of defense against cascading failures:
1. Asynchronous Decoupling: Core business flows do not use blocking synchronous RPC between microservices. If Notification Service crashes, Claims Service is unaffected.
2. Circuit Breakers and Timeouts: For necessary synchronous calls (like calling the legacy Policy Administration System or Stripe), we implement Resilience4j circuit breakers with strict 2-second timeouts and half-open probes. When error rates exceed 50%, the circuit opens immediately, returning a fallback response rather than exhausting thread pools.
3. Connection Pool Capping: Each service pod caps its HikariCP connection pool to 20 connections. A slow query in one service cannot starve the database of connections for other services.
4. Bulkheading: Separate container task definitions on ECS Fargate ensure CPU/memory isolation so one runaway process cannot deprive neighboring services of resources."

**Technical Bullets:**
- Resilience4j: Sliding window of 100 calls, 50% failure rate threshold, 10s open state duration.
- Timeouts: Connect timeout 500ms, Read timeout 2000ms on all HTTP client calls.
- ECS Isolation: Strict task CPU/memory hard limits (`task_definition.cpu: 512`, `memory: 1024`).

---

### Q11: How do you maintain consistency when a business transaction spans Claims, Workflow, Payment and Notification?

**Spoken Answer:**
"In a distributed microservices environment, two-phase commit (2PC) distributed transactions are an anti-pattern because they hold distributed locks, kill scalability, and fail if any single participant is unreachable.

Instead, we maintain consistency using the **Choreographed Saga Pattern backed by Eventual Consistency**.

For example, when a claim reaches settlement:
1. Workshop Service marks repairs complete and publishes `RepairCompleted`.
2. Payment Service consumes the event, calculates the deductible, charges the customer via Stripe, and publishes `PaymentSettled`.
3. Claims Service consumes `PaymentSettled` and transitions the claim state to `SETTLED`.
4. If the payment fails (e.g., credit card declined), Payment Service publishes `PaymentFailed`. Claims Service consumes this and transitions the claim to `PAYMENT_PENDING`, triggering an escalation task in Camunda to alert the case manager.

Each service maintains local ACID consistency in its own database, and the overall business process reaches eventual consistency via ordered domain events."

**Technical Bullets:**
- Pattern: Choreographed Saga for routine flows; Orchestrated Saga via Camunda 8 for complex multi-party claims requiring human escalation.
- Compensation Actions: Every forward action has a defined compensating reverse event (e.g., `PaymentFailed` -> `RevertClaimToPendingPayment`).

---

### Q12: Would you use Saga, distributed transactions, or eventual consistency? Why?

**Spoken Answer:**
"We explicitly use **Eventual Consistency via the Saga pattern**, and we reject traditional distributed transactions (XA / 2PC).

Why? Distributed transactions require all coordinating databases to hold row locks across network boundaries until all nodes agree to commit. In an insurance domain where a claim lifecycle spans days, weeks, and third-party repair body shops, holding distributed database locks is physically impossible. Furthermore, distributed transactions prioritize Consistency over Availability (CP in CAP theorem), which violates our 24x7 availability requirement.

With the Saga pattern, each microservice executes a local ACID transaction in its own database and emits an event. If a subsequent step in the business process fails, compensating transactions are executed to roll back prior business states gracefully. This gives us high availability, horizontal scalability, and business-level consistency."

**Technical Bullets:**
- Trade-off: AP over CP per CAP Theorem for distributed operations.
- Local ACID: Aurora PostgreSQL enforces ACID within each service boundary.
- Global Consistency: Event-driven choreography ensures all services converge to the final state within seconds.

---

### Q13: What happens when an event is delivered twice?

**Spoken Answer:**
"Kafka provides **at-least-once delivery** by default. Network retries, consumer rebalances, or producer retries mean that duplicate events will inevitably occur.

In our architecture, every consumer is built to be strictly **idempotent**. 

When an event arrives:
1. The consumer extracts the unique `eventId` (UUIDv4) from the Kafka message header.
2. It executes an atomic `SETNX` (set if not exists) operation in Amazon ElastiCache Redis: `SETNX processed_event:{eventId} 1 EX 86400` (24-hour TTL).
3. If Redis returns 0, the event has already been processed. The consumer acknowledges the offset immediately and skips processing.
4. If Redis returns 1, the consumer proceeds with business processing inside a local database transaction. 
5. For database-level safety, the consumer table also includes a `UNIQUE` constraint on `event_id`, ensuring that even if Redis restarts, a duplicate insert will trigger a unique constraint violation and be safely ignored."

**Technical Bullets:**
- Delivery Guarantee: At-least-once messaging.
- Deduplication Tier 1: Redis `SETNX` cache check (sub-millisecond latency).
- Deduplication Tier 2: Relational database `UNIQUE (event_id)` constraint.

---

### Q14: How do you guarantee idempotency?

**Spoken Answer:**
"Idempotency is enforced at two critical interfaces: client-to-API and service-to-service.

1. Client-to-API (e.g., Submitting a Claim or Paying a Deductible):
The client frontend generates a unique UUIDv4 `Idempotency-Key` and sends it as an HTTP header. 
In the Payment Service, we check Amazon MemoryDB for Redis:
- If the key exists and processing is complete, we return the cached HTTP response payload immediately without charging the card again.
- If the key exists and status is `IN_PROGRESS`, we return HTTP 409 Conflict with a `Retry-After` header.
- If new, we atomically claim the key with a 120-second lease, process the transaction, store the final response in MemoryDB, and commit.

2. Service-to-Service (Kafka Event Processing):
Every event payload includes a deterministic `eventId`. Downstream consumers record processed IDs in an `idempotent_consumer` table within their local database transaction, or use database upserts (`INSERT ... ON CONFLICT DO NOTHING`)."

**Technical Bullets:**
- Header: `Idempotency-Key: <UUIDv4>`.
- Storage: MemoryDB for Redis (multi-AZ transactional persistence) for financial transactions; Aurora DB unique index for event deduplication.
- Response Caching: Storing the serialized response body in Redis for 24 hours ensures identical retried requests receive the identical result.

---

### Q15: What happens if the database transaction succeeds but Kafka publication fails?

**Spoken Answer:**
"This is the classic **Dual-Write Problem**. If an application writes to PostgreSQL and then immediately calls `kafkaTemplate.send()`, a JVM crash or network drop between the two lines of code leaves the database updated but the event lost forever.

We eliminate this problem completely using the **Transactional Outbox Pattern**:
1. When a service processes a command, it writes both the business entity (e.g., `claims` row) and an outbox record (e.g., `outbox` row containing topic, partition key, and JSON payload) inside the **exact same local ACID database transaction**.
2. Either both commit to disk, or both roll back. It is physically impossible for the database write to succeed without the event being recorded in the outbox table.
3. An asynchronous worker (or Debezium CDC reading Postgres WAL) continuously reads unprocessed outbox entries and publishes them to Kafka with retries.
4. Once Kafka confirms receipt with an acknowledgment (ack=all), the outbox row is marked published or deleted."

**Technical Bullets:**
- Pattern: Transactional Outbox.
- Mechanism: Spring `@Transactional` encompasses entity repository write and `OutboxRepository.save()`.
- Publisher: Background scheduled polling worker or Debezium Postgres CDC connector via Amazon MSK Connect.

---

### Q16: Why ECS Fargate first and EKS later?

**Spoken Answer:**
"We chose AWS ECS Fargate for Day 1 through our initial production release because of **operational velocity, total cost of ownership, and zero cluster management overhead**.

Our engineering team consists of 12 professionals with a delivery timeline of 14 months and a budget of $2.0M. Kubernetes (EKS) brings massive operational tax: managing control plane upgrades, worker node groups, VPC CNI plugins, CoreDNS tuning, ingress controllers, Helm charts, and continuous security patching. 

ECS Fargate allows our developers to define a container image, specify CPU and memory, and let AWS manage the underlying compute, patching, and multi-AZ placement. It satisfies every NFR in the assignment: automatic container restarts on failure, sub-second horizontal auto-scaling, and multi-AZ isolation, while keeping DevOps overhead near zero during initial delivery."

**Technical Bullets:**
- Operational Simplicity: No Kubernetes control plane to manage, patch, or upgrade.
- Cost: Pay strictly for active vCPU/GB seconds consumed; zero idle worker node costs.
- Native AWS Integration: Native IAM roles for tasks, CloudWatch logging, and ALB target group integration out-of-the-box.

---

### Q17: What exact condition tells you it is time to move from ECS to EKS?

**Spoken Answer:**
"We will trigger the migration from ECS Fargate to EKS when any of the following three measurable conditions is met:
1. Container Fleet Density and Cost: When our baseline container fleet exceeds 150 tasks continuously across 8 services. At that scale, managing compute via EKS with EC2 Graviton spot/reserved instances becomes 35-40% cheaper than Fargate vCPU-hour pricing.
2. Advanced Traffic Routing and Service Mesh: When cross-service traffic requires sophisticated Canary deployments, fine-grained mTLS service mesh routing (Istio / Linkerd), or complex circuit-breaking topologies not natively supported by ECS and ALB.
3. Hybrid On-Premise Portability Requirement: If YCompany mandates hybrid deployment where the exact same Kubernetes manifests and Helm charts must run on on-premise OpenShift/Rancher clusters as well as AWS."

**Technical Bullets:**
- Metric 1: Fleet scale > 150 tasks continuously (breakeven cost threshold vs EC2 Graviton).
- Metric 2: Requirement for Istio/Linkerd service mesh features (mTLS, distributed fault injection).
- Metric 3: Strict multi-cloud or on-prem container standardization requirement.

---

### Q18: Why not use EKS from day one?

**Spoken Answer:**
"Using EKS from day one would be a classic case of **premature optimization and accidental complexity**.

On day one, our primary operational risk is delivering working business software across 4 portals and 8 microservices within 14 months. If we introduce EKS on day one, our lone DevOps engineer spends 40% of their time debugging CNI networking, RBAC role mappings, ingress controllers, and cluster upgrades instead of building clean CI/CD pipelines, automated testing harnesses, and monitoring dashboards.

ECS Fargate gives us containerized portability from day one. Because our code is packaged into standard OCI Docker containers, migrating from ECS to EKS later is purely an infrastructure configuration shift; it requires zero code changes in our Spring Boot services."

**Technical Bullets:**
- Conway's Law & Team Size: A 12-person team cannot afford dedicated K8s cluster reliability engineers.
- Zero Lock-in: Docker containers run identically on ECS, EKS, or on-prem Docker runtime.
- Opportunity Cost: Time saved on Kubernetes plumbing is reinvested in domain logic and automated test coverage.

---

### Q19: How does this architecture support the stated 200M+ customer base?

**Spoken Answer:**
"The architecture supports 200 million policyholders through a four-tier decoupling strategy:
1. Edge Offloading: CloudFront CDN absorbs up to 70% of static read traffic and workshop directory searches before requests ever reach our servers.
2. Serverless Identity: AWS Cognito absorbs user authentication without requiring us to manage database tables or session state for 200M logins.
3. Media Offloading: Pre-signed Amazon S3 URLs mean multi-megabyte photo uploads flow directly into S3, never touching microservice memory.
4. CQRS and Partitioning: Claims are partitioned by year and region in Aurora PostgreSQL. Read queries hit three read replicas, while write transactions are processed in milliseconds via the Transactional Outbox pattern and Kafka."

**Technical Bullets:**
- Policyholder Volume: 200M registered customers does not mean 200M concurrent users.
- Traffic Absorption: Edge CDN + S3 direct uploads eliminate 85% of raw network and memory load.
- Database: Hierarchical table partitioning (`claims_2026_us_east`) enables partition pruning, keeping query times <15ms across 60M lifetime records.

---

### Q20: Does 200M customers actually mean 200M concurrent users? What capacity assumptions did you make?

**Spoken Answer:**
"No, 200 million customers does NOT mean 200 million concurrent users. In enterprise insurance, confusing total policyholders with concurrent users leads to multi-million-dollar sizing errors.

Here is our capacity model based on actuarial insurance metrics:
- In auto insurance, the industry average claim frequency is 5% to 6% per policyholder annually.
- For 200M policyholders, that equals roughly **12 million claims per year**.
- Across 250 business days, that is an average of **48,000 claims filed per day**.
- During an extreme localized weather event (storm surge), volume spikes 4x to 5x, reaching **200,000 claims per day**.
- Assuming an 8-hour peak window, 200,000 claims/day translates to approximately **7 writes per second (TPS) on average, peaking at 50 to 100 write TPS**.
- Including downstream lifecycle updates (surveyor notes, repair updates), peak write throughput is **~250 TPS**.
- With a standard 10:1 read-to-write ratio for dashboard lookups and tracking, peak read traffic is **~2,500 QPS**.

Our ECS Fargate container fleet, Aurora PostgreSQL cluster, and 32-partition Kafka topics are sized specifically to handle 250 write TPS and 2,500 read QPS with 80% headroom to spare."

**Technical Bullets:**
- 200M Customers = 12M claims/year (6% annual incidence).
- Baseline: 48,000 claims/day (~7 write TPS baseline).
- Peak Storm Surge: 200,000 claims/day (~50-100 write TPS, total platform write peak ~250 TPS).
- Read Peak: ~2,500 QPS (served via CloudFront, Redis, and Aurora Read Replicas).
- Concurrency: Real-world peak concurrent active sessions is ~15,000-25,000 users, easily supported by our ALB and API Gateway.
