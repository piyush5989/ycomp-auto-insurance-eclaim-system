# eClaims Technical Discussion - Executive Summary & Interview Pitch

## 1. 30-Second Elevator Pitch

"eClaims is an enterprise digital claims platform designed for YCompany, a US auto insurance carrier serving over 200 million policyholders. The system replaces a 45 to 60-day paper-based, cheque-only process with a digital workflow operating under 15 days. 

For the POC, I engineered a high-performance modular monolith built on Java 21 with Virtual Threads, Spring Boot 3.2, PostgreSQL multi-schema architecture, Kafka-compatible event streaming via Redpanda, Redis idempotency caching, Keycloak with dynamic UMA 2.0 authorization, and a responsive React 18 TypeScript frontend. 

The architecture enforces strict hexagonal boundaries verified by ArchUnit, guaranteeing an evolutionary path to independent microservices with zero domain code refactoring. The production blueprint scales to 200M+ users on AWS with sub-5000ms p99 latency, 99.99% availability, and multi-region disaster recovery with RTO under 1 hour and RPO under 15 minutes."

---

## 2. 2-Minute Architectural Walkthrough

"When approaching this problem, I split the challenge into two complementary facets: the Target Production Architecture required for 200 million users, and a fully functional Proof of Concept (POC) that validates every core business and technical requirement.

### Business Transformation
The legacy process suffered from long cycle times (45-60 days), manual field reports, no electronic payment capability, zero real-time customer visibility, and lack of fraud detection. 
eClaims transforms this by providing three dedicated portals:
1. Customer Portal: Self-service claim submission, workshop selection, vehicle drop-off tracking, rental vehicle booking, and digital payments with PDF receipts.
2. Internal Portal: Role-specific workspaces for Surveyors (field inspection), Adjustors (adjudication), Case Managers (reassignment and overrides), Auditors (immutable compliance trail), and Management (regional and KPI dashboards).
3. Workshop Portal: Partner repair shops manage work orders, update repair milestones with photos, and submit final invoices.

### Architectural Decisions
- Architecture Style: In production, the system is designed as an event-driven microservices ecosystem on AWS ECS Fargate migrating to EKS. For the POC, I implemented a Modular Monolith. Each domain context (Claims, Workflow, Documents, Workshops, Payments, Reporting, Notifications) resides in its own Maven module and isolates its persistence to its own PostgreSQL schema.
- Concurrency & Performance: Enabled Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`). This provides high I/O throughput matching reactive runtimes like WebFlux without sacrificing the imperative programming model or breaking ThreadLocal contexts like MDC correlation IDs.
- Dynamic Security & Authorization: To meet the strict assignment requirement that role permissions must be configurable without code changes, I integrated Keycloak UMA 2.0 (User-Managed Access). Endpoints evaluate permissions dynamically via `@authz.isAllowed(resource, scope)` with Caffeine caching.
- Event-Driven Backbone: All critical business state transitions publish domain events to Kafka (Redpanda). Downstream services consume these asynchronously with Redis SETNX deduplication, ensuring idempotency and decoupling long-running processes.
- Financial Integrity: Payments enforce a 24-hour Redis idempotency window and recalculate final bills server-side to prevent client-side tampering.
- Compliance: Document storage supports S3/MinIO with SHA-256 checksum verification and an immutable append-only audit trail in PostgreSQL and Kafka."

---

## 3. Key Business Metrics & ROI Targets

| Metric | Current Manual Process | eClaims Target State | Improvement |
|--------|------------------------|----------------------|-------------|
| Claims Settlement Cycle | 45-60 days | 10-15 days | 75% faster |
| Claims Processing Cost | Baseline ($100%) | Target 15% of baseline | 85% cost reduction |
| Customer Satisfaction (CSAT) | 2.1 / 5.0 | 4.5 / 5.0 | +114% improvement |
| Fraud Detection Accuracy | 15% (manual checks) | 85% (Rule Engine + ML) | 467% improvement |
| System Availability | 95% (business hours) | 99.99% (24x7) | Near-zero downtime |
| Payment Disbursement | Cheque by post (7-10 days) | Instant electronic ACH/Card | Real-time settlement |

---

## 4. Portals & Role-Based Access Control (RBAC) Matrix

| User Role | Target Portal | Primary Responsibilities | Data Scope |
|-----------|---------------|--------------------------|------------|
| CUSTOMER | Customer Portal (`/customer`) | Submit claims, select workshop, drop off car, pay bill, view status | Own claims only |
| SURVEYOR | Internal Portal (`/internal`) | Inspect damaged vehicle, assess damage amount, upload photos | Assigned claims only |
| ADJUSTOR | Internal Portal (`/internal`) | Review assessment & evidence, approve or reject claim with amount | Assigned claims only |
| CASE_MANAGER | Internal Portal (`/internal`) | Oversee claim queue, reassign surveyor/adjustor, manual override | All claims (Region/All) |
| AUDITOR | Internal Portal (`/internal`) | Read-only inspection of claim history and system audit events | All claims (Read-only) |
| REGIONAL_MGR | Internal Portal (`/internal`) | Monitor regional claim volumes, cycle times, and payout totals | Regional data only |
| TOP_MANAGEMENT | Internal Portal (`/internal`) | Executive cross-region KPI dashboards, fraud ageing matrix | Nationwide aggregated |
| WORKSHOP | Workshop Portal (`/workshop`) | Accept vehicles, create work orders, post repair status, final bill | Linked workshop claims |

---

## 5. Technology Stack Summary

- Backend: Java 21, Spring Boot 3.2.5, Spring Security 6, Spring Data JPA, Hibernate, MapStruct, Lombok.
- Frontend: React 18.3, TypeScript 5.4, Vite 5.3, TailwindCSS, TanStack React Query v5, React Router v6, keycloak-js.
- Database: PostgreSQL 16 (Multi-schema: claims, documents, workflow, workshops, payments, reporting, audit, customers, notifications).
- Caching & Idempotency: Redis 7 (Alpine), Caffeine local cache (authz decisions).
- Messaging: Redpanda (Kafka 3.x compatible API, zero-ZooKeeper).
- Identity & Access: Keycloak 24 (OpenID Connect, OAuth2, UMA 2.0).
- Storage: MinIO (S3-compatible) with fallback to local filesystem.
- Testing & Verification: JUnit 5, AssertJ, ArchUnit 1.3, k6 performance load testing, Testcontainers.
- CI/CD & Containers: Docker, Docker Compose, AWS CodeBuild, AWS CodeDeploy, Blue-Green canary deployments.
