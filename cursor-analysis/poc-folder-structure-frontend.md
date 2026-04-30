# eClaims POC – React Frontend Folder Structure

> **Stack**: React 18 · TypeScript · Vite · React Query (TanStack) · React Router v6 · Axios · Keycloak-js · Tailwind CSS

---

## Overview

The frontend is a **single React application** that hosts 3 separate portal experiences as route groups. This matches the architecture ("Customer Portal", "Internal Portal", "Workshop Portal") without needing 3 separate deployments for the POC.

**Portal routing**:
```
/customer/*    → Customer Portal  (role: CUSTOMER)
/internal/*    → Internal Portal  (roles: SURVEYOR, ADJUSTOR, CASE_MANAGER, AUDITOR, REGIONAL_MGR, TOP_MANAGEMENT)
/workshop/*    → Workshop Portal  (role: WORKSHOP)
/              → Login / Role-based redirect
```

---

## Root Structure

```
eclaims-frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.ts
├── .env.example                         ← VITE_API_BASE_URL, VITE_KEYCLOAK_URL, etc.
├── index.html
│
├── public/
│   └── assets/                          ← Static icons, logos
│
└── src/
    ├── main.tsx                         ← App entry point (ReactDOM.createRoot)
    ├── App.tsx                          ← Router + AuthProvider + QueryClientProvider
    │
    ├── portals/                         ← PRESENTATION TIER — one folder per portal
    │   ├── customer/
    │   ├── internal/
    │   └── workshop/
    │
    ├── features/                        ← FEATURE (BUSINESS) TIER — domain-aligned feature slices
    │   ├── claims/
    │   ├── documents/
    │   ├── workflow/
    │   ├── notifications/
    │   ├── workshops/
    │   ├── payments/
    │   └── reporting/
    │
    ├── shared/                          ← Shared UI components, hooks, utilities
    │   ├── components/
    │   ├── hooks/
    │   ├── api/
    │   ├── auth/
    │   ├── types/
    │   └── utils/
    │
    └── infra/                           ← Infrastructure (API client, event bus)
        ├── http/
        └── events/
```

---

## Portal Layer (`src/portals/`)

### Customer Portal

```
portals/customer/
├── CustomerPortalRoutes.tsx        ← Route definitions for /customer/*
├── CustomerLayout.tsx              ← Shared nav + header for customer
├── pages/
│   ├── DashboardPage.tsx           ← Overview: active claims, recent activity
│   ├── SubmitClaimPage.tsx         ← Multi-step form: accident details + document upload
│   ├── ClaimDetailPage.tsx         ← Claim status timeline + repair progress
│   ├── ClaimsListPage.tsx          ← All claims with status badges
│   ├── WorkshopSearchPage.tsx      ← Find partner workshops by location
│   ├── AppointmentPage.tsx         ← Book appointment with workshop
│   ├── PaymentPage.tsx             ← Review final bill + electronic payment
│   └── ProfilePage.tsx             ← Change address / billing cycle
└── components/                     ← Customer-specific UI components
    ├── ClaimStatusTimeline.tsx
    ├── ClaimSubmissionStepper.tsx
    ├── WorkshopCard.tsx
    └── RepairProgressCard.tsx
```

### Internal Portal

```
portals/internal/
├── InternalPortalRoutes.tsx
├── InternalLayout.tsx
├── pages/
│   ├── DashboardPage.tsx           ← KPI cards, pending actions by role
│   ├── ClaimsQueuePage.tsx         ← List of assigned/unassigned claims
│   ├── ClaimDetailPage.tsx         ← Full claim view: docs, assessment, adjudication
│   ├── SurveySubmitPage.tsx        ← Surveyor: submit damage assessment
│   ├── AdjudicatePage.tsx          ← Adjustor: approve/reject claim + amount
│   ├── DelegatePage.tsx            ← Case Manager: reassign surveyor/adjustor
│   ├── AuditViewPage.tsx           ← Auditor: read-only claim + audit trail
│   └── reporting/
│       ├── RegionalReportPage.tsx
│       ├── ClaimsKpiPage.tsx
│       └── FraudAgeingPage.tsx
└── components/
    ├── AssessmentForm.tsx
    ├── AdjudicationPanel.tsx
    ├── ClaimAuditTrail.tsx
    └── KpiCard.tsx
```

### Workshop Portal

```
portals/workshop/
├── WorkshopPortalRoutes.tsx
├── WorkshopLayout.tsx
├── pages/
│   ├── DashboardPage.tsx           ← Pending work orders, payment status overview
│   ├── WorkOrderPage.tsx           ← Submit estimate + work order for a claim
│   ├── RepairUpdatePage.tsx        ← Update repair status, ETA
│   ├── FinalBillPage.tsx           ← Submit final bill for customer payment
│   └── PaymentTrackingPage.tsx     ← Track payment per claim
└── components/
    ├── WorkOrderForm.tsx
    ├── RepairStatusUpdater.tsx
    └── PaymentStatusBadge.tsx
```

---

## Feature Layer (`src/features/`)

Each feature slice owns its **API hooks, state, types, and service logic**. This is the "business logic" tier on the frontend.

### Example: `features/claims/`

