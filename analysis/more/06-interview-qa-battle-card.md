# eClaims Technical Discussion - Q&A Battle Card

This document provides battle-tested answers to the toughest technical and architectural questions anticipated during the evaluation discussion.

---

## Category 1: High-Level Architecture & Scalability (200M+ Scale)

### Q1: "How does your system guarantee the NFR requirement: 99% of requests complete in under 5,000ms for 200M+ users?"
**Answer:**
"We achieve the 5,000ms p99 SLA through a defense-in-depth performance architecture:
1. Edge Offloading: CloudFront CDN caches all static web assets, workshop directories, and reference datasets, absorbing over 70% of inbound web requests at edge locations.
2. Lightweight Concurrency: Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`) eliminate carrier thread bottlenecks during blocking database or network I/O, allowing tens of thousands of concurrent requests without thread exhaustion.
3. Multi-Tier Caching: We use Caffeine for local microsecond authorization decisions (5-minute TTL) and ElastiCache Redis Cluster for shared state (workshop lookups, payment idempotency, and session caching).
4. CQRS Read Models: Heavy reporting queries do not touch the transactional `claims.claims` table. Instead, `ClaimKpiSnapshotRefreshJob` recomputes pre-aggregated summary tables (`reporting.claim_kpi_snapshots`), returning dashboard metrics in under 50ms.
5. S3 Pre-signed URLs: In production, large media and document uploads bypass the API servers entirely, transferring directly from the browser to Amazon S3.
6. Benchmarked Proof: In our k6 load tests (`infra/load-tests/claim-submission.js`), claim submission p99 ran at 1,450ms (well under the 2,500ms target) and claim details view p99 clocked at 320ms (under the 1,200ms target)."

---

### Q2: "Why did you build a Modular Monolith for the POC instead of deploying 8 separate microservices?"
**Answer:**
"A modular monolith is an architectural strategy, not a compromise.
1. Domain Integrity First: Splitting services across physical networks before solidifying domain boundaries leads to 'distributed monoliths' - chatty network hops, distributed locking issues, and cascading failures.
2. Hexagonal Clean Boundaries: Each bounded context (`claims`, `workflow`, `documents`, `workshops`, `payments`, `reporting`, `notifications`) is encapsulated in its own Maven module. Crucially, domain code has zero dependencies on infrastructure, JPA, or Spring, enforced automatically by ArchUnit tests.
3. Schema Isolation: Each module has exclusive ownership over its own PostgreSQL schema. Cross-schema joins in repositories are strictly prohibited.
4. Frictionless Microservice Extraction: Because all inter-module communication occurs via defined Java interface ports or asynchronous Kafka events (`DomainEvent<T>`), extracting any module into a standalone container requires only swapping in-process adapters for HTTP/gRPC or Kafka adapters without changing a single line of business logic."

---

### Q3: "What happens if the primary PostgreSQL database experiences write saturation during a major catastrophe?"
**Answer:**
"During a catastrophe (e.g. widespread hurricane causing 100,000 simultaneous claims):
1. Ingress Throttling: AWS WAF and Amazon API Gateway rate limiters throttle traffic and enforce fair queuing.
2. Asynchronous Buffering: Rather than executing heavy synchronous database writes, claim submissions can be routed into an Amazon MSK buffer. The API returns an immediate acknowledgment with a tracking ticket (HTTP 202 Accepted), while worker consumers persist claims sequentially to Aurora.
3. Read Traffic Offloading: Aurora read replicas (up to 15 nodes) handle all customer queries, status tracking, and internal queues. The writer node is reserved exclusively for state mutations.
4. Connection Pooling: PgBouncer and HikariCP pool connections efficiently, preventing database thread exhaustion."

---

## Category 2: Code Craftsmanship, DDD & Implementation

### Q4: "How is the Claims state machine implemented, and how do you guarantee valid transitions?"
**Answer:**
"The state machine is implemented inside the `Claim` Aggregate Root (`Claim.java`) following strict Domain-Driven Design:
1. Invariants Guarded: State transitions cannot be bypassed via setters. Every transition method (e.g. `assignSurveyor`, `beginSurvey`, `completeSurvey`, `approve`, `settle`) calls `requireStatus(expectedStatus, operationName)`.
2. Encapsulation: The constructor is private; creation is allowed only via factory methods `Claim.submit(...)` and `Claim.reconstitute(...)`.
3. Side-Effect Decoupling: State changes register domain events (`ClaimStatusChangedEvent`, `ClaimAssignedEvent`) inside the aggregate root. The application service saves the aggregate and then publishes the registered events to Kafka, ensuring atomic persistence before external notification."

---

### Q5: "How do you guarantee that a user cannot be double-charged during payment?"
**Answer:**
"We implement a three-tier idempotency and security mechanism in `PaymentApplicationService.java`:
1. Client Idempotency Key: The frontend generates a UUID idempotency key sent in the request header.
2. Redis SETNX Store: We check `idempotency:<key>` in Redis. If the key exists, the cached `PaymentResponse` is returned immediately without contacting the payment gateway or touching the database.
3. 24-Hour Expiration: Keys expire after 24 hours, covering transient network retries and duplicate button clicks.
4. Server-Side Bill Recalculation: We never trust the payment amount passed in the client request. The backend calculates `totalDue = max(0, final_cost - approved_amount) + processing_fee` directly from verified database records before invoking the payment gateway."

---

### Q6: "What is the self-healing workforce provisioning mechanism in your workflow module?"
**Answer:**
"When using external Identity Providers like Keycloak, user UUIDs (`sub`) can drift from seed data if the Keycloak realm is re-imported or flushed.
Rather than requiring manual database intervention, we implemented `WorkforceProvisioningFilter` and `WorkforceProvisioningService`:
1. Upon every authenticated request from a surveyor or adjustor, the filter inspects their JWT.
2. If the user's email exists in `workflow.surveyors` or `workflow.adjustors` but with a different primary key, it detects 'ID drift'.
3. In a single atomic database transaction, it updates all foreign key references (`workflow.assignments`, `claims.claims`), deletes the stale row, and inserts the user with their current Keycloak `sub` as primary key.
4. This ensures that personal queues (`assignedTo=me`) and assignment lookups always resolve correctly without manual data repairs."

---

## Category 3: Security, Identity & Compliance

### Q7: "The assignment requires RBAC permissions to be configurable without code changes. How did you solve this?"
**Answer:**
"Traditional Spring Security hardcodes role checks like `@PreAuthorize("hasRole('CASE_MANAGER')")`, which requires a code deployment whenever permissions change.
We solved this by integrating Keycloak UMA 2.0 (User-Managed Access):
1. Endpoints use dynamic expression checks: `@PreAuthorize("@authz.isAllowed('claim', 'submit')")`.
2. The `KeycloakAuthorizationService` makes an HTTP request to Keycloak's token endpoint requesting an authorization decision for resource `claim` and scope `submit`.
3. Keycloak evaluates policies configured dynamically in the Keycloak Admin Console (e.g. role policies, time-of-day policies, user attribute policies).
4. Decisions are cached in a local Caffeine cache for 5 minutes to maintain low latency.
5. Security administrators can add, revoke, or reassign endpoint permissions inside the Keycloak UI in real time with zero application downtime or redeployments."

---

### Q8: "How do you resolve the legal conflict between GDPR Right to Erasure and the 7-year Insurance Retention mandate?"
**Answer:**
"In `ClaimDataRetentionService.java`, we implement a strict compliance reconciliation policy:
1. Active Claim Regulatory Hold: If a policyholder requests erasure while a claim is open (`SUBMITTED`, `ASSIGNED`, `UNDER_SURVEY`, `UNDER_ADJUDICATION`), the request is rejected. Insurance statutes legally require open claims to be fully investigated.
2. Terminal Claim Anonymization: Once a claim reaches a terminal state (`SETTLED`, `ARCHIVED`, `REJECTED`, `WITHDRAWN`), we fulfill GDPR/CCPA by anonymizing all personally identifiable information (PII) - replacing name, email, phone, and address with `ANONYMISED_<UUID>`.
3. Financial & Claim Audit Preserved: The claim reference, accident metadata, financial payout, and damage reports are retained in PostgreSQL and S3 Object Lock (WORM storage) for 7 years to satisfy state insurance commissioner audits."

---

### Q9: "Where do you store JWT tokens on the frontend, and how do you prevent XSS and CSRF?"
**Answer:**
"1. In-Memory Token Storage: Tokens are never stored in `localStorage` or `sessionStorage`. They exist exclusively in React memory within `KeycloakProvider.tsx`. If an attacker executes an XSS injection, they cannot extract tokens from persistent browser storage.
2. Silent Refresh: An invisible iframe (`silent-check-sso.html`) handles background token refreshes 60 seconds prior to expiration via Keycloak session cookies.
3. CSRF Protection: Because our API is completely stateless and uses `Authorization: Bearer <JWT>` headers (not automatic browser session cookies), standard CSRF attacks cannot forge authenticated requests."

---

## Category 4: Resiliency, Cloud & Operations

### Q10: "Kafka guarantees at-least-once delivery. How do you prevent duplicate notifications or duplicate assignments?"
**Answer:**
"Every Kafka consumer in eClaims (`ClaimEventConsumer`, `AutoAssignmentService`, `ClaimWorkflowEventConsumer`) enforces Redis-backed atomic deduplication:
```java
String dedupKey = "event:dedup:" + event.eventId();
Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedupKey, "PROCESSED", Duration.ofDays(7));
if (Boolean.FALSE.equals(isNew)) {
    log.debug("Duplicate event {} ignored", event.eventId());
    return;
}
```
If Kafka redelivers an event due to a network timeout or consumer rebalance, the duplicate is dropped immediately before any business logic, email, or database mutation executes."

---

### Q11: "How does your deployment handle Disaster Recovery, and what are your RTO and RPO targets?"
**Answer:**
"Our multi-region AWS topology uses an Active-Passive Warm Standby architecture:
- Primary Region: `us-east-1` (Active)
- DR Region: `us-west-2` (Passive / Warm Standby)
- Recovery Point Objective (RPO) < 15 minutes:
  - Aurora PostgreSQL asynchronous cross-region replication keeps replica lag typically under 5 minutes.
  - Amazon MSK MirrorMaker 2 mirrors Kafka topics continuously across regions.
  - Amazon S3 Cross-Region Replication (CRR) replicates uploaded documents within seconds.
- Recovery Time Objective (RTO) < 1 hour:
  - Route 53 DNS Failover with 60-second health check intervals automatically re-routes traffic to the secondary ALB.
  - Standby ECS Fargate services run at minimal capacity (1 task) and auto-scale to full capacity upon failover.
  - Aurora DR replica is promoted to primary writer via automated AWS Systems Manager runbooks."

---

## Category 5: Continuous Improvement & Future Scope

### Q12: "If you had two more weeks to work on this system, what would you enhance?"
**Answer:**
"If given two additional weeks, I would focus on three high-impact production readiness areas:
1. Camunda 8 BPMN Engine Integration: Wire the workflow module to Camunda 8 SaaS to provide visual process tracking and automated SLA timer escalations (e.g. 48-hour surveyor deadline).
2. Direct S3 Pre-Signed Uploads: Migrate document upload from the backend proxy to direct browser-to-S3 pre-signed URLs, eliminating container bandwidth overhead during peak claim surges.
3. AWS SageMaker Fraud Inference: Connect the `FraudDetectionService` to an ML model hosted on SageMaker to analyze historical claims patterns and detect organized fraud rings beyond simple static rules."
