## eClaims – Technology Decision Notes (Quick Reference)

This note captures the key trade-offs and recommendations for:
- Backend API framework (**Node.js** vs **Spring Boot**)
- Messaging backbone (**RabbitMQ** vs **Apache Kafka**)
- Identity & access (**Keycloak** vs “JWT-only”)

Use this as justification content for **Q1 (Solution Approach)** and as input for **Q3 (DAR)**.

---

## 1) Backend API: Node.js vs Spring Boot

### Important context for this assignment
eClaims is an enterprise insurance domain with:
- complex business rules (coverage, adjudication, overrides, audit)
- workflow orchestration (case manager/surveyor/adjustor handoffs, timeouts)
- strong compliance needs (audit trail, no-repudiation)

### Comparison (practical view)
- **Enterprise maturity**
  - **Node.js**: solid, but often ends up custom-building many “enterprise” concerns.
  - **Spring Boot**: extremely mature in insurance/fintech; deep ecosystem for security, workflows, rules, batch, observability.
- **Workflow/BPMN integration**
  - **Node.js**: possible, but tends to be more glue code.
  - **Spring Boot**: first-class integration patterns (especially with Camunda).
- **Business rules & fraud**
  - **Node.js**: rules engine usually custom or library-level.
  - **Spring Boot**: native fit for rule engines (e.g., Drools / rules frameworks) and policy-driven design.
- **Type safety**
  - **Node.js + TypeScript**: good, but runtime gaps still exist.
  - **Java/Kotlin**: compile-time safety and stronger tooling for large teams.
- **Long-running workflows / sagas**
  - **Node.js**: doable, but easier to get wrong without strong conventions.
  - **Spring Boot**: broad patterns/support in enterprise architecture.

### Recommendation
**Prefer Spring Boot (Java 21 or Kotlin)** for the core eClaims backend because it aligns better with:
- workflow orchestration needs
- compliance/audit/no-repudiation needs
- complex domain logic and rule evaluation

### When Node.js is still a good choice
Node.js is excellent for **stateless, I/O-heavy** services such as:
- notification fan-out service (email/SMS/push)
- edge/API adapter layers
- lightweight partner integrations

---

## 2) Messaging: RabbitMQ vs Apache Kafka

### Key distinction
- **RabbitMQ**: message broker optimized for **task queues**, work distribution, retries, routing.
- **Kafka**: distributed **event log** optimized for durable event streams, replay, and analytics.

### Comparison (eClaims lens)
- **Audit & compliance**
  - **RabbitMQ**: messages are typically consumed and removed; you must build/maintain a separate audit trail.
  - **Kafka**: event retention is a core feature; replay provides strong auditability.
- **Event replay (critical for notifications)**
  - **RabbitMQ**: replay is not a native pattern.
  - **Kafka**: replay is native—great for recovering from downstream failures.
- **Analytics & fraud detection**
  - **RabbitMQ**: requires separate pipelines.
  - **Kafka**: stream processing and multiple independent consumers are natural.
- **Throughput & scale**
  - **RabbitMQ**: very good for queues but not designed as an immutable log at massive scale.
  - **Kafka**: designed for high-throughput event streaming with partitions.

### Recommendation
**Prefer Kafka** as the backbone for claim lifecycle events (e.g., `ClaimSubmitted`, `SurveyUploaded`, `ClaimApproved`, `RepairStatusUpdated`) because it directly supports:
- no-repudiation/audit trail via retained events
- multiple consumers (notifications, reporting, fraud signals)
- replay for resiliency and operational recovery

### When RabbitMQ is still a good choice
RabbitMQ is ideal for:
- internal job queues (send an email, generate a PDF, call an external API with retries)
- short-lived tasks where event history is not the business record

> Practical pattern: **Kafka for domain events**, **RabbitMQ (or SQS) for task queues**.

---

## 3) Identity: Keycloak vs “JWT-only”

### Clarification (common confusion)
**JWT is a token format**, not a complete identity solution.
- You still need an **Identity Provider (IdP)** to handle user auth, MFA, SSO, user lifecycle, and issuing tokens.
- **Keycloak issues JWTs** (and supports OAuth2/OIDC/SAML). Your services validate those JWTs.

### Comparison
- **“JWT-only” (build it yourself)**
  - Pros: minimal dependencies, quick for a POC
  - Cons: you must implement and operate: MFA, SSO, role management UI, password policies, account lockout, audit logs, session mgmt, token rotation, user provisioning, etc.
  - Risk: conflicts with the requirement that **role actions must be configurable without code changes**.
- **Keycloak (self-hosted IdP)**
  - Pros: admin UI for RBAC & policies, MFA, federation, SSO, standards-based (OIDC/SAML), audit events
  - Pros: works for **on-prem + cloud** (explicit NFR requirement)
  - Cons: requires operating Keycloak (HA, upgrades, backups)
- **Managed IdPs (e.g., AWS Cognito / Auth0 / Okta)**
  - Pros: less operational burden
  - Cons: on-prem support may be limited; cost can scale with MAU; vendor lock-in considerations

### Recommendation
- **Production / architecture doc (Q1):** use **Keycloak** (or a managed IdP if cloud-only is acceptable) to satisfy:
  - RBAC with many roles (Customer, Surveyor, Adjuster, Case Manager, Auditor, Workshop, Regional Manager, Top Management)
  - “configurable without code changes”
  - compliance-grade authentication audit trails
- **POC (Q4):** it’s acceptable to use a simplified **JWT-based auth** implementation for speed, but clearly state:
  - “POC uses JWT for simplicity; production uses Keycloak/Cognito as IdP.”

---

## One-line guidance you can reuse in documents
- **Backend:** “Spring Boot chosen for enterprise workflow + rules + compliance maturity; Node.js reserved for I/O-heavy edge/notification services.”
- **Messaging:** “Kafka chosen as durable event backbone to support auditability, replay, analytics, and fraud detection; queues used only for ephemeral tasks.”
- **Auth:** “Keycloak chosen as standards-based IdP enabling RBAC configurability without code changes; services validate JWTs issued by the IdP.”

