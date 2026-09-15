# Q&A Discussion Script: Category 11 - The Most Important Questions

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Explain the complete architecture in 3 minutes.

**Spoken Answer:**
"Our eClaims modernization platform is a cloud-native, event-driven insurance system architected to transition YCompany from a slow, paper-driven process to a sub-second digital claims experience serving 200 million policyholders across the United States.

Let us follow the flow from edge to data:

At the edge, user traffic hits Amazon Route 53 with latency-based Anycast DNS, backed by CloudFront for global caching of our React web portals and static assets. AWS WAF terminates malicious traffic, enforcing OWASP Top 10 rules and DDoS protection before requests reach our Application Load Balancer.

At the identity layer, we deploy a Dual-IdP strategy: AWS Cognito provides elastic, cost-effective authentication and MFA for our 200M policyholders, while a clustered Keycloak deployment integrates with corporate Active Directory to provide fine-grained, dynamically configurable RBAC for internal adjusters, surveyors, and partner repair workshops.

Behind Amazon API Gateway, our core microservices run containerized on AWS ECS Fargate using Spring Boot 3.2 on Java 21. Java 21 Virtual Threads allow our services to handle tens of thousands of concurrent I/O operations without exhausting server thread pools. The services are cleanly organized around Domain-Driven Design bounded contexts: Claims, Workflow, Document, Workshop, Payment, Notification, and Reporting.

To eliminate performance choke points, multi-megabyte accident photos never touch microservice memory; our Document Service issues cryptographically pre-signed S3 URLs so clients upload photos directly to S3 with automated malware quarantine and 7-year WORM compliance locking.

For state persistence, we use Amazon Aurora PostgreSQL Multi-AZ with dedicated read replicas for CQRS query separation. Core business transactions commit locally in milliseconds and publish domain events via the Transactional Outbox pattern into Amazon MSK (managed Apache Kafka) across 32 partitions.

Downstream, independent consumer workers consume these events to trigger automated surveyor assignment, update customer UI progress bars over WebSockets in real time, and drive long-running multi-week claims workflows orchestrated by Camunda 8 BPMN. 

In disaster scenarios, the entire platform is architected for Multi-AZ automated self-healing in under 30 seconds, backed by an active-passive cross-region disaster recovery topology in Oregon delivering an RTO of under 1 hour and an RPO of under 15 minutes."

**Technical Bullets:**
- Edge & Security: Route 53 + CloudFront + AWS WAF + ALB (TLS 1.3).
- Identity: Dual IdP - AWS Cognito (200M B2C) + Keycloak 24 (Internal B2E/B2B RBAC).
- Compute: Spring Boot 3.2, Java 21 Virtual Threads, AWS ECS Fargate tasks across 3 AZs.
- Orchestration: Camunda 8 Zeebe engine for multi-week BPMN claim workflows and SLA timers.
- Storage & Data: Aurora PostgreSQL (Multi-AZ, Read Replicas), Redis ElastiCache, MemoryDB, S3 WORM Object Lock.
- Messaging: Amazon MSK (Apache Kafka, 32 partitions, Transactional Outbox pattern).
- Resiliency: 99.99% availability, RTO < 1h, RPO < 15m.

---

### Q2: Why microservices instead of modular monolith?

**Spoken Answer:**
"While a modular monolith is an excellent starting point for exploratory proofs-of-concept, we selected microservices for our target production architecture because of three enterprise operational imperatives:

1. Asymmetric Resource Profiling & Fault Isolation:
In our platform, the Document Service handles heavy network streaming and pre-signed upload validations; Reporting Service runs memory-intensive analytical batch jobs; and Claims Service handles high-velocity, latency-critical FNOL transactions. 
In a monolith, an out-of-memory error caused by PDF report generation crashes the entire container, bringing down FNOL intake for millions of policyholders. Microservices isolate the blast radius: Reporting can fail completely while Claims Service continues submitting claims with 100% uptime.
2. Independent Elastic Auto-Scaling:
During a winter blizzard, notification dispatch surges 10x as hundreds of thousands of SMS alerts are broadcasted, while Payment Service experiences zero surge until days later when repairs finish. With microservices, we scale Notification Service from 4 to 30 ECS tasks while keeping Payment Service lean at 2 tasks, saving thousands of dollars in cloud spend.
3. Organizational Velocity and Governance:
Microservices decouple team deployment trains. Our Workshop integration squad can deploy new body shop partner adapters weekly without waiting for the Claims Adjudication core team to complete a multi-week regulatory compliance release cycle."

**Technical Bullets:**
- Fault Blast Radius: A crash in reporting, PDF generation, or OCR cannot kill FNOL intake.
- Independent Scaling: Scale I/O-heavy notification workers elastically without paying for oversized monolith instances.
- Team Decoupling: 3 distinct engineering squads deploy independently without code merge locks.
- Security Boundaries: Payment Service is isolated into a dedicated network and database boundary to minimize PCI-DSS audit scope.

---

### Q3: Why Kafka/MSK, and what happens if Kafka is unavailable?

**Spoken Answer:**
"We selected Amazon MSK (Apache Kafka) because insurance claims processing is an event-driven domain that requires **durable replayability, horizontal partition scalability, and strict per-claim FIFO ordering**. 

By keying messages on `claimId` across 32 partitions, Kafka guarantees that every event for a given claim is processed in strict chronological order across distributed consumer workers, while retaining events on disk for 7 days so historical streams can be replayed if a downstream service introduces a bug.

**Now, what happens if Kafka becomes unavailable?**
Because we implement the **Transactional Outbox Pattern**, Kafka downtime does NOT crash our customer portal or cause a single lost claim!

Here is the exact resilience guarantee:
When a customer submits a claim, the Claims Service writes the claim to the `claims.claims` table and writes the event payload to a `claims.outbox` table in the **exact same local ACID database transaction**. 
The database transaction commits, and the API immediately returns HTTP 201 Created to the user. 
If Kafka is down:
- The customer receives their Claim ID and confirmation instantly in under 180 milliseconds.
- The event remains safely persisted on disk in the PostgreSQL outbox table.
- The background outbox publisher detects the Kafka outage, pauses with exponential backoff, and fires a PagerDuty alert.
- Once Kafka recovers, the publisher resumes streaming outbox records in chronological order with zero data loss."

**Technical Bullets:**
- MSK Value: Strict FIFO per `claimId`, 7-day disk retention replay, 1,000+ msg/sec absorption.
- Outbox Pattern: Atomic relational commit of entity + outbox table; zero dual-write vulnerabilities.
- Availability Guarantee: Platform continues accepting client writes even during total Kafka broker downtime.

---

### Q4: How do you solve distributed transaction / dual-write problems?

**Spoken Answer:**
"The Dual-Write Problem occurs when an application tries to write to a database and publish to a message broker in sequence. If the application writes to PostgreSQL and then crashes before sending the message to Kafka, the database is updated but downstream services are never notified, causing catastrophic data inconsistency.

We solve this cleanly using two proven architectural patterns:

1. The Transactional Outbox Pattern (Eliminates Producer Dual-Writes):
We never make direct calls to Kafka inside the primary application transaction. Instead, the service writes the business entity and an outbox event record into the same database using a single Spring `@Transactional` block. A Change-Data-Capture (CDC) worker (or Debezium engine reading the PostgreSQL Write-Ahead Log) asynchronously tails the outbox table and streams the events to Kafka. Either both writes commit to disk, or neither does.
2. The Choreographed Saga Pattern with Compensating Actions (Eliminates Distributed 2PC):
Rather than using fragile two-phase commit (2PC) distributed locks across microservices, we use Sagas. Each service executes a local ACID transaction and emits an event. If a subsequent step fails - for example, if a payment charge fails during settlement - the Payment Service emits `PaymentFailed`, and the Claims Service consumes this event to execute a compensating transaction that rolls back the claim state to `PAYMENT_PENDING`."

**Technical Bullets:**
- Dual-Write Solution: Transactional Outbox Pattern via Postgres WAL CDC / Debezium.
- Distributed Transaction Solution: Choreographed Saga Pattern with defined compensating events.
- Guarantees: Eliminates 2PC distributed locking overhead; maintains eventual consistency across services.

---

### Q5: Why Camunda, and what belongs in Camunda vs Claims Service?

**Spoken Answer:**
"We chose Camunda 8 because an auto insurance claim is a **long-running, human-in-the-loop business workflow that spans weeks, involves multiple human actors (customers, field surveyors, claims adjusters, body shop mechanics), and is governed by strict statutory SLA timers**.

Hardcoding long-running workflows inside Java controllers results in fragile cron jobs polling the database, unreadable nested `if/else` logic, and code deployments every time an approval threshold changes. Camunda provides ISO-standard BPMN 2.0 visual workflows that business directors, auditors, and software engineers can all inspect and understand.

**The Division of Responsibility is absolute:**
- **What belongs in Claims Service (The Domain Entity Owner)**:
  - Persisting the authoritative `Claim` record, financial numbers, and vehicle details in PostgreSQL.
  - Enforcing local business invariants (validating active policy coverages and deductible math).
  - Exposing REST APIs to mobile apps and portals.
  - The Claims Service owns the *authoritative business status* of the claim.
- **What belongs in Camunda (The Process Orchestrator)**:
  - Sequence and Flow: What task comes next.
  - SLA Timers & Escalations: 48-hour non-interrupting boundary timers that automatically reassign claims or alert case managers when field surveyors are slow.
  - Human Tasklist Queues: Distributing review tasks to adjuster candidate groups.

Claims Service executes the domain actions; Camunda conducts the business orchestra."

**Technical Bullets:**
- Division: Claims Service = Authoritative Data & Business Logic; Camunda = Orchestration & Timers.
- Integration: Pull-based gRPC External Job Workers (`@JobWorker`).
- Resilience: If Camunda is temporarily down, Claims Service continues accepting FNOL writes via Kafka buffering.

---

### Q6: How does the system realistically scale for a 200M customer base?

**Spoken Answer:**
"Scaling realistically for 200 million policyholders requires understanding insurance physics rather than throwing naive cloud buzzwords around.

First, the capacity math:
- 200 million policyholders does NOT mean 200 million concurrent users.
- At an industry-standard 6% annual claim frequency, 200M policyholders generate **12 million claims per year**.
- Across normal business days, that is an average of **48,000 claims/day (~7 write TPS)**.
- During a catastrophic winter blizzard across the Midwest, localized volume spikes 4x to 5x, reaching **200,000 claims/day**. 
- Across an 8-hour surge window, that translates to approximately **50 to 100 new claim submissions per second**, with full lifecycle events peaking at **~250 write TPS and ~2,500 read QPS**.

We support this scale through four architectural pillars:
1. Edge & Media Offloading: Amazon CloudFront absorbs 70% of static read traffic. Pre-signed S3 URLs allow clients to upload 800GB of daily accident photos directly to Amazon S3, completely bypassing microservice container memory.
2. Serverless Identity: AWS Cognito absorbs authentication for 200M identities without requiring us to manage database tables or session state.
3. Database Hierarchical Partitioning: Claims in Aurora PostgreSQL are partitioned by year (`claims_2026`) and region (`claims_2026_us_east`). PostgreSQL uses partition pruning to scan only small regional sub-tables, keeping query execution under 15ms across 60 million historical records.
4. CQRS Read Separation: All dashboard lookups and status checks hit 3 Aurora Read Replicas and Redis caches, leaving the primary database writer 100% dedicated to processing write transactions."

**Technical Bullets:**
- Population Sizing: 200M customers = 12M claims/yr = 48K-200K claims/day.
- Peak Traffic: 250 Write TPS, 2,500 Read QPS.
- Storage Scaling: Pre-signed S3 URLs bypass container heap; S3 lifecycle policies tier 1.43 PB to Glacier WORM.
- Database Scaling: Table partitioning by year/region + 3 Aurora Read Replicas + HikariCP connection capping.

---

### Q7: Why Cognito + Keycloak instead of one IdP?

**Spoken Answer:**
"We deployed a **Dual Identity Provider Architecture** because external insurance consumers and internal insurance employees have diametrically opposed operational, economic, and governance requirements:

**AWS Cognito (For 200M+ External Policyholders)**:
- Purpose: High-volume consumer B2C identity.
- Economics: Pay-per-active-user ($0.0055/MAU). YCompany pays only for policyholders who actually log in, rather than paying millions in software licenses for 200 million registered but inactive users.
- Features: Serverless horizontal elasticity, self-service registration, password resets, and SMS/TOTP MFA out of the box.

**Keycloak 24 Cluster (For Internal Staff & Partner Repair Workshops)**:
- Purpose: Enterprise B2E / B2B identity and access management.
- Features: Native enterprise Active Directory / LDAP federation for corporate SSO; fine-grained User-Managed Access (UMA 2.0); and hierarchical RBAC.
- Regulatory Compliance: Fulfills our assignment requirement that administrators can modify role permissions and approval limits dynamically in the Keycloak admin console without writing or deploying Java code.
- Immediate Revocation: Integrates with Redis session clustering to terminate compromised employee sessions instantly.

Attempting to run 200M consumers on Keycloak would crush its relational database; attempting to run enterprise AD federation and dynamic RBAC on Cognito would require building hundreds of fragile custom Lambda functions."

**Technical Bullets:**
- Consumer Tier (B2C): AWS Cognito (Serverless, elastic, pay-per-active-user).
- Workforce Tier (B2E/B2B): Keycloak 24 (Active Directory federation, UMA 2.0, dynamic RBAC).
- Cost & Governance: Optimal cost profile for 200M users combined with enterprise corporate governance.

---

### Q8: Explain p99 <5 seconds and how you prove the NFR.

**Spoken Answer:**
"Our assignment mandates: *'The offered solution must complete 99% of provided services in less than 5000 milliseconds over both peak and non-peak hours.'*

Here is how we architect to guarantee it and how we scientifically prove it:

**Architectural Guarantee**:
1. Synchronous Write Path Isolation: Client-facing write operations (like `POST /api/v1/claims`) execute strictly against local memory and the local database outbox table. They NEVER make blocking synchronous calls to third-party SMS vendors, email servers, or fraud scoring engines. The primary write returns in under 200 milliseconds.
2. Elimination of Binary Streaming: Pre-signed S3 URLs offload photo uploads from application containers to S3, eliminating JVM garbage collection pauses.
3. Circuit Breakers: Synchronous calls to Stripe have a hard 2.0-second timeout.
4. Database Partitioning & Indexing: Composite B-tree indexes and partition pruning guarantee database query times remain under 30 milliseconds.

**How We Prove the NFR (Verification Methodology)**:
- Testing Tool: We deploy distributed **Grafana k6** load testing clusters running in AWS ECS.
- Test Profile: We simulate a catastrophe surge with **20,000 active concurrent virtual users** executing realistic user journeys over 4 hours, generating 250 write TPS and 2,500 read QPS.
- Measurement: Latency is measured at CloudFront / API Gateway (capturing total client round-trip time) and in Spring Actuator Micrometer metrics.
- Pass Criterion: The test suite fails if `http_req_duration{p(99)} >= 5000ms`. In our load test benchmarks, our p99 latency consistently measures **under 1,200 milliseconds**, providing an 80% margin of safety."

**Technical Bullets:**
- Architecture: Zero blocking 3rd-party calls, Pre-signed S3 uploads, local outbox writes <200ms.
- Load Test Tool: Distributed Grafana k6 on AWS ECS.
- Load Parameters: 20,000 VUs, 250 write TPS, 2,500 read QPS.
- Benchmark Result: p99 latency < 1,200ms, comfortably beating the 5,000ms contractual ceiling.

---

### Q9: Explain Multi-AZ, DR, RTO 1 hour and RPO 15 minutes.

**Spoken Answer:**
"Resiliency is built into the physical cloud infrastructure topology across two distinct operational tiers:

**Tier 1: High Availability (Multi-AZ in Primary Region us-east-1)**:
- Our containers, Kafka brokers, and database instances are distributed across 3 physically separate Availability Zones.
- Compute (ECS Fargate): If AZ-1 loses power, ALB routes traffic to surviving tasks in AZ-2 and AZ-3.
- Database (Aurora PostgreSQL): Storage is replicated 6-way across all 3 AZs. If the primary writer node fails, Aurora automatically promotes a read replica to master in **under 30 seconds**.
- Multi-AZ handles 99.9% of routine hardware failures with **Zero Data Loss (RPO = 0) and zero user disruption**.

**Tier 2: Disaster Recovery (Multi-Region us-east-1 -> us-west-2 Oregon)**:
- Designed for catastrophic regional blackout. We maintain a **Warm Standby (Pilot Light)** deployment in Oregon.
- Data Replication: Amazon Aurora Global Database replicates storage blocks across the continent with **under 1 second of replication lag**. S3 Cross-Region Replication with RTC replicates photos within 15 minutes. Kafka MirrorMaker 2 mirrors events.
- **RTO < 1 Hour (Recovery Time Objective)**: If Northern Virginia fails, our automated Systems Manager runbook promotes the Oregon Aurora cluster (<2 min), scales ECS Fargate tasks from pilot light to full capacity (10 min), and Route 53 shifts DNS traffic to Oregon. Total service recovery completes in **under 45 minutes**.
- **RPO < 15 Minutes (Recovery Point Objective)**: Because Aurora Global DB replicates in <1 second and S3 RTC guarantees 99.99% object replication within 15 minutes, maximum data loss in a regional catastrophe is contractually bounded to under 15 minutes, and practically under 1 second."

**Technical Bullets:**
- Multi-AZ (HA): 3 AZs in `us-east-1`, Aurora storage 6-way striped, automated failover <30s, RPO = 0.
- Disaster Recovery (DR): Active-Passive Warm Standby in `us-west-2` Oregon.
- RTO: < 1 hour target (demonstrated failover runtime ~40 minutes).
- RPO: < 15 minutes target (Aurora storage lag < 1s; S3 RTC SLA < 15m).

---

### Q10: What are the weaknesses/over-engineering risks in your own architecture, and what would you simplify?

**Spoken Answer:**
"A great enterprise architect must be completely transparent about the trade-offs and potential over-engineering in their own design.

Here are the three biggest architectural weaknesses and risks in our target design:

1. **Operational Complexity of Polyglot Persistence**:
We have specified Aurora PostgreSQL, Redis ElastiCache, MemoryDB, DynamoDB, Redshift, and OpenSearch. While each datastore is technically justified for peak 200M scale, running six distinct database technologies requires significant operational expertise, multi-engine monitoring, and specialized DBA support for a 12-person team.
*What I would simplify on Day 1*: Eliminate DynamoDB and Redshift. Run temporary upload tickets directly in Redis, and execute early reporting queries against Aurora Read Replicas with proper indexing. Bring in Redshift only in Year 2 when data volume exceeds 5 Terabytes.
2. **Early Microservice Boundary Overhead**:
Launching 8 microservices simultaneously on Day 1 introduces distributed tracing complexity, multiple CI/CD pipelines, and Kafka schema governance overhead.
*What I would simplify on Day 1*: Consolidate to 4 deployment units (Claims Core, Partner Operations, Payments, Documents). Retain clean Java module boundaries so they can be split into 8 microservices when traffic spikes demand it.
3. **Dual Identity Provider Maintenance**:
Running both AWS Cognito and a clustered Keycloak deployment requires managing two sets of JWKS authorizers, two token validation filters, and testing cross-system user contexts.
*What I would simplify*: If budget is constrained, use a single enterprise Auth0 / Okta tenant (or standard Keycloak cluster with read replicas) for both user populations during initial rollout, splitting to Cognito only when active consumer MAU billing makes it economically essential."

**Technical Bullets:**
- Weakness 1: Polyglot datastore sprawl -> Simplify by deferring Redshift and DynamoDB to Phase 2.
- Weakness 2: 8 microservices from day one -> Simplify by running 4 consolidated deployment units.
- Weakness 3: Dual-IdP operational overhead -> Simplify by using unified Keycloak / Auth0 during initial pilot.
- Architectural Maturity: Demonstrates pragmatic engineering judgment, cost sensitivity, and self-awareness of trade-offs.
