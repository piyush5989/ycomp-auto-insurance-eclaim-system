# eClaims Codebase Assessment — Developer & Architect Perspective

| Field | Value |
|---|---|
| Purpose | Stakeholder discussion input — what is actually built vs. what the design documents describe |
| Scope | `eclaims-backend/` (Spring Boot modular monolith) and `eclaims-frontend/` (React SPA) |
| Companion documents | [solution-approach.md](./solution-approach.md) · [techstack-dar.md](./techstack-dar.md) · [database-design-deliverable.md](./database-design-deliverable.md) · [api-design-deliverable.md](./api-design-deliverable.md) · [nfr-summary.md](./nfr-summary.md) |
| Method | Direct code inspection (package layout, ArchUnit tests, controllers, CI workflow, test directories) — not a re-statement of the design docs |

This document is deliberately evidence-based: every claim below is anchored to a file. Where the code does **not** match what a design document describes, that gap is called out explicitly — this is the most useful information for a stakeholder conversation, because it tells you exactly what a production build-out has to add, not just what it has to scale.

---

## 1. Executive Summary

The codebase is a **working proof-of-concept**, built as a **modular monolith** that intentionally mirrors the target microservices decomposition described in `solution-approach.md`. The claims module demonstrates genuine hexagonal (ports-and-adapters) architecture with one enforcing ArchUnit test. Event-driven plumbing (Kafka/Redpanda), Redis-backed idempotency, and Keycloak-based RBAC are real and functioning — not just diagrams.

The three biggest gaps between the design documents and the code, in order of stakeholder relevance:

1. **Test coverage is nearly absent.** Backend: 2 test files total (one domain unit test, one ArchUnit test), no integration tests, no Testcontainers usage despite the dependency being declared. Frontend: zero tests, no test framework installed at all. The NFR summary target of "≥80% unit test coverage" is not met today.
2. **Resilience4j and CI/CD deployment are not implemented**, only declared. No `@CircuitBreaker`/`@Retry`/`@RateLimiter` annotations exist anywhere in the code. The GitHub Actions `deploy` job is a stub that echoes "Deploy target not configured."
3. **Architecture boundary enforcement is inconsistent.** Only the `claims` module has an ArchUnit test, and it only checks intra-module layering — there is no cross-module boundary test, and the composition root (`SecurityConfig`) already reaches into another module's `infrastructure` package, which is exactly the kind of coupling ArchUnit is meant to prevent.

None of this makes the POC unfit for its purpose — it demonstrates the domain model, the event-driven flow, and the UI end-to-end. But a stakeholder sizing a production build-out needs to know these are POC-stage gaps, not solved problems.

---

## 2. Architect Perspective

### 2.1 Two architectures in play

There are effectively two documents worth distinguishing in the conversation:

| | What it is | Where it lives |
|---|---|---|
| **Target production architecture** | Event-driven microservices on AWS (ECS Fargate → EKS, Aurora, MSK, Cognito+Keycloak, Camunda 8, S3+Textract, SageMaker) sized for 200M+ customers | `solution-approach.md`, `techstack-dar.md` — aspirational, not yet built |
| **As-built POC architecture** | Spring Boot modular monolith with 9 bounded-context modules, Docker-composed infra (Postgres, Redpanda, Redis, Keycloak, MinIO, Mailhog) | `eclaims-backend/`, this assessment |

The as-built system is explicitly designed as a **stepping stone**: `eclaims-backend/README.md` documents a "Microservice Extraction Path" mapping each module 1:1 to a future service (`modules/claims` → `eclaims-claims-service`, etc.) with a 5-step extraction recipe per module (own Maven project → Kafka adapter swap → REST/Feign swap → own schema → own image). This is a credible, low-risk migration path *if* the module boundaries hold — see §2.2 for where they already don't.

### 2.2 Boundary discipline: strong in one place, absent elsewhere

The dependency rule stated in the README — `Presentation → Application → Domain ← Infrastructure`, domain never imports Spring/JPA/Kafka — is real and enforced, but **only inside the `claims` module**, via a single ArchUnit test (`modules/claims/src/test/java/com/yclaims/claims/architecture/ClaimsArchitectureTest.java`). It checks:
- `domain` must not depend on `infrastructure`, `presentation`, `org.springframework..`, `jakarta.persistence..`
- `presentation` must not depend on `infrastructure`

