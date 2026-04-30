# Customer Portal & App — FR Coverage Check

Scope: code in `eclaims-backend/` + `eclaims-frontend/` as of workspace state on 2026-04-29.

## Summary

- **Implemented end-to-end (backend + customer UI)**: claim submission, electronic payment initiation, workshop search (by “location” text), claim status visible in portal.
- **Implemented in backend but not surfaced in customer UI**: supporting document upload APIs, claim status change notifications (email), repair/work-order progress events.
- **Missing**: customer self-registration “using policy details”, changing correspondence address, changing billing cycle.
- **Partial / needs product clarification**: “partner service providers” beyond workshops (authorized service stations, car rental), and “repair tracking” for customers (no customer API/UI to view work orders / progress reports).

## FR-by-FR Matrix

### FR1 — Customer can create a login using their policy details

- **Status**: **Missing**
- **What exists**:
  - Frontend uses Keycloak SSO login only: `eclaims-frontend/src/shared/pages/LoginPage.tsx` (button “Sign In with Keycloak”).
  - Keycloak realm disables registration: `eclaims-backend/infra/keycloak/eclaims-realm.json` has `"registrationAllowed": false`.
  - Only “policy” validation logic found is tied to claim submission (not onboarding): `eclaims-backend/modules/claims/src/main/java/com/yclaims/claims/infrastructure/integration/PolicyServiceStubAdapter.java`.
- **Gap**:
  - No “sign up / create account” flow in UI.
  - No backend endpoint for validating policy details and provisioning a customer identity.

### FR2 — Customer should be able to change the correspondence address

- **Status**: **Missing**
- **Evidence**:
  - No backend code references found for correspondence/address update flows (no controllers/services/repositories for customer profile management in this repo).
  - No customer portal page/components found for address changes.
- **Gap**:
  - Needs a customer profile domain/API (e.g., `/api/v1/customers/me/address`) plus UI.

### FR3 — Customer should be able to change the billing cycle

- **Status**: **Missing**
- **Evidence**:
  - No backend code references found for “billing cycle”.
  - No customer portal page/components found for billing cycle changes.
- **Gap**:
  - Needs billing preferences domain/API + UI, plus integration to the billing system.

### FR4 — Customer should be able to make electronic payment

- **Status**: **Implemented (end-to-end, with mock gateway)**
- **Backend evidence**:
  - Payment initiation endpoint: `POST /api/v1/payments` in `eclaims-backend/modules/payments/src/main/java/com/yclaims/payments/presentation/PaymentController.java`.
  - Uses idempotency key header: `Idempotency-Key` (documented in controller).
  - Mock gateway adapter exists: `eclaims-backend/modules/payments/src/main/java/com/yclaims/payments/infrastructure/gateway/MockPaymentGatewayAdapter.java` (presence found via search).
- **Frontend evidence**:
  - Customer payment page initiates payment via API: `eclaims-frontend/src/portals/customer/pages/PaymentPage.tsx` calls `httpClient.post('/payments', ...)` and sets `Idempotency-Key`.
- **Notes / constraints**:
  - This is an “initiate payment” flow; settlement/real payment provider integration depends on the gateway implementation (currently mock).

### FR5 — Customer should be able to submit claims

- **Status**: **Implemented (end-to-end)**
- **Backend evidence**:
  - Claim submission endpoint: `POST /api/v1/claims` in `eclaims-backend/modules/claims/src/main/java/com/yclaims/claims/presentation/ClaimController.java`.
  - Customer claim listing: `GET /api/v1/claims/my-claims` in the same controller.
  - Claim detail: `GET /api/v1/claims/{claimId}`.
- **Frontend evidence**:
  - Customer claim submission UI: `eclaims-frontend/src/portals/customer/pages/SubmitClaimPage.tsx` (multi-step form includes `policyNumber`, `vehicleRegistration`, incident details).
  - Customer claim list/detail pages exist: `eclaims-frontend/src/portals/customer/pages/ClaimsListPage.tsx` and `ClaimDetailPage.tsx`.

### FR6 — Customer should be able to submit any supporting document

