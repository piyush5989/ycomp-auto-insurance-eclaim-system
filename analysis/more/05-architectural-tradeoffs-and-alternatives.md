# eClaims Architectural Trade-offs & Alternatives Analysis

This document details the trade-offs, rationale, and alternatives evaluated for every major technical decision in the eClaims platform. These justifications serve as authoritative talking points during architectural evaluations.

---

## 1. Modular Monolith vs. Distributed Microservices

| Attribute | Modular Monolith (POC Choice) | Distributed Microservices (Production Target) |
|-----------|--------------------------------|------------------------------------------------|
| Deployment Overhead | Single deployable artifact; runs on 1 container or JVM | Dozens of distinct containers, meshes, and pipelines |
| Network Latency | In-memory procedure calls; sub-millisecond | Inter-service network hops (REST/gRPC); 10-50ms latency |
| Data Consistency | Acid transactions within module schemas | Eventual consistency; requires Saga pattern & compensations |
| Refactoring Risk | Low; IDE refactoring and compile-time type safety | High; API contract versioning and breaking changes |
| Team Independence | Best for single agile team (12-15 engineers) | Best for multiple autonomous squads (50+ engineers) |

### Rationale & Defense:
- Why Modular Monolith for POC: Building distributed microservices prematurely introduces distributed transaction complexity, network unreliability, and deployment friction before the domain model is stable.
- The Architectural Compromise: Strict Hexagonal boundaries and package isolation enforced by ArchUnit tests. Communication occurs only through Java interface ports or asynchronous domain events.
- Extraction Tipping Point: Transition to standalone microservices when:
  1. Different modules require distinct scaling ratios (e.g. Claim Submission requires 100x the compute of Workshop Billing).
  2. Separate engineering squads require independent deployment cadences without cross-team merge conflicts.
  3. Failure isolation requires physical memory and process boundaries.

---

## 2. Dual Identity: AWS Cognito vs. Keycloak UMA 2.0

| Decision Driver | AWS Cognito | Keycloak 24 (Self-Hosted) |
|-----------------|-------------|---------------------------|
| Target User Base | 200M+ Policyholders (Customers) | 1,000s Internal Staff & Workshops |
| Operational Burden | Zero; fully managed serverless AWS service | Requires container management, clustering, DB backups |
| Dynamic Permissions | Fixed group and attribute claims; code changes needed | Dynamic UMA 2.0 policies; zero code redeployment |
| Enterprise SSO | Basic OIDC / SAML brokering | Complex Active Directory / LDAP forest federation |
| Cost Profile | Free tier up to 50k MAU; highly cost-effective at scale | Infrastructure costs (compute, database, memory) |

### Rationale & Defense:
- Storing 200M consumer accounts in Keycloak would require huge relational database clusters, massive JVM heap allocations, and dedicated ops teams. Cognito handles hundreds of millions of consumer identities effortlessly.
- Conversely, Cognito lacks User-Managed Access (UMA 2.0) and fine-grained dynamic policy evaluation. Keycloak satisfies the assignment requirement that internal staff role permissions must be modified dynamically without code changes.
- Dual Identity reconciles both needs while exposing standard OIDC JWTs to backend resource servers.

---

## 3. Concurrency Model: Java 21 Virtual Threads vs. Spring WebFlux

| Metric / Dimension | Spring WebFlux (Reactive / Reactor) | Java 21 Virtual Threads (Spring Boot 3.x) |
|---------------------|-------------------------------------|------------------------------------------|
| Programming Model | Functional / Asynchronous (Mono/Flux) | Standard Imperative (blocking I/O syntax) |
| Debugging & Traces | Obfuscated stack traces; context loss across threads | Clean, continuous stack traces with line numbers |
| Context Propagation | Requires manual reactor context passing | ThreadLocal works seamlessly (MDC, SecurityContext) |
| Ecosystem Support | Requires reactive drivers (R2DBC, WebClient) | Works with all existing JDBC, JPA, and blocking libraries |
| Throughput Under Load | Very High (event loop non-blocking) | Very High (virtual threads unmount on blocking I/O) |

### Rationale & Defense:
- Reactive programming introduces high cognitive overhead, "callback hell", and steep learning curves for team onboarding. Furthermore, traditional JDBC/JPA libraries cannot be used without blocking the event loop.
- Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`) deliver the throughput and resource efficiency of reactive frameworks while keeping simple, maintainable synchronous code. ThreadLocal security contexts and SLF4J MDC correlation IDs function without custom reactor context hooks.

---

## 4. Orchestration vs. Choreography: Camunda 8 vs. Kafka Events

| Pattern | Mechanism | Strengths | Trade-offs |
|---------|-----------|-----------|------------|
| Event Choreography (POC) | Asynchronous Kafka domain events (`claim.created`, `vehicle.droppedoff`) | Decoupled services, high throughput, zero centralized bottleneck | Difficult to visualize entire business flow; distributed debugging |
| Workflow Orchestration (Phase 2 Target) | Centralized BPMN 2.0 engine (Camunda 8 SaaS) | Visual workflow models, audit visibility, automated SLA timer escalations | Single orchestrator dependency; potential bottleneck |

### Rationale & Defense:
- In auto insurance claims, the lifecycle takes weeks and involves human intervention (field inspections, approvals, vehicle repairs).
- In the POC, event choreography via Kafka handles all state transitions cleanly and idempotently with Redis deduplication.
- For full enterprise production, Camunda 8 BPMN is planned for Phase 2 to manage long-running human tasks and SLA timer escalations (e.g. auto-escalating to a Case Manager if an inspection is not completed within 48 hours).

---

## 5. Persistence: Multi-Schema PostgreSQL vs. Database-per-Service

| Architecture | Multi-Schema Single DB (POC Choice) | Database-per-Service (Distributed Target) |
|--------------|--------------------------------------|-------------------------------------------|
| Isolation Level | Logical separation via SQL schemas (`claims.*`, `workflow.*`) | Physical separation across distinct database instances |
| Cross-Module Queries | Strictly forbidden by architectural governance | Physically impossible; requires API or event streaming |
| Operational Simplicity | Single database backup, single connection pool, easy local dev | Multiple connection pools, independent backups, high ops cost |
| Schema Migration | Coordinated via consolidated SQL / Flyway scripts | Distributed migrations; independent schema versioning |

### Rationale & Defense:
- In the POC, schemas provide complete logical isolation. Repositories in the `claims` module are barred from querying `workflow` or `payments` tables.
- Cross-schema joins are eliminated in application repositories. Cross-module data aggregation (e.g. reporting) is handled via pre-aggregated snapshot tables populated by scheduled jobs or Kafka event listeners.
- In production, each schema can be promoted to an isolated Aurora PostgreSQL instance with zero changes to module repository interfaces.

---

## 6. Fraud Detection: Rule-Based Engine vs. Machine Learning Scoring

| Dimension | Rule-Based Engine (Phase 1 Implemented) | AWS SageMaker ML Model (Phase 2 Roadmap) |
|-----------|------------------------------------------|------------------------------------------|
| Determinism & Explainability | 100% explainable; precise rule triggers recorded in DB | Probabilistic risk score; requires model explainability (SHAP) |
| Implementation Effort | Low; pure Java domain service (`FraudDetectionService`) | High; data pipeline, feature engineering, model training |
| Regulatory Compliance | Readily approved by insurance commissioners | Requires algorithmic bias auditing and model governance |
| Novel Pattern Detection | Poor; catches only predefined fraud indicators | High; identifies complex fraud rings and subtle anomalies |

### Rationale & Defense:
- Phase 1 implements deterministic business rules: theft claims missing police reports, repeat vehicle claims within 90 days, assessments exceeding 120% vehicle value, and claims exceeding $50,000.
- This provides immediate fraud mitigation on Day 1 without waiting for months of historical training data.
- Phase 2 integrates AWS SageMaker for real-time risk scoring, supplementing the rule engine with deep pattern recognition.

---

## 7. Document Ingestion: Backend Multipart Upload vs. Direct S3 Pre-signed URLs

| Method | Backend Multipart Proxy (POC Implemented) | S3 Pre-signed URLs (Production Target) |
|--------|-------------------------------------------|----------------------------------------|
| Traffic Path | Client -> Application Server -> MinIO / S3 | Client -> Direct to S3 Bucket |
| Server Resource Impact | High memory & network I/O buffering files in backend | Near zero server I/O; backend only generates short-lived URL |
| File Validation | Immediate synchronous validation (MIME, size, virus scan) | Asynchronous validation via S3 ObjectCreated Lambda triggers |
| Simplicity | Simple client-side implementation; single API call | Two-step client flow: request pre-signed URL, then PUT to S3 |

### Rationale & Defense:
- For the POC, backend multipart upload provides immediate validation, synchronous SHA-256 hash generation, and metadata persistence in one request.
- For production serving 200M users with peak document volumes (5MB per claim, 50,000 daily claims = 250GB daily), pre-signed S3 URLs are specified to offload heavy file streaming entirely from backend compute containers.