No equivalent test exists for `workflow`, `documents`, `payments`, `notifications`, `workshops`, `customers`, `reporting`, or `rentals`. Two concrete consequences an architect should flag:

- **`workflow` has no domain layer at all.** Persistence entities (`AdjustorEntity`, `AssignmentEntity`, `SurveyorEntity`) are used directly from the application layer — it's a transaction-script module, not hexagonal. This is fine for a POC but means the "extract to its own service" recipe is not equally low-risk for every module; `workflow` and `documents` will need domain-layer retrofitting before extraction, `claims` will not.
- **Cross-module coupling already exists at the composition root.** `app/eclaims-api/.../config/SecurityConfig.java` directly imports `com.yclaims.workflow.application.WorkforceProvisioningService` **and** `com.yclaims.workflow.infrastructure.web.WorkforceProvisioningFilter` — i.e., the app module reaches into another module's `infrastructure` package. If microservice extraction happens module-by-module, this coupling has to be resolved first, or `workflow` and the app module become inseparable.

**Stakeholder framing:** the module-per-bounded-context structure is real and a genuine asset, but the "clean extraction" story is currently proven for one module (`claims`) and not yet true for the rest. Recommend budgeting refactoring time per module before counting on the extraction path as a given.

### 2.3 What's substituted vs. deferred vs. missing

`README.md` (root) already carries an honest module-by-module POC-status table (implemented / stub / deferred). The architecturally significant categories for a stakeholder conversation:

| Category | Design doc target | POC reality | Migration risk |
|---|---|---|---|
| Identity (customer) | AWS Cognito, 200M users | Keycloak, same OAuth2/JWT contract | Low — adapter swap only, confirmed same contract |
| Message broker | Amazon MSK | Redpanda (Kafka wire-compatible) | Low — no application code changes needed per README |
| Workflow engine | Camunda 8 BPMN | Hand-rolled `AutoAssignmentService` + enum state guards in `Claim` aggregate (no BPMN, no visual audit trail) | **Medium** — Camunda gives timer escalations and auditor-readable process diagrams that the current code does not have at all |
| Primary DB | Aurora PostgreSQL Multi-AZ | Single PostgreSQL 16 container | Low — schema is portable, but concurrency/failover behavior is unvalidated |
| Fraud detection | Rule engine (P1) + SageMaker ML (P2) | Rule engine only, per `FraudDetectionService` | As planned |
| Document storage | S3 + Object Lock (WORM) + Textract OCR | MinIO (S3-compatible), no OCR | Low storage risk, OCR is genuinely absent (not stubbed) |
| Resilience (circuit breakers/retries) | Resilience4j across services | **Declared in pom, zero usage in code** | **This is not a "swap the adapter" gap — it needs to be written** |
| Schema migrations | Implied production-grade change management | Plain numbered SQL scripts in `infra/db/init/`, no Flyway/Liquibase | **Needs to be introduced before production** — not a deferral, a genuine gap |

### 2.4 Security architecture — matches design intent

This is one area where code and docs align well. `SecurityConfig` implements a stateless OAuth2 JWT resource server with a custom Keycloak role-claim converter, method-level `@PreAuthorize` checks routed through a custom `@authz.isAllowed(resource, action)` bean (used consistently across `ClaimController`, `DocumentController`, `PaymentController`, `NotificationController`, `CustomerProfileController` — 11+ distinct action checks in `ClaimController` alone), with fine-grained regional/role logic in `ClaimAccessPolicyImpl`. The RBAC matrix in `solution-approach.md` §9.2 is a reasonably faithful description of what's enforced in code, not just aspiration.

### 2.5 Recommendation for the stakeholder conversation

Position this POC as validating **three** things well — domain modeling (claims aggregate + state transitions), event-driven flow (Kafka publish/consume with idempotent dedup), and security/RBAC — while being explicit that **testing, resilience engineering, and cross-module boundary enforcement are Phase 1 production work items, not scaling exercises.** The phased roadmap in `techstack-dar.md` §5.2 (Phase 1: months 1–6) already allocates time for infra swap-outs; it should also explicitly allocate time for retrofitting test coverage and resilience patterns, since those don't come for free when swapping Postgres→Aurora or Redpanda→MSK.

---

## 3. Developer Perspective

### 3.1 Backend — module structure

Maven multi-module reactor, group id `com.yclaims`, Java 21, Spring Boot 3.2.5. 12 modules total: `shared/kernel`, `shared/contracts`, 9 bounded-context modules under `modules/`, and the composition root `app/eclaims-api`.

**Claims module** (the reference implementation of hexagonal architecture):

```
modules/claims/src/main/java/com/yclaims/claims/
├── domain/
│   ├── model/       Claim (aggregate root), ClaimId, ClaimStatus, ClaimType, AccidentDetails
│   ├── event/       ClaimSubmittedEvent, ClaimStatusChangedEvent, ClaimAssignedEvent
│   ├── port/out/    ClaimRepository, DomainEventPublisher, PolicyServicePort, WorkshopEmailPort
│   └── service/     FraudDetectionService
├── application/     ClaimApplicationService, ClaimAccessPolicyImpl, command/*
├── infrastructure/
│   ├── persistence/ ClaimEntity, ClaimJpaRepository, ClaimPersistenceAdapter, mapper/
│   ├── event/       KafkaClaimEventPublisher, ClaimWorkflowEventConsumer
│   └── integration/ PolicyServiceStubAdapter, WorkshopEmailJdbcAdapter
└── presentation/    ClaimController, dto/*, mapper/ClaimDtoMapper
```

`ClaimController`'s own header comment states the intended data flow: *"HTTP JSON → DTO → Command → ApplicationService → Domain → Repository"* and *"JPA entities never appear here."* — and that's accurate for this module.

By contrast, **`workflow`** has no `domain/` package — `AdjustorEntity`/`AssignmentEntity`/`SurveyorEntity` live directly under `infrastructure/persistence` and are consumed straight from `application/`. **`documents`** is in between: it has `domain/model/` and a `DocumentStoragePort` with two real adapters (`LocalFileSystemStorageAdapter`, `MinioDocumentStorageAdapter`), but no rich aggregate. New backend contributors should treat `claims` as the pattern to imitate, not assume every module already follows it.

### 3.2 Domain model & state machine

`Claim` (`domain/model/Claim.java`, ~330 lines) is a genuine aggregate root extending `AggregateRoot` (from `shared/kernel`). `ClaimStatus` is an enum of 16 states (`DRAFT` … `ARCHIVED`) with `isTerminal()`/`isActive()` helpers.

One documentation/code mismatch worth knowing before you go looking for it: both `ClaimStatus.java`'s Javadoc and `ClaimStateMachineTest.java`'s comment reference a `ClaimStateMachine` class — **it doesn't exist**. Transitions are guarded inline in `Claim` via a private `requireStatus(expected, operation)` method, called from `beginSurvey()`, `completeSurvey()`, `assignAdjudicator()`, `approve()`, `reject()`, `settle()`, etc. A few transitions (`withdraw`, `reassignSurveyor`) use bespoke multi-status checks instead. Every transition calls `registerEvent(...)` to enqueue a domain event. This is tested in `ClaimStateMachineTest.java` — plain JUnit5 + AssertJ, no Spring context.

### 3.3 Event-driven plumbing

Shared event contract in `shared/contracts/src/main/java/com/yclaims/contracts/`:
- `events/DomainEvent.java` — generic envelope (`eventId`, `eventType`, `aggregateId`, `correlationId`, `payload`)
- `events/v1/*Payload.java` — versioned payload records (`ClaimCreatedPayload`, `SurveyorAssignedPayload`, `WorkshopSelectedPayload`, `PaymentSettledPayload`, etc.)
- `KafkaTopics.java` — centralized topic constants

**Producer:** `KafkaClaimEventPublisher` implements the domain-owned `DomainEventPublisher` port via `KafkaTemplate<String, Object>`, keyed by aggregate id. Failure handling is logging-only (`whenComplete`) — no retry/backoff.

**Consumer:** `ClaimWorkflowEventConsumer` — `@KafkaListener(topics = KafkaTopics.CLAIM_EVENTS, groupId = "claims-workflow-consumer")`, `@Transactional`, dispatches on `eventType` string, and **de-duplicates via Redis `SETNX`** (`kafka:processed:claims:<eventId>`, 24h TTL) before processing — a real idempotent-consumer implementation, not a stub. Equivalent consumers exist in `notifications` (`ClaimEventConsumer`) and an audit listener in the app module (`AuditLogKafkaListener`).

### 3.4 Security & API

`SecurityConfig` (`app/eclaims-api/.../config/`): stateless OAuth2 JWT resource server, `@EnableMethodSecurity(prePostEnabled = true)`, custom `keycloakJwtConverter()` mapping `realm_access.roles` → `ROLE_<X>` authorities. Method security is almost entirely via a custom bean: `@PreAuthorize("@authz.isAllowed('claim', 'submit')")` style checks (11+ in `ClaimController` alone), with one inconsistent spot using plain `hasAnyRole('AUDITOR','CASE_MANAGER')` in `DocumentController` — worth normalizing to one pattern.

All controllers are versioned `/api/v1/...`, documented with SpringDoc/OpenAPI (`springdoc-openapi-starter-webmvc-ui` 2.5.0 — Swagger UI is real, not just described), validated with Bean Validation (`@Valid @RequestBody`, `@NotBlank`/`@NotNull` on DTOs), and wrapped in a shared `ApiResponse<T>` envelope from `shared/kernel`.

### 3.5 Persistence

Standard Spring Data JPA (`*Entity` + `*JpaRepository` + `*PersistenceAdapter`/mapper bridging pattern). **No Flyway or Liquibase** — schema comes from four plain numbered SQL scripts in `infra/db/init/` (`01_schemas_and_base_tables.sql` … `04_seed_data_for_development.sql`). This is fine for a Docker-composed POC but is a genuine gap for production change management, not a deferred infra swap.

Reporting uses a scheduled read-model refresh, not CQRS/event-sourcing: `ClaimKpiSnapshotRefreshJob` (`@Scheduled(fixedDelay=...)`) periodically materializes `ClaimKpiSnapshotRepository`.

### 3.6 Testing — the biggest developer-facing gap

Backend: **exactly two test files exist**, both in `claims`:
- `domain/ClaimStateMachineTest.java` — pure unit test of the aggregate
- `architecture/ClaimsArchitectureTest.java` — the ArchUnit suite

No other module has any tests. `testcontainers-bom` is declared in the root pom's dependency management but **is not used anywhere** — no integration tests exist against real Postgres/Kafka/Redis.

Frontend: **zero tests, no test framework installed** (no Vitest/Jest/Playwright/Cypress/RTL in `package.json`, no `*.test.*`/`*.spec.*` files, no `test` script). `lint`, `type-check`, and `build` are the only automated correctness gates.

### 3.7 CI/CD

`.github/workflows/ci.yml` runs 4 jobs: `backend-ci` (`./mvnw clean verify`, Java 21, surefire report upload), `frontend-ci` (type-check/lint/build), `docker-build-push` (GHCR push on `main`), and `deploy` — which is a **stub**: commented-out "Option A/SSH" and "Option B/ECS" blocks, ending in `echo "Deploy target not configured."`. Builds and packages are automated; deployment is not.

### 3.8 Resilience patterns — declared, not implemented

`resilience4j-spring-boot3` is only added as a dependency in one module's pom (`notifications`). A repo-wide search for `@CircuitBreaker`/`@Retry`/`@RateLimiter`/`@Bulkhead` finds **zero usages**. What *is* real:
- **Payment idempotency**: `PaymentApplicationService.initiatePayment()` — Redis cache key `idempotency:<key>`, validates the cached response matches the same `claimId`, 24h TTL.
- **Kafka consumer idempotency**: as above, Redis `SETNX` dedup.

Everything else (Kafka publish failures, outbound HTTP/gateway calls) is logging-only with no retry/backoff/circuit breaking.

### 3.9 Frontend — structure

71 TS/TSX files, feature-sliced + portal-sliced:

```
src/
├── portals/{customer,internal,workshop}/   layout + routes + pages per portal
├── features/{claims,documents,notifications,profile,workshops}/  api/hooks/types/validation
└── shared/{auth,api,components,types,utils,pages}/
```

**Routing**: React Router v6, three portal roots each behind `<ProtectedRoute requiredRoles={[...]}>` (`src/App.tsx`), role check via `hasAnyRole()` in `shared/auth/roleUtils.ts`.

**Auth**: `keycloak-js` directly (not react-oidc-context) — singleton instance in `shared/auth/keycloakInstance.ts`, React context provider in `KeycloakProvider.tsx` using `check-sso` + silent-check-sso iframe flow, token kept in memory only (explicit inline OWASP XSS-avoidance comment), `updateToken(60)` refresh with forced logout on failure.

**Data/state**: TanStack Query v5 for all server state (typed feature API modules under `features/*/api/`); plain `useState` for local UI state. **Zustand is declared in `package.json` but has zero usages anywhere in `src/`** — dead dependency, worth removing or noting as intentionally reserved.

**API client**: single Axios instance (`shared/api/httpClient.ts`) with a request interceptor injecting the Bearer token + `X-Correlation-ID`, and a response interceptor normalizing errors to `{code, correlationId, statusCode}`. Notably, authenticated document downloads deliberately avoid `<a href>`/`window.open` on the raw URL (which wouldn't carry the Bearer token) and instead fetch as a blob through the interceptor-equipped client.

**Forms**: React Hook Form + Zod (`features/claims/validation/submitClaimSchema.ts`) for the claim submission form; simpler inline edits use plain controlled inputs.

**UI**: No third-party component library — Tailwind CSS with a custom status-color map, plus a small set of genuinely reused primitives (`Badge`/`StatusBadge`, generic `DataTable<T>` explicitly built for reuse across claims queues/work orders/reports).

**Notable flow — document upload**: client-side validation happens before the network call (`ClaimDetailPage.tsx`): MIME allow-list, 5MB cap with a 3MB compression warning, and actual client-side image compression to WebP via `browser-image-compression` before upload — a nice piece of UX engineering worth highlighting to stakeholders as attention to real-world usage (poor mobile connections, large photo uploads).

---

## 4. Consolidated "Documented vs. Implemented" Table

For the stakeholder deck — the fastest way to answer "is this real?":

| Capability | Design doc says | Code shows |
|---|---|---|
| Hexagonal architecture | All modules | Fully in `claims`; partial in `documents`; absent in `workflow` |
| Cross-module boundary enforcement | Implied by "loosely coupled microservices" | One ArchUnit test, intra-module only, in `claims` alone; app module already reaches into `workflow` internals |
| Claim state machine | Dedicated `ClaimStateMachine` | Inline guards in `Claim` aggregate; no such class exists |
| Kafka event-driven flow | Yes | Real — publisher + idempotent consumer with Redis dedup |
| Payment idempotency | Yes | Real — Redis-backed, validated |
| RBAC / Keycloak | Yes | Real — JWT converter + `@PreAuthorize` via custom `@authz` bean across controllers |
| Resilience4j (circuit breakers/retries) | "Circuit Breaker (Resilience4j) per service" | Not implemented — zero annotation usages, dependency added to one module only |
| Schema migrations (Flyway/Liquibase) | Implied | Plain SQL init scripts, no migration tool |
| Unit/integration test coverage ≥80% (NFR target) | Yes | 2 backend test files, 0 frontend tests |
| CI build/package | Yes | Real — 4-job GitHub Actions pipeline |
| CI/CD deployment automation | Blue-green, zero-downtime | Stub job, not implemented |
| Camunda 8 BPMN workflow | Target architecture | Hand-rolled assignment service, no BPMN/visual audit trail |
| S3 Object Lock / Textract OCR | Target architecture | MinIO storage only, no OCR at all |

---

## 5. Discussion Points for Stakeholders

1. **Scope the "hardening" work explicitly.** Test coverage, Resilience4j, and Flyway/Liquibase adoption are not covered by the AWS infra swap-out described in Phase 1 of the roadmap — they need their own line items.
2. **Decide how far to standardize hexagonal architecture before extraction.** `claims` is ready to extract; `workflow` needs a domain layer first. This affects the Phase 2 extraction order and estimate.
3. **Resolve the `SecurityConfig` → `workflow.infrastructure` coupling** before treating module extraction as risk-free.
4. **Camunda 8 adoption is a bigger lift than most other swaps** — it's not an adapter swap, it's new process modeling work replacing bespoke Java logic. Worth confirming this is still in scope given effort/cost tradeoffs.
5. **The frontend has no automated tests at all.** For a customer-facing insurance portal, this is worth flagging as a P1 gap regardless of backend priorities.
