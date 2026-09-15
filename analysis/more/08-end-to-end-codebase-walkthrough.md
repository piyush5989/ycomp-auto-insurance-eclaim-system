# End-to-End Codebase Walkthrough and System Mechanics

This document provides a comprehensive, interconnected walkthrough of the eClaims system across both the Spring Boot modular monolith backend and the React TypeScript frontend. It explains exactly how authentication, auto-assignment, event-driven messaging, cross-role synchronization, and management overrides operate at the code, package, and database levels.

---

## 1. System Map: Architecture & Codebase Layout

### 1.1 Backend Modules (`eclaims-backend/`)
The backend is structured as a Hexagonal (Ports & Adapters) Modular Monolith with strict boundary enforcement via ArchUnit:

```
eclaims-backend/
├── shared/
│   ├── kernel/           # Core domain abstractions (AggregateRoot, DomainEvent, UserContextHolder)
│   └── contracts/        # Cross-module DTOs, Kafka topics, shared enums (UserRole, ClaimStatus)
├── modules/
│   ├── claims/           # Claim lifecycle, Aggregate Root, Fraud evaluation, Access Policy
│   ├── workflow/         # Auto-assignment (Surveyors, Adjustors), Workforce reconciliation
│   ├── workshops/        # Workshop directory, Work orders, Repair status tracking
│   ├── documents/        # File upload/download, SHA-256 verification, RBAC document matrix
│   ├── payments/         # Tamper-proof bill calculation, Idempotent payment processing
│   ├── notifications/    # Kafka event consumers, email/SMS dispatch (MailHog)
│   ├── customers/        # Customer address & billing preferences
│   ├── reporting/        # CQRS KPI aggregation & regional analytics
│   └── rentals/          # Replacement vehicle reservation
└── app/
    └── eclaims-api/      # Main entry point, SecurityConfig, Keycloak integration, Audit listeners
```

### 1.2 Frontend Structure (`eclaims-frontend/`)
The frontend is built with React 18, Vite, TypeScript, TailwindCSS, and TanStack React Query:

```
eclaims-frontend/src/
├── shared/
│   ├── auth/             # KeycloakProvider, keycloakInstance, role utilities
│   ├── api/              # Axios httpClient with JWT & Correlation ID interceptors
│   └── components/       # UI building blocks (DataTable, Badges, Modals)
├── portals/
│   ├── customer/         # Submit claim, track progress, vehicle drop-off, payment
│   ├── internal/         # Surveyor assessment, Adjustor adjudication, Case Manager queue
│   └── workshop/         # Work order creation, Repair status update, Media upload
└── features/             # TanStack Query hooks, validation schemas, API adapters
```

---

## 2. Authentication, Security & User Provisioning

### 2.1 User Self-Registration Flow
1. **Frontend**: The user fills out `RegisterPage.tsx` with email, password, policy number, and vehicle registration.
2. **API Request**: Axios sends `POST /api/v1/onboarding/register` to the backend.
3. **Controller**: `OnboardingController.java` (`com.yclaims.app.onboarding`) delegates to `OnboardingApplicationService.java`.
4. **Policy Verification**:
   - `OnboardingApplicationService` invokes `PolicyServicePort.validate(policyNumber, vehicleRegistration)`.
   - In dev/test, `PolicyServiceStubAdapter.java` validates that the policy exists, is active, and the vehicle registration matches.
5. **Keycloak User Creation**:
   - `OnboardingApplicationService` uses `KeycloakAdminClient` to call Keycloak's Admin REST API:
     - Sets username to email (lowercase).
     - Sets permanent password credential.
     - Adds user attributes: `customerId`, `policyNumber`, `vehicleRegistration`.
     - Assigns the realm role `customer`.
6. **Prefill Storage**: Upon receiving HTTP 201, `RegisterPage.tsx` calls `saveCustomerClaimPrefill()` so the user's policy and plate are saved for automatic claim prefilling.

### 2.2 Login & Token Flow
1. **Login Trigger**: In `LoginPage.tsx`, clicking "Sign In" calls `keycloak.login()` via the Keycloak JS SDK (`keycloakInstance.ts`).
2. **Keycloak Redirection**: Keycloak presents the standard OIDC login form. Upon successful authentication, Keycloak issues an OpenID Connect Authorization Code and redirects to `http://localhost:5173/`.
3. **In-Memory Token Storage**:
   - In `KeycloakProvider.tsx`, the token is stored strictly in memory (`useState` / JS closures), preventing XSS token-theft vulnerabilities (OWASP compliance). No tokens are saved in `localStorage` or `sessionStorage`.
4. **API Request Authorization**:
   - `httpClient.ts` configures an Axios request interceptor that calls `getToken()` from `keycloakInstance.ts`.
   - Every outgoing request receives:
     - `Authorization: Bearer <JWT>`
     - `X-Correlation-ID: <UUID>`

### 2.3 Backend Security Enforcement
1. **Filter Chain (`SecurityConfig.java`)**:
   - Spring Security runs as a stateless OAuth2 Resource Server:
     - Validates Keycloak JWT signature and expiry.
     - `JwtAuthenticationConverter` maps `realm_access.roles` into Spring authorities (`ROLE_CUSTOMER`, `ROLE_SURVEYOR`, `ROLE_ADJUSTOR`, `ROLE_CASE_MANAGER`, `ROLE_WORKSHOP`).
2. **Self-Healing Workforce (`WorkforceProvisioningFilter.java`)**:
   - Positioned immediately after `BearerTokenAuthenticationFilter`.
   - When an internal user (`surveyor` or `adjustor`) makes a request, `WorkforceProvisioningService` inspects the DB table (`workflow.surveyors` or `workflow.adjustors`).
   - If the user exists in Keycloak but is missing or has drifted in Postgres, it automatically provisions or updates their record in the database.
3. **Dynamic Authorization (`@PreAuthorize("@authz.isAllowed('resource', 'scope')")`)**:
   - `KeycloakAuthorizationService.java` checks permissions against Keycloak's UMA 2.0 policy decision point. Decisions are cached in Caffeine cache for 5 minutes.
4. **Row-Level Security (`ClaimAccessPolicyImpl.java`)**:
   - `assertCanAccessClaim(claimId)` verifies that:
     - Customers can only view their own claims (`customerId == claim.customerId`).
     - Surveyors can only view claims assigned to them (`surveyorId == claim.assignedSurveyorId`).
     - Adjustors can only view claims assigned to them (`adjustorId == claim.assignedAdjustorId`).
     - Workshops can only view claims assigned to their workshop ID.
     - Privileged roles (`case_manager`, `auditor`, `top_management`) have broad read/management access.

---

## 3. Claim Lifecycle & Auto-Assignment Mechanics

### 3.1 Step 1: Claim Submission
- **Frontend**: Customer submits `SubmitClaimPage.tsx` -> `POST /api/v1/claims`.
- **Backend Application Layer**: `ClaimApplicationService.submitClaim(...)`:
  1. Validates policy and vehicle registration via `PolicyServicePort`.
  2. Runs fraud evaluation: `FraudDetectionService.evaluate(...)` (checks for previous claims, theft without police report, high value).
  3. Aggregate creation: Calls `Claim.submit(...)` in `com.yclaims.claims.domain.model.Claim`.
  4. State machine: Sets status to `SUBMITTED` and registers `ClaimSubmittedEvent`.
  5. Persistence: `ClaimJpaRepository` saves to `claims.claims` table.
  6. Outbox / Event publishing: Publishes `claim.created` to Kafka topic `claim-events`.
  7. Notifications: `ClaimEventConsumer` in `notifications` module picks up `claim.created` and sends confirmation email via MailHog.

### 3.2 Step 2: Workshop Selection & Vehicle Drop-off
- **Workshop Selection**:
  - Customer picks a workshop on `SelectWorkshopPage.tsx` -> `POST /api/v1/workshops/claims/{claimId}/select-workshop`.
  - Claim transitions to `WORKSHOP_SELECTED`.
- **Vehicle Drop-off**:
  - Customer/Workshop records drop-off on `VehicleDropOffPage.tsx` (captures mileage, fuel level, photos).
  - Endpoint: `POST /api/v1/workshops/claims/{claimId}/vehicle-dropoff`.
  - Claim transitions to `VEHICLE_AT_WORKSHOP`.
  - Publishes `vehicle.droppedoff` event to Kafka topic `claim-events`.

### 3.3 Step 3: Auto-Assignment of Surveyor
The assignment is entirely event-driven and automated:
1. **Event Trigger**: `AutoAssignmentService.java` (`modules/workflow`) listens on `claim-events` for `vehicle.droppedoff`.
2. **Deduplication**: Checks Redis via `SETNX kafka:processed:workflow:<eventId>` with a 24-hour TTL.
3. **Region & ZIP Resolution**:
   - The service extracts the workshop ZIP code from the payload.
   - It queries `workflow.surveyor_zip_coverage` (trying ZIP5 first, then ZIP3 prefix, then regional fallback).
4. **Workload Balancing Algorithm**:
   - Queries `workflow.assignments` for all active surveyors in that region:
     ```sql
     SELECT surveyor_id, COUNT(*) FROM workflow.assignments 
     WHERE active = true AND surveyor_id IN (...) 
     GROUP BY surveyor_id;
     ```
   - Selects the surveyor with the minimum active workload.
   - If no surveyor is available, publishes `claim.escalated` for Case Manager intervention.
5. **Assignment Execution**:
   - Saves new assignment into `workflow.assignments`.
   - Publishes `surveyor.assigned` event to `claim-events`.
   - Publishes `notification.requested` to `notification-events` (alerts surveyor via email).
6. **Claim State Transition**:
   - `ClaimWorkflowEventConsumer` (`modules/claims`) consumes `surveyor.assigned`.
   - Calls `ClaimApplicationService.updateClaimStatus(ASSIGNED)`.
   - `Claim.assignSurveyor(...)` sets `assignedSurveyorId` and changes status to `ASSIGNED`.

### 3.4 Step 4: Survey Assessment
1. **Surveyor View**: Surveyor logs in, views `MyAssignmentsPage.tsx` -> opens `AssessClaimPage.tsx`.
2. **Begin Survey**: Surveyor clicks "Begin Assessment" -> `PATCH /api/v1/claims/{id}/status?status=UNDER_SURVEY`.
3. **Submit Assessment**: Surveyor enters damage notes and assessed repair amount -> `PATCH /api/v1/claims/{id}/status?status=SURVEYED&assessedAmount=...`.
4. **Domain Transition**: `Claim.completeSurvey(assessedAmount)` sets status to `SURVEYED`.
5. **Event Emission**: Publishes `claim.status.changed` (newStatus = `SURVEYED`) to `claim-events`.

### 3.5 Step 5: Auto-Assignment of Adjustor
1. **Event Trigger**: `AutoAssignmentService` listens on `claim-events` for `claim.status.changed` where `newStatus == "SURVEYED"`.
2. **Adjustor Workload Evaluation**:
   - Retrieves all active adjustors from `workflow.adjustors`.
   - Queries `claims.claims` for active adjudications:
     ```sql
     SELECT assigned_adjustor_id, COUNT(*) FROM claims.claims 
     WHERE assigned_adjustor_id IN (...) AND status = 'UNDER_ADJUDICATION' 
     GROUP BY assigned_adjustor_id;
     ```
   - Picks the adjustor with the lowest workload.
3. **Assignment Execution**:
   - Publishes `adjustor.assigned` event to `claim-events`.
   - Publishes `notification.requested` to `notification-events` to alert adjustor.
4. **Claim State Transition**:
   - `ClaimWorkflowEventConsumer` consumes `adjustor.assigned`.
   - Calls `ClaimApplicationService.assignAdjudicator(adjustorId)`.
   - Sets `assignedAdjustorId` on the claim (status remains `SURVEYED` until adjustor begins review).

---

