# eClaims Modernisation Platform - Senior Solution Architect Technical Evaluation

| Metadata | Value |
|---|---|
| Evaluation Type | Senior Solution Architect Technical Assessment |
| Target System | YCompany eClaims Processing System |
| Architect / Reviewer | Senior Principal Enterprise Solutions Architect |
| Target Scale | 200M+ Policyholders across US Geographies |
| Scope | Architecture, Source Code, System Diagrams, and Design Deliverables |
| Date | September 2026 |

---

## 1. Reconstructed End-to-End Architecture and Request/Data Flows

### 1.1 High-Level End-to-End Architecture Topology

The eClaims platform is conceived as a multi-tier, event-driven insurance claims ecosystem transitioning from an exploratory modular monolith proof-of-concept (POC) to an enterprise-grade cloud-native target architecture on AWS.

```
+----------------------------------------------------------------------------------------------------+
|                                      CLIENT / ACCESS TIER                                          |
|  +--------------------+  +----------------------+  +---------------------+  +-------------------+  |
|  | Customer Web (PWA) |  | Customer Mobile App  |  | Internal Staff App  |  | Partner Workshop  |  |
|  | React 18 + TS Vite |  | React Native (Phase2)|  | Case/Adj/Surv/Audit |  | Portal (React 18) |  |
|  +---------+----------+  +----------+-----------+  +----------+----------+  +---------+---------+  |
+------------|------------------------|-------------------------|-----------------------|------------+
             |                        |                         |                       |
             v                        v                         v                       v
+----------------------------------------------------------------------------------------------------+
|                                     EDGE & SECURITY TIER                                           |
|  - AWS Route 53 (Global DNS, Latency-based Routing, Health Check Failover TTL 60s)                 |
|  - AWS CloudFront (CDN Edge Distribution, Static Asset Caching, TLS 1.3 Termination)               |
|  - AWS WAF & Shield Advanced (OWASP Top 10 Rules, Rate Limiting, DDoS Mitigation)                  |
|  - AWS Application Load Balancer (ALB - Multi-AZ Ingress, Path Routing, mTLS)                      |
+----------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
|                                 IDENTITY & API MANAGEMENT TIER                                     |
|  - Amazon API Gateway (Throttling, Token Validation, WebSocket Real-time Session Termination)       |
|  - Dual Identity Provider Architecture:                                                            |
|      * AWS Cognito: 200M+ External Policyholders (Self-registration, OIDC, JWT)                    |
|      * Keycloak 24 Cluster: Internal Staff & Partners (Fine-grained UMA 2.0 RBAC, AD Federation)   |
+----------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
|                            APPLICATION & CORE PROCESSING TIER                                      |
|  Runtime: Spring Boot 3.2.5 on Java 21 with Project Loom Virtual Threads Enabled                   |
|  Deployment: AWS ECS Fargate Tasks (Private Subnets) -> Future Amazon EKS Multi-AZ Cluster         |
|                                                                                                    |
|  [Bounded Context Modules / Target Microservices]:                                                 |
|    +--------------------+  +--------------------+  +--------------------+  +--------------------+  |
|    |   Claims Module    |  |  Workforce Module  |  |  Documents Module  |  |  Workshops Module  |  |
|    | - Lifecycle FSM    |  | - Auto-Assignment  |  | - Upload/Retrieval |  | - Work Orders      |  |
|    | - Policy Check     |  | - Surveyor/Adjuster|  | - S3 Storage Port  |  | - Status Tracker   |  |
|    | - Rule Fraud Check |  | - JIT Provisioning |  | - Audit Trail      |  | - Invoice Prep     |  |
|    +--------------------+  +--------------------+  +--------------------+  +--------------------+  |
|    +--------------------+  +--------------------+  +--------------------+  +--------------------+  |
|    |  Payments Module   |  | Notifications Mod  |  |  Reporting Module  |  |   Rentals Module   |  |
|    | - Redis Idempotency|  | - Kafka Consumers  |  | - Snapshot Caches  |  | - Vehicle Booking  |  |
|    | - Bill Calculation |  | - SES/Twilio Adap  |  | - Scheduled Aggreg |  |   (Partner Stub)   |  |
|    | - Stripe Adapter   |  | - In-App Alerts    |  | - Regional Metrics |  |                    |  |
|    +--------------------+  +--------------------+  +--------------------+  +--------------------+  |
+----------------------------------------------------------------------------------------------------+
                                  |                                     |
                                  v                                     v
+---------------------------------------------------+ +----------------------------------------------+
|               EVENT STREAMING TIER                | |                 DATA TIER                    |
|  - Apache Kafka / Amazon MSK (Managed Redpanda POC)| |  - Amazon Aurora PostgreSQL (Multi-AZ)      |
|  - Topics: claim-events, audit-events,            | |    * Isolated Schemas per Bounded Context    |
|    payment-events, repair-events, notifications   | |  - Redis 7 / AWS ElastiCache & MemoryDB      |
|  - Dead Letter Queue (DLQ) & Exponential Backoff  | |    * Payment Idempotency Keys (SETNX)        |
|  - Event-Carried State Transfer across Contexts   | |    * Policy & Workshop Caching               |
|                                                   | |  - AWS S3 + Object Lock (WORM Compliant)     |
|                                                   | |  - Amazon Redshift / Data Lake (Target OLAP) |
+---------------------------------------------------+ +----------------------------------------------+
```

### 1.2 Reconstructed End-to-End Request and Data Flows

#### Flow A: Policyholder Onboarding and Authentication
1. **User Request**: The policyholder initiates registration via the React Web SPA (`RegisterPage.tsx`), providing their policy number, vehicle registration, email, and password.
2. **Policy Verification**: The request hits `/api/v1/onboarding/register`. `OnboardingApplicationService` invokes `PolicyServicePort.validate()` to ensure the policy number is active in the Policy Management System (PMS) and that the vehicle registration matches.
3. **IdP Account Provisioning**: Upon successful verification, the backend calls the Keycloak Admin Client REST API (`KeycloakAdminConfig`) to create the user account in the `eclaims` realm and assigns the `CUSTOMER` realm role.
4. **Token Issuance**: The customer logs in via Keycloak OIDC endpoint (`/protocol/openid-connect/token`), receiving an asymmetric RS256-signed JWT containing identity claims and `realm_access.roles`.
5. **Session Bootstrap**: Subsequent requests carry the `Authorization: Bearer <JWT>` header, which Spring Security's `BearerTokenAuthenticationFilter` validates against Keycloak's JWKS endpoint.

#### Flow B: First Notice of Loss (FNOL) Claims Submission
1. **Submission**: The authenticated customer fills in incident details, accident location, police report flags, and attaches photos/documents on `SubmitClaimPage.tsx`.
2. **Gateway Ingress**: The browser issues a `POST /api/v1/claims` with a JSON payload. Amazon API Gateway / Spring Security parses the Bearer JWT. `KeycloakAuthorizationService` verifies the `claim#submit` permission.
3. **Policy Pre-Check & Creation**: `ClaimController` extracts the caller identity from `UserContextHolder` and calls `ClaimApplicationService.submitClaim()`. The service validates the policy against `PolicyServicePort`, constructs the `Claim` aggregate root in `SUBMITTED` status, and runs the rule-based `FraudDetectionService`.
4. **Database Persistence**: The claim is persisted into `claims.claims` table via `ClaimJpaRepository`. A transition record is logged in `claims.claim_history`.
5. **Event Dispatch**: `ClaimApplicationService` publishes `ClaimCreatedPayload` to the Kafka topic `claim-events` and an immutable audit record to `audit-events`.
6. **Binary Document Upload**: In a separate parallel request (`POST /api/v1/documents/upload`), the frontend transmits multipart binaries to `DocumentController`. `DocumentApplicationService` streams bytes to `DocumentStoragePort` (MinIO/S3), writes metadata to `documents.documents`, and logs document creation in `documents.document_audit_log`.

#### Flow C: Auto-Assignment and Field Assessment Workflow
1. **Event Trigger**: When a vehicle is marked as dropped off at a repair workshop (`vehicle.droppedoff`), or when claim status advances, the `AutoAssignmentService` in the workflow module consumes the event from `claim-events`.
2. **Deduplication Check**: `AutoAssignmentService` evaluates the `eventId` in Redis using `SETNX` with a 24-hour TTL to prevent duplicate processing.
3. **Surveyor Resolution**: The service queries `workflow.surveyor_zip_coverage` by full 5-digit ZIP or 3-digit prefix, identifies active surveyors, evaluates current active assignment workloads, and selects the lowest-loaded candidate.
4. **Assignment Emission**: The service writes to `workflow.assignments` and publishes `SurveyorAssignedPayload` to Kafka.
5. **State Synchronization**: `ClaimWorkflowEventConsumer` in the claims module receives `surveyor.assigned`, updates the claim state to `ASSIGNED`, and registers the assigned surveyor ID.
6. **Field Survey Execution**: The assigned surveyor logs in, views their queue on `AssessClaimPage.tsx`, conducts physical/remote inspection, and submits the assessed damage cost via `PATCH /api/v1/claims/{id}/status` to `SURVEYED`.

