# eClaims POC – Local Development Stack

> **Purpose**: Define the complete local runtime environment for the POC. One `docker-compose up` command brings up all infrastructure; the backend and frontend run natively for fast development.

---

## Runtime Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        LOCAL DEVELOPMENT STACK                      │
│                                                                     │
│  Browser                                                            │
│    │  http://localhost:5173                                         │
│    ↓                                                                │
│  React Dev Server (Vite PWA)  ── Proxy /api → localhost:8090       │
│                                                                     │
│  Spring Boot API  (Java 21 Virtual Threads)                         │
│    │  localhost:8090                                                │
│    ├── Keycloak     localhost:8080  (Identity / JWT / RBAC)         │
│    ├── PostgreSQL   localhost:5432  (Primary DB — 7 schemas)        │
│    ├── Redpanda     localhost:9092  (Kafka-compatible event bus)    │
│    ├── Redis        localhost:6379  (Cache + Sessions + Idempotency)│
│    └── Mailhog      localhost:1025  (SMTP — catches all emails)     │
│                                                                     │
│  Redpanda Console   localhost:8082  (Kafka topic UI — live events)  │
│  Keycloak Admin     localhost:8080/admin  (roles, users)            │
│  Mailhog UI         localhost:8025  (see notifications arrive live) │
│  Swagger UI         localhost:8090/swagger-ui.html                  │
│  Actuator Health    localhost:8090/actuator/health/readiness        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## `docker-compose.yml`

```yaml
version: "3.9"

services:

  # ─── PostgreSQL ────────────────────────────────────────────────────
  postgres:
    image: postgres:16-alpine
    container_name: eclaims-postgres
    environment:
      POSTGRES_DB: eclaims
      POSTGRES_USER: eclaims
      POSTGRES_PASSWORD: eclaims_dev
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./infra/db/init:/docker-entrypoint-initdb.d   # Schema init SQL
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U eclaims"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ─── Redpanda (Kafka-compatible, no ZooKeeper, lightweight) ────────
  redpanda:
    image: redpandadata/redpanda:latest
    container_name: eclaims-redpanda
    command:
      - redpanda start
      - --overprovisioned
      - --smp 1
      - --memory 512M
      - --reserve-memory 0M
      - --node-id 0
      - --kafka-addr PLAINTEXT://0.0.0.0:9092
      - --advertise-kafka-addr PLAINTEXT://localhost:9092
    ports:
      - "9092:9092"    # Kafka API
      - "9644:9644"    # Redpanda Admin API
    healthcheck:
      test: ["CMD-SHELL", "rpk cluster health | grep -E 'Healthy:.+true'"]
      interval: 15s
      timeout: 10s
      retries: 5

  # ─── Redpanda Console (Kafka UI) ───────────────────────────────────
  redpanda-console:
    image: redpandadata/console:latest
    container_name: eclaims-redpanda-console
    ports:
      - "8082:8080"
    environment:
      KAFKA_BROKERS: redpanda:9092
    depends_on:
      redpanda:
        condition: service_healthy

  # ─── Redis ─────────────────────────────────────────────────────────
  redis:
    image: redis:7-alpine
    container_name: eclaims-redis
    ports:
      - "6379:6379"
    command: redis-server --requirepass redis_dev
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "redis_dev", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ─── Mailhog (local SMTP catcher — shows notification emails in browser) ──
  mailhog:
    image: mailhog/mailhog:latest
    container_name: eclaims-mailhog
    ports:
      - "8025:8025"    # Web UI — open localhost:8025 to see all emails
      - "1025:1025"    # SMTP — Spring Mail points here
    healthcheck:
      test: ["CMD", "wget", "-q", "--spider", "http://localhost:8025"]
      interval: 10s
      timeout: 5s
      retries: 3

  # ─── Keycloak ──────────────────────────────────────────────────────
  keycloak:
    image: quay.io/keycloak/keycloak:24.0
    container_name: eclaims-keycloak
    command: start-dev --import-realm
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
      KC_DB: postgres
      KC_DB_URL: jdbc:postgresql://postgres:5432/eclaims
      KC_DB_USERNAME: eclaims
      KC_DB_PASSWORD: eclaims_dev
      KC_HOSTNAME_STRICT: "false"
      KC_HTTP_ENABLED: "true"
    ports:
      - "8080:8080"
    volumes:
      - ./infra/keycloak/eclaims-realm.json:/opt/keycloak/data/import/eclaims-realm.json
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres_data:
```

