# Q&A Discussion Script: Category 5 - API Design

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Why REST?

**Spoken Answer:**
"We chose REST over GraphQL and gRPC for our primary client-facing APIs because of **ecosystem maturity, edge cacheability, client heterogeneity, and partner interoperability**.

Our platform serves four distinct client categories: React web portals, React Native iOS/Android apps, third-party repair body shops, and legacy internal insurance back-office systems. 

REST over HTTPS gives us:
1. Universal Interoperability: Every programming language, mobile platform, and legacy workshop management system has native HTTP client libraries.
2. Native Edge Caching: REST's standard HTTP verbs (`GET`) and cache-control headers (`ETag`, `Cache-Control`) allow Amazon CloudFront and browser caches to offload static read requests effortlessly, which GraphQL cannot do without specialized extensions.
3. Simple Tooling & Security: AWS WAF, API Gateway, and enterprise API catalogs have first-class, out-of-the-box support for OpenAPI 3.0 / Swagger specifications and REST inspection.

While we evaluate gRPC internally for high-throughput inter-service streaming (such as Camunda Zeebe workers), REST remains the gold standard for our external edge and client tiers."

**Technical Bullets:**
- Standard: OpenAPI 3.0 contract specification.
- Edge Compatibility: CloudFront CDN natively caches `GET` responses via HTTP status codes and headers.
- Simplicity: Lower learning curve and simpler debugging compared to GraphQL query complexity attacks.

---

### Q2: Why /api/v1 URL versioning?

**Spoken Answer:**
"We chose URI path versioning (`/api/v1/...`) over header or query parameter versioning because of **transparency, CDN edge cache friendliness, and mobile app lifecycle management**.

With URI path versioning:
1. Routing Simplicity: Amazon API Gateway, CloudFront, and Application Load Balancers can easily inspect the URL path and route `/api/v1` traffic to current microservice containers and `/api/v2` to a canary cluster without inspecting HTTP request headers.
2. Edge Caching: Caching proxies and CDNs naturally key their caches on the full URI. Header-based versioning (`Accept: application/vnd.ycompany.v1+json`) requires complex `Vary` header configurations that frequently lead to cache poisoning or cache misses.
3. Mobile App Support: Unlike web browsers where users instantly receive the latest code on reload, mobile apps live on customer phones for months without being updated. URI path versioning makes it unmistakable in access logs and metrics which legacy mobile app versions are still active in the wild."

**Technical Bullets:**
- Routing: Path-based routing rules in AWS API Gateway and ALB.
- Caching: High CDN cache hit ratios without complex `Vary: Accept` header logic.
- Observability: Direct visibility into version traffic distribution in CloudWatch logs.

---

### Q3: PUT vs PATCH?

**Spoken Answer:**
"The distinction between PUT and PATCH comes down to **complete resource replacement versus partial resource modification**.

Under RFC 7231, `PUT` is idempotent and replaces the target resource in its entirety. If a client sends a PUT request with only `{ "email": "new@domain.com" }`, all other unmentioned fields on that resource (like `phoneNumber`, `address`, `firstName`) are overwritten or wiped to `null`.

`PATCH` (RFC 5789), by contrast, applies a partial delta update to an existing resource. The client sends only the fields that changed, leaving all other existing fields completely untouched. 

In our eClaims platform:
- We use `PUT` when creating or completely replacing an idempotent sub-resource (e.g., uploading and replacing an entire policyholder contact profile).
- We use `PATCH` for almost all operational updates - such as updating a claim status, revising a damage estimate, or changing an appointment date - because multiple actors modify different aspects of a claim concurrently."

**Technical Bullets:**
- PUT: Full replacement; idempotent; missing fields are set to null/default.
- PATCH: Partial delta modification; non-destructive to unmentioned fields.
- RFC Standards: RFC 7231 (PUT) vs RFC 5789 (PATCH).

---

### Q4: Why is claim status update PATCH?

**Spoken Answer:**
"Claim status updates are strictly implemented as `PATCH /api/v1/claims/{claimId}/status` because **a status transition is a targeted lifecycle state mutation, NOT a replacement of the entire claim entity**.

A `Claim` aggregate root contains over 40 distinct attributes: policy details, vehicle VIN, accident coordinates, surveyor assessments, document references, and audit logs. 

If status changes were executed via `PUT /api/v1/claims/{claimId}`:
1. The client would have to send the entire 20KB JSON payload back to the server just to change `status` from `SUBMITTED` to `UNDER_REVIEW`.
2. It introduces catastrophic race conditions: if an adjuster sends a full PUT to approve a claim while a customer is concurrently uploading a photo, the adjuster's PUT could overwrite and erase the customer's photo metadata!
3. By using `PATCH`, the request payload is lean: `{ "status": "SURVEYED", "reason": "Inspection complete" }`, enforcing targeted, atomic state transition validation."

**Technical Bullets:**
- Payload Size: 200 bytes for PATCH vs 20KB for full PUT entity.
- Concurrency Safety: Eliminates accidental overwrites of unrelated fields during simultaneous edits.
- State Machine Guard: Dedicated controller endpoint `ClaimStatusController.patchStatus()` validates allowed state machine transitions (e.g., cannot jump from `SUBMITTED` directly to `SETTLED`).

---

### Q5: How do you make POST /claims idempotent?

**Spoken Answer:**
"We make `POST /api/v1/claims` idempotent using a client-generated **Idempotency Key pattern backed by Amazon ElastiCache / MemoryDB**.

Here is the exact request lifecycle:
1. When the customer opens the FNOL submission wizard on their mobile app or browser, the client generates a unique UUIDv4 string (the `Idempotency-Key`).
2. When the user taps 'Submit Claim', the client passes this key in the HTTP header: `Idempotency-Key: 7b3a9e21-4f81-4b82-bc12-d819e91f1c24`.
3. In our API Gateway or Spring Boot filter, we execute an atomic Redis command:
   `SET idempotency:claim:{key} "IN_PROGRESS" NX EX 120`.
4. If Redis returns 0 (key already exists):
   - If the value is `"IN_PROGRESS"`, another thread is currently processing the same request. We return **HTTP 409 Conflict** with a `Retry-After: 2` header.
   - If the value contains a serialized response payload (e.g., `{ "claimId": "CLM-2026-004812", "status": "SUBMITTED" }`), we immediately return the cached **HTTP 201 Created** response without executing any backend code or database writes.
5. If Redis returns 1 (new key):
   - The Claims Service saves the claim to PostgreSQL, publishes the outbox event, and writes the completed response JSON into the Redis idempotency key with a 24-hour TTL before returning HTTP 201."

**Technical Bullets:**
- Header: `Idempotency-Key: <UUIDv4>`.
- State Machine: `IN_PROGRESS` (lease 120s) -> `COMPLETED` (payload cached 24h).
- Atomic Operation: Redis `SET ... NX EX` guarantees single-flight execution.

---

### Q6: What happens if the customer clicks Submit Claim twice?

**Spoken Answer:**
"If a nervous customer double-clicks 'Submit Claim' rapidly on their phone, our idempotency architecture guarantees that **only ONE claim is created in the database, and the customer receives an identical success confirmation for both clicks**.

Here is what happens under the hood:
- Click 1: The mobile app sends `POST /api/v1/claims` with `Idempotency-Key: ABC`. The server acquires the Redis lock, begins the database transaction, creates `CLM-2026-004812`, caches the response, and returns HTTP 201 Created.
- Click 2 (arriving 100ms later): The second HTTP request carries the identical `Idempotency-Key: ABC`. 
If Click 1 is still processing, the server returns HTTP 409 Conflict, and the mobile SDK silently waits and polls. If Click 1 has finished, the server intercepts the request at the cache layer, returns the identical HTTP 201 with `CLM-2026-004812`, and never executes a second database insert.

The customer sees a smooth UI experience with zero duplicate claims, zero duplicate adjusters assigned, and zero duplicate SMS messages."

**Technical Bullets:**
- UX Handling: Frontend disables the submit button immediately on click and generates a single UUID per wizard session.
- Backend Safety: Redis idempotency filter intercepts duplicate requests before they reach the controller.
- Outcome: Exactly one database row inserted; zero duplicate side-effects.

---

### Q7: Why isn't customerId accepted in the request?

**Spoken Answer:**
"Accepting `customerId` in the JSON request body or URL query parameter is one of the most dangerous security vulnerabilities in API design, known as an **Insecure Direct Object Reference (IDOR)** or Broken Object Level Authorization (BOLA).

If our endpoint accepted `POST /api/v1/claims` with body `{ "customerId": "CUST-999", "damage": 5000 }`, any malicious actor or compromised browser could simply modify the JSON payload and submit fraudulent claims against another policyholder's account!

In our architecture, the client never supplies `customerId`. Instead, the caller's identity is **cryptographically derived directly from the verified JWT access token** issued by AWS Cognito."

**Technical Bullets:**
- Security Vulnerability Avoided: OWASP API Security Top 10 - API1:2023 Broken Object Level Authorization (BOLA).
- Identity Source: Extracted strictly from the signed JWT claim (`sub` or `custom:customerId`).
- Tamper Resistance: Asymmetric RS256 signature ensures client cannot spoof customer identity.

---

### Q8: How do you determine customer identity?

**Spoken Answer:**
"We determine customer identity through a secure, tamper-proof pipeline:

1. Authentication: The user logs in via AWS Cognito using their credentials and MFA. Cognito returns an RS256-signed JWT Access Token.
2. Gateway Ingress: The mobile app or browser attaches this token to every request in the `Authorization: Bearer <JWT>` header.
3. Signature Verification: Amazon API Gateway validates the token signature against Cognito's public JSON Web Key Set (JWKS), verifying that the token is untampered and unexpired.
4. Identity Extraction: In our Spring Boot backend, our `JwtAuthenticationFilter` extracts the cryptographically verified `sub` (subject claim) and custom attributes from the Spring `SecurityContextHolder`.
5. Context Injection: The controller calls `UserContextHolder.getCurrentCustomerId()`, guaranteeing that business logic only ever operates on the authenticated user's true identity."

**Technical Bullets:**
- Protocol: OAuth 2.0 / OpenID Connect (OIDC) Bearer Tokens.
- Validation: Asymmetric RS256 signature check against JWKS endpoint.
- Code Pattern: Extracted from `SecurityContextHolder.getContext().getAuthentication().getPrincipal()`.

---

### Q9: How do you authorize /claims/{claimId}?

**Spoken Answer:**
"Authorizing access to a specific claim resource requires **Attribute-Based Access Control (ABAC) combined with Role-Based Access Control (RBAC)**:

When a request arrives at `GET /api/v1/claims/{claimId}`:
1. Role Check: Spring Security checks the caller's realm roles in the JWT:
   - If the user has role `ROLE_ADMIN` or `ROLE_AUDITOR`: Access is granted for audit compliance.
   - If the user has role `ROLE_ADJUSTER` or `ROLE_SURVEYOR`: The system checks whether this staff member is actively assigned to this specific claim in the `workflow.assignments` table.
2. Customer Ownership Check (Preventing IDOR):
   - If the user is a `ROLE_CUSTOMER`: The Claims Service executes an ownership query:
     `SELECT id FROM claims.claims WHERE id = :claimId AND customer_id = :authenticatedUserId;`
   - If the query returns a match, access is granted.
   - If the claim belongs to another customer, the service immediately aborts and returns an **HTTP 404 Not Found** (or HTTP 403 Forbidden)."

**Technical Bullets:**
- Pattern: ABAC (Attribute-Based Access Control) + RBAC.
- Defense in Depth: Role checked in Spring Security filter; resource ownership checked in database query.
- Framework: Spring Security `@PreAuthorize("@claimSecurityService.canAccessClaim(#claimId)")`.

---

### Q10: How do you prevent customer A from retrieving customer B's claim by changing the UUID?

**Spoken Answer:**
"This is the textbook BOLA/IDOR attack scenario: Customer A changes the URL from `/api/v1/claims/UUID-111` to `/api/v1/claims/UUID-222`.

We defend against this through two strict practices:
1. Database-Level Ownership Scoping: We NEVER write a query like `claimRepository.findById(claimId)` for customer requests! 
Instead, customer queries are strictly scoped by their authenticated identity:
`claimRepository.findByIdAndCustomerId(claimId, authenticatedCustomerId)`.
If Customer A guesses Customer B's UUID, the database query returns `Optional.empty()`.
2. Opaque Error Responses (HTTP 404 vs 403):
When `findByIdAndCustomerId` returns empty, our API responds with **HTTP 404 Not Found**, NOT 403 Forbidden. 
Why? Because returning 403 tells the attacker: *'That claim UUID exists, but you are not allowed to see it,'* allowing them to enumerate valid claim IDs. Returning 404 completely conceals whether the resource even exists."

**Technical Bullets:**
- Query Pattern: `SELECT * FROM claims WHERE id = :id AND customer_id = :authUserId`.
- Status Code: HTTP 404 Not Found prevents resource enumeration.
- ID Format: UUIDv4 (128-bit random) provides 5.3x10^36 possible values, making brute-force enumeration mathematically impossible.

---

### Q11: How do you handle pagination?

**Spoken Answer:**
"We handle pagination using a dual strategy based on the specific consumer and data scale:

1. For Administrative and Staff Portals (Adjuster Worklists, Fraud Triage):
We use **Offset-Based Pagination** (`page=1&size=25&sort=createdAt,desc`). This allows adjusters to jump directly to specific pages (e.g., Jump to Page 4) and view total record counts. Because triage queues are filtered by status and indexed by composite keys, page sizes are small and bounded.

2. For High-Volume Public Feeds and Audit Trails (Customer Claim History, Mobile Activity Feeds, Audit Logs):
We use **Cursor-Based (Keyset) Pagination** (`limit=20&cursor=eyJjcmVhdGVkQXQiOjE3...`). The cursor encodes the timestamp and ID of the last item in the previous page. This guarantees constant execution time and zero page-drift."

**Technical Bullets:**
- Standard: Spring Data `Pageable` for administrative queues; custom keyset cursor for infinite-scroll mobile feeds.
- Default Constraints: Max page size hard-capped at 100 records (`@Max(100)`) to prevent heap exhaustion.

---

### Q12: Offset versus cursor pagination?

**Spoken Answer:**
"Here is the architectural comparison between Offset and Cursor pagination:

**Offset Pagination (`LIMIT 20 OFFSET 10000`)**:
- How it works: The database scans the first 10,020 rows, discards the first 10,000, and returns the last 20.
- The Problem: At high offsets (e.g., page 500 in an audit table with millions of rows), query execution times skyrocket to 5-10 seconds, causing high disk I/O. Furthermore, it suffers from **Page Drift**: if a new claim is inserted while a user navigates from Page 1 to Page 2, records shift and the user sees duplicate items.
- Best for: Admin screens needing total page counts and direct page navigation on small, filtered datasets.

**Cursor (Keyset) Pagination (`WHERE (created_at, id) < (:last_timestamp, :last_id) ORDER BY created_at DESC LIMIT 20`)**:
- How it works: The query seeks directly to the index leaf node using a deterministic cursor.
- The Advantage: **Execution time is O(1) constant**, running in <5ms whether fetching page 1 or page 10,000. It is immune to page drift and ideal for mobile infinite scrolling."

**Technical Bullets:**
- Offset: Easy implementation, supports random page jumping, but O(N) database cost and vulnerable to page drift.
- Cursor: O(1) indexed seek time, stable during concurrent inserts, but cannot jump to arbitrary pages.

---

### Q13: How do you handle API rate limiting?

**Spoken Answer:**
"We implement a **multi-tiered defense-in-depth rate limiting strategy** combining edge-level throttling with application-level token buckets:

1. Edge Tier (Amazon API Gateway & AWS WAF):
We enforce global IP and API key rate limits at the perimeter before requests ever consume container compute:
- Global limit: 10,000 requests per second (RPS) with a 5,000 RPS burst buffer.
- Per-Client limit: Authenticated customer tokens are throttled to 50 requests per minute.
- AWS WAF Rate-Based Rules: Automatically blocks any IP that sends more than 2,000 requests in a 5-minute window to defend against brute-force DDoS.

2. Application Tier (Bucket4j with Redis):
For critical, high-cost business endpoints (like FNOL submission, SMS resend, and payment initiation), we enforce granular application token buckets in Redis to prevent abuse."

**Technical Bullets:**
- Edge: API Gateway Usage Plans + WAF Rate-Based Rules.
- App Tier: Bucket4j token-bucket algorithm distributed across ElastiCache Redis.
- Headers Returned: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
- Rejection: HTTP 429 Too Many Requests with `Retry-After: <seconds>`.

---

### Q14: API Gateway throttling versus application-level throttling?

**Spoken Answer:**
"Both are necessary because they solve fundamentally different problems:

**API Gateway Throttling (Infrastructure Defense)**:
- Operates at the network perimeter.
- Protects downstream microservices and container compute from being overwhelmed by volumetric traffic surges, bots, and DDoS attacks.
- Rejects requests with HTTP 429 in under 5 milliseconds without consuming any backend CPU, memory, or database connections.
- Limitation: Limited business context; throttles based on IP, API keys, or simple JWT claims.

**Application-Level Throttling (Business Logic Defense)**:
- Operates inside the microservice layer (via Redis and Bucket4j).
- Enforces complex business rules: e.g., 'A customer can only submit 3 claim draft uploads per hour,' or 'A workshop can only update repair estimates 10 times a day.'
- Can inspect database state, user tiers, and request payload attributes."

**Technical Bullets:**
- Gateway: Macro protection, coarse-grained (IP/Client ID), zero backend cost.
- Application: Micro protection, fine-grained, domain-aware, requires Redis lookup.

---

### Q15: How do you propagate correlation IDs?

**Spoken Answer:**
"In a distributed microservices platform, tracing a single request across multiple services and Kafka topics is essential for observability. We propagate correlation IDs using **W3C Trace Context and OpenTelemetry**:

1. Edge Ingress: When a request arrives at the Application Load Balancer or API Gateway, if no correlation header exists, the gateway generates a UUIDv4 and sets `X-Correlation-ID: c84e21a0-...` (and standard `traceparent`).
2. HTTP Propagation: Spring Boot filters (via Micrometer Tracing / OpenTelemetry) capture `X-Correlation-ID` and inject it into the Logging Mapped Diagnostic Context (MDC) so that every single log line automatically prints: `[eclaims-claims, corr-id=c84e21a0]`.
3. Outbound HTTP: Feign or `RestClient` interceptors copy the header to downstream REST calls.
4. Kafka Message Headers: When emitting an event to Kafka, our outbox publisher injects `correlation_id` directly into the Kafka Record Header. Downstream consumers extract it and restore it to their local MDC."

**Technical Bullets:**
- Header: `X-Correlation-ID` and W3C standard `traceparent`.
- Logging: SLF4J MDC (Mapped Diagnostic Context) outputs correlation ID in every JSON log line.
- Kafka Integration: Record Headers propagate trace context across asynchronous message boundaries.

---

### Q16: What does a 409 Conflict represent?

**Spoken Answer:**
"In our API design, **HTTP 409 Conflict represents a state violation where the request cannot be completed due to a conflict with the current state of the target resource**.

We return 409 in three specific business scenarios:
1. Optimistic Locking Collisions: When User A attempts to update a claim with a stale row version (e.g., database version is 6, but client sent version 5), indicating another user modified the claim concurrently.
2. Invalid State Machine Transitions: If an adjuster attempts to approve a claim that is currently in `REJECTED` or `SETTLED` status.
3. Concurrent Idempotency Locks: When a duplicate `POST /api/v1/payments` request arrives while the first payment transaction is actively executing in-flight (`IN_PROGRESS`).

The 409 response payload always includes a machine-readable error code and message explaining the exact conflict."

**Technical Bullets:**
- RFC: RFC 7231 Section 6.5.8.
- Common Error Codes: `CONCURRENT_MODIFICATION`, `INVALID_STATE_TRANSITION`, `IDEMPOTENCY_IN_PROGRESS`.
- Client Action: Client should refresh resource state or retry with backoff.

---

### Q17: 400 versus 422?

**Spoken Answer:**
"The distinction between HTTP 400 and HTTP 422 comes down to **syntactic errors versus semantic validation errors**:

**HTTP 400 Bad Request (Syntactic / Structural Error)**:
- The server cannot parse or understand the request.
- Examples: Malformed JSON syntax (missing closing bracket), invalid HTTP query parameters, unrecognizable date format (`"date": "tomorrow"`), or missing required HTTP headers.

**HTTP 422 Unprocessable Content (Semantic / Business Rule Error)**:
- The JSON syntax is 100% valid and well-formed, but the data violates business rules or domain invariants.
- Examples: A claim submission with a valid integer damage estimate of `-500` (cannot be negative), an accident date set in the year 2035 (future date), or a deductible higher than policy limits.
- Spring Boot returns 422 with a structured field-by-field validation error list:
  `{ "errors": [{ "field": "accidentDate", "message": "Date of loss cannot be in the future" }] }`."