#### Flow D: Workshop Estimation, Repair Tracking, and Customer Updates
1. **Workshop Claim Linking**: The customer or case manager assigns a certified repair workshop (`SelectWorkshopRequest`). `WorkshopApplicationService` records the selection and emits `workshop.selected`.
2. **Work Order Creation**: The repair facility opens `WorkOrderPage.tsx`, creating a work order with estimated labor, parts cost, and target completion date (`POST /api/v1/workshops/work-orders`).
3. **Repair Stage Progression**: As technicians repair the vehicle, status updates (`IN_PROGRESS`, `PARTS_ORDERED`, `PAINTING`, `COMPLETED`) are submitted via `RepairUpdatePage.tsx`.
4. **Audit and Events**: Each transition updates `workshops.work_orders`, writes an entry to `workshops.work_order_status_history`, and broadcasts `repair.status.updated` to Kafka.
5. **Notification Fan-out**: `ClaimEventConsumer` in the notifications module consumes the repair event, composes an email via `EmailNotificationAdapter` (Mailhog / AWS SES), logs an SMS record, and persists an in-app alert into `notifications.customer_notifications`.

#### Flow E: Adjudication, Settlement, and Idempotent Electronic Payment
1. **Adjustor Review**: An adjustor picks up the surveyed claim on `AdjudicateClaimPage.tsx`, reviews surveyor assessment notes, police reports, and workshop estimates, and issues an approval with an `approvedAmount` and deductible.
2. **Billing Calculation**: When customer prepares to pay, `PaymentController.previewBill()` calls `PaymentApplicationService.previewBill()`, calculating `totalDue = (workshopFinalCost - approvedAmount) + processingFee`.
3. **Idempotent Payment Initiation**: The customer submits payment via `POST /api/v1/payments` with a client-generated UUID `Idempotency-Key` header.
4. **Cache Interception**: `PaymentApplicationService` checks Redis for `idempotency:<key>`. If found, it immediately returns the cached `PaymentResponse` without executing gateway charges.
5. **Gateway Settlement**: If new, it invokes `PaymentGatewayPort.processPayment()`, saves the transaction in `payments.payments`, records the result in Redis with a 24-hour TTL, and publishes `PaymentSettledPayload` to Kafka.
6. **Terminal Settlement**: The claims module consumes `payment.settled`, transitions claim status to `SETTLED`, and seals the claim lifecycle.

---

## 2. Major Architectural Decisions and Rationale Analysis

### Decision 1: Modular Monolith Architecture for POC transitioning to Microservices Target
- **What was chosen**: A single Maven multi-module project comprising 11 modules (`kernel`, `contracts`, `claims`, `customers`, `documents`, `workflow`, `notifications`, `workshops`, `payments`, `reporting`, `rentals`, `app/eclaims-api`), deployed as a unified Spring Boot application.
- **Why it was likely chosen**: Building a distributed microservices platform from day zero introduces massive operational overhead: distributed tracing, network latency, distributed transactions, deployment complexity, and service discovery. The modular monolith allows the team to iterate rapidly on complex insurance domain rules, maintain compile-time type safety across module contracts, and validate bounded contexts without managing 8 independent CI/CD pipelines and infrastructure deployments.
- **Microservices Alignment**: Each module implements Hexagonal Architecture (Ports and Adapters) with ArchUnit boundary rules preventing domain leakage, theoretically allowing clean extraction to standalone ECS/EKS microservices in Phase 2.

### Decision 2: Dual Identity Provider Architecture (AWS Cognito + Keycloak)
- **What was chosen**: AWS Cognito is specified for 200M+ external customer policyholders, while Keycloak 24 is deployed for internal staff (case managers, adjustors, surveyors, auditors) and workshop partners.
- **Why it was likely chosen**:
  - *Cognito for Customers*: Managing 200 million consumer user records in a self-hosted Keycloak cluster requires massive database scaling, connection pool sizing, and high-availability operations. Cognito offers zero-ops serverless scale, native AWS integration, and predictable consumer pricing.
  - *Keycloak for Internal Operations*: Enterprise insurance workflows demand fine-grained User-Managed Access (UMA 2.0), complex dynamic Role-Based Access Control (RBAC), and Active Directory / LDAP federation for corporate employees. Keycloak excels at complex enterprise authorization models where permissions can be altered without deploying code.

### Decision 3: Event-Driven Core using Apache Kafka / Amazon MSK
- **What was chosen**: Asynchronous publish-subscribe messaging backbone using Apache Kafka (Amazon MSK in production, Redpanda locally in Docker), with standardized `DomainEvent<T>` envelopes.
- **Why it was likely chosen**: Claim processing is inherently asynchronous and spans multiple business days. Coupling claim creation directly to notification delivery, surveyor routing, document analysis, and analytics would create brittle cascading failures and high latency. Kafka provides durable, replayable event logs, strict partition-level ordering per claim, and decouples write-heavy ingestion from downstream consumers.

### Decision 4: Relational Multi-Schema PostgreSQL (Amazon Aurora) + Redis Caching
- **What was chosen**: PostgreSQL 16 (Amazon Aurora Multi-AZ) with logically isolated schemas per bounded context (`claims`, `documents`, `workflow`, `workshops`, `payments`, `reporting`, `audit`), augmented by Redis 7 for caching and idempotency keys.
- **Why it was likely chosen**: Financial insurance systems require strict ACID compliance, transactional integrity, foreign key validation, and complex relational queries. Documenting logical schemas enforces domain boundaries while avoiding the complexity of 8 physical databases in the initial phase. Redis provides sub-millisecond retrieval for hot policy records, workshop metadata, and distributed lock/idempotency primitives.

### Decision 5: Java 21 and Spring Boot 3.x with Project Loom Virtual Threads
- **What was chosen**: Modern Java 21 LTS runtime leveraging Spring Boot 3.2.5 with `spring.threads.virtual.enabled: true`.
- **Why it was likely chosen**: Traditional Spring MVC allocates one OS thread per HTTP request. Under massive concurrency (e.g. thousands of concurrent policyholder submissions or webhook callbacks), platform thread memory footprint and context switching degrade performance. Virtual threads enable high-throughput synchronous blocking code style (JDBC, REST calls) with near-reactive scalability without the cognitive and debugging complexity of Spring WebFlux or Project Reactor.

### Decision 6: AWS S3 with Object Lock (WORM) Storage
- **What was chosen**: Amazon S3 with Write-Once-Read-Many (WORM) compliance policies for all claim documents, images, and reports.
- **Why it was likely chosen**: Insurance regulations (state insurance commissions, SEC, FINRA rules) mandate immutable document retention for a minimum of 7 years. S3 Object Lock prevents accidental or malicious alteration or deletion of evidence (police reports, accident photos, surveyor loss adjustments), providing legally defensible non-repudiation.

### Decision 7: Container Orchestration Progression (ECS Fargate -> Amazon EKS)
- **What was chosen**: Deploying containerized services on AWS ECS Fargate for Phase 1, followed by migration to Amazon EKS in Phase 2.
- **Why it was likely chosen**: ECS Fargate eliminates EC2 node provisioning, AMI patching, OS maintenance, and Kubernetes cluster management overhead, accelerating Phase 1 delivery. EKS is reserved for Phase 2 when advanced traffic routing, Istio service mesh, cross-cluster autoscaling (Karpenter), and hybrid cloud capabilities become necessary.

---

## 3. Critical System Analysis: Weak Points, Inconsistencies, and Risks

### 3.1 Architectural Weak Points

#### The Dual-Write Hazard (Missing Transactional Outbox)
In `ClaimApplicationService.submitClaim()`, the system updates the database and immediately invokes Kafka:
```java
Claim saved = claimRepository.save(claim);
publishClaimCreatedEvent(saved, cmd.correlationId());
```
In `KafkaClaimEventPublisher.java`, `kafkaTemplate.send()` executes asynchronously:
- If the database transaction fails to commit *after* the Kafka message is transmitted, downstream consumers (notifications, workflow, reporting) process an event for a claim that does not exist in the database.
- Conversely, if Kafka is unreachable or network times out, the database transaction has already succeeded, leaving downstream consumers permanently out of sync.
- **Architectural verdict**: Direct publisher calls inside `@Transactional` methods without a Transactional Outbox pattern or Change Data Capture (CDC via Debezium) violate eventual consistency guarantees.

#### Cross-Schema SQL Coupling Violating Bounded Contexts
Despite declaring strict boundary isolation ("Each schema corresponds to one bounded-context module... cross-module data access is via API calls or events only"), multiple modules directly query foreign schemas:
- `PaymentApplicationService` executes raw SQL via `JdbcTemplate` joining `claims.claims` and `workshops.work_orders`.
- `WorkshopApplicationService` executes raw SQL against `claims.claims` (`WHERE workshop_id = ?`).
- `ClaimAccessPolicyImpl` queries `workshops.workshops` to resolve workshop entity IDs.
- `ClaimKpiSnapshotRefreshJob` runs cross-schema queries against `claims.claims` every 60 seconds.
- **Architectural verdict**: The modular monolith is tightly coupled at the SQL database layer. Extracting these modules into independent microservices with dedicated physical databases will immediately break these transactions and queries.

### 3.2 Scalability Bottlenecks and Risks

#### Kafka Partition Under-Provisioning
In `KafkaConfig.java`, topic partitions are hardcoded:
- `claim-events`: 3 partitions
- `audit-events`: 3 partitions
- `payment-events`: 2 partitions
- `repair-events`: 2 partitions
- `notification-events`: 2 partitions
- **Analysis**: Kafka parallelism per consumer group is strictly bounded by the number of partitions. With only 3 partitions on `claim-events`, at most 3 container instances in any consumer group can consume events in parallel. At 50,000 peak daily claims and multi-channel notification fan-out, consumers will build up massive lag, violating the 30-second notification SLA.

#### Periodic Table Scan Aggregation on OLTP Datastore
In `ClaimKpiSnapshotRefreshJob.java`, a `@Scheduled` background worker executes every 60 seconds:
```sql
SELECT 'global', COUNT(*),
       COUNT(*) FILTER (WHERE date_trunc('day', created_at) = date_trunc('day', NOW())),
       COUNT(*) FILTER (WHERE status = 'SUBMITTED'), ...
FROM claims.claims;
```
- **Analysis**: At enterprise scale with 4M to 10M claims per year, running full table scans with multiple conditional aggregations every 60 seconds on the primary transactional database will cause buffer cache pollution, CPU spikes, lock contention, and degrade transaction latency for live claim submissions.

#### Just-In-Time (JIT) Database Writes inside HTTP Request Filter
`WorkforceProvisioningFilter.java` executes on every incoming HTTP request matching `/api/*`:
```java
if (workforceProvisioningService.shouldProvisionSurveyor(authorities)) {
    workforceProvisioningService.upsertSurveyorFromJwt(jwt);
}
```
- **Analysis**: Every HTTP GET or POST request issued by a surveyor or adjustor triggers database queries and potential upsert writes inside a servlet filter. Under high concurrency, this multiplies write load on the database and creates connection pool exhaustion.

### 3.3 Security and Authorization Vulnerabilities

#### Fail-Open Flaw in ClaimAccessPolicy
In `ClaimAccessPolicyImpl.java`:
```java
Optional<UserContext> ctxOpt = UserContextHolder.current();
if (ctxOpt.isEmpty()) {
    return; // Silently permits execution!
}
```
- **Analysis**: If `UserContextHolder` fails to populate due to an unhandled security filter edge-case, anonymous request, or asynchronous context detachment, the authorization check returns cleanly instead of denying access. In security architecture, access policies must be strictly **fail-closed** (`throw new UnauthorisedException(...)`).

#### UMA 2.0 Synchronous HTTP Bottleneck and Single Point of Failure
In `KeycloakAuthorizationService.java`:
- Every authorization check (`@authz.isAllowed('claim', 'submit')`) not cached in the local node's Caffeine cache triggers a synchronous HTTP POST call to Keycloak's token endpoint (`urn:ietf:params:oauth:grant-type:uma-ticket`).
- If Keycloak experiences latency or becomes unavailable, the entire API tier halts.
- Furthermore, because Caffeine is an in-memory JVM cache, permission revocations in Keycloak are not pushed to ECS tasks, allowing revoked users up to 5 minutes of continued unauthorized access on that node.

### 3.4 Document Ingestion Bottleneck

#### Multipart API Proxying vs Pre-signed S3 Uploads
The architecture documentation and NFR summary claim: "Document binaries bypass the API via pre-signed URLs (async - user never waits for S3 upload)".
However, the implementation in `DocumentController.java` is:
```java
@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ApiResponse<DocumentMetadataResponse>> uploadDocument(
        @RequestParam UUID claimId,
        @RequestParam String documentType,
        @RequestParam("file") MultipartFile file)
```
- **Analysis**: File uploads stream directly through the Spring Boot API container memory and disk before being forwarded to MinIO/S3. Under peak load with thousands of 5MB files (accident photos, police reports), the API containers will suffer severe memory pressure, garbage collection pauses, and network bandwidth saturation.

### 3.5 Domain and Geographic Inconsistencies

#### US Insurance Requirements vs Indian Locale Artifacts
- The business specification mandates a system for **200M+ policyholders across US geographies**.
- However, `database-design-deliverable.md` defines:
  ```sql
  country VARCHAR(100) DEFAULT 'India'
  ```
- And `api-design-deliverable.md` specifies example payloads containing:
  ```json
  "incidentLocation": "Mumbai, Maharashtra",
  "policeReportNumber": "FIR-2026-001"
  ```
- **Architectural verdict**: Significant portions of the deliverables contain copied or unadapted artifacts that contradict the stated US market scope (ZIP codes, state jurisdictions, US insurance terminology).

---

## 4. Architectural Decisions, Realistic Alternatives, and Trade-offs

| Decision Area | Chosen Approach (Current / Target) | Realistic Alternative | Trade-off Analysis |
|---|---|---|---|
| **Event Publishing & Data Consistency** | Direct async call to `KafkaTemplate` inside `@Transactional` method | **Transactional Outbox Pattern** with Debezium CDC and Kafka Connect | *Current*: Simple to code, but risks dual-write inconsistency and silent event loss on rollback.<br>*Alternative*: Eliminates dual-write, guarantees at-least-once delivery, decouples DB commit from broker availability. Introduces operational overhead of Kafka Connect / Debezium. |
| **API Authorization Architecture** | Keycloak UMA 2.0 dynamic evaluation via synchronous HTTP POST + Caffeine cache | **Self-Contained JWT Claims (RBAC/ABAC)** + Open Policy Agent (OPA) sidecar | *Current*: Centralized authorization rules changeable in Keycloak UI without redeployment; heavy HTTP latency penalty and Keycloak dependency.<br>*Alternative*: Zero-network latency, verifiable offline by Spring Security or API Gateway; requires token refresh to reflect role changes. |
| **Document Ingestion** | Multipart upload proxied through Spring Boot application container | **Direct-to-S3 Pre-signed URLs** with S3 EventBridge notifications | *Current*: Tightly controls validation in Java; chokes container memory and bandwidth.<br>*Alternative*: API issues short-lived pre-signed PUT URL; client uploads directly to S3. S3 triggers Lambda for virus scanning and metadata registration. High throughput, zero compute burden. |
| **Analytics & Reporting Pipeline** | Scheduled 60s in-database SQL `COUNT(*)` aggregations on OLTP schema | **Event-Driven CQRS Read-Model** or Streaming ETL into Amazon Redshift / ClickHouse | *Current*: Zero extra infrastructure, but degrades OLTP performance at scale.<br>*Alternative*: Event consumers incrementally update materialized reporting views in DynamoDB or stream events via Kinesis Firehose into ClickHouse/Redshift. Isolates analytical workloads completely. |
| **Monolith to Microservices** | Modular Monolith deployed as single container with shared database | **Domain Microservices** with Database-per-Service and gRPC / Kafka inter-service communication | *Current*: Fast deployment, zero network boundary overhead, easy local development; high coupling and blast radius.<br>*Alternative*: Fault isolation, independent deployment cadence, tailored scaling per domain; high operational complexity and eventual consistency challenges. |
| **Customer Identity** | AWS Cognito for customers + Keycloak for internal workforce | **Unified Keycloak Cluster** or **Okta / Auth0 Enterprise** | *Current*: Optimizes cost for 200M users while providing UMA for staff; creates dual-IdP operational complexity and split session tracking.<br>*Alternative*: Single identity pipeline simplifies frontend integration, but self-hosting 200M users in Keycloak requires massive infrastructure investment. |

