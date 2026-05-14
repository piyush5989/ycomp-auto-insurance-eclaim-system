# eClaims POC - Build and Run Guide

This document explains how to **build, run, and validate** the eClaims proof-of-concept after obtaining the source code.

---

## Deliverables (Design Documents)

**[Solution Approach Document (solution-approach.md)](./design-documents/solution-approach.md)**

This master document contains the executive summary and links to all detailed deliverables:
1. **[Technology Stack DAR](./design-documents/techstack-dar.md)** - Comprehensive tech stack selection for 200M+ users
2. **[API Design Specification](./design-documents/api-design-deliverable.md)** - REST API specs and integration patterns
3. **[Database Design Document](./design-documents/database-design-deliverable.md)** - Data model and performance optimization
4. **[Non-Functional Requirements](./design-documents/nfr-summary.md)** - Detailed NFR analysis for enterprise scale
5. **[Project Estimation](./design-documents/project-estimation-deliverable.md)** - Resource planning, timeline, and budget

All architecture diagrams (System Context, Solution Architecture, Deployment, CI/CD, Event-Driven) are embedded within these documents and available as `.svg` files in the `design-documents/` folder.

---

## 1. What you are running

The solution is a **modular monolith** with two main parts:

| Folder | Role |
|--------|------|
| `eclaims-backend/` | Spring Boot API (REST, security, integrations) |
| `eclaims-frontend/` | React (Vite) web application |

Supporting services (database, cache, messaging, identity, mail, object storage) run in **Docker** for a predictable POC environment.

---

## 2. Technology stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| API | **Java 21**, **Spring Boot 3.x** | REST API, business logic, OAuth2 resource server |
| Build (backend) | **Maven** (wrapper: `mvnw`) | Compile, test, package |
| UI | **React 18**, **TypeScript**, **Vite** | Browser SPA |
| Build (frontend) | **Node.js 20**, **npm** | Install deps, type-check, lint, dev server |
| Database | **PostgreSQL 16** | Persistent data |
| Cache | **Redis 7** | Caching, payment idempotency helpers |
| Messaging | **Redpanda** (Kafka-compatible) | Domain events, notifications, audit pipeline |
| Identity | **Keycloak 24** | Login, JWT, roles |
| Mail (dev) | **Mailhog** | Catches outbound email locally |
| Object storage | **MinIO** | Document uploads (S3-compatible) when using the `minio` profile |
| Optional | **Docker Compose** | Orchestrates the stack above |

You do **not** need a cloud account to validate the POC using the steps below. Cloud deployment (for example ECS) is optional.

---

## 3. Prerequisites on your machine

Install the following before you start.

| Software | Recommended version | Notes |
|----------|---------------------|--------|
| **Docker Desktop** (or Docker Engine + Compose v2) | Current stable | Required to start Postgres, Redis, Redpanda, Keycloak, etc. |
| **Java JDK** | **21** (Temurin or Oracle) | Only needed if you run the API **outside** Docker with Maven |
| **Node.js** | **20 LTS** | Only needed if you run the frontend **outside** Docker with `npm run dev` |
| **Git** | Any recent | Optional; only if you clone instead of unzip |

**Operating system:** Windows, macOS, or Linux. On Windows, use **PowerShell** for the provided scripts.

**Hardware:** At least **8 GB RAM** free for Docker is recommended (Keycloak and Redpanda are memory-heavy).

---

## 4. Preparing a clean zip (for deliverables)

While **packaging** this solution for submission, we have remove generated and downloaded artifacts so the archive is small and reproducible. Recipients will re-create these with Docker and Maven or npm.

**Remove or exclude from the zip:**

- `eclaims-backend/**/target/` (Maven build output)
- `eclaims-frontend/node_modules/` (npm dependencies)
- `eclaims-frontend/dist/` (production frontend build)
- Local IDE folders if present (for example `.idea/`, `.vscode/`)
- Any local `.env` files with secrets (do not distribute secrets)

**Keep in the zip:**

- All source under `eclaims-backend/` and `eclaims-frontend/`
- `eclaims-backend/mvnw`, `eclaims-backend/.mvn/`, `eclaims-backend/pom.xml`, and module sources
- `eclaims-frontend/package.json` and `package-lock.json`
- `eclaims-backend/docker-compose.yml`, `eclaims-backend/docker-compose.app.yml`, and `eclaims-backend/Dockerfile`
- `eclaims-frontend/Dockerfile`, `nginx.conf`, `nginx.ecs.conf`
- `infra/` and `.github/` if you want CI references included

After unzip, the user follows **Section 5** or **Section 6** below.

---

## 5. Recommended path - full stack in Docker

This path builds **API and UI images** and runs them **with** all infrastructure. Best for a quick end-to-end check without installing Java or Node on the host (Docker still required).

### 5.1 Start everything

Open PowerShell (Windows) or terminal (macOS/Linux) and run:

```bash
cd eclaims-backend
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build
```

**First run:** image builds can take **several minutes**. Wait until containers are healthy (especially Keycloak, often **1 to 2 minutes** after Postgres is ready).

**Database initialization:** The database is automatically initialized with schema and seed data on first startup via the consolidated SQL scripts in `infra/db/init/`.

### 5.2 URLs to validate

| What | URL | Notes |
|------|-----|--------|
| **Web app** | http://localhost:3000 | React UI (nginx in Docker) |
| **API (direct)** | http://localhost:8090 | Spring Boot |
| **Swagger** | http://localhost:8090/swagger-ui.html | API documentation |
| **API readiness** | http://localhost:8090/actuator/health/readiness | Should report healthy when dependencies are up |
| **Keycloak** | http://localhost:8080 | Admin console: `admin` / `admin` (POC only) |
| **Mail (inbox)** | http://localhost:8025 | Mailhog UI |
| **Kafka UI** | http://localhost:8082 | Redpanda Console |
| **MinIO console** | http://localhost:9001 | POC credentials in compose files |

### 5.3 Stop everything

```bash
cd eclaims-backend
docker compose -f docker-compose.yml -f docker-compose.app.yml down
```

To also remove volumes (wipes database and MinIO data):

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml down -v
```

---

## 6. Alternative path - Docker infra only, API and UI on the host

Use this if you prefer debugging in the IDE with **Java 21** and **Node 20** installed.

### 6.1 Start infrastructure only

```bash
cd eclaims-backend
docker compose up -d
```

Wait until Keycloak is healthy (**about 30 to 60 seconds** after start).

### 6.2 Run the backend

```bash
cd eclaims-backend

# On Windows PowerShell:
.\mvnw.cmd spring-boot:run -pl app/eclaims-api -am -Dspring-boot.run.profiles=local

# On Linux/macOS:
chmod +x mvnw
./mvnw spring-boot:run -pl app/eclaims-api -am -Dspring-boot.run.profiles=local
```

Leave this terminal open. The API listens on **http://localhost:8090**.

### 6.3 Run the frontend

Open a **second** terminal:

```bash
cd eclaims-frontend
npm install
npm run dev
```

Open **http://localhost:5173** in a browser (Vite default port).

### 6.4 Stop

- Stop the backend and frontend with **Ctrl+C** in each terminal.
- Stop Docker infra: `cd eclaims-backend && docker compose down`

---

## 7. How to validate that the setup works

1. **Readiness:** Open http://localhost:8090/actuator/health/readiness. Expect a **UP** status when Postgres, Redis, and Redpanda are reachable.
2. **Swagger:** Open http://localhost:8090/swagger-ui.html and confirm the page loads.
3. **UI:** Open http://localhost:3000 (Docker path) or http://localhost:5173 (host path). You should see the login or landing experience backed by Keycloak.
4. **Demo users:** Use these accounts (all with password **`Test@1234`**):
   - `customer1` - Customer Portal
   - `surveyor1` - Internal Portal (Surveyor role)
   - `adjustor1` - Internal Portal (Adjustor role) 
   - `casemanager1` - Internal Portal (Case Manager role)
   - `workshop1` - Workshop Portal
   - `auditor1` - Internal Portal (Auditor role)

For deeper backend documentation (modules, NFRs, architecture), see **`eclaims-backend/README.md`**.

---

## 8. Database reset and clean setup

If you encounter issues or want a completely fresh database state:

### 8.1 Quick database reset (PowerShell)

For a clean database without rebuilding containers:

```powershell
cd eclaims-backend
.\reset-db-simple.ps1
```

This script stops containers, removes the database volume, and restarts everything with fresh data.

### 8.2 Full reset with interactive options

For more control over the reset process:

```powershell
cd eclaims-backend
.\reset-and-init-db.ps1
```

This provides options to reset just the database or also reset the Keycloak realm.

### 8.3 Reset Keycloak realm only

If you need to reload Keycloak permissions without touching the database:

```powershell
cd eclaims-backend
.\scripts\reset-keycloak-realm.ps1
```

---

## 9. Common problems

| Symptom | What to check |
|---------|----------------|
| Port already in use | Stop other apps using **3000**, **5173**, **5432**, **6379**, **8080**, **8090**, **9092** |
| Keycloak or API not ready | Wait longer; check `docker compose ps` and container logs |
| Database connection errors | Use database reset script: `.\reset-db-simple.ps1` |
| Permission denied errors (Windows) | Run PowerShell as Administrator |
| `mvnw` permission denied (Linux/macOS) | Run `chmod +x mvnw` |
| `npm install` fails | Use **Node 20**; delete `node_modules` and retry |
| "Failed to load claim" errors | Reset Keycloak realm: `.\scripts\reset-keycloak-realm.ps1` |
| Database constraint violations | Run full database reset: `.\reset-and-init-db.ps1` |
| Containers not starting | Ensure Docker Desktop is running; try `docker compose down -v` then restart |

### 9.1 Detailed troubleshooting

**Database issues:**
- If you see constraint violations or "column does not exist" errors, run `.\reset-db-simple.ps1` for a fresh database
- The database schema is automatically created from consolidated SQL files in `infra/db/init/`

**Authentication issues:**
- "Could not load claim" or permission errors usually indicate Keycloak realm issues
- Run `.\scripts\reset-keycloak-realm.ps1` to reload the realm with correct permissions

**Container startup issues:**
- Keycloak takes 1-2 minutes to become healthy after Postgres is ready
- Check container logs: `docker compose logs keycloak` or `docker compose logs eclaims-backend`
- Ensure at least 8GB RAM is available for Docker

---

## 10. Build without running (compile check)

**Backend (includes tests):**

```bash
cd eclaims-backend
./mvnw clean verify
```

**Frontend:**

```bash
cd eclaims-frontend
npm ci
npm run type-check
npm run lint
npm run build
```

---

## 11. Further reading

- **Evaluation Deliverables:** `design-documents/solution-approach.md` (Master document for the assessment)
- **Backend architecture and demo accounts:** `eclaims-backend/README.md`

If anything in this guide does not match your unzip layout, ensure your archive root contains **`eclaims-backend`** and **`eclaims-frontend`** as sibling folders next to this `README.md`.