**Technical Bullets:**
- HTTP 400: Malformed syntax, deserialization failure, unparseable payload.
- HTTP 422: Valid syntax, failed domain constraints (`@Valid`, Hibernate Validator, business invariants).
- Standard: RFC 4918 / RFC 9110.

---

### Q18: How do you version APIs without breaking mobile applications?

**Spoken Answer:**
"Mobile application users cannot be forced to update immediately; older app builds linger in production for 12 to 24 months.

We ensure backward compatibility without breaking mobile apps through three rules:
1. Strict Additive Changes: We never remove fields, rename keys, or alter existing data types in existing API versions (`/api/v1`). If we introduce a new feature (like EV battery health status), we add it as an optional field with a sensible default. Older mobile apps simply ignore the new JSON key.
2. Deprecation Policy & Sunset Headers: When a breaking change is inevitable, we launch `/api/v2`. The `/api/v1` endpoint continues running in parallel, returning the standard RFC 8594 `Sunset: <date>` and `Deprecation: true` HTTP headers to notify client SDKs.
3. Contract Testing: We execute automated consumer-driven contract tests (Pact) in our CI pipeline to guarantee that backend API changes do not break mobile schema expectations."

**Technical Bullets:**
- Policy: Strict additive changes within a major version.
- Headers: RFC 8594 `Sunset: Wed, 11 Nov 2026 00:00:00 GMT` and `Deprecation: true`.
- Testing: Pact / Spring Cloud Contract tests validate legacy mobile contracts against PR builds.

---

### Q19: How do external workshops authenticate?

**Spoken Answer:**
"External repair workshops authenticate through a dedicated **Partner B2B Identity Flow** managed by our Keycloak cluster:

1. Interactive Workshop Portal Access: Workshop mechanics and managers log in via the React Partner Portal using OpenID Connect (OIDC) Authorization Code Flow with PKCE. Their accounts are assigned the `ROLE_WORKSHOP_MANAGER` or `ROLE_WORKSHOP_TECHNICIAN` realm roles, with fine-grained permissions to view only claims assigned to their specific workshop facility ID (`facility_id`).
2. Machine-to-Machine Integration (Shop Management Systems): Major certified body shop chains (like Caliber Collision) integrate their internal shop management software directly with our REST APIs. For this, we issue **OAuth 2.0 Client Credentials Grant** tokens (m2m tokens) with scoped permissions (e.g., `scope: "repair:workorders:write"`).
3. Network Whitelisting: External workshop API traffic is restricted to static egress IPs and protected by API Gateway rate limits."

**Technical Bullets:**
- Interactive: OIDC Authorization Code Flow + PKCE via Keycloak.
- B2B API: OAuth 2.0 Client Credentials Grant with facility scoping.
- Authorization: Scoped claims validate that the workshop can only query work orders where `workshop_id == token.facility_id`.

---

### Q20: How would you secure file upload APIs?

**Spoken Answer:**
"Allowing direct file uploads to application servers is a major attack vector for malware, denial-of-service, and remote code execution.

We secure document and photo uploads using **Pre-Signed S3 URLs and Asynchronous Malware Quarantine**:

1. Zero Server File I/O: The client never streams file bytes through our Spring Boot microservices. 
The client sends a lightweight JSON metadata request:
`POST /api/v1/documents/upload-ticket` with `{ "fileName": "bumper.jpg", "mimeType": "image/jpeg", "fileSize": 3145728 }`.
2. Strict Validation: The Document Service validates that the MIME type is in an approved whitelist (`image/jpeg`, `image/png`, `application/pdf`), file size is under 15MB, and user has permission to upload to that claim.
3. Cryptographic Pre-Signed URL: The service generates a temporary Amazon S3 pre-signed PUT URL with a **strict 5-minute expiration window** and exact Content-Type headers.
4. Direct-to-S3 Upload: The client uploads the binary directly to an **isolated Quarantine S3 Bucket**.
5. Asynchronous Scanning: S3 triggers an event that invokes an AWS Lambda antivirus scanner (ClamAV) and AWS Textract. Only once the file is certified clean is it moved to the primary compliant WORM storage bucket."

**Technical Bullets:**
- Pattern: Pre-Signed S3 URLs (Zero container RAM/disk consumption).
- Whitelist: Strict MIME-type inspection, extension validation, and 15MB size caps.
- Security Pipeline: Quarantine Bucket -> ClamAV Antivirus Lambda -> Compliance WORM Storage.