---

## 5. Senior Architect Evaluation Questions

### Question 1: Distributed Consistency & Dual-Write
"In your claim submission flow, you persist the claim to PostgreSQL and immediately publish a `ClaimCreated` event to Kafka inside the same transaction block. How do you guarantee consistency between PostgreSQL and Kafka during database deadlocks, broker downtime, or container crashes, and how would you redesign this for zero event loss at 200M scale?"

### Question 2: Identity & Fine-Grained Authorization at Scale
"You chose a dual-identity architecture using AWS Cognito for 200M customers and Keycloak with UMA 2.0 for internal staff. Your implementation executes a synchronous HTTP check to Keycloak on every cache-miss. How will this behave under peak traffic with 10,000 requests/sec, and how would you achieve sub-5ms authorization decisions without creating a single point of failure?"

### Question 3: High-Throughput Binary Document Ingestion
"The solution approach documents state that document binaries bypass the API via pre-signed S3 URLs, yet the controller accepts `MultipartFile` streams directly. What are the operational failure modes of proxying file uploads through ECS Fargate containers at peak volume, and how would you architect an end-to-end asynchronous, virus-scanned, WORM-compliant ingestion pipeline?"

### Question 4: Database Boundaries and Microservices Extraction
"Your architecture diagrams depict independent microservices owning their respective data stores, yet your code shows `PaymentApplicationService` and `WorkshopApplicationService` executing cross-schema SQL joins on `claims.claims`. How does this leak affect your ability to extract these services, and what data modeling patterns would you introduce to decouple them?"

### Question 5: Real-Time Analytics vs. OLTP Engine Protection
"Your reporting module runs a scheduled background job executing full table scans with `COUNT(*)` and conditional filters on `claims.claims` every 60 seconds. What is the impact of this query pattern as the table reaches 10 million rows, and how would you re-architect reporting to deliver real-time KPIs without touching the transactional write path?"

### Question 6: Disaster Recovery, Multi-Region Replication, and RPO/RTO
"Your DR strategy specifies an Active-Passive architecture across `us-east-1` and `us-west-2` with an RTO of 1 hour and RPO of 15 minutes. In the event of a total AWS region failure in `us-east-1`, walk me through the exact failover sequence for Route 53, Aurora Global Database, MSK MirrorMaker 2, and S3 replication. What data is at risk during the 15-minute window, and how do you reconcile split-brain situations?"

---

## 6. Question Preparation Packages: Answers, Deep Dives, and Follow-ups

### Preparation Package 1: Distributed Consistency & Dual-Write

#### Concise Answer
"The current implementation suffers from the dual-write anti-pattern. If the database transaction fails after the event is sent, or if Kafka is down during commit, the system becomes inconsistent. To resolve this, I would implement the **Transactional Outbox Pattern** using an `outbox` table within the same ACID transaction, combined with **Debezium Change Data Capture (CDC)** reading the PostgreSQL Write-Ahead Log (WAL) to publish events to Kafka with guaranteed at-least-once delivery."

#### Deeper Technical Answer
"In a distributed architecture, invoking external network calls (Kafka producer) within a database transaction boundary violates the ACID contract. If `kafkaTemplate.send()` succeeds but PostgreSQL transaction commit fails due to a serialization error or constraint violation, an orphan event is dispatched. If Kafka broker acknowledges slowly, the open DB connection is held open, exhausting HikariCP connection pools.

To fix this:
1. **Outbox Table**: Create an `outbox_events` table in the `claims` schema. When `submitClaim()` runs, the claim entity and an outbox record (containing payload, aggregate ID, event type, and timestamp) are inserted in the exact same relational transaction.
2. **Log-Based CDC**: Deploy Debezium via Kafka Connect to tail the PostgreSQL Write-Ahead Log (WAL) using `test_decoding` or `pgoutput` logical replication.
3. **Kafka Delivery**: Debezium reads the WAL changes asynchronously and publishes them to the `claim-events` topic with partition key set to `claimId`.
4. **Idempotent Consumers**: Downstream consumers enforce deduplication using transactional Redis keys (`SETNX`) or an inbox deduplication table in PostgreSQL before executing state changes."

#### Trade-offs
- *Transactional Outbox via CDC*: Provides rock-solid consistency, zero dual-write window, and low application latency (DB write only). However, it adds operational complexity: managing Kafka Connect clusters, monitoring replication slot disk growth, and handling schema migrations.
- *Two-Phase Commit (XA Transactions)*: Technically coordinates DB and broker, but introduces high latency, poor scalability, and is unsupported by modern cloud brokers like AWS MSK.

#### Follow-up Questions to Expect
1. *"What happens if the Kafka consumer processes the outbox event twice?"* (Answer: Consumers must be strictly idempotent using deduplication on `eventId` or unique constraint on consumer DB).
2. *"How do you prevent the PostgreSQL WAL from filling up the disk if Kafka Connect halts?"* (Answer: Set up CloudWatch alarms on `pg_replication_slots.active` and disk space; implement automated failover or slot dropping policies).

#### Weak Answers to Avoid
- *"We can just wrap the Kafka call in a try-catch block and rollback the DB if it fails."* (Fatal flaw: Network timeouts can fail the call even when the broker received the message, or DB commit can fail after the try block).
- *"We can use Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)`."* (Flaw: If the server crashes between DB commit and Kafka send, the event is permanently lost).

---

### Preparation Package 2: Identity & Fine-Grained Authorization at Scale

#### Concise Answer
"Making synchronous HTTP round-trips to Keycloak's UMA endpoint on API requests introduces a critical bottleneck and single point of failure. At 200M scale, authorization must be **stateless and decentralized**. I would embed coarse-grained roles and tenant scopes directly within the signed JWT, evaluate fine-grained resource rules in-process using an Open Policy Agent (OPA) sidecar or embedded evaluator, and restrict Keycloak strictly to token issuance."

#### Deeper Technical Answer
"The current reliance on Keycloak UMA 2.0 (`KeycloakAuthorizationService`) sends a POST request with an authorization ticket for every uncached evaluation. Even with Caffeine caching, a cache miss penalty is 20-50ms of network overhead. If Keycloak restarts, the whole API tier cascades into HTTP 500/403 errors.

The enterprise architecture solution:
1. **JWT Claim Enrichment**: At login, Keycloak issues a cryptographically signed RS256/ES256 JWT containing verified user claims: `userId`, `roles`, `region`, and `assignedZipCodes`.
2. **Stateless Local Validation**: Spring Security's `JwtAuthenticationConverter` verifies the signature using the cached public JWKS keys. This requires zero network hops during request execution.
3. **Attribute-Based Access Control (ABAC)**: For context-dependent rules (e.g. 'Surveyor can only access claims assigned to them in their ZIP code'), the business service performs in-memory evaluation against the loaded domain entity:
   ```java
   if (!claim.getAssignedSurveyorId().equals(userContext.userId())) {
       throw new AccessDeniedException();
   }
   ```
4. **Open Policy Agent (OPA)**: For dynamic policies requiring external configuration without code deployment, deploy OPA as an ECS/EKS sidecar. The microservice queries OPA via local localhost Unix domain sockets (< 1ms latency) with policy bundles synchronized asynchronously from AWS S3."

#### Trade-offs
- *Decentralized JWT/OPA*: Sub-millisecond latency, zero external network dependency, high availability. Trade-off: Revocation latency. If a user is deactivated, their JWT remains valid until expiration (e.g. 15 minutes), unless short token TTLs (5 min) paired with refresh token rotation or a distributed Redis blocklist are implemented.
- *Centralized Keycloak UMA*: Immediate policy enforcement and instant revocation, but severe throughput limitations and high operational blast radius.

