# eClaims POC – Spring Boot Modular Monolith Folder Structure

> **Stack**: Java 21 (Virtual Threads enabled) · Spring Boot 3.x · Maven (multi-module) · PostgreSQL · Redpanda/Kafka · Redis · Keycloak · Mailhog

---

## Repository Root Layout

```
eclaims-backend/
├── pom.xml                         ← Parent POM (dependency management, module declarations)
├── docker-compose.yml              ← Local infra: Postgres, Redpanda, Redis, Keycloak, Mailhog
├── .env.example                    ← Environment variable template (never commit .env)
├── README.md                       ← Architecture overview, extraction path, how to run
│
├── modules/                        ← Bounded-context modules (one per domain)
│   ├── claims/
│   ├── documents/
│   ├── workflow/
│   ├── notifications/
│   ├── workshops/
│   ├── payments/
│   └── reporting/
│
├── shared/
│   ├── kernel/                     ← Cross-cutting primitives (IDs, time, exceptions, base entities)
│   └── contracts/                  ← Shared event/DTO schemas (versioned)
│
└── app/
    └── eclaims-api/                ← Spring Boot application entry point (assembles all modules)
```

---

## Parent POM (`pom.xml`)

```xml
<modules>
    <module>shared/kernel</module>
    <module>shared/contracts</module>
    <module>modules/claims</module>
    <module>modules/documents</module>
    <module>modules/workflow</module>
    <module>modules/notifications</module>
    <module>modules/workshops</module>
    <module>modules/payments</module>
    <module>modules/reporting</module>
    <module>app/eclaims-api</module>
</modules>
```

**Dependency rule**: `modules/*` may depend on `shared/kernel` and `shared/contracts`.  
`modules/*` must **not** depend on each other directly. Inter-module communication is via Kafka events or shared contracts only.

---

## Application Entry Point (`app/eclaims-api/`)

```
app/eclaims-api/
├── pom.xml                         ← Depends on all modules; Spring Boot plugin here
└── src/
    └── main/
        ├── java/com/yclaims/app/
        │   └── EClaimsApplication.java   ← @SpringBootApplication
        └── resources/
            ├── application.yml           ← Common config
            ├── application-local.yml     ← Local Docker overrides
            ├── application-prod.yml      ← Production overrides (secrets via env vars)
            └── keycloak/
                └── eclaims-realm.json    ← Keycloak realm import (roles, clients pre-seeded)
```

---

## Module Structure (Repeated for Each Module)

Every module follows the **same 4-layer internal structure**. Shown here for `claims`:

```
modules/claims/
├── pom.xml
└── src/
    └── main/
        └── java/com/yclaims/claims/
            │
            ├── presentation/                      ← LAYER 1: Presentation (Web Tier)
            │   ├── ClaimController.java            REST endpoints (@RestController)
            │   ├── dto/
            │   │   ├── ClaimSubmissionRequest.java  ← Input DTO (Bean Validation annotations)
            │   │   ├── ClaimResponse.java           ← Output DTO (no domain objects exposed)
            │   │   └── ClaimStatusResponse.java
            │   └── mapper/
            │       └── ClaimDtoMapper.java          ← Maps domain ↔ DTO (MapStruct)
            │
            ├── application/                       ← LAYER 2: Application (Use Cases)
            │   ├── ClaimApplicationService.java    Orchestrates use cases, owns transactions
            │   ├── usecase/
            │   │   ├── SubmitClaimUseCase.java
            │   │   ├── UpdateClaimStatusUseCase.java
            │   │   ├── AssignClaimUseCase.java
            │   │   └── GetClaimDetailsUseCase.java
            │   └── command/                        ← Commands (CQRS input objects)
            │       ├── SubmitClaimCommand.java
            │       └── UpdateClaimStatusCommand.java
            │
            ├── domain/                            ← LAYER 3: Domain (Business Core)
            │   ├── model/
            │   │   ├── Claim.java                  ← Aggregate root (@Entity or plain POJO)
            │   │   ├── ClaimId.java                ← Value object (typed ID)
            │   │   ├── ClaimStatus.java             ← Enum: DRAFT → SUBMITTED → ASSIGNED → ...
            │   │   ├── PolicyNumber.java            ← Value object
            │   │   └── AccidentDetails.java         ← Value object (embedded)
            │   ├── service/
            │   │   ├── ClaimStateMachine.java       ← State transition rules + validation
            │   │   └── FraudDetectionService.java   ← Rule-based fraud check (domain logic)
            │   ├── event/
            │   │   ├── ClaimSubmittedEvent.java     ← Domain event (record / immutable)
            │   │   ├── ClaimStatusChangedEvent.java
            │   │   └── ClaimAssignedEvent.java
            │   ├── port/                           ← Ports (interfaces the domain needs)
            │   │   ├── out/
            │   │   │   ├── ClaimRepository.java     ← Persistence port (interface)
            │   │   │   ├── DomainEventPublisher.java← Event publishing port (interface)
            │   │   │   └── PolicyServicePort.java   ← External policy lookup (interface)
            │   │   └── in/
            │   │       └── ClaimUseCasePort.java    ← Inbound port (use case interface)
            │   └── exception/
            │       ├── ClaimNotFoundException.java
            │       ├── InvalidClaimStateException.java
            │       └── PolicyValidationException.java
            │
            └── infrastructure/                    ← LAYER 4: Infrastructure (Data Access + Adapters)
                ├── persistence/
                │   ├── ClaimJpaRepository.java      ← Spring Data JPA interface
                │   ├── ClaimEntity.java             ← JPA @Entity (separate from domain model)
                │   ├── ClaimPersistenceAdapter.java ← Implements ClaimRepository port
                │   └── mapper/
                │       └── ClaimEntityMapper.java   ← Domain model ↔ JPA entity
                ├── event/
                │   ├── KafkaClaimEventPublisher.java← Implements DomainEventPublisher port
                │   └── config/
                │       └── KafkaClaimsConfig.java   ← Topic + producer bean config
                ├── integration/
                │   └── PolicyServiceRestAdapter.java← Implements PolicyServicePort (REST call)
                └── config/
                    └── ClaimsModuleConfig.java      ← @Configuration: wires all beans
```

---

## All 7 Modules — Responsibilities at a Glance

| Module | Key Domain Models | Key Use Cases | Ports (out) | Events Published |
|--------|------------------|---------------|-------------|-----------------|
| `claims` | `Claim`, `ClaimStatus`, `AccidentDetails` | Submit, Assign, Status update, Adjudicate | `ClaimRepository`, `PolicyServicePort`, `DomainEventPublisher` | `ClaimSubmittedEvent`, `ClaimStatusChangedEvent`, `ClaimApprovedEvent` |
| `documents` | `Document`, `DocumentType`, `StorageMetadata` | Upload document, Retrieve, Archive | `DocumentStoragePort` (S3/local), `DocumentRepository` | `DocumentUploadedEvent` |
| `workflow` | `WorkflowInstance`, `Assignment`, `SurveyCriteria` | Auto-assign, Delegate, Escalate | `AssignmentRepository`, `DomainEventPublisher` | `ClaimAssignedEvent`, `EscalationTriggeredEvent` |
| `notifications` | `NotificationLog`, `NotificationType` | Send email, Send SMS, Archive comms | `EmailNotificationPort`, `SmsNotificationPort`, `PushNotificationPort` | (Consumer only — subscribes to claim/workflow events) |
| `workshops` | `Workshop`, `WorkOrder`, `RepairStatus` | Submit work order, Update repair status, Final bill | `WorkshopRepository`, `DomainEventPublisher` | `RepairStatusUpdatedEvent`, `FinalBillRaisedEvent` |
| `payments` | `Payment`, `PaymentStatus`, `PaymentIntent` | Initiate payment, Confirm, Settle workshop | `PaymentGatewayPort` (Stripe/mock), `PaymentRepository` | `PaymentInitiatedEvent`, `PaymentSettledEvent` |
| `reporting` | `ClaimKpi`, `RegionalReport`, `FraudAgeingRecord` | Generate KPI report, Fraud ageing, Regional claims | `ReportCacheRepository` (read model), `ReportExportPort` (PDF) | (Consumer only — materialises from claim events) |

---

## Shared Kernel (`shared/kernel/`)

```
shared/kernel/
└── src/main/java/com/yclaims/kernel/
    ├── domain/
    │   ├── AggregateRoot.java          ← Base class for aggregates (holds domain events list)
    │   ├── ValueObject.java            ← Marker interface for value objects
    │   └── EntityId.java               ← Base typed ID (UUID-backed)
    ├── exception/
    │   ├── DomainException.java
    │   ├── NotFoundException.java
    │   └── UnauthorisedException.java
    ├── security/
    │   ├── UserContext.java            ← Holds current user + roles from JWT
    │   └── UserContextHolder.java      ← ThreadLocal accessor (set by security filter)
    ├── web/
    │   ├── GlobalExceptionHandler.java ← @ControllerAdvice; maps domain exceptions → HTTP codes
    │   ├── ApiResponse.java            ← Envelope: { data, error { code, message, fieldErrors }, meta { correlationId, timestamp, version } }
    │   └── CorrelationIdFilter.java    ← Sets MDC correlationId + userId; echoes X-Correlation-ID header
    ├── cache/
    │   └── CacheMetricsConfig.java     ← Registers Redis hit/miss ratio as Micrometer gauge (target: > 85%)
    └── audit/
        ├── AuditEvent.java             ← Enhanced: eventId, correlationId, userId, userRole, action,
        │                                           entityType, entityId, oldValue (JSON), newValue (JSON),
        │                                           ipAddress, userAgent, sessionId, occurredAt
        └── AuditRepository.java        ← Port: append-only audit log interface (no UPDATE/DELETE)
```