```
features/claims/
├── index.ts                        ← Public API of the feature (re-exports)
├── api/
│   ├── claimsApi.ts                ← Axios calls: POST /api/v1/claims, GET /api/v1/claims/:id
│   └── claimsApi.types.ts          ← Request/response types (DTOs matching backend)
├── hooks/
│   ├── useSubmitClaim.ts           ← useMutation (React Query): submit claim
│   ├── useClaimDetails.ts          ← useQuery: fetch single claim
│   ├── useClaimsList.ts            ← useQuery: fetch paginated claims list
│   └── useClaimStatus.ts           ← useQuery: poll claim status
├── model/
│   ├── Claim.ts                    ← Frontend domain model (mapped from API DTO)
│   └── ClaimStatus.ts              ← Enum: mirrors backend ClaimStatus
├── store/
│   └── claimsStore.ts              ← Zustand store for UI state (selected claim, filters)
└── validation/
    └── submitClaimSchema.ts        ← Zod schema for client-side form validation
```

### All Features at a Glance

| Feature Folder | Key Hooks | API Endpoints Used |
|----------------|-----------|-------------------|
| `claims` | `useSubmitClaim`, `useClaimDetails`, `useClaimsList` | `POST /claims`, `GET /claims/:id`, `PATCH /claims/:id/status` |
| `documents` | `useUploadDocument`, `useDocumentList` | `POST /documents/upload`, `GET /documents/:claimId` |
| `workflow` | `useAssignClaim`, `useDelegateClaim` | `POST /workflow/assign`, `POST /workflow/delegate` |
| `notifications` | `useNotificationList`, `useMarkRead` | `GET /notifications`, `PATCH /notifications/:id/read` |
| `workshops` | `useWorkshopSearch`, `useWorkOrder` | `GET /workshops?location=`, `POST /work-orders` |
| `payments` | `useInitiatePayment`, `usePaymentStatus` | `POST /payments/initiate`, `GET /payments/:id` |
| `reporting` | `useKpiReport`, `useRegionalReport`, `useFraudAgeing` | `GET /reports/kpi`, `GET /reports/regional`, `GET /reports/fraud-ageing` |

---

## Shared Layer (`src/shared/`)

```
shared/
├── components/                     ← Reusable UI components (design system)
│   ├── ui/
│   │   ├── Button.tsx
│   │   ├── Badge.tsx               ← Status badges (SUBMITTED, APPROVED, etc.)
│   │   ├── Card.tsx
│   │   ├── Modal.tsx
│   │   ├── DataTable.tsx           ← Generic sortable/paginated table
│   │   ├── FileUpload.tsx          ← Drag-and-drop document upload
│   │   ├── Stepper.tsx             ← Multi-step form progress
│   │   └── NotificationToast.tsx
│   └── layout/
│       ├── PageHeader.tsx
│       ├── Sidebar.tsx
│       └── ProtectedRoute.tsx      ← Role-based route guard
│
├── hooks/
│   ├── useAuth.ts                  ← Returns current user + roles from Keycloak context
│   ├── usePagination.ts
│   └── useDebounce.ts
│
├── auth/
│   ├── KeycloakProvider.tsx        ← Keycloak init + React context
│   ├── AuthGuard.tsx               ← Checks token + required roles
│   └── roleUtils.ts                ← hasRole(), getRolesFromToken()
│
├── api/
│   ├── httpClient.ts               ← Axios instance: base URL + JWT interceptor
│   └── errorHandler.ts             ← Maps HTTP error codes → user-friendly messages
│
├── types/
│   ├── UserRole.ts                 ← Enum: mirrors backend UserRole
│   ├── ApiResponse.ts              ← Generic API envelope type
│   └── PagedResponse.ts
│
└── utils/
    ├── formatDate.ts
    ├── formatCurrency.ts
    └── claimStatusLabel.ts         ← Maps ClaimStatus enum → display string + color
```

---

## Infrastructure Layer (`src/infra/`)

```
infra/
├── http/
│   └── apiClientFactory.ts         ← Creates typed API clients per feature module
└── events/
    └── notificationBus.ts          ← WebSocket / SSE listener for real-time claim updates
```

---

## Auth Flow (Keycloak Integration)

```
User opens app
    ↓
KeycloakProvider initialises (keycloak-js)
    ↓
Keycloak redirects to /auth if not authenticated
    ↓
User logs in (Keycloak handles credentials)
    ↓
JWT returned → stored in Keycloak context (memory, not localStorage)
    ↓
Axios interceptor attaches: Authorization: Bearer <token>
    ↓
ProtectedRoute checks token roles → redirects to correct portal
    ↓
React Query fetches data with authenticated requests
```

---

## Environment Variables (`.env.example`)

```bash
VITE_API_BASE_URL=http://localhost:8090/api/v1
VITE_KEYCLOAK_URL=http://localhost:8080
VITE_KEYCLOAK_REALM=eclaims
VITE_KEYCLOAK_CLIENT_ID=eclaims-web
```

---

## Layer Summary (Frontend Mirrors Backend Tiers)

| Frontend Layer | Folder | Backend Equivalent |
|---------------|--------|--------------------|
| **Presentation tier** | `portals/` — pages, layouts, portal routes | Presentation layer (Controllers) |
| **Business / Feature tier** | `features/` — hooks, models, validation, state | Application + Domain layers |
| **Data access / Infrastructure tier** | `infra/`, `shared/api/` — HTTP client, API calls | Infrastructure (adapters, repositories) |
| **Cross-cutting** | `shared/` — auth, components, utils | Shared kernel |