---

## Database Init Scripts (`infra/db/init/`)

These run automatically when the Postgres container starts for the first time:

```
infra/db/init/
├── 01_create_schemas.sql      ← CREATE SCHEMA claims, documents, workflow, …
├── 02_claims_schema.sql       ← Tables: claims, claim_history
├── 03_documents_schema.sql    ← Tables: documents
├── 04_workflow_schema.sql     ← Tables: workflow_instances, assignments
├── 05_workshops_schema.sql    ← Tables: workshops, work_orders
├── 06_payments_schema.sql     ← Tables: payments
├── 07_reporting_schema.sql    ← Tables: claim_kpi_snapshots, regional_reports
└── 08_audit_schema.sql        ← Table: audit_log (append-only, no DELETE)
```

Sample `01_create_schemas.sql`:
```sql
CREATE SCHEMA IF NOT EXISTS claims;
CREATE SCHEMA IF NOT EXISTS documents;
CREATE SCHEMA IF NOT EXISTS workflow;
CREATE SCHEMA IF NOT EXISTS workshops;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS reporting;
CREATE SCHEMA IF NOT EXISTS audit;
```

---

## Keycloak Realm Setup (`infra/keycloak/eclaims-realm.json`)

Pre-seeds:
- Realm name: `eclaims`
- Client: `eclaims-web` (public client, frontend)
- Client: `eclaims-api` (confidential, backend-to-backend)
- Realm roles: `customer`, `surveyor`, `adjustor`, `case_manager`, `auditor`, `workshop`, `regional_mgr`, `top_management`
- Test users (one per role — for POC demo)

Test users seeded for POC:
| Username | Password | Role |
|----------|----------|------|
| `customer1` | `Test@1234` | customer |
| `surveyor1` | `Test@1234` | surveyor |
| `adjustor1` | `Test@1234` | adjustor |
| `casemanager1` | `Test@1234` | case_manager |
| `auditor1` | `Test@1234` | auditor |
| `workshop1` | `Test@1234` | workshop |

---

## Kafka Topics (Redpanda)

Created on application startup via Spring Boot `KafkaAdmin`:

| Topic | Partitions | Retention | Producers | Consumers |
|-------|------------|-----------|-----------|-----------|
| `claim-events` | 3 | 7 days | claims-module, workflow-module | notification-module, reporting-module |
| `audit-events` | 1 | 7 years | all modules | audit-consumer (append-only log) |
| `payment-events` | 2 | 7 days | payment-module | notification-module |
| `repair-events` | 2 | 7 days | workshop-module | notification-module, reporting-module |

---

## Spring Boot `application-local.yml`

```yaml
spring:
  threads:
    virtual:
      enabled: true      # Java 21 Virtual Threads — near-Node.js concurrency

  datasource:
    url: jdbc:postgresql://localhost:5432/eclaims
    username: eclaims
    password: eclaims_dev
    hikari:
      maximum-pool-size: 10

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: eclaims-local
      auto-offset-reset: earliest
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer

  data:
    redis:
      host: localhost
      port: 6379
      password: redis_dev

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8080/realms/eclaims
          jwk-set-uri: http://localhost:8080/realms/eclaims/protocol/openid-connect/certs

  mail:
    host: localhost
    port: 1025          # Mailhog SMTP
    properties:
      mail.smtp.auth: false
      mail.smtp.starttls.enable: false

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      probes:
        enabled: true   # Enables /actuator/health/liveness and /actuator/health/readiness
      show-details: always
      group:
        readiness:
          include: db, redis, kafka
        liveness:
          include: ping

logging:
  pattern:
    console: "%d{ISO8601} [%X{correlationId}] %-5level %logger{36} - %msg%n"
  level:
    com.yclaims: DEBUG
    org.springframework.security: INFO
    org.apache.kafka: WARN
```

---

## Quick Start Commands

```bash
# 1. Start all infrastructure (Postgres, Redpanda, Redis, Keycloak, Mailhog)
docker-compose up -d

# 2. Wait for Keycloak to be healthy (~30s) then start backend
./mvnw spring-boot:run -pl app/eclaims-api -am -Dspring-boot.run.profiles=local

# 3. Start frontend
cd eclaims-frontend
npm install
npm run dev

# 4. (Optional) Run K6 load test
k6 run --env BASE_URL=http://localhost:8090/api/v1 infra/load-tests/claim-submission.js

# ── Access Points ─────────────────────────────────────────────────────
# Frontend (PWA):          http://localhost:5173
# Backend API:             http://localhost:8090/api/v1
# API Docs (Swagger):      http://localhost:8090/swagger-ui.html
# Keycloak Admin:          http://localhost:8080/admin         (admin / admin)
# Kafka UI (Redpanda):     http://localhost:8082               (live topic view)
# Email Inbox (Mailhog):   http://localhost:8025               (see notifications arrive)
# Actuator — Liveness:     http://localhost:8090/actuator/health/liveness
# Actuator — Readiness:    http://localhost:8090/actuator/health/readiness
# Actuator — Metrics:      http://localhost:8090/actuator/metrics
# Actuator — Prometheus:   http://localhost:8090/actuator/prometheus
```

---

## Infra Folder Structure (Full)

```
infra/
├── db/
│   └── init/
│       ├── 01_create_schemas.sql
│       ├── 02_claims_schema.sql        ← Includes composite indexes + natural key constraint
│       ├── 03_documents_schema.sql
│       ├── 04_workflow_schema.sql
│       ├── 05_workshops_schema.sql
│       ├── 06_payments_schema.sql
│       ├── 07_reporting_schema.sql
│       └── 08_audit_schema.sql        ← Append-only; no UPDATE/DELETE granted
│
├── keycloak/
│   └── eclaims-realm.json              ← Realm + 8 roles + test users pre-seeded
│
├── monitoring/
│   └── prometheus-alerts.yml          ← HighResponseTime, HighErrorRate, LowCacheHitRatio, KafkaConsumerLag
│
├── load-tests/
│   └── claim-submission.js            ← K6 script: 200 concurrent users; p99 thresholds as code
│
└── docs/
    ├── architecture-to-poc-map.md      ← Traces every architecture concern → code location
    └── extraction-guide.md             ← Step-by-step: module → microservice
```

---

## What Each Running Component Demonstrates

| Component | Architectural Concept Demonstrated |
|-----------|-----------------------------------|
| **PostgreSQL** | Data tier; per-module schema isolation; transactional integrity; composite indexes |
| **Redpanda (Kafka)** | Event-driven architecture; async decoupling; standard event envelope; idempotent consumers |
| **Redpanda Console** | Live Kafka topic view — evaluator watches `claim.created` messages appear in real time |
| **Redis** | Cache-aside pattern; key naming convention; idempotency store; consumer dedup; 85%+ hit ratio target |
| **Keycloak** | Security tier; RBAC configurable without code changes; 8 roles + test users pre-seeded |
| **Mailhog** | Notification architecture — evaluator sees real email arrive at `localhost:8025` on claim submission |
| **Spring Boot** | Business + data access layers; all 7 modules; Virtual Threads; clean architecture |
| **React (PWA)** | Presentation tier; 3 portals by role; service layer separation; offline-first customer portal |
| **Swagger UI** | API contract; versioned endpoints (`/api/v1`); OpenAPI spec; all request/response DTOs visible |
| **Actuator `/health/liveness`** | JVM health — container restart if fails |
| **Actuator `/health/readiness`** | Dependency health (DB + Redis + Kafka) — removes from load balancer if fails |
| **Actuator `/actuator/metrics`** | Micrometer metrics; cache hit ratio gauge; request latency histograms |