#### Follow-up Questions to Expect
1. *"How do you handle instant revocation if a rogue surveyor is terminated mid-shift?"* (Answer: Maintain a high-speed Redis distributed revocation set of invalid `jti` or user IDs checked in filter memory, or use short 5-minute JWT lifetimes).
2. *"Why split Cognito and Keycloak instead of using Cognito for everything?"* (Answer: Cognito handles 200M consumer accounts cost-effectively, but lacks Keycloak's native UMA 2.0, enterprise identity brokering, and fine-grained permissions needed for complex staff hierarchies).

#### Weak Answers to Avoid
- *"We can increase the Caffeine cache TTL from 5 minutes to 2 hours."* (Flaw: Severely worsens security drift; permissions revoked in Keycloak won't take effect for hours).
- *"Keycloak will scale easily if we add more memory to the Docker container."* (Flaw: Keycloak cluster synchronization over Infinispan becomes brittle under massive connection loads).

---

### Preparation Package 3: High-Throughput Binary Document Ingestion

#### Concise Answer
"Proxying document binaries through Spring Boot application containers saturates JVM memory and network I/O. I would re-architect this to use **Direct-to-S3 Pre-signed URLs**. The client requests an upload authorization URL from the API, uploads directly to S3 via HTTP PUT, and S3 asynchronously triggers an EventBridge workflow for virus scanning (ClamAV/AWS GuardDuty) and metadata registration."

#### Deeper Technical Answer
"Handling multipart uploads inside ECS Fargate containers causes severe scalability degradation. With 50,000 daily claims and an average of 5 photos/documents per claim (5MB each), the API tier must stream ~1.25 TB of binary data daily. In Spring Boot, buffering multipart streams consumes heap memory, triggers frequent stop-the-world GC pauses, and exhausts container network interfaces.

Production Ingestion Architecture:
1. **Pre-signed URL Handshake**:
   - Client calls `POST /api/v1/documents/presigned-url` with document metadata (filename, content type, SHA-256 hash, claimId).
   - `DocumentService` validates file type (JPEG, PNG, PDF) and size constraint, creates a pending metadata record in PostgreSQL (`status = PENDING_UPLOAD`), and generates an S3 Pre-signed PUT URL using AWS KMS encryption keys with a 15-minute expiration.
2. **Direct Client Upload**:
   - The browser or mobile client uploads the binary directly to the S3 bucket using HTTP PUT. The payload never touches the API containers.
3. **Asynchronous Verification Pipeline**:
   - S3 fires an `ObjectCreated` event to Amazon EventBridge.
   - An AWS Lambda function executes an automated anti-malware scan (AWS GuardDuty or containerized ClamAV).
   - If clean, Lambda invokes `DocumentService` via internal API or Kafka event to mark `status = ACTIVE` and trigger Amazon Textract for OCR.
   - If infected, the object is quarantined or deleted, and an alert is broadcast to the customer.
4. **WORM Compliance**: S3 Object Lock in Compliance Mode prevents deletion or alteration for 7 years."

#### Trade-offs
- *Direct-to-S3 Pre-signed Uploads*: Infinite elasticity, zero API compute/memory overhead, fast uploads. Trade-off: Complex client-side orchestration (two-step upload) and handling abandoned uploads (requires S3 lifecycle rules to purge unverified objects).
- *Server-Side Multipart Streaming*: Simpler single-call client API, immediate validation. Trade-off: Unacceptable compute and network costs at scale.

#### Follow-up Questions to Expect
1. *"How do you prevent a malicious customer from uploading an executable disguised as a PNG to S3?"* (Answer: Validate S3 bucket policies enforcing content-type, run asynchronous MIME-type binary inspection in Lambda, and quarantine files before exposing download URLs).
2. *"How does the frontend know when the document is verified and ready?"* (Answer: S3 EventBridge -> Notification Service -> WebSocket push to the user's browser).

#### Weak Answers to Avoid
- *"We can just increase the ECS Fargate container memory to 16GB and increase Tomcat's max-threads."* (Flaw: Treats the symptom at extreme cost while leaving the network bottleneck intact).
- *"We can store the images as byte arrays (BLOBs) directly in PostgreSQL."* (Fatal flaw: Rapid database bloat, backup nightmares, and I/O collapse).

---

### Preparation Package 4: Database Boundaries and Microservices Extraction

#### Concise Answer
"The current cross-schema SQL queries violate domain isolation and prevent true microservice extraction. To decouple them, I would replace synchronous database joins with **Event-Carried State Transfer (ECST)** and **API Gateway / BFF Aggregation**, where each service maintains a local denormalized read-projection of foreign data synchronized via Kafka domain events."

#### Deeper Technical Answer
"Currently, `PaymentApplicationService` executes:
```sql
SELECT c.approved_amount, wo.final_cost 
FROM claims.claims c 
JOIN workshops.work_orders wo ON wo.claim_id = c.id;
```
If `payments`, `claims`, and `workshops` are separated into independent microservices with isolated physical databases, this SQL join fails immediately.

To decouple this cleanly:
1. **Event-Carried State Transfer (ECST)**:
   - When a claim is approved, the Claims Service emits `ClaimApprovedEvent { claimId, approvedAmount, deductible }`.
   - When a workshop finishes repairs, the Workshop Service emits `RepairCompletedEvent { claimId, finalCost, workshopId }`.
   - The Payment Service consumes both events and maintains a local projection table `payments.claim_billing_context (claim_id, approved_amount, final_cost, ready_to_bill)`.
2. **Local Read Execution**: When calculating the final bill, `PaymentApplicationService` queries its own local table with zero cross-service network calls and zero cross-database queries.
3. **BFF / Gateway Composition**: For user interface views requiring composite data (e.g. claim details + workshop address + document links), the API Gateway or a Backend-For-Frontend (BFF) orchestrates parallel REST calls or uses GraphQL to aggregate responses from individual services."

#### Trade-offs
- *Event-Carried State Transfer*: Complete runtime autonomy, high availability, zero latency coupling. Trade-off: Eventual consistency (slight delay between state changes and projection updates) and data duplication across schemas.
- *Synchronous REST/gRPC Calls*: Immediate consistency. Trade-off: Fragile temporal coupling; if Workshop Service is down, Payment Service cannot calculate bills.

#### Follow-up Questions to Expect
1. *"What if the payment is initiated before the RepairCompletedEvent arrives due to consumer lag?"* (Answer: Enforce business state guards; the payment endpoint verifies that the billing context is in `READY_TO_BILL` status, returning a 409 Conflict if preceding domain milestones have not completed).
2. *"How do you handle schema evolution across microservices?"* (Answer: Confluent Schema Registry with Avro/Protobuf enforcing Backward Compatibility).

#### Weak Answers to Avoid
- *"We can use a distributed database like CockroachDB so all microservices can query all tables."* (Flaw: Re-creates a distributed monolith with shared database anti-pattern).
- *"The services can just make synchronous Feign/REST calls to each other whenever they need data."* (Flaw: Creates synchronous call cascades, distributed deadlocks, and latency compounding).

---

### Preparation Package 5: Real-Time Analytics vs. OLTP Engine Protection

#### Concise Answer
"Running scheduled `COUNT(*)` aggregations against the transactional write database introduces severe I/O contention. I would replace the polling job with a **Streaming CQRS Projection**. Kafka domain events will be consumed by a dedicated reporting pipeline that updates pre-aggregated summary counters in Redis or DynamoDB in real-time, while historical analytics are offloaded to **Amazon Redshift** via Amazon Kinesis Firehose."

#### Deeper Technical Answer
"Executing `COUNT(*) FILTER (...)` across millions of rows every 60 seconds triggers sequential table scans, thrashing PostgreSQL's `shared_buffers` and forcing disk reads. This directly degrades write performance for core claim submissions.

Architecture for Scalable Analytics:
1. **Real-time Operational Metrics (CQRS)**:
   - Instead of a polling job, deploy `ClaimEventReportConsumer` listening to `claim-events`.
   - When `ClaimCreatedEvent` arrives: Increment `submitted_today` and `total_claims` counters in Redis using atomic `HINCRBY`.
   - When `ClaimStatusChangedEvent` arrives: Atomically decrement old status counter and increment new status counter.
   - Dashboard endpoints read pre-computed metrics directly from Redis in < 2ms without issuing a single SQL query to PostgreSQL.
2. **Historical & Executive Analytics (OLAP Data Warehouse)**:
   - Stream all Kafka events into Amazon Kinesis Data Firehose.
   - Firehose micro-batches events, converts them to Parquet format, and writes them to an Amazon S3 Data Lake.
   - Amazon Redshift / AWS Athena queries the Parquet lake for heavy management reporting, fraud ageing, and regional trend analysis.
3. **Database Read Replicas**: If relational queries are mandatory, direct all reporting queries to an Aurora Read Replica configured with PgBouncer connection pooling, completely isolating the write primary."

#### Trade-offs
- *Streaming CQRS + Redis*: Sub-millisecond response times, zero load on transactional database. Trade-off: Must handle out-of-order events and build snapshot reconciliation scripts in case of Redis cache loss.
- *OLTP Periodic Aggregations*: Zero extra services to maintain. Trade-off: Database write lock contention, slow dashboard load times, and hard scalability ceiling.

#### Follow-up Questions to Expect
1. *"How do you recover if the Redis analytics counters drift out of sync with the database?"* (Answer: Run an offline nightly reconciliation batch job against an Aurora read replica to recalibrate counters).
2. *"Why not use PostgreSQL Materialized Views?"* (Answer: Refreshing materialized views requires exclusive locks or heavy background worker I/O unless incremental view maintenance (IVM) is supported).

#### Weak Answers to Avoid
- *"We can just add more composite indexes on `claims.claims`."* (Flaw: More indexes increase write overhead and do not prevent table scans for complex multi-condition counts).
- *"We can run the scheduled job every 15 minutes instead of every 60 seconds."* (Flaw: Makes dashboards stale while still causing periodic CPU/IO spikes).

---

### Preparation Package 6: Disaster Recovery, Multi-Region Replication, and RPO/RTO

#### Concise Answer
"Achieving an RTO < 1 hour and RPO < 15 minutes across `us-east-1` (Active) and `us-west-2` (Passive) requires automated DNS failover, continuous cross-region data replication, and warm-standby compute. We use **Amazon Route 53 Application Recovery Controller (ARC)** for routing, **Aurora Global Database** with < 1 second storage replication, **MSK MirrorMaker 2** for Kafka mirroring, and **S3 Cross-Region Replication**."

#### Deeper Technical Answer
"Disaster recovery between AWS regions requires a coordinated multi-tier failover sequence:
1. **Data Layer Replication**:
   - *Relational Data*: Amazon Aurora Global Database replicates storage blocks asynchronously from `us-east-1` to `us-west-2` with typical replication latency of < 1 second (far exceeding the 15-minute RPO target).
   - *Object Data*: S3 Cross-Region Replication (CRR) automatically replicates uploaded claim documents and WORM metadata.
   - *Event Streams*: Apache Kafka MirrorMaker 2 continuously replicates topic partitions and consumer group offsets to `us-west-2`.
2. **Compute Readiness (Warm Standby)**:
   - ECS Fargate task definitions are maintained in `us-west-2` running at minimal scale (e.g. 1 task per service).
3. **Failover Execution Runbook (Target RTO < 30 min)**:
   - Step 1: Health check failure triggers Amazon Route 53 Application Recovery Controller (ARC).
   - Step 2: Promote Aurora Global Database secondary in `us-west-2` to standalone read-write primary (takes < 2 minutes).
   - Step 3: Trigger Application Auto Scaling on ECS Fargate in `us-west-2` to scale tasks to production capacity (takes 3-5 minutes).
   - Step 4: Update Route 53 routing control to flip traffic to the `us-west-2` Application Load Balancer.
4. **Data Reconciliation for In-Flight Transactions**:
   - Any transactions committed in `us-east-1` within the sub-second replication window that failed to reach `us-west-2` are identified via reconciliation audits once `us-east-1` recovers, matching payment gateway webhooks against database records."

#### Trade-offs
- *Active-Passive with Aurora Global Database*: Low operational complexity, zero risk of cross-region distributed deadlocks, near-zero RPO. Trade-off: DR infrastructure incurs idle standby costs (~40% of primary).
- *Active-Active Multi-Region*: Near-zero RTO. Trade-off: Extreme complexity managing multi-master conflicts, cross-region write latencies, and distributed data consistency.

#### Follow-up Questions to Expect
1. *"What happens to Kafka consumer group offsets when you fail over to the secondary region?"* (Answer: MirrorMaker 2 synchronizes offset mappings via the `remote-offsets` topic, allowing consumers in region B to resume processing from approximately the same offset).
2. *"How do you test this DR strategy without impacting production?"* (Answer: Conduct quarterly non-disruptive DR drills using Route 53 weighted canary routing and isolated test VPCs).

#### Weak Answers to Avoid
- *"We take hourly database backups and restore them in the secondary region."* (Fatal flaw: Restoring a multi-terabyte Aurora snapshot takes several hours, completely violating the 1-hour RTO and 15-minute RPO).
- *"Route 53 will switch automatically and everything will just work."* (Flaw: Ignores database promotion, DNS TTL caching in clients, and compute scaling latency).

---

## 7. Architecture vs. Implementation Gap Analysis

A rigorous audit of the repository reveals significant divergences between what was planned in design deliverables and what was actually constructed in the codebase.

| Architecture / Design Document Specification | Actual Implementation in Codebase | Severity | Technical Impact & Remediation |
|---|---|---|---|
| **System Pattern**: Distributed microservices deployed as independent services with dedicated databases | **Modular Monolith**: Single Spring Boot JAR (`eclaims-api`) packaging all 11 modules with a shared database instance | Medium | Appropriate for POC stage, but design documents prematurely claim microservice boundaries. Plan formal extraction in Phase 2. |
| **Document Ingestion**: Direct-to-S3 Pre-signed URLs bypassing API containers (`solution-approach.md`) | **Server-side Multipart**: Binary streams proxied through `DocumentController` via `MultipartFile` | High | High risk of memory exhaustion and network saturation under load. Implement pre-signed URL generator. |
| **Workflow Engine**: Camunda 8 SaaS BPMN 2.0 process orchestration (`solution-approach.md`, `techstack-dar.md`) | **Custom Java Logic**: Zero Camunda dependencies; custom `AutoAssignmentService` with basic if-else logic | Medium | Lack of visual BPMN process tracking and SLA timer escalations. Integrate Camunda Zeebe client in Phase 2. |
| **Notification Engine**: Node.js NestJS microservice (`solution-architecture.mmd`, `solution-approach.md`) | **Spring Boot Java Module**: `modules/notifications` is 100% Java Spring Boot | Low | Contradiction between documentation and code. Documentation must be corrected to reflect Java ecosystem uniformity. |
| **Frontend Architecture**: Next.js App Router with Server-Side Rendering (SSR) & Redux Toolkit | **Vite React SPA**: Client-side single page app using TanStack Query + Zustand | Medium | SEO is irrelevant for authenticated portal, but first-contentful-paint on mobile may suffer without SSR. Update DAR. |
| **Reporting Architecture**: Amazon Redshift + Kafka stream consumers updating materialized views | **Scheduled Polling**: `ClaimKpiSnapshotRefreshJob` runs `COUNT(*)` table scans on OLTP DB every 60s | High | Will cause OLTP database degradation at scale. Must implement event-driven counter updates. |
| **Security - Authorization**: Stateless OIDC tokens with RBAC configurable without code | **Synchronous Keycloak UMA**: HTTP POST to Keycloak per authorization check + fail-open vulnerability | High | Extreme throughput bottleneck and security risk. Migrate to stateless JWT claims and fix fail-open bug. |
| **Database Security**: Row-Level Security (RLS) and column-level encryption via `pgp_sym_encrypt` | **Standard PostgreSQL Tables**: No RLS policies, no `pgp_sym_encrypt` in SQL init scripts | High | PII data stored in cleartext in database. Implement AWS KMS field-level encryption or Hibernate Envers/Vault. |
| **Fraud Detection**: Amazon SageMaker ML-based scoring pipeline (`solution-approach.md`) | **Hardcoded Rule Engine**: 4 static rules in `FraudDetectionService` (which also contradict its own Javadoc) | Medium | Phase 1 rule engine is functional, but claims of ML readiness are premature. Harmonize Javadoc and code. |
| **Geographic Alignment**: US Auto Insurance provider serving 200M+ policyholders across US states | **Indian Localization Artifacts**: DB script has `DEFAULT 'India'`, API spec has `Mumbai, Maharashtra` | Medium | Inconsistent documentation artifacts. Clean up all sample data and defaults to US addresses and ZIPs. |
| **Testing & Quality Assurance**: >= 80% unit test coverage, Testcontainers integration tests, SonarQube | **Minimal Tests**: Only 2 test classes exist in the entire repo (`ClaimStateMachineTest`, `ClaimsArchitectureTest`) | Critical | High production risk. Zero integration tests for payments, workflow, notifications, or document modules. |

---

## 8. Stress-Testing Architectural Assumptions

### 8.1 Scalability Assumptions at 200 Million Users
- **The Stated Assumption**: The platform serves 200M policyholders with 4M to 10M annual claims (50,000 peak claims per day).
- **The Challenge**: 
  - *Database Sizing*: 10M claims/year * 20KB metadata = 200GB/year. With 5 documents per claim at 5MB = 250TB/year in S3. The storage numbers are manageable, but the transactional write volume during catastrophe events (e.g. hail storms or hurricanes in a major US region) will surge from 50k to 500,000 claims/day.
  - *Connection Pool Exhaustion*: Aurora PostgreSQL max connections top out around 4,000-5,000. If 50 ECS Fargate tasks run with HikariCP pools of 50 connections each, connection exhaustion will occur. **Mitigation**: Must introduce AWS RDS Proxy to pool and multiplex database connections.

### 8.2 High Availability and 99.99% Availability Target
- **The Stated Assumption**: 99.99% uptime (< 52.6 minutes of downtime per year).
- **The Challenge**:
  - The design relies on Keycloak, Redis, Kafka, and PostgreSQL. In a single-region deployment, the composite availability of 4 chained components with 99.9% individual SLA is:
    \[ 0.999 \times 0.999 \times 0.999 \times 0.999 = 99.6\% \]
    This equates to ~35 hours of annual downtime!
  - **Mitigation**: To achieve four nines (99.99%), the core ingestion path must be decoupled from real-time dependencies: write to Amazon API Gateway / SQS queue first, allowing claim submission even if downstream database or identity nodes are undergoing maintenance.

### 8.3 Disaster Recovery and 15-Minute RPO
- **The Stated Assumption**: RTO < 1 hour, RPO < 15 minutes via active-passive DR.
- **The Challenge**:
  - If MSK Kafka replication relies on MirrorMaker 2 across regions, consumer offset synchronization is asynchronous and subject to replication lag during high network traffic. In an ungraceful failover, duplicate message consumption or missed events can occur.
  - **Mitigation**: All Kafka consumers must be strictly idempotent with durable deduplication state replicated across regions.

### 8.4 Consistency vs. Latency (CAP Theorem Reality)
- **The Stated Assumption**: Strong consistency in financial claims with sub-5000ms latency.
- **The Challenge**:
  - The system attempts ACID transactions locally while delegating workflow and notifications to eventual consistency over Kafka. Because there is no Transactional Outbox, the system achieves neither strong consistency nor guaranteed eventual consistency.
  - **Mitigation**: Implement the Outbox pattern with Debezium to provide mathematically provable eventual consistency.

### 8.5 Observability at Scale
- **The Stated Assumption**: Distributed tracing via OpenTelemetry / AWS X-Ray and centralized ELK logging.
- **The Challenge**:
  - Logging full JSON payloads and stack traces for 50,000 claims/day across 8 microservices will generate over 50GB of log data daily, leading to massive CloudWatch ingestion and indexing bills.
  - **Mitigation**: Implement trace sampling (e.g. 5% sample rate on 2xx responses, 100% on 4xx/5xx errors) and structured JSON logging with strict PII masking.

### 8.6 Cost Realism
- **The Stated Assumption**: Phase 1 infrastructure OPEX estimated at ~$48K to $99K per year.
- **The Challenge**:
  - Sizing Amazon Aurora Multi-AZ (db.r6g.2xlarge primary + replica), Amazon MSK (3x m5.xlarge brokers), AWS WAF with Shield Advanced ($3,000/month flat fee alone!), ElastiCache Redis cluster, and multi-region S3 replication easily exceeds $120,000 to $180,000 per year before accounting for compute tasks.
  - **Mitigation**: For Phase 1, use AWS WAF standard rules (omitting Shield Advanced until revenue justifies it), start with smaller Aurora instances with storage autoscaling, and size MSK to t3/m5 instances.

---

## 9. 60-Minute Mock Technical Evaluation Discussion Blueprint

**Format**: Panel Technical Evaluation (Interviewer: Chief Enterprise Architect; Candidate: Senior Solution Architect)  
**Tone**: Direct, rigorous, architectural, code-aware.

---

### Phase 1: Architecture Vision, Topology, and Scale (00:00 - 12:00)

**Interviewer**:  
"Welcome. We are evaluating your architectural design for YCompany's digital claims transformation. You are designing for 200 million policyholders across the United States. Walk me through the high-level architecture: explain how a claim travels from a customer's phone to final settlement, and justify your choice of a modular monolith in the POC versus microservices in production."

**Candidate Response**:  
"Thank you. The architecture is structured around bounded contexts defined by insurance domain boundaries: Claims Lifecycle, Document Management, Workforce Routing, Workshop Collaboration, Payments, and Reporting.

At runtime, the customer submits their claim via our React PWA or mobile app. Traffic resolves through Route 53 to CloudFront and AWS WAF, terminating at an Application Load Balancer. The request reaches our Spring Boot backend running Java 21 with virtual threads. We validate the policy against our Policy Service port, persist the claim in our PostgreSQL Aurora database, and emit a `ClaimCreated` event to Apache Kafka. That Kafka event asynchronously triggers our workforce engine to auto-assign a surveyor based on ZIP code workload algorithms, fires notification fan-out via Amazon SES and SMS, and updates our reporting read models.

Regarding the topology choice: we deliberately adopted a Modular Monolith with Hexagonal Architecture for Phase 1 rather than launching with 8 independent microservices. In enterprise transformations, the greatest early risk is misidentifying domain boundaries and paying the distributed system tax: network latency, distributed deadlocks, multi-pipeline CI/CD overhead, and eventual consistency debugging. By enforcing internal package isolation using ArchUnit and keeping domain models free of framework dependencies, each module can be extracted into an independent ECS or EKS service in Phase 2 with zero domain refactoring."

**Interviewer (Probing Question)**:  
"You say you have clean module isolation ready for extraction, but I reviewed your code. In `PaymentApplicationService` and `WorkshopApplicationService`, you execute raw SQL queries that join `claims.claims` and `workshops.work_orders`. If I extract Payments into a separate container with its own database tomorrow, your application will crash. How do you defend that?"

**Candidate Response**:  
"That is an accurate observation of our current POC code. While our entity layer and Java packages respect boundaries, we allowed cross-schema joins in our JDBC read paths for quick query aggregation. 

In our production microservices migration plan, this is addressed via **Event-Carried State Transfer**. The Payments module will subscribe to `claim.approved` and `repair.completed` events, maintaining its own local projection table `claim_billing_context`. When calculating bills, Payments queries its own database locally. This eliminates cross-service SQL joins entirely and ensures that if the Claims service is down, Payments can still calculate workshop invoices autonomously."

---

### Phase 2: Distributed Data, Transactions, and Consistency (12:00 - 25:00)

**Interviewer**:  
"Let's dive into data integrity. In your `ClaimApplicationService.submitClaim()`, you write to PostgreSQL and call `publishClaimCreatedEvent` to Kafka inside a single `@Transactional` method. If PostgreSQL deadlocks on commit after the Kafka message is transmitted, what happens? How do you solve the dual-write problem at 200M scale?"

**Candidate Response**:  
"Under the current implementation, that creates a dual-write failure: an event would be published to Kafka for a claim that was rolled back in the database, causing downstream consumers to fail or notify the user for a non-existent claim.

To solve this for production, we implement the **Transactional Outbox Pattern** backed by **Debezium Change Data Capture**:
1. Within the same local PostgreSQL transaction that inserts the claim, we insert an event envelope into an `outbox_events` table.
2. If the transaction rolls back, neither the claim nor the outbox record is persisted.
3. Debezium, deployed on Kafka Connect, monitors the PostgreSQL Write-Ahead Log (WAL) using logical decoding. It streams committed outbox entries into the `claim-events` Kafka topic with guaranteed at-least-once delivery.
4. Downstream consumers maintain idempotency by recording processed `eventId` keys in Redis using atomic `SETNX` operations or in local consumer deduplication tables. This completely eliminates dual-writes without requiring slow two-phase commit protocols."

**Interviewer (Challenging Question)**:  
"If you have 50,000 peak claims per day and your Kafka topics are configured with only 3 partitions, as shown in your `KafkaConfig.java`, how will your consumer groups keep up with notification and assignment processing?"

**Candidate Response**:  
"Three partitions is an artifact of our local Docker POC environment. In a production Kafka cluster on Amazon MSK, 3 partitions limits consumer parallelism to exactly 3 consumer threads per consumer group.

For production, we size partitions based on target throughput. With 50,000 daily claims, our peak burst rate during severe weather events can reach 2,000 events/minute. We partition `claim-events` into **24 to 32 partitions**, keyed by `claimId` (UUID hash). This guarantees that:
1. All events for any single claim land on the same partition, preserving strict chronological ordering.
2. We can scale our consumer tasks up to 32 instances in ECS Fargate to parallelize heavy workloads like PDF generation, OCR, and notification dispatch without consumer lag."

---

### Phase 3: Identity, Security, and Compliance (25:00 - 38:00)

**Interviewer**:  
"You have a split identity architecture: AWS Cognito for 200M policyholders and Keycloak for internal staff. In `SecurityConfig` and `KeycloakAuthorizationService`, every permission check executes a synchronous HTTP POST to Keycloak's UMA endpoint. At 10,000 requests/second, Keycloak will fall over. Why did you build it this way, and how do you fix it?"

**Candidate Response**:  
"The UMA 2.0 integration was chosen to satisfy the enterprise requirement that internal role permissions must be dynamically configurable in Keycloak without code redeployment. We added a Caffeine cache with a 5-minute TTL to mitigate latency.

However, in an enterprise production environment, this synchronous check is a scalability bottleneck and a single point of failure. The production solution is **Decentralized Authorization**:
1. We embed coarse-grained roles and jurisdictional scopes (e.g. `roles: ["SURVEYOR"], region: "WEST", zipCodes: ["90210", "90211"]`) directly into the signed JWT at login.
2. Spring Security validates the token signature locally using cached public keys (JWKS) with zero network calls.
3. For fine-grained resource logic, we use an Open Policy Agent (OPA) sidecar or native Spring Security expression handlers evaluating claims in memory.
4. If dynamic policy management without code release is mandatory, OPA downloads pre-compiled policy bundles from S3, evaluating them in sub-millisecond local memory."

**Interviewer (Security Deep-Dive)**:  
"I noticed a flaw in your `ClaimAccessPolicyImpl.java`: when `UserContextHolder.current()` returns empty, the method executes a silent `return;`. That is a classic fail-open vulnerability. How did that get past architectural review, and what is your defense-in-depth posture?"

**Candidate Response**:  
"That is a severe bug in the POC authorization implementation that must be corrected immediately. In any security-critical system, access control must be **strictly fail-closed**. If user context is absent, it must throw an `UnauthorisedException` with an immediate audit log emission.

Our complete defense-in-depth architecture consists of:
1. **Network Edge**: AWS WAF blocking SQL injection, cross-site scripting, and unauthorized CIDR blocks.
2. **Gateway Ingress**: API Gateway enforcing JWT signature validation and rate limiting.
3. **Application Layer**: Fail-closed access policies checking resource ownership (`customerId == claim.customerId`).
4. **Data Layer**: Field-level encryption for PII (SSN, banking details) using AWS KMS customer-managed keys.
5. **Non-Repudiation**: Append-only audit logs published to an immutable Kafka `audit-events` topic retained for 7 years."

---

### Phase 4: High-Throughput Ingestion, Storage, and Performance (38:00 - 50:00)

**Interviewer**:  
"Let's look at your document ingestion. Your documentation states that binaries bypass the API via pre-signed S3 URLs, but your actual controller accepts `MultipartFile` uploads directly. Why does this discrepancy exist, and what happens to your ECS tasks if 500 users upload 5MB accident videos simultaneously?"

**Candidate Response**:  
"The current controller code represents a POC shortcut where developers implemented standard multipart uploads to validate file handling on local disk and MinIO.

In production, streaming 500 concurrent 5MB files (2.5GB) through ECS Fargate containers will exhaust heap memory, trigger major garbage collection freezes, and saturate container network interfaces. 

To achieve our NFR of 99% of requests completing in under 5,000ms, we must implement **Direct-to-S3 Pre-Signed URL Uploads**:
1. The frontend calls `POST /api/v1/documents/presigned-url`, sending only file metadata (file name, MIME type, byte size, hash).
2. The API validates the metadata, creates a `PENDING_UPLOAD` row in PostgreSQL, and generates an S3 pre-signed PUT URL with a 15-minute expiration and strict content-type constraints.
3. The client uploads the binary directly to S3 via HTTP PUT.
4. S3 fires an `ObjectCreated` event to Amazon EventBridge, triggering an AWS Lambda function that runs an automated antivirus scan (AWS GuardDuty / ClamAV) and triggers Amazon Textract for OCR.
5. Once verified, Lambda calls our internal API to advance the document status to `ACTIVE`. The API containers never touch binary streams."

**Interviewer (NFR & Database Challenge)**:  
"Your reporting module runs `ClaimKpiSnapshotRefreshJob` every 60 seconds, doing `COUNT(*)` aggregations across the `claims.claims` table. What happens when that table has 10 million rows?"

**Candidate Response**:  
"A full table scan with multiple conditional filters on 10 million rows will take 15 to 30 seconds to execute, consuming 100% CPU on the database and thrashing the buffer cache.

We replace this with **Event-Driven CQRS Read Projections**:
1. We eliminate the scheduled SQL polling job entirely.
2. A dedicated `ReportingConsumer` listens to Kafka `claim-events`. When a claim status changes, it atomically updates pre-aggregated counter hashes in Redis using `HINCRBY`.
3. When managers open the dashboard, the API fetches the pre-aggregated numbers from Redis in under 2 milliseconds.
4. For heavy ad-hoc analytics and regional slicing, data is streamed into Amazon Redshift or queried against Aurora Read Replicas, leaving the transactional write database completely protected."

---

### Phase 5: Disaster Recovery, Operations, and Closing (50:00 - 60:00)

**Interviewer**:  
"Walk me through your Disaster Recovery plan. `us-east-1` goes completely dark. You have an RTO of 1 hour and an RPO of 15 minutes. Walk me through the exact operational sequence to restore service in `us-west-2`."

**Candidate Response**:  
"Our DR strategy is Active-Passive warm standby across `us-east-1` (Primary) and `us-west-2` (DR):
1. **Continuous Replication (Normal Operations)**:
   - Aurora Global Database continuously replicates storage blocks asynchronously to `us-west-2` with sub-second lag.
   - S3 Cross-Region Replication mirrors all claim documents and Object Lock compliance metadata.
   - MSK MirrorMaker 2 continuously replicates Kafka topic partitions and consumer offset mappings.
   - ECS Fargate tasks in `us-west-2` run at a minimal warm footprint (1 task per service).
2. **Failover Execution (< 30 Minutes Total)**:
   - *Minute 0-5*: Amazon Route 53 Application Recovery Controller (ARC) detects primary region failure via health probes.
   - *Minute 5-10*: We trigger Aurora Global Database failover, promoting the secondary cluster in `us-west-2` to standalone read-write primary (takes ~1-2 minutes).
   - *Minute 10-15*: Application Auto Scaling scales up ECS Fargate tasks in `us-west-2` from 1 to production capacity (e.g. 20 tasks).
   - *Minute 15-20*: Route 53 updates DNS routing controls, switching client traffic to the `us-west-2` Application Load Balancer.
   - *Minute 20-30*: MSK consumers in `us-west-2` resume event processing from synchronized offset positions.
3. **Reconciliation**:
   - Because Aurora Global Database replication latency is typically < 1 second, the actual data loss is well within our 15-minute RPO. Any pending payment gateway webhooks during the switch are replayed from Stripe/gateway logs once the system stabilizes."

**Interviewer (Wrap-up Evaluation)**:  
"You have demonstrated strong grasp of both the high-level architecture and the low-level code implementation. You correctly identified the critical gaps: the dual-write hazard, the synchronous UMA bottleneck, the database-level coupling between bounded contexts, and the upload proxying flaw. 

Your proposed remedies - Transactional Outbox via Debezium, direct-to-S3 pre-signed URLs, decentralized JWT/OPA authorization, and event-driven CQRS read models - reflect true senior solution architect proficiency. Thank you."

---

## 10. Summary and Architectural Next Steps

| Workstream | Immediate Priority (Sprint 1-2) | Phase 2 Production Migration |
|---|---|---|
| **Data Integrity** | Implement Transactional Outbox table in `claims` and `payments` | Deploy Debezium CDC on Kafka Connect |
| **Security** | Fix fail-open check in `ClaimAccessPolicyImpl.java` to fail-closed | Migrate from synchronous Keycloak UMA to stateless JWT claims + OPA |
| **Ingestion** | Build pre-signed S3 URL endpoint in `DocumentController` | Configure S3 EventBridge -> Lambda anti-malware pipeline |
| **Service Decoupling** | Replace cross-schema JDBC joins with event-carried state transfer | Split modular monolith into independent ECS/EKS microservices |
| **Analytics** | Replace 60s scheduled SQL table scan with Redis atomic counter updates | Stream events to Amazon Redshift / Athena for business intelligence |
| **Quality** | Write comprehensive integration tests using Testcontainers | Establish automated security gating (OWASP Dependency-Check, Trivy) |