---

## Shared Contracts (`shared/contracts/`)

```
shared/contracts/
└── src/main/java/com/yclaims/contracts/
    ├── events/
    │   ├── DomainEvent.java             ← Standard envelope for ALL Kafka messages
    │   │                                   Fields: eventId, eventType, correlationId,
    │   │                                           causationId, aggregateId, aggregateType,
    │   │                                           version, occurredAt, payload<T>
    │   ├── v1/
    │   │   ├── ClaimCreatedPayload.java      ← Payload inside DomainEvent<ClaimCreatedPayload>
    │   │   ├── ClaimStatusChangedPayload.java
    │   │   ├── ClaimApprovedPayload.java
    │   │   ├── PaymentSettledPayload.java
    │   │   └── RepairStatusUpdatedPayload.java
    │   └── v2/                              ← Future: additive schema changes only
    └── api/
        ├── ClaimStatusDto.java              ← Shared enums/types used across modules
        └── UserRole.java                    ← Enum: CUSTOMER, SURVEYOR, ADJUSTOR, CASE_MANAGER, etc.
```

> **Rule**: Every Kafka message is `DomainEvent<T>`. Consumers always have `eventId` for dedup,  
> `correlationId` for tracing, and `causationId` for event chain debugging.

---

## Security Configuration (in `app/eclaims-api/`)

```
resources/security/
└── SecurityConfig.java
    ├── Keycloak JWT converter (maps realm roles → Spring authorities)
    ├── Method security: @PreAuthorize("hasRole('ADJUSTOR')")
    ├── CORS configuration
    └── Rate limiting via Redis (custom filter)
```

**RBAC role mapping** (Keycloak realm role → Spring Security authority):
```
customer       → ROLE_CUSTOMER
surveyor       → ROLE_SURVEYOR
adjustor       → ROLE_ADJUSTOR
case_manager   → ROLE_CASE_MANAGER
auditor        → ROLE_AUDITOR
workshop       → ROLE_WORKSHOP
regional_mgr   → ROLE_REGIONAL_MGR
top_management → ROLE_TOP_MANAGEMENT
```

---

## Database Schema Layout (PostgreSQL)

One database, schemas logically segregated by module (simulates separate DBs for microservices):

```
Database: eclaims
├── schema: claims       → tables: claims, claim_history, policy_cache
├── schema: documents    → tables: documents, document_versions
├── schema: workflow     → tables: workflow_instances, assignments
├── schema: workshops    → tables: workshops, work_orders, repair_updates
├── schema: payments     → tables: payments, payment_intents
├── schema: reporting    → tables: claim_kpi_snapshots, regional_reports (read model)
└── schema: audit        → tables: audit_log (append-only, no UPDATE/DELETE)
```

> **Rule**: A module's `infrastructure/persistence` layer may **only** access its own schema.  
> Cross-module data access is only via API calls or event consumption — not by joining across schemas.

---

## Test Structure (per module)

```
modules/claims/src/test/java/com/yclaims/claims/
├── domain/
│   ├── ClaimStateMachineTest.java       ← Unit tests: pure domain logic, no Spring
│   └── FraudDetectionServiceTest.java
├── application/
│   └── SubmitClaimUseCaseTest.java      ← Unit tests: mock ports (Mockito)
└── infrastructure/
    ├── ClaimPersistenceAdapterIT.java   ← Integration: Testcontainers + real Postgres
    └── KafkaClaimEventPublisherIT.java  ← Integration: Testcontainers + real Kafka
```

---

## Dependency Direction Rule (enforced via ArchUnit)

```
Presentation  →  Application  →  Domain  ←  Infrastructure
                                              (implements ports)
```

- Domain must **never** import from `infrastructure`, `presentation`, or Spring/JPA.
- Infrastructure **implements** the ports defined in Domain.
- Application orchestrates, owns transactions, calls domain services.
- Presentation only converts HTTP ↔ DTOs and delegates to Application.

ArchUnit test to enforce this:
```java
@ArchTest
static final ArchRule domainIsolation = noClasses()
    .that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..infrastructure..", "..presentation..", "org.springframework..");
```
