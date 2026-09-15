# Frontend Architecture, Styling, and Integration Guide

## 1. Frontend Technology Stack

The eClaims frontend is built as a modern, enterprise-grade Single Page Application (SPA) with a focus on performance, strict type safety, and clear role boundaries:


| Technology                | Version             | Purpose in eClaims                                                              |
| ------------------------- | ------------------- | ------------------------------------------------------------------------------- |
| **React**                 | `18.3.1`            | Core UI library, component-based view rendering                                 |
| **TypeScript**            | `5.4.5`             | Strict static typing for domain models, DTOs, and component props               |
| **Vite**                  | `5.3.1`             | Next-generation build tool and dev server with fast HMR and proxy               |
| **Tailwind CSS**          | `3.4.4`             | Utility-first styling engine with customized corporate design system            |
| **TanStack React Query**  | `5.40.0`            | Server-state management, caching, background refetching, and query invalidation |
| **React Router DOM**      | `6.23.1`            | Declarative routing with protected routes and role-based portal segmentation    |
| **Keycloak JS**           | `24.0.4`            | OpenID Connect (OIDC) client for authentication and in-memory token management  |
| **React Hook Form + Zod** | `7.51.5` / `3.23.8` | Form state management and runtime schema validation                             |
| **Axios**                 | `1.7.2`             | HTTP client with automatic JWT injection, correlation IDs, and error wrapping   |
| **Lucide React**          | `0.395.0`           | Consistent, accessible icon set                                                 |
| **Vite PWA (Workbox)**    | `0.20.1`            | Progressive Web App offline caching and service worker lifecycle                |


---



## 2. Styling System & Visual Design



### 2.1 Tailwind Theme Configuration (`tailwind.config.ts`)

The visual theme is tailored to an insurance enterprise application:

- **Primary Palette**: Professional blue scale (`primary-50` to `primary-900`), where `#1565c0` (`primary-800`) serves as the brand primary color.
- **Status Palette**: Explicit colors mapped directly to claim and repair lifecycle stages:
  - `submitted`: `#3b82f6` (blue)
  - `assigned`: `#8b5cf6` (purple)
  - `under_survey`: `#f59e0b` (amber)
  - `surveyed`: `#f97316` (orange)
  - `under_adjudication`: `#ef4444` (red-orange)
  - `approved`: `#10b981` (emerald green)
  - `rejected`: `#dc2626` (red)
  - `settled`: `#059669` (dark green)
  - `withdrawn`: `#6b7280` (gray)



### 2.2 Reusable Component Styles (`src/index.css`)

Global CSS uses Tailwind's `@layer components` to avoid repetitive markup and ensure visual consistency across all forms and tables:

- `.btn-primary`: Brand blue button with hover transitions, focus rings, and disabled state styling.
- `.btn-secondary`: Crisp white button with subtle gray border (`border-gray-300`).
- `.card`: Clean container featuring white background, rounded corners (`rounded-xl`), delicate borders (`border-gray-100`), and subtle drop shadow (`shadow-sm`).
- `.input`: Accessible form input with border outlines, focus ring (`ring-primary-500`), and disabled background.



### 2.3 Shared UI Components (`src/shared/components/ui/`)

- `Badge.tsx`: Displays role and lifecycle badges with dynamic color mapping (`StatusBadge`).
- `DataTable.tsx`: Generic, reusable tabular layout supporting custom column accessors, click-to-view navigation, empty state fallback, and animated loading spinners.
- `NotificationBell.tsx`: Live dropdown header widget showing unread notifications with polling indicators and direct navigation links.

---



## 3. Page Rendering & Portal Architecture



### 3.1 Portal Division & Routing Guard (`App.tsx` & `ProtectedRoute.tsx`)

The frontend is divided into three distinct portals protected by Keycloak roles:

1. **Customer Portal (**`/customer/`***)**:
  - Guarded by `ROLE_CUSTOMER`.
  - Main pages: Dashboard, Claims List, Claim Details, Submit Claim Wizard, Workshop Finder, Vehicle Drop-off, Rental Vehicle, Payment, and Profile.
2. **Internal Portal (**`/internal/`***)**:
  - Guarded by internal roles (`ROLE_SURVEYOR`, `ROLE_ADJUSTOR`, `ROLE_CASE_MANAGER`, `ROLE_AUDITOR`, `ROLE_REGIONAL_MGR`, `ROLE_TOP_MANAGEMENT`).
  - Main pages: Claims Queue, Surveyor Assessment, Adjustor Adjudication, Audit Log Explorer, and Regional/Executive KPI Reports.
3. **Workshop Portal (**`/workshop/`***)**:
  - Guarded by `ROLE_WORKSHOP`.
  - Main pages: Workshop Dashboard, Work Order Submission, Repair Status Management, and Photo/Video Media Uploads.



### 3.2 Layout Structure (`Sidebar.tsx` & Portal Layouts)

Each portal wraps its pages inside a consistent layout:

- **Left Navigation (**`Sidebar.tsx`**)**: Fixed 64-width sidebar (`w-64 bg-primary-800 text-white`) showing the portal title, active link highlights, user initials avatar, and a sign-out button.
- **Top Header**: Minimalist white header containing action tools like the `NotificationBell`.
- **Main Viewport**: Scrollable container (`overflow-y-auto`) with responsive padding and centering.

---



## 4. Backend Communication Layer (`httpClient.ts`)

All communication to the Spring Boot REST endpoints runs through a centralized Axios client:

1. **Base URL & Dev Proxy**:
  - Uses `/api/v1` as the base URL.
  - In local development, `vite.config.ts` proxies requests matching `/api` directly to `http://localhost:8090`, eliminating cross-origin (CORS) preflight friction.
2. **JWT Request Interceptor**:
  - Calls `getToken()` from `keycloakInstance.ts`.
  - Injects `Authorization: Bearer <token>` on every request.
  - Generates or appends `X-Correlation-ID: <UUID>` so every frontend action can be traced in backend logs and Kafka messages.
3. **Multipart / File Upload Handling**:
  - Detects `FormData` payloads (for accident photos and repair videos) and deletes explicit `Content-Type` headers so the browser sets the correct multipart boundary automatically.
4. **Error Interceptor**:
  - Unpacks backend error envelopes, extracting `code`, `message`, `correlationId`, and HTTP status codes into structured JavaScript `Error` objects.

---



## 5. How Event-Driven Architecture (EDA) is Reflected on the Frontend

Browsers cannot connect to Kafka topics directly over TCP. The frontend bridges event-driven architecture using a push-and-pull reactivity pattern:

```
[Backend Kafka Topics]
(claim-events, repair-events, payment-events)
          │
          ▼
[ClaimEventConsumer.java]
          │
          ▼
[Postgres DB: notifications.customer_notifications]
          ▲
          │ (HTTP GET /notifications/mine every 30s)
          │
[Frontend NotificationBell.tsx (TanStack Query)]
          │
          ▼ (Notification badge count updates)
   Customer clicks notification ──> navigates to /customer/claims/{claimId}
```



### Mechanisms of Event Reflection:

1. **Periodic Notification Polling (**`NotificationBell.tsx`**)**:
  - Employs TanStack Query with `refetchInterval: 30_000` (30 seconds).
  - When Kafka processes events like `repair.status.updated` or `claim.status.changed`, the notification record is persisted in PostgreSQL.
  - The notification bell pulls the new notification, illuminates the red badge, and displays the event title (e.g., "Repair Update: IN_PROGRESS").
2. **Query Invalidation & Cache Synchronization**:
  - Mutations immediately invalidate or update query keys:
    - When a workshop updates a repair status, `setQueryData` writes the new status directly into `['workshop', 'my-work-orders']` and `['work-order', claimId]`.
    - When a customer submits a claim, `invalidateQueries({ queryKey: ['claims'] })` guarantees that the claims table refreshes.
3. **Always-Fresh Mount Strategy (**`refetchOnMount: 'always'`**,** `staleTime: 0`**)**:
  - Critical detail pages (such as `ClaimDetailPage.tsx` and `WorkOrdersListPage.tsx`) use `staleTime: 0` and `refetchOnMount: 'always'`, so whenever a user navigates between screens or switches tabs, the latest status is fetched immediately.
4. **PWA Runtime Caching (**`vite.config.ts`**)**:
  - Workbox service worker rules:
    - `StaleWhileRevalidate` for single claim details (`/api/v1/claims/*`): loads cached claim instantly, then updates in the background.
    - `NetworkFirst` for claims lists (`/api/v1/claims`): prioritizes live data to avoid showing outdated queues.

 