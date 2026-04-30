# eClaims POC – Bill of Materials & Design Guide

> **Purpose**: Define what to actually build and run for the minimal working POC, what to stub/seam for later extraction, and how every architectural concept from `q1-solution-approach.md` maps to a visible, evaluable element of the codebase.

---

## 1. POC Philosophy

| Goal | Strategy |
|------|----------|
| Demonstrate **layers, tiers, modules** | Modular monolith with strict intra-module layering |
| Demonstrate **microservices readiness** | Each module = future service; no cross-module DB access |
| Keep scope feasible | Run 4–5 infra components locally; stub the rest with port interfaces |
| Match solution approach document | Every bounded context from the solution doc is represented as a module |

---

## 2. POC Bill of Materials

### 2.1 Components to Actually Run (Real, Local, Docker)

| Component | Role | How |
|-----------|------|-----|
| **React 18 (TypeScript)** | Customer Portal + Internal Portal + Workshop Portal (3 route groups) | `npm run dev` (Vite) — PWA enabled |
| **Spring Boot 3.x (Java 21)** | Modular monolith backend (all 7 modules); **Virtual Threads enabled** | Single JAR / `mvn spring-boot:run` |
| **PostgreSQL 16** | Primary transactional store; separate schema per module | Docker container (`docker-compose`) |
| **Apache Kafka (Redpanda)** | Async event bus — dot-notation topics (`claim.created`, `audit.event`) | Docker container (Redpanda is a lightweight Kafka drop-in) |
| **Redis 7** | Session store + cache-aside + idempotency store + consumer dedup | Docker container |
| **Keycloak 24** | Identity Provider – OAuth2/OIDC + RBAC; 8 roles pre-seeded | Docker container |
| **Mailhog** | Local SMTP catcher — shows real email notifications in browser UI | Docker container (`localhost:8025`) |

> **Total containers**: 5 (Postgres, Redpanda/Kafka, Redis, Keycloak, Mailhog). All start with `docker-compose up -d`.  
> **Mailhog** is the single highest-impact demo addition — evaluator sees notification emails arrive live at `localhost:8025`.

---

### 2.2 Concepts Demonstrated via Code Seams (Port Interfaces — No Runtime Cost)

These are **not stubbed away**; they are implemented as clean **interfaces/ports** so the evaluator can see *where* the real component would plug in. The implementation behind the port may be a local mock or a no-op for POC.

| Concept | Interface / Port | POC Impl | Real Impl (Production) |
|---------|-----------------|----------|----------------------|
| **Document Storage** | `DocumentStoragePort` | Local filesystem store | AWS S3 via `S3DocumentStorageAdapter` |
| **SMS Notification** | `SmsNotificationPort` | Log-to-console adapter | Twilio adapter |
| **Push Notification** | `PushNotificationPort` | No-op adapter | Firebase FCM adapter |
| **Payment Gateway** | `PaymentGatewayPort` | Mock approval adapter | Stripe adapter |
| **OCR / Document Analysis** | `DocumentAnalysisPort` | Returns dummy metadata | AWS Textract adapter |
| **Policy Management System** | `PolicyServicePort` | Returns seeded test policies | REST call to PMS |
| **Car Rental** | `CarRentalPort` | Stub (Phase 2 placeholder) | Partner API |
| **API Gateway** | Acts as first-class concern in routing layer | Spring Security filter chain | Kong gateway (production) |

---

### 2.3 What Is Out of Scope for POC (Document, Don't Build)

| Item | Why Excluded |
|------|-------------|
| AWS deployment (ECS, MSK, RDS) | Local Docker covers the same concept |
| Terraform / IaC | Not relevant to code evaluation |
| CI/CD pipeline | Mention in README; not built |
| DR / Multi-AZ | Architecture doc covers it |
| ML Fraud Detection | Phase 2 per scope |
| Mobile (React Native) | Web portal sufficient for POC |
| Enterprise SSO / AD | Phase 2 per scope |
| Production Keycloak HA | Single Keycloak node for POC |

---

## 3. Architectural Concepts → POC Coverage Mapping

This is the key table showing the evaluator exactly where each design concern is visible:

| Concept from Solution Approach | Where Visible in POC |
|-------------------------------|----------------------|
| **Multi-layer architecture** | Every module has `presentation / application / domain / infrastructure` packages |
| **Bounded context / module isolation** | 7 modules in `/modules` — no cross-module DB access; only via ports |
| **API & Security layer** | `Spring Security + Keycloak JWT filter` in `shared/security`; 8 RBAC roles enforced |
| **Claims state machine** | `ClaimStateMachine` (hand-coded FSM) in `claims/domain`; state transitions validated + persisted |
| **Event-driven architecture** | Standard `DomainEvent<T>` envelope with `eventId + causationId + version`; Redpanda console shows live flow |
| **Idempotency** | Natural key dedup for claim creation; Redis idempotency key for payments; dedup for Kafka consumers |
| **Document management** | `document` module — upload, metadata store, port for storage backend; local FS for POC, S3 port ready |
| **Workflow / Assignment** | `workflow` module — auto-assignment service with region + availability logic; escalation timers |
| **Notifications** | `notification` module — Kafka consumer; real email via Mailhog (visible at `localhost:8025`) + SMS port |
| **Reporting** | `reporting` module — read model, aggregated KPI endpoint, fraud ageing; Kafka consumer materialises view |
| **Payments** | `payment` module — payment intent + confirm; Redis idempotency key; Stripe port (mock for POC) |
| **Caching** | Redis cache-aside (`@Cacheable` + `@CacheEvict`); key naming convention; 85%+ hit ratio target |
| **Audit / No-repudiation** | Enhanced `AuditEvent` with `oldValue/newValue/ipAddress/userAgent`; Kafka `audit.event` topic; append-only |
| **RBAC configurable without code change** | Keycloak admin console — roles/permissions are JWT claims; `@PreAuthorize` reads claims |
| **PII protection** | Field-level masking via custom Jackson serializers on DTO layer; SSN/bank account masked |
| **Observability** | Structured logs + MDC correlation ID; `/health/liveness` + `/health/readiness` separate probes; Micrometer metrics |
| **Performance targets** | Granular per-operation SLAs (view claim `< 800ms`, submission `< 1500ms`, etc.); K6 load test in `infra/load-tests/` |
| **Scale justification** | Java 21 Virtual Threads enabled; stateless JWT; Redis sessions; documented ECS auto-scaling path |
| **GDPR/CCPA compliance** | `ClaimDataRetentionService` — PII anonymisation on settled claims upon deletion request |
| **API versioning** | All REST endpoints under `/api/v1/...`; contracts versioned in `shared/contracts/events/v1/` |
| **Frontend presentation tier** | React 18 PWA; 3 portal route groups; service layer separation; React Query + Zustand; Husky hooks |
| **Technology stack justification** | `README.md` per module + `docs/architecture-to-poc-map.md` links every decision to solution approach |

---

## 4. Runtime Stack – Local Development

```
docker-compose up
├── postgres:16          → localhost:5432  (claims, documents, audit, users DBs)
├── redpanda             → localhost:9092  (Kafka-compatible; lighter than full Kafka)
├── redis:7              → localhost:6379  (session + cache)
└── keycloak:24          → localhost:8080  (IdP; realm eclaims pre-seeded via import)

Spring Boot API          → localhost:8090  (all modules, single JAR)
React Dev Server         → localhost:5173  (Vite HMR)
```

> **One command to start infra**: `docker-compose up -d`
> **One command to start backend**: `./mvnw spring-boot:run`
> **One command to start frontend**: `npm run dev`

---

## 5. Module-to-Microservice Extraction Path

The evaluator must be able to see this clearly documented (in README and architecture diagram):

```
POC (Modular Monolith)               →  Production (Microservices)
─────────────────────────────────────────────────────────────────
modules/claims         (module)      →  eclaims-claims-service       (Spring Boot app)
modules/documents      (module)      →  eclaims-document-service      (Spring Boot app)
modules/workflow       (module)      →  eclaims-workflow-service      (Spring Boot + Camunda)
modules/notifications  (module)      →  eclaims-notification-service  (Node.js NestJS)
modules/workshops      (module)      →  eclaims-workshop-service      (Spring Boot app)
modules/payments       (module)      →  eclaims-payment-service       (Spring Boot app)
modules/reporting      (module)      →  eclaims-reporting-service     (Spring Boot app)
shared/kernel          (shared lib)  →  eclaims-shared-contracts      (Maven library artifact)
```

Extraction steps per module:
1. Move module to its own Maven project.
2. Replace in-process event bus with Kafka adapter (port stays the same).
3. Replace in-process inter-module calls with REST/Feign (adapter only, port stays the same).
4. Point module at its own database schema.
5. Deploy as independent Docker image.

> **The ports/adapters pattern guarantees zero domain code change during extraction.**

---

## 6. Non-Functional Requirements Coverage in POC

| NFR | POC Evidence | Measurable Target |
|-----|-------------|-------------------|
| **Performance** | Redis cache-aside; async doc upload; granular per-operation SLAs | View claim `< 800ms p95`; submit `< 1500ms p95` |
| **Availability** | Docker health checks; `/health/liveness` + `/health/readiness`; documented multi-AZ | `99.9%` uptime documented |
| **Scalability** | Java 21 Virtual Threads; stateless JWT; Redis sessions; ECS HPA documented | Scale 3× on CPU > 70% |
| **Security (OWASP)** | Spring Security + Keycloak JWT; `@Valid` on all inputs; PII masking on DTOs; no secrets in code | Quarterly pen test plan |
| **Idempotency** | Natural key dedup (claims); Redis idempotency key (payments); consumer dedup (Kafka) | Zero duplicate records on retry |
| **Audit / No-repudiation** | Enhanced `AuditEvent` (oldValue/newValue/ipAddress/userAgent); Kafka `audit.event`; append-only DB | 100% write coverage |
| **Fraud detection** | Rule-based fraud flag in adjudication service; fraud ageing in reporting module | `< 1hr` detection SLA |
| **Observability** | MDC correlation IDs in logs; `/actuator/metrics`; Prometheus alert rules; K6 load test | p99 traced per endpoint |
| **Caching effectiveness** | Redis cache-aside + `@CacheEvict`; key naming convention; Micrometer cache gauge | `> 85%` hit ratio |
| **Compliance / Archival** | Documents + metadata stored; archived flag; 7yr retention policy; `ClaimDataRetentionService` | CCPA/GDPR delete supported |
| **Load handling** | K6 load test script (`infra/load-tests/`); thresholds as code | `p99 < 2500ms` at 200 users |
