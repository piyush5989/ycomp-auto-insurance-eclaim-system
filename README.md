# eClaims POC - Build and Run Guide

This document explains user to **build, run, and validate** the eClaims proof-of-concept after obtaining the source (for example by unzipping a delivery archive).

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
| **Docker Desktop** (or Docker Engine + Compose v2) | Current stable | Required to start Postgres, Redis, Kafka, Keycloak, etc. |
| **Java JDK** | **21** (Temurin or Oracle) | Only needed if you run the API **outside** Docker with Maven |
| **Node.js** | **20 LTS** | Only needed if you run the frontend **outside** Docker with `npm run dev` |
| **Git** | Any recent | Optional; only if you clone instead of unzip |

**Operating system:** Windows, macOS, or Linux. On Windows, use **PowerShell** or **WSL** for shell commands shown below.

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

Open a terminal and run:

```bash
cd eclaims-backend
docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build
```

**First run:** image builds can take **several minutes**. Wait until containers are healthy (especially Keycloak, often **1 to 2 minutes** after Postgres is ready).

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
chmod +x mvnw   # Linux or macOS only; on Windows Git Bash may need this
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

1. **Readiness:** Open http://localhost:8090/actuator/health/readiness (or the same path through nginx on port **3000** only if that route is enabled in your build). Expect a **UP** status when Postgres, Redis, and Kafka are reachable.
2. **Swagger:** Open http://localhost:8090/swagger-ui.html and confirm the page loads.
3. **UI:** Open http://localhost:3000 (Docker path) or http://localhost:5173 (host path). You should see the login or landing experience backed by Keycloak.
4. **Demo users:** Use the accounts listed in `eclaims-backend/README.md` (password **`Test@1234`**) to sign in through Keycloak and exercise a role-based portal.

For deeper backend-only documentation (modules, NFRs, demo matrix), see **`eclaims-backend/README.md`**.

---

## 8. Common problems

| Symptom | What to check |
|---------|----------------|
| Port already in use | Stop other apps using **3000**, **5173**, **5432**, **6379**, **8080**, **8090**, **9092** |
| Keycloak or API not ready | Wait longer; check `docker compose ps` and container logs |
| `mvnw` permission denied | Run `chmod +x mvnw` (Unix-like shells) |
| `npm install` fails | Use **Node 20**; delete `node_modules` and retry |
| Windows path issues | Run commands from the folder that contains `eclaims-backend` and `eclaims-frontend` as shown |

---

## 9. Build without running (compile check)

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

## 10. Further reading

- **Backend architecture and demo accounts:** `eclaims-backend/README.md`
- **ECS and CI/CD (optional):** `infra/ecs/README.md` and `.github/workflows/ci.yml`

If anything in this guide does not match your unzip layout, ensure your archive root contains **`eclaims-backend`** and **`eclaims-frontend`** as sibling folders next to this `README.md`.
