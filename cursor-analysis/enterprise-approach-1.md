## Enterprise-grade approach (Identity, AuthZ, Scale) — Design Note

### Context
- Customer scale: up to ~200M customers (global), web + mobile portals (customer, internal, partners).
- Key requirements:
  - Identity management with role-based authentication + authorization
  - "Actions per role must be configurable without code changes"
  - Customer onboarding: "create login using policy details"
  - 24x7, scalable, secure, auditable

### 1) Identity: CIAM layer (AuthN) — use Keycloak or Managed CIAM
Decision: separate "who are you" (authentication) from "what can you do" (authorization policy).

Option A — Keycloak (self-managed CIAM)
- Pros: full control, works on-prem & cloud, avoids vendor lock-in, flexible
- Cons: enterprise operations burden (HA, upgrades, DB ops, incident response), hardening/bot defense are on us

Option B — Managed CIAM (Okta/Auth0, Ping, ForgeRock, Entra External ID, etc.)
- Pros: SLA-backed scale, built-in bot/fraud defenses, easier global performance, reduces identity outage risk
- Cons: cost, vendor constraints, data residency considerations

Recommendation (architecture):
- If org can operate identity platform reliably → Keycloak is acceptable.
- If customer auth is mission-critical with massive peaks + reduced ops appetite → managed CIAM is safer.

Key principle: Regardless of CIAM choice, the app is an OIDC resource server and stays portable.

### 2) Customer onboarding: "create login using policy details"
Implement an onboarding workflow that links an identity to an authoritative policy record.

Flow (high-level):
1) Customer enters policyNumber + vehicleReg (+ DOB/phone/zip/lastName as anti-fraud checks)
2) Onboarding service verifies with Policy Admin System (PMS)
3) If verified, create identity in CIAM and bind it to a stable business identifier:
   - customerId (from PMS) stored as user attribute / claim
   - policyNumbers NOT used as identity keys (can change / multiple policies)
4) Issue token; customer accesses portal

Notes:
- Keep "policy verification" separate from "identity creation"
- Add rate limits, throttling, and risk checks (to prevent policy enumeration)

### 3) Authorization: make actions configurable WITHOUT code changes (Policy-based AuthZ)
Hardcoding permissions in controllers (e.g., @PreAuthorize("hasRole(...)")) does NOT satisfy the requirement.

Target: centralize permissions as policy, editable at runtime:
- Use ABAC/ReBAC style decisions: subject + action + resource + context
- Enforce consistently across APIs (not only UI)

Preferred options:
A) OPA (Open Policy Agent) / policy engine
- Policy stored as config (versioned), can be updated without redeploying application
- Backend asks: allow(user, action, resource, context)?
- Supports complex constraints (region, claimStatus, amount thresholds, workshop network, etc.)

B) Keycloak Authorization Services (UMA/resource+scope policies)
- Permissions managed in Keycloak admin console (no app code change)
- Works well for resource/scope models but can get complex; still viable

Minimum governance requirements:
- Policy changes audited (who changed what, when)
- Promotion pipeline for policy changes (dev/test/prod)
- Break-glass/admin workflows

### 4) Multi-portal role model (Customer / Internal / Partner)
- Identity layer stores high-level roles (customer, workshop, surveyor, adjustor, case_manager, auditor, regional_mgr, top_management)
- Authorization policy decides actions:
  - Example actions: claim.submit, claim.view, claim.updateStatus, document.upload, workorder.update, report.viewRegional
- Resource ownership enforced via business attributes:
  - customerId on claims/documents
  - workshopId on work orders
  - region on internal roles

### 5) Scale + performance for millions of users
Architecture patterns:
- Edge: CDN/WAF + rate limiting + bot protection + API gateway
- Stateless APIs with horizontally scalable compute
- Token design: keep JWT claims small; fetch large entitlements from policy/permission service if needed
- Cache strategy:
  - short-lived caches for reference data (workshops/providers)
  - read models for reporting dashboards
- Async/event-driven for notifications, document processing, external provider integration
- Observability: correlation IDs, centralized logs, metrics, traces, SLOs (p99 < 5s)

### 6) Partner providers (workshops + authorized stations + car rental)
Model "service providers" as a first-class domain:
- ProviderType = WORKSHOP | AUTH_SERVICE_STATION | CAR_RENTAL
- Search supports location + zip + radius + availability
- Appointment scheduling: async integration (provider APIs), fallback to manual booking

### 7) Repair tracking + notifications
- Work order lifecycle is owned by provider portal
- Customers track repair progress via:
  - read API: claim -> current work order -> repair status timeline
  - notifications: email/SMS/push on repair.status.updated and delivery date changes
- Important: repair events must include customer identity reference or be enriched downstream.

### 8) Security & compliance essentials
- Encrypt at rest (DB + object storage), TLS in transit
- Audit trail for:
  - access to PII
  - claim state transitions
  - policy/authorization changes
- Data retention / anonymization policies post-settlement
- Non-repudiation: immutable event/audit log + signed notifications if required

### Output of this note
- This design keeps identity provider swappable, meets configurability via policy-based authZ, and supports enterprise scale and governance.