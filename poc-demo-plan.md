# eClaims POC — End-to-End Demo Readiness Runbook

> **Goal:** One document to get from cold machine → clean DB → running app → demo-verified,
> and to record what still needs to be wired for a real cloud pipeline.

---

## Quick Status Snapshot

| Item | State | Notes |
|---|---|---|
| Backend build | Ready | `mvnw clean verify` |
| Frontend build | Ready | `tsc && vite build` |
| Docker infra | Ready | `docker-compose.yml` — 6 infra services with health checks |
| Docker app stack | **Added** | `docker-compose.app.yml` — backend + frontend containers |
| Backend Dockerfile | **Added** | Multi-stage, Java 21 JRE, non-root user |
| Frontend Dockerfile | **Added** | Node 20 build → nginx:alpine serve |
| nginx.conf | **Added** | SPA routing + `/api` proxy to backend |
| GitHub Actions CI/CD | **Added** | `.github/workflows/ci.yml` — 4 jobs |
| Keycloak realm | Ready | `eclaims-realm.json` with all 8 roles + users |
| Keycloak JWT fix | **Done** | `KC_HOSTNAME=localhost` added to fix Docker issuer mismatch |
| DB reset script | Ready | `scripts/clean-app-data.ps1` |
| Keycloak UUID reset | Ready | `scripts/reset-keycloak-realm.ps1` |
| Reporting RBAC fix | Done | Replaced UMA `@authz.isAllowed` with `hasAnyRole` |
| All 8 role users | Done | `regionalmgr1` + `topmanagement1` in realm |

---

## Phase 1 — Pre-flight Checks

Run these before anything else to avoid "it compiled on my machine" surprises.

```powershell
# Java 21 required (Virtual Threads)
java -version          # expect: openjdk 21.x

# Node 18+ required (Vite 5 / TypeScript 5)
node --version         # expect: v18.x or v20.x
npm --version

# Docker Desktop running
docker info            # must not error
docker compose version # expect: v2.x
```

---

## Phase 2 — Backend Build & Test

Run from `eclaims-backend/`.

```powershell
cd eclaims-backend

# Full clean build — compiles all 9 modules, runs unit tests, ArchUnit checks
.\mvnw.cmd clean verify

# Skip tests for a faster compile-only check (use when iterating)
.\mvnw.cmd clean install -DskipTests
```

Expected: `BUILD SUCCESS` for all 9 modules:
`shared/kernel`, `shared/contracts`, `modules/claims`, `modules/workflow`,
`modules/documents`, `modules/notifications`, `modules/payments`,
`modules/workshops`, `modules/reporting`, `app/eclaims-api`

**If test failures occur:** Check `target/surefire-reports/*.txt` in the failing module.
Testcontainers tests need Docker running — skip with `-DskipTests` if Docker is not up yet.

---

## Phase 3 — Frontend Build & Type-Check

Run from `eclaims-frontend/`.

```powershell
cd eclaims-frontend

npm install            # only needed once or after package.json changes

npm run type-check     # tsc --noEmit — catches type errors before build
npm run lint           # ESLint with zero-warning policy
npm run build          # tsc && vite build — produces dist/
```

Expected: no TypeScript errors, no ESLint warnings, `dist/` folder created.

---

## Phase 4 — Start Local Infrastructure

Run from `eclaims-backend/`. All 6 services have health checks — wait for all `healthy`.

```powershell
cd eclaims-backend

docker compose up -d

# Watch until all services are healthy (takes ~90s for Keycloak)
docker compose ps
# All Status values should show:  Up (healthy)
```

| Container | Port | Purpose |
|---|---|---|
| `eclaims-postgres` | 5432 | PostgreSQL — app DB + Keycloak DB |
| `eclaims-redpanda` | 9092 | Kafka-compatible broker |
| `eclaims-redpanda-console` | 8082 | Kafka topic UI (watch events live) |
| `eclaims-redis` | 6379 | Cache + idempotency keys |
| `eclaims-mailhog` | 8025 / 1025 | SMTP catcher — see emails at http://localhost:8025 |
| `eclaims-minio` | 9000 / 9001 | S3-compatible storage |
| `eclaims-keycloak` | 8080 | IdP — Admin UI at http://localhost:8080/admin (admin/admin) |

**First-time only or after realm corruption:** Run the UUID-sync reset script to ensure
Keycloak user IDs match the DB seed values:

