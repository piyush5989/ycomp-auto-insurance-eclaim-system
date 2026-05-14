# eClaims Backend - Modular Monolith

Enterprise digital insurance claims processing system.  
Spring Boot 3.x · Java 21 · PostgreSQL · Redpanda/Kafka · Redis · Keycloak

---

## Quick Start

```bash
# 1. Start all infrastructure (Postgres, Redpanda, Redis, Keycloak, Mailhog, MinIO)
docker-compose up -d

# 2. Wait for Keycloak to be healthy (~30–60s), then start backend
# On Windows:
.\mvnw.cmd spring-boot:run -pl app/eclaims-api -am -Dspring-boot.run.profiles=local
# On Linux/macOS:
./mvnw spring-boot:run -pl app/eclaims-api -am -Dspring-boot.run.profiles=local

# 3. Start frontend
cd ../eclaims-frontend && npm install && npm run dev

# 4. (Optional) Run load test (requires k6 installation)
k6 run --env BASE_URL=http://localhost:8090/api/v1 infra/load-tests/claim-submission.js
```

## Access Points

| Service | URL | Credentials |
|---------|-----|-------------|
| Frontend (PWA) | http://localhost:5173 | See demo accounts below |
| Frontend (Docker) | http://localhost:3000 | When using full Docker setup |
| API Docs (Swagger) | http://localhost:8090/swagger-ui.html | — |
| Keycloak Admin | http://localhost:8080/admin | admin / admin |
| Kafka UI (Redpanda Console) | http://localhost:8082 | — |
| Email Inbox (Mailhog) | http://localhost:8025 | — |
| MinIO Console | http://localhost:9001 | eclaims / eclaims_dev |
| Actuator — Liveness | http://localhost:8090/actuator/health/liveness | — |
| Actuator — Readiness | http://localhost:8090/actuator/health/readiness | — |
| Actuator — Prometheus | http://localhost:8090/actuator/prometheus | — |

## Demo Accounts (password: `Test@1234`)

| Username | Role | Portal |
|----------|------|--------|
| `customer1` | Customer | /customer |
| `surveyor1` | Surveyor | /internal |
| `adjustor1` | Adjustor | /internal |
| `casemanager1` | Case Manager | /internal |
| `auditor1` | Auditor | /internal |
| `workshop1` | Workshop | /workshop |

---

## Architecture

### Module Structure

```
modules/
├── claims/         → Claim lifecycle, state machine, fraud detection
├── documents/      → Document upload, metadata, storage port
├── workflow/       → Auto-assignment, escalation, surveyor management
├── notifications/  → Kafka consumers → email (Mailhog) + SMS (stub)
├── workshops/      → Work orders, repair status, partner workshops
├── payments/       → Payment intent, Redis idempotency, gateway port
└── reporting/      → Pre-aggregated KPI read model, fraud ageing

shared/
├── kernel/         → AggregateRoot, exceptions, ApiResponse, CorrelationIdFilter, AuditEvent
└── contracts/      → DomainEvent<T> envelope, v1 event payloads, UserRole enum
```

### Dependency Direction Rule

```
Presentation → Application → Domain ← Infrastructure
```

Domain must never import Spring, JPA, Kafka, or infrastructure packages.  
Enforced by ArchUnit tests in every module.

### Microservice Extraction Path

Each module can be extracted to its own Spring Boot application with zero domain code changes:

| Module | Future Service |
|--------|---------------|
| `modules/claims` | `eclaims-claims-service` |
| `modules/documents` | `eclaims-document-service` |
| `modules/workflow` | `eclaims-workflow-service` |
| `modules/notifications` | `eclaims-notification-service` |
| `modules/workshops` | `eclaims-workshop-service` |
| `modules/payments` | `eclaims-payment-service` |
| `modules/reporting` | `eclaims-reporting-service` |

Extraction steps (per module):
1. Move module to its own Maven project
2. Replace in-process event bus with Kafka adapter (port stays the same)
3. Replace in-process calls with REST/Feign (adapter only, port stays same)
4. Point at its own database schema
5. Deploy as independent Docker image

---

## NFR Coverage

> **Note:** For the complete production architecture, technology stack selection, and enterprise design documents, please see the `design-documents/solution-approach.md` file in the project root.

| NFR | Evidence |
|-----|----------|
| Performance (p99 < 5s) | Per-operation SLAs: view claim < 800ms, submit < 1500ms |
| Virtual Threads | `spring.threads.virtual.enabled: true` in application.yml |
| Idempotency | Natural key dedup (claims) + Redis idempotency key (payments) + event dedup (Kafka consumers) |
| Observability | MDC correlation IDs + `/health/liveness` + `/health/readiness` + Prometheus metrics |
| Security | Keycloak JWT + 8 RBAC roles + @PreAuthorize + PII masking on DTOs + @Valid on all inputs |
| Audit trail | Enhanced AuditEvent (oldValue/newValue/IP/userAgent) → Kafka `audit-events` → append-only DB |
| GDPR/CCPA | `ClaimDataRetentionService.anonymiseCustomerData()` for settled claims |
| Caching | Redis cache-aside + explicit @CacheEvict + 85%+ hit ratio target (Micrometer gauge) |
| Load testing | K6 script in `infra/load-tests/` with p99 thresholds as code |
