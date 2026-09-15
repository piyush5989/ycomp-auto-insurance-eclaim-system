# eClaims Architecture and System Design Document

## 1. System Architecture Overview

The eClaims platform architecture is designed to reconcile two critical demands:
1. Production Target Blueprint: Engineered to support 200+ million policyholders across the United States with high throughput, 99.99% availability, and strict regulatory compliance.
2. Proof of Concept (POC) Implementation: A fully operational, verifiable modular monolith demonstrating all functional, architectural, and non-functional requirements without the infrastructure burden of running dozens of separate distributed services locally.

```
+------------------------------------------------------------------------------------+
|                                    EDGE LAYER                                      |
|  Route 53 (Global DNS) -> CloudFront CDN -> AWS WAF (OWASP) -> Application LB      |
+------------------------------------------------------------------------------------+
                                          |
                                          v
+------------------------------------------------------------------------------------+
|                              API GATEWAY & IDENTITY                                |
|  Amazon API Gateway (Rate Limiting, Throttling, Routing)                           |
|  Dual Identity: AWS Cognito (200M Customers) + Keycloak HA (Internal Staff)        |
+------------------------------------------------------------------------------------+
                                          |
                                          v
+------------------------------------------------------------------------------------+
|                         MICROSERVICES / RUNTIME LAYER                              |
|  ECS Fargate (Phase 1) -> EKS (Phase 2) running Java 21 + Spring Boot 3.x          |
|  - Claims Service       - Workflow Service (Camunda 8)  - Document Service (OCR)   |
|  - Workshop Service     - Payment Service (Stripe)      - Reporting Service        |
|  - Customer Profile     - Notification Service (NestJS) - Audit Service            |
+------------------------------------------------------------------------------------+
                                          |
                   +----------------------+----------------------+
                   |                                             |
                   v                                             v
+--------------------------------------+     +---------------------------------------+
|          DATA & CACHE LAYER          |     |        EVENT STREAMING BACKBONE       |
| - Aurora PostgreSQL Multi-AZ (ACID)  |     | - Amazon MSK (Managed Apache Kafka)   |
| - DynamoDB (Low-latency NoSQL)       |     | - Topics: claim-events, payment-events|
| - ElastiCache Redis (Cache, Idemp)   |     |   repair-events, audit-events         |
| - S3 + Object Lock (WORM 7-yr)       |     | - Redis SETNX consumer deduplication  |
| - Redshift (Petabyte Analytics)      |     | - Kafka MirrorMaker 2 (Cross-region)  |
+--------------------------------------+     +---------------------------------------+
```

---

## 2. Why Modular Monolith for POC? (Architecture Defense)

A frequent interview trap is: "Why didn't you build 8 separate microservices for the POC?"

Here is the exact architectural defense:
1. Domain Boundary Integrity Before Network Boundaries: Microservices split physical processes; modular monolith splits logical boundaries. Creating distributed systems before validating domain models leads to distributed monoliths (high latency, chatty RPC, distributed transaction failures).
2. Hexagonal Architecture (Ports and Adapters): Every module (`claims`, `workflow`, `documents`, `workshops`, `payments`, `reporting`, `notifications`) is self-contained. The domain layer has zero knowledge of Spring, JPA, Kafka, or HTTP.
3. Automated ArchUnit Guardrails: Architectural rules are checked via continuous testing in `ClaimsArchitectureTest.java`:
   - Domain must not depend on Infrastructure.
   - Domain must not depend on Presentation.
   - Domain must not depend on Spring framework classes.
   - Domain must not depend on JPA (`jakarta.persistence.*`).
   - Presentation must not access Infrastructure directly.
4. Schema-per-Module Data Isolation: Within PostgreSQL, each module owns its dedicated schema (`claims.*`, `workflow.*`, `documents.*`, `workshops.*`, `payments.*`, `reporting.*`, `audit.*`). No module is permitted to execute cross-schema JOINs in its repositories.
5. Zero-Code Refactor Extraction Path: Because domain interactions occur exclusively through domain events (`DomainEvent<T>`) or Java interface ports, extracting any module into a standalone Spring Boot microservice requires:
   - Moving the module folder to an independent repository.
   - Swapping in-memory beans for HTTP/REST or Kafka adapters.
   - Pointing the module datasource to an isolated database instance.
   - Not a single line of domain or business logic needs to change.

---

## 3. Scalability Strategy for 200M+ Users

To support 200M+ policyholders and 50,000 peak daily claims, the system implements multi-tier scaling:

### 3.1 Edge and Traffic Ingress
- CloudFront CDN: Caches static web assets, common lookup tables (workshop directories, vehicle models), reducing origin server load by 70%+.
- AWS WAF + Shield Advanced: Edge mitigation against Layer 7 DDoS, credential stuffing, and OWASP Top 10 exploits.
- API Gateway Rate Limiting: Leaky bucket algorithm prevents API flooding. Unauthenticated endpoints (e.g. self-registration) enforce IP-based rate limiting (10 req/sec).

### 3.2 Compute and Concurrency (Java 21 Virtual Threads)
- Spring Boot 3.2.5 enables Project Loom virtual threads (`spring.threads.virtual.enabled: true`).
- Traditional platform threads allocate ~1MB stack memory per thread, capping standard JVMs at ~2,000 to 5,000 concurrent threads before encountering OutOfMemoryError or CPU thrashing.
- Virtual threads are lightweight user-mode threads managed by the JVM. Millions of virtual threads can be spawned simultaneously. When an I/O operation occurs (DB query, Kafka send, Redis lookup, S3 upload), the virtual thread unmounts from its carrier thread, allowing other work to execute.
- This delivers the concurrency characteristics of reactive frameworks (Node.js, Spring WebFlux) while preserving clean, debuggable synchronous code and preserving ThreadLocal context.

### 3.3 Database Scalability & Read-Write Segregation
- Aurora PostgreSQL Multi-AZ with up to 15 auto-scaling Read Replicas.
- CQRS Pattern in Reporting: Heavy analytics and executive dashboards never query the OLTP `claims.claims` table. Instead, a scheduled process (`ClaimKpiSnapshotRefreshJob`) refreshes the pre-aggregated read model table `reporting.claim_kpi_snapshots` and caches results in Redis.
- Strategic Indexing: Compound and partial indexes optimize common queries:
  - Partial index `idx_claims_assigned_surveyor` indexes only claims in `ASSIGNED` status.
  - Partial index `uq_claims_idempotency_key` ensures idempotency uniqueness without indexing null values.
  - Compound index `idx_claims_status_priority_date` accelerates internal queue sorting.
- Partitioning Strategy: The production `claims.claims` and `audit.audit_log` tables use PostgreSQL declarative range partitioning based on `created_at` (monthly partitions). Partitions older than 1 year are moved to cold tablespaces or archived to S3.

### 3.4 Caching Architecture
- Layer 1: Caffeine Local In-Memory Cache for ultra-frequent, static authorizations (Keycloak UMA decisions cached for 5 minutes).
- Layer 2: Redis Cluster for distributed state:
  - Cache-aside pattern for workshop searches, user profiles, and bill previews.
  - Idempotency key store (24h TTL) for financial transactions.
  - Event deduplication cache (7-day TTL) for Kafka consumers.

---

## 4. Dual Identity & Dynamic Authorization Strategy

A standout architectural design in this solution is the **Dual Identity Strategy**:

```
                                  +-------------------+
                                  | Client / Browser  |
                                  +-------------------+
                                            |
                         +------------------+------------------+
                         |                                     |
                         v                                     v
                 [Customer Portal]                     [Internal / Workshop]
                         |                                     |
                         v                                     v
               +--------------------+               +--------------------+
               |    AWS Cognito     |               |    Keycloak 24     |
               | (200M Policyholders|               | (Internal Staff &  |
               |  Consumer Scale)   |               |  Workshop Partners)|
               +--------------------+               +--------------------+
                         \                                     /
                          \                                   /
                           v                                 v
                     +---------------------------------------------+
                     | Standard OIDC / OAuth2 JWT (RFC 7519)       |
                     | Sub (UUID), Roles, Email, Realm Access      |
                     +---------------------------------------------+
                                           |
                                           v
                     +---------------------------------------------+
                     | Spring Boot API - SecurityFilterChain        |
                     | Stateless Resource Server                   |
                     | Keycloak UMA 2.0 Dynamic Policy Evaluation  |
                     +---------------------------------------------+
```

### Why Dual Identity?
- Consumer Scale: Managing 200M+ policyholders inside Keycloak would demand massive relational database clusters, extensive session storage, and continuous administrative overhead. AWS Cognito is purpose-built for hundreds of millions of consumer identities with zero server management.
- Enterprise Complexity: Internal insurance staff (surveyors, adjustors, case managers, auditors) require fine-grained RBAC, Active Directory federation, and dynamic permission modifications without deploying code. Keycloak excels at identity brokering and dynamic policy engines.
- Unified Application Contract: Both providers issue standard OIDC JWT tokens. The Spring Boot backend consumes tokens uniformly via `SecurityFilterChain` and extracts authorities via `JwtAuthenticationConverter`.

### Keycloak UMA 2.0 (User-Managed Access) Implementation
Assignment Requirement: "Role actions and permissions must be configurable without code changes."

Implementation Mechanism:
- Instead of hardcoding `@PreAuthorize("hasRole('CASE_MANAGER')")` across controllers, the codebase uses:
  `@PreAuthorize("@authz.isAllowed('claim', 'submit')")`
  `@PreAuthorize("@authz.isAllowed('claim', 'read')")`
- The `AuthorizationExpressionBean` delegates to `KeycloakAuthorizationService`.
- `KeycloakAuthorizationService` makes an HTTP call to Keycloak's token endpoint requesting an authorization ticket:
  `grant_type=urn:ietf:params:oauth:grant-type:uma-ticket`
  `permission=resource#scope`
- Keycloak evaluates its internal policies (role-based, time-based, attribute-based) and returns an affirmative or negative decision.
- The decision is cached in Caffeine locally for 5 minutes to eliminate HTTP latency on subsequent requests.
- Administrators can reconfigure permissions, add scopes, or alter role policies in Keycloak Console instantly with zero code redeployment.

### Row-Level Data Isolation (Defense in Depth)
Beyond RBAC, `ClaimAccessPolicyImpl` enforces strict multi-tenant boundary checks:
- Customer: Can only view or modify claims where `claim.customerId == jwt.sub`.
- Surveyor: Can only view or assess claims where `claim.assignedSurveyorId == jwt.sub`.
- Adjustor: Can only adjudicate claims where `claim.assignedAdjustorId == jwt.sub`.
- Workshop: Can only view work orders matching their linked workshop entity ID.
- Privileged Roles: Case Managers, Auditors, Regional Managers, and Top Management are granted wider data visibility.

---

## 5. Event-Driven Messaging Backbone (Redpanda / Kafka)

All asynchronous business processes and cross-context notifications flow through Kafka:

### Kafka Topics Architecture
1. `claim-events`:
   - `claim.created`: Emitted when a new claim is submitted.
   - `workshop.selected`: Customer selects repair partner.
   - `vehicle.droppedoff`: Customer delivers car to workshop. Triggers auto-assignment of surveyor.
   - `surveyor.assigned`: Workflow module assigns surveyor. Updates claim status to ASSIGNED.
   - `claim.status.changed`: Emitted on every state transition (e.g. `SURVEYED`, `APPROVED`, `REJECTED`).
   - `claim.adjudicated`: Emitted when adjustor renders formal decision.
2. `repair-events`:
   - `repair.status.updated`: Workshop updates repair milestones (e.g. in progress, parts delayed, completed).
3. `payment-events`:
   - `payment.settled`: Financial gateway confirms successful transaction. Updates claim to `PAYMENT_PROCESSED`.
4. `audit-events`:
   - Immutable audit stream recording actor, action, timestamp, correlation ID, old value, and new value.

### Consumer Idempotency & Deduplication
In distributed streaming, Kafka guarantees "at-least-once" delivery. Network retries or rebalances can cause identical messages to be received multiple times.
Every consumer in eClaims implements atomic deduplication via Redis:
```java
// Pattern executed by ClaimEventConsumer, AutoAssignmentService, and ClaimWorkflowEventConsumer:
String dedupKey = "event:dedup:" + eventId;
Boolean isNew = stringRedisTemplate.opsForValue()
    .setIfAbsent(dedupKey, "PROCESSED", Duration.ofDays(7));
if (Boolean.FALSE.equals(isNew)) {
    // Duplicate detected - skip without side effects
    return;
}
```

---

## 6. Disaster Recovery & Business Continuity (Multi-Region AWS)

The assignment mandates enterprise disaster recovery. The production design specifies:
- Primary Region: `us-east-1` (N. Virginia)
- Secondary Region: `us-west-2` (Oregon)
- Strategy: Active-Passive Warm Standby

| Disaster Recovery Parameter | Target SLA | Implementation Architecture |
|-----------------------------|------------|-----------------------------|
| Recovery Time Objective (RTO) | < 1 hour | Route 53 DNS failover with 60s health checks; warm ECS standby tasks |
| Recovery Point Objective (RPO) | < 15 minutes | Aurora PostgreSQL cross-region replication lag < 5 min; MSK MirrorMaker 2 |
| Document Durability | 99.999999999% (11 nines) | S3 Cross-Region Replication (CRR) + S3 Object Lock |
| Database Backups | Point-in-time recovery | AWS Backup continuous automated retention for 35 days; Glacier 7 years |