```powershell
.\scripts\reset-keycloak-realm.ps1
# Takes ~2 min — waits for Keycloak to re-import eclaims-realm.json
```

---

## Phase 5 — Reset Demo Database

Wipe all application data to a clean, predictable state. Workshops and surveyors
are automatically re-seeded.

```powershell
# Postgres only
.\scripts\clean-app-data.ps1

# Postgres + Redis cache flush (recommended before demo)
.\scripts\clean-app-data.ps1 -IncludeRedis
```

---

## Phase 6 — Start the Backend

```powershell
cd eclaims-backend
.\scripts\restart-backend.ps1
# Builds all modules, launches Spring Boot on http://localhost:8090 with local profile
```

Wait for: `Started EclaimsApiApplication in X.XXX seconds`

Verify health:

```powershell
# Liveness
Invoke-RestMethod http://localhost:8090/actuator/health/liveness
# Readiness (all downstream deps must be UP)
Invoke-RestMethod http://localhost:8090/actuator/health/readiness
```

Expected `status: UP` for both. If readiness shows a dependency `DOWN`:
- Postgres DOWN → `docker compose restart postgres`
- Redis DOWN → `docker compose restart redis`
- Kafka DOWN → `docker compose restart redpanda`

---

## Phase 7 — Start the Frontend

```powershell
cd eclaims-frontend
npm run dev
# Vite dev server: http://localhost:5173
```

---

## Phase 8 — Smoke Tests (RBAC + Core Flows)

Use these curl/PowerShell checks to confirm the key fix areas before handing off to demo.

### 8a. Get a bearer token for casemanager1

```powershell
$token = (Invoke-RestMethod `
  -Method POST `
  -Uri "http://localhost:8080/realms/eclaims/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=eclaims-web&username=casemanager1&password=Test%401234"
).access_token
```

### 8b. Reporting RBAC — was blocked, now fixed

```powershell
# Must return 200 with KPI data (previously 403 AccessDeniedException)
Invoke-RestMethod `
  -Uri "http://localhost:8090/api/v1/reports/kpi" `
  -Headers @{ Authorization = "Bearer $token" }
```

### 8c. Top management all-regions report

```powershell
$topToken = (Invoke-RestMethod `
  -Method POST `
  -Uri "http://localhost:8080/realms/eclaims/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body "grant_type=password&client_id=eclaims-web&username=topmanagement1&password=Test%401234"
).access_token

Invoke-RestMethod `
  -Uri "http://localhost:8090/api/v1/reports/regional/all" `
  -Headers @{ Authorization = "Bearer $topToken" }
```

### 8d. OpenAPI — full endpoint catalogue

Open http://localhost:8090/swagger-ui.html — should load without auth.

---

## Phase 9 — Demo Credentials (all 8 roles)

All passwords: `Test@1234`

| Username | Role | Demo actions |
|---|---|---|
| `customer1` | `customer` | Submit claim, upload docs, track status, rental booking, payments |
| `customer2` | `customer` | Second customer — test duplicate claim detection |
| `surveyor1` | `surveyor` | My Assignments, start survey, upload damage photos, submit assessment |
| `surveyor2` | `surveyor` | Second surveyor for WEST region |
| `adjustor1` | `adjustor` | Adjudicate claim, approve/reject, set payout |
| `casemanager1` | `case_manager` | Assign/reassign, KPI dashboard, fraud-ageing report, my-claims report |
| `auditor1` | `auditor` | Read-only audit trail, KPI report |
| `autofix_east` | `workshop` | Accept work order, update repair status |
| `quickrepair_west` | `workshop` | WEST region workshop |
| `regionalmgr1` | `regional_mgr` | Regional KPI report |
| `topmanagement1` | `top_management` | All-regions KPI comparison |

---

## Phase 10 — Run the Full Stack in Docker (all-in-one)

Run both infra and app containers together from `eclaims-backend/`:

```powershell
# First time or after code changes — build + start everything
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build

# Check all containers healthy (~3 min for first build)
docker compose -f docker-compose.yml -f docker-compose.app.yml ps

# Tail logs
docker compose -f docker-compose.yml -f docker-compose.app.yml logs -f eclaims-backend eclaims-frontend

# Rebuild only the app after a code change (infra stays up)
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build eclaims-backend eclaims-frontend

# Stop everything
docker compose -f docker-compose.yml -f docker-compose.app.yml down
```

Access points when running fully in Docker:

| URL | What |
|---|---|
| http://localhost:3000 | Frontend (nginx → React) |
| http://localhost:8090/swagger-ui.html | Backend Swagger UI (direct) |
| http://localhost:8080/admin | Keycloak Admin (admin/admin) |
| http://localhost:8025 | Mailhog (email catcher) |
| http://localhost:8082 | Redpanda Console (Kafka events) |
| http://localhost:9001 | MinIO Console (eclaims/eclaims_dev) |

---

## Phase 11 — GitHub Actions CI/CD

### What the pipeline does

File: `.github/workflows/ci.yml`

```
Push / PR
    ├── backend-ci ─── mvnw clean verify (compile + unit tests + ArchUnit)
    └── frontend-ci ── npm ci → type-check → lint → build
                              │
              (main branch only, after both jobs pass)
                              ▼
              docker-build-push ── build + push to GHCR
                              │     ghcr.io/<owner>/eclaims-backend:sha-XXXXXX
                              │     ghcr.io/<owner>/eclaims-frontend:sha-XXXXXX
                              ▼
                  deploy (poc environment, approval gate)
                      └── placeholder — wire SSH or ECS here
```

### One-time GitHub setup

1. Push the repo to GitHub (if not already there).
2. The `GITHUB_TOKEN` for GHCR push is **auto-provided** — no secrets to add.
3. Create a `poc` environment in **Settings → Environments** and optionally add
   required reviewers to gate the deploy step.
4. Set Keycloak URL for the cloud build in **Settings → Variables → Actions**:

| Variable | Value |
|---|---|
| `VITE_KEYCLOAK_URL` | Your public Keycloak URL |
| `VITE_KEYCLOAK_REALM` | `eclaims` |
| `VITE_KEYCLOAK_CLIENT_ID` | `eclaims-web` |

### Pull and run the CI-built images locally

```powershell
# Login to GHCR (GitHub personal access token with read:packages scope)
echo $YOUR_PAT | docker login ghcr.io -u YOUR_GITHUB_USER --password-stdin

# Pull latest images built by CI
docker pull ghcr.io/<owner>/eclaims-backend:latest
docker pull ghcr.io/<owner>/eclaims-frontend:latest

# Tag as :local so docker-compose.app.yml picks them up without rebuilding
docker tag ghcr.io/<owner>/eclaims-backend:latest eclaims-backend:local
docker tag ghcr.io/<owner>/eclaims-frontend:latest eclaims-frontend:local

# Start (no --build since images are already pulled)
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d
```

---

## Phase 12 — What Still Needs to Be Done for Production Cloud

### 12a. Environment variables for cloud

All connection strings are already 12-factor (env-var driven). Replace these for a cloud environment:

| Variable | Source in prod |
|---|---|
| `SPRING_DATASOURCE_URL` | AWS RDS Multi-AZ endpoint |
| `SPRING_DATASOURCE_PASSWORD` | AWS Secrets Manager via ECS task role |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | AWS MSK endpoint |
| `SPRING_DATA_REDIS_HOST` / `_PASSWORD` | ElastiCache |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Public Keycloak URL |
| `MINIO_ENDPOINT` / `_ACCESS_KEY` / `_SECRET_KEY` | AWS S3 + IAM role |
| `VITE_KEYCLOAK_URL` (build arg) | Public Keycloak URL |

### 12b. Database migration strategy

Replace `ddl-auto: update` with `ddl-auto: validate` and add Flyway:

```xml
<dependency>
  <groupId>org.flywaydb</groupId>
  <artifactId>flyway-core</artifactId>
</dependency>
```

Run a migration ECS task / Kubernetes Job before rolling new app containers.

### 12c. Deploy step options (wire one into ci.yml)

**Option A — VM/bare-metal:** `appleboy/ssh-action` → `docker compose pull && up -d`
**Option B — AWS ECS:** `aws-actions/amazon-ecs-deploy-task-definition`
**Option C — Azure Container Apps:** `azure/container-apps-deploy-action`

Both option stubs are commented out in `.github/workflows/ci.yml`.

---

## Appendix — Port Reference

| Port | Service |
|---|---|
| 5173 | Frontend (Vite dev) |
| 8025 | Mailhog (email UI) |
| 8080 | Keycloak Admin |
| 8082 | Redpanda Console (Kafka topics) |
| 8090 | Spring Boot API |
| 9000 | MinIO S3 API |
| 9001 | MinIO Console UI |
| 9092 | Redpanda Kafka |