## 4. Event-Driven Architecture (EDA) & Logging

### 4.1 Kafka Topics & Event Topology
All inter-module communication uses domain events with standard schema definitions in `eclaims-contracts`:

| Topic Name | Event Type | Publisher Module | Primary Consumers |
| :--- | :--- | :--- | :--- |
| `claim-events` | `claim.created` | `claims` | `notifications` |
| `claim-events` | `workshop.selected` | `workshops` | `claims` |
| `claim-events` | `vehicle.droppedoff` | `workshops` | `claims`, `workflow` |
| `claim-events` | `surveyor.assigned` | `workflow` | `claims`, `notifications` |
| `claim-events` | `claim.status.changed` | `claims` | `workflow`, `notifications` |
| `claim-events` | `adjustor.assigned` | `workflow` | `claims`, `notifications` |
| `claim-events` | `claim.escalated` | `workflow` | `notifications`, Case Manager dashboard |
| `repair-events` | `repair.status.updated` | `workshops` | `notifications` |
| `payment-events` | `payment.settled` | `payments` | `claims`, `notifications` |
| `audit-events` | `audit.event` | All modules | `AuditLogKafkaListener` (`audit.audit_log`) |
| `notification-events` | `notification.requested` | `workflow`, `claims` | `notifications` |

### 4.2 Standard Event Envelope Format
Every event implements `DomainEvent<T>`:
```json
{
  "eventId": "658a6789-1f25-4703-b792-3b317196c0e9",
  "eventType": "repair.status.updated",
  "correlationId": "ebd6de35-0d21-45c1-bc64-41a09ae40fc7",
  "aggregateId": "27805a0f-6e31-4303-9c92-98bad172fa41",
  "aggregateType": "Claim",
  "version": "v1",
  "occurredAt": "2026-09-14T11:37:29.653Z",
  "payload": {
    "claimId": "27805a0f-6e31-4303-9c92-98bad172fa41",
    "workOrderId": "a1b2c3d4-0000-0000-0000-000000000001",
    "repairStatus": "IN_PROGRESS",
    "note": "Parts arrived, disassembly underway"
  }
}
```

### 4.3 Log Traceability in Production & POC
The system uses MDC (Mapped Diagnostic Context) logging with correlation ID propagation.

Log entry pattern:
```
TIMESTAMP [CORRELATION-ID] [USER-ID] LEVEL LOGGER - MESSAGE
```

Real log snippet from your execution:
```text
2026-09-14 11:37:29,653 [ebd6de35-0d21-45c1-bc64-41a09ae40fc7] [60000000-0000-0000-0000-000000000002] DEBUG c.y.a.s.KeycloakAuthorizationService - Authz decision: user=60000000-0000-0000-0000-000000000002 workshop#repair-status-update -> true
2026-09-14 11:37:29,670 [no-correlation] [anonymous] INFO  c.y.n.i.kafka.ClaimEventConsumer - Processing repair event [658a6789-1f25-4703-b792-3b317196c0e9] type=repair.status.updated
2026-09-14 11:37:29,671 [no-correlation] [anonymous] INFO  c.y.n.a.NotificationApplicationService - [ebd6de35-0d21-45c1-bc64-41a09ae40fc7] Repair status IN_PROGRESS | email+SMS for claim 27805a0f-6e31-4303-9c92-98bad172fa41
2026-09-14 11:37:29,760 [no-correlation] [anonymous] INFO  c.y.n.i.e.EmailNotificationAdapter - Email sent to loadtest@eclaims.test | Subject: eClaims - Repair Status Update: IN_PROGRESS
```

Explanation of the log:
1. Workshop user submits status update -> `KeycloakAuthorizationService` approves scope `workshop#repair-status-update`.
2. `WorkshopApplicationService` updates Postgres and fires `repair.status.updated` to Kafka.
3. `ClaimEventConsumer` in notifications module consumes the event.
4. `NotificationApplicationService` renders template and dispatches email via `EmailNotificationAdapter`.

---

## 5. Cross-Role Visibility & State Synchronization