- **Status**: **Backend implemented, customer UI missing**
- **Backend evidence**:
  - Document upload API: `POST /api/v1/documents/upload` in `eclaims-backend/modules/documents/src/main/java/com/yclaims/documents/presentation/DocumentController.java`.
  - Document list/download URL: `GET /api/v1/documents/claim/{claimId}` and `GET /api/v1/documents/{documentId}/download-url`.
- **Frontend evidence**:
  - No customer page/component found that calls `/documents/*` or exposes upload UI.
- **Gap**:
  - Add customer portal UI for uploading documents per claim (and optionally show document list with download links).

### FR7 — Customer should be notified and alerted if any status change happens on claims

- **Status**: **Implemented (email notifications), partial for SMS/in-app**
- **Backend evidence**:
  - Kafka consumer reacts to claim events: `eclaims-backend/modules/notifications/src/main/java/com/yclaims/notifications/infrastructure/kafka/ClaimEventConsumer.java` handles `"claim.status.changed"`.
  - Notification orchestration sends email: `eclaims-backend/modules/notifications/src/main/java/com/yclaims/notifications/application/NotificationApplicationService.java`.
  - SMS is stubbed (“Phase 2” placeholder in service: phone number not present in payload).
- **Frontend evidence**:
  - No in-app notification center / real-time alerts found in customer portal.
- **Gap**:
  - If “alerted” implies in-app/push, add UI + delivery (WebSocket/SSE/push) and persist notifications per customer.

### FR8 — Customer should be able to check Partner Service providers (Repair workshop, authorized service station, Car Rental) based on location or zip address

- **Status**: **Partial (workshops only; location search is by city substring)**
- **Backend evidence**:
  - Workshop search endpoint: `GET /api/v1/workshops?location=...` in `eclaims-backend/modules/workshops/src/main/java/com/yclaims/workshops/presentation/WorkshopController.java`.
  - Search implementation: `WorkshopApplicationService.searchWorkshops()` searches by city (`findByCityContainingIgnoreCaseAndActiveTrue`) or returns all active.
  - `WorkshopEntity` has `city` and `zipCode`, but API parameter is only `location` and is treated as city text, not zip-specific filtering.
- **Frontend evidence**:
  - Customer workshop search UI: `eclaims-frontend/src/portals/customer/pages/WorkshopSearchPage.tsx` calls `/workshops?location=<search>`.
- **Gaps**:
  - No provider types beyond workshops (no “authorized service station” vs “car rental” domain).
  - Zip-based search is not explicitly supported (despite data model containing `zipCode`).

### FR9 — Customer should be able to keep track of repair based on work order and progress report submitted by the Repair agency

- **Status**: **Partial (workshop can submit/update; customer tracking not implemented)**
- **Backend evidence**:
  - Workshop can submit work orders: `POST /api/v1/work-orders` (role `WORKSHOP`) in `WorkshopController`.
  - Workshop can update repair status: `PATCH /api/v1/work-orders/{workOrderId}/repair-status` (role `WORKSHOP`), which publishes Kafka event `"repair.status.updated"` in `WorkshopApplicationService`.
  - Repair updates are published to Kafka topic `repair-events`, but there is **no consumer** in `modules/notifications` subscribing to that topic.
  - Published `RepairStatusUpdatedPayload` is currently **missing customer enrichment** (`customerId` / `customerEmail` are set to null in publisher), so customer-facing alerts can’t be delivered without adding that data (or enriching downstream).
  - No customer-facing API to retrieve work orders / repair status (no `GET /work-orders/...` endpoint in controller).
  - Claim API response (`ClaimResponse`) only contains `workshopId`, not work order / progress status: `eclaims-backend/modules/claims/src/main/java/com/yclaims/claims/presentation/dto/ClaimResponse.java`.
- **Frontend evidence**:
  - Customer claim detail (`ClaimDetailPage.tsx`) does not show work order/progress info.
  - Workshop portal pages exist (e.g., `eclaims-frontend/src/portals/workshop/pages/WorkOrderPage.tsx`, `RepairUpdatePage.tsx`) suggesting repair updates are workshop-facing.
- **Gaps**:
  - Customer tracking needs either:
    - a customer-accessible “work order details/status” API, or
    - embedding repair/work-order status into claim details, plus UI.
  - Optional: subscribe customer notifications to `"repair-events"` topic (currently only claim/payment events are consumed by notifications module).