### 5.1 Repair Status & Work Order Synchronization
- **Workshop Action**:
  - Workshop updates status on `RepairUpdatePage.tsx` -> `PATCH /api/v1/workshops/work-orders/{id}/repair-status`.
  - Backend `WorkshopApplicationService` updates `workshops.work_orders`, inserts audit entry into `workshops.work_order_status_history`, and fires Kafka event.
- **Immediate Frontend Reflection**:
  - `RepairUpdatePage.tsx` synchronously updates TanStack Query cache (`queryClient.setQueryData`) for `['workshop', 'my-work-orders']` and `['work-order', claimId]`.
  - `useMyWorkOrders.ts` and `ClaimDetailPage.tsx` have `staleTime: 0` and `refetchOnMount: 'always'`, guaranteeing that when either Workshop or Customer opens the page, the newest status is immediately visible on first load.
- **Customer View**:
  - Customer visiting `ClaimDetailPage.tsx` sees the live status badge (`IN_PROGRESS`, `PARTS_ORDERED`, `COMPLETED`), estimated completion date, and chronological progress history timeline (`RepairProgressHistory` component).
  - When status reaches `COMPLETED`, the UI dynamically computes and displays the customer deductible payment card.

### 5.2 Document Upload & Inspection Visibility
- **Upload**:
  - When a Customer uploads accident pictures, `ClaimDetailPage.tsx` sends `POST /api/v1/documents/claims/{claimId}`.
  - `DocumentApplicationService.java` verifies write permissions (`WRITE_ROLES`), calculates SHA-256 checksum, saves to storage, and writes metadata to `documents.documents`.
- **Visibility to Surveyor & Adjustor**:
  - When Surveyor opens `AssessClaimPage.tsx` or Adjustor opens `AdjudicateClaimPage.tsx`, TanStack Query calls `GET /api/v1/documents/claims/{claimId}`.
  - Because all roles query the centralized `documents.documents` table filtered by `claimId`, every uploaded document is immediately available for preview and download across all portals.

### 5.3 Senior Management & Case Manager Overrides
Case Managers possess override authority to break deadlocks or correct disputes:
1. **Reassign Surveyor**:
   - `POST /api/v1/claims/{id}/reassign-surveyor` -> `Claim.reassignSurveyor(...)`. Updates `assignedSurveyorId` and records audit history.
2. **Reassign Adjustor**:
   - `POST /api/v1/claims/{id}/reassign-adjustor` -> `Claim.reassignAdjustor(...)`. If the claim was in `SURVEYED`, it transitions directly to `UNDER_ADJUDICATION`.
3. **Override Decision**:
   - `POST /api/v1/claims/{id}/override` -> `Claim.markOverridden(userId, reason, newAmount)`.
   - Modifies `approvedAmount`, saves `overrideReason` and `overrideAt` timestamp.
   - Automatically publishes an entry to `audit.audit_log` via `AuditPublisher` for compliance audit trails.

---

## 6. Interview Quick Reference: Connecting the Tech Dots

When asked about how everything connects in technical interviews:

1. **How is security handled?**
   - Dual identity architecture: AWS Cognito for customer scale (production) / Keycloak for enterprise internal roles + dev POC.
   - Frontend stores JWT in memory (no `localStorage` for tokens). Axios interceptors append Bearer token and `X-Correlation-ID`.
   - Backend uses stateless Spring Security Resource Server with UMA 2.0 dynamic authorization and self-healing workforce filters.
2. **How does auto-assignment work?**
   - Event-driven choreography via Kafka `claim-events`.
   - Vehicle drop-off triggers surveyor assignment based on workshop ZIP coverage and real-time workload balancing.
   - Survey completion triggers adjustor assignment based on active adjudication counts.
   - If no candidates match, claims automatically escalate to Case Managers.
3. **How is consistency maintained?**
   - Idempotent consumers with Redis SETNX deduplication (24h TTL).
   - Strict Hexagonal Architecture preventing cross-module database foreign keys.
   - Optimistic locking and guarded state machines in domain aggregates.
