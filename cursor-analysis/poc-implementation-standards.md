# eClaims POC – Implementation Standards & Enhancement Guide

> **Purpose**: Definitive coding standards, architectural patterns, and implementation rules for the eClaims POC.  
> Consolidates enhancements from prompt analysis, reference doc cherry-picks, and architectural decisions.  
> All items are **additive** — they sharpen implementation quality without changing the architecture.

---

## 1. Backend Coding Standards (Spring Boot)

### 1.1 Entity–DTO Separation — Hard Rule

**Rule**: JPA `@Entity` classes must **never** appear in API responses or requests. Every controller method takes a DTO in and returns a DTO out.

```
Request Flow:   HTTP JSON  →  RequestDTO  →  Service  →  Entity  →  DB
Response Flow:  DB  →  Entity  →  Service  →  ResponseDTO  →  HTTP JSON
```

- `@Entity` classes must carry **no Jackson annotations** (`@JsonIgnore` if needed)
- MapStruct mappers are the **only** crossing point between Entity and DTO
- Domain model objects are also never exposed — Services return DTOs to Controllers

**Why**: Prevents accidental field exposure, decouples persistence schema from API contract, allows both to evolve independently.

---

### 1.2 Lombok Usage Guidelines

| Annotation | Allowed On | Rule |
|-----------|-----------|------|
| `@RequiredArgsConstructor` | `@Service`, `@Component`, `@Repository` | ✅ Use — enables constructor injection without boilerplate |
| `@Builder` | DTOs, Event records, Command objects | ✅ Use — clean immutable object construction |
| `@Value` | Immutable DTOs, Value Objects | ✅ Use — immutability enforced |
| `@Getter` | DTOs, Response objects | ✅ Use |
| `@Slf4j` | Any class needing logging | ✅ Use |
| `@Data` | JPA `@Entity` classes | ❌ **Never** — breaks Hibernate proxy `equals/hashCode`; causes infinite recursion on bidirectional relationships |
| `@AllArgsConstructor` | JPA `@Entity` classes | ❌ **Never** — JPA requires no-arg constructor; conflicts with proxy creation |
| `@SneakyThrows` | Anywhere | ❌ **Never** — silently swallows checked exceptions; hides error handling |
| `@EqualsAndHashCode` | `@Entity` classes | ❌ **Never** — use `id`-only equals/hashCode manually on entities |

**Correct entity pattern:**
```java
@Entity
@Table(name = "claims")
@Getter                         // ✅ Lombok Getter only
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // ✅ JPA requires no-arg
public class ClaimEntity {
    @Id
    private UUID id;
    
    // equals/hashCode based on id only — written manually
    @Override
    public boolean equals(Object o) { ... }
}
```

---

### 1.3 Constructor Injection — No Field Injection

```java
// ✅ Correct — @RequiredArgsConstructor generates this
@Service
@RequiredArgsConstructor
public class ClaimService {
    private final ClaimRepository claimRepository;
    private final DomainEventPublisher eventPublisher;
}

// ❌ Never do this
@Service
public class ClaimService {
    @Autowired
    private ClaimRepository claimRepository;
}
```

---

### 1.4 Java 21 Virtual Threads — Enable for Scale Justification

Enable in `application.yml`. This gives Spring Boot near-Node.js concurrency levels, directly addressing the 200M customer scale question:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Also configure the async thread pool:
```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("claimsTaskExecutor")
    public TaskExecutor taskExecutor() {
        return new TaskExecutor() {
            private final ExecutorService executor =
                Executors.newVirtualThreadPerTaskExecutor();
            @Override
            public void execute(Runnable task) { executor.submit(task); }
        };
    }
}
```

---

## 2. API Design Standards

### 2.1 Consistent Response Envelope

Every API response — success or error — uses the same wrapper:

```java
public record ApiResponse<T>(
    T data,
    ApiError error,
    ApiMeta meta
) {
    public record ApiError(String code, String message, Map<String, String> fieldErrors) {}
    public record ApiMeta(String correlationId, Instant timestamp, String version) {}

    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(data, null,
            new ApiMeta(correlationId, Instant.now(), "v1"));
    }

    public static <T> ApiResponse<T> error(String code, String message, String correlationId) {
        return new ApiResponse<>(null,
            new ApiError(code, message, null),
            new ApiMeta(correlationId, Instant.now(), "v1"));
    }
}
```

**HTTP Status Code Rules:**
| Scenario | Code |
|----------|------|
| Resource created | `201 Created` |
| Successful read/update | `200 OK` |
| Async operation accepted | `202 Accepted` |
| Validation failure | `400 Bad Request` |
| Unauthenticated | `401 Unauthorized` |
| Insufficient role | `403 Forbidden` |
| Resource not found | `404 Not Found` |
| Duplicate / idempotency hit | `200 OK` (return existing) |
| Unhandled server error | `500 Internal Server Error` |

---

### 2.2 Idempotency Implementation

#### Claim Creation — Natural Key Deduplication
```java
// In ClaimEntity — database-enforced uniqueness
@Table(name = "claims", uniqueConstraints = {
    @UniqueConstraint(
        name = "uq_claim_natural_key",
        columnNames = {"policy_number", "incident_date", "vehicle_registration"}
    )
})
```

Service logic:
```java
public ClaimResponse submitClaim(SubmitClaimCommand cmd) {
    // Check for existing claim with same natural key
    return claimRepository
        .findByNaturalKey(cmd.policyNumber(), cmd.incidentDate(), cmd.vehicleRegistration())
        .map(claimMapper::toResponse)           // Return existing — idempotent
        .orElseGet(() -> createNewClaim(cmd));  // Create new
}
```

#### Payment Processing — Client Idempotency Key
```java
@PostMapping("/payments")
public ResponseEntity<ApiResponse<PaymentResponse>> initiatePayment(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody PaymentRequest request) {

    // Check Redis for existing result
    PaymentResponse cached = idempotencyStore.get(idempotencyKey);
    if (cached != null) {
        return ResponseEntity.ok(ApiResponse.success(cached, correlationId()));
    }
    
    PaymentResponse result = paymentService.initiate(request);
    idempotencyStore.put(idempotencyKey, result, Duration.ofHours(24));
    return ResponseEntity.status(201).body(ApiResponse.success(result, correlationId()));
}
```

---

## 3. Kafka Event-Driven Standards

### 3.1 Topic Naming Convention (Dot Notation)

```
claim.created
claim.assigned
claim.approved
claim.rejected
claim.settled
claim.status.changed
document.uploaded
payment.initiated
payment.settled
repair.status.updated
audit.event               ← All modules publish here; immutable, 7yr retention
```

### 3.2 Standard Event Envelope

All Kafka messages use this wrapper. No event is published without these fields:

```java
public record DomainEvent<T>(
    String eventId,         // UUID — primary key for idempotent consumer dedup
    String eventType,       // e.g. "claim.created"
    String correlationId,   // traces the full user request across modules
    String causationId,     // eventId of the event that triggered this one (chain tracing)
    String aggregateId,     // ID of the aggregate this event belongs to (e.g. claimId)
    String aggregateType,   // e.g. "Claim", "Payment"
    String version,         // "v1" — for schema evolution without breaking consumers
    Instant occurredAt,     // when the domain fact occurred (not processing time)
    T payload               // the actual domain data
) {}
```

**Example usage:**
```java
DomainEvent<ClaimCreatedPayload> event = new DomainEvent<>(
    UUID.randomUUID().toString(),
    "claim.created",
    correlationId,
    null,                           // no causation for user-initiated events
    claim.getId().toString(),
    "Claim",
    "v1",
    Instant.now(),
    new ClaimCreatedPayload(claim.getId(), claim.getPolicyNumber(), claim.getStatus())
);
```

### 3.3 Idempotent Consumer Pattern

Every Kafka consumer must deduplicate using `eventId`:

```java
@KafkaListener(topics = "claim.created", groupId = "notification-service")
public void handleClaimCreated(DomainEvent<ClaimCreatedPayload> event) {
    String dedupeKey = "kafka:processed:" + event.eventId();
    
    // SETNX — atomic set-if-not-exists; returns false if already processed
    Boolean isNew = redisTemplate.opsForValue()
        .setIfAbsent(dedupeKey, "1", Duration.ofHours(24));
    
    if (Boolean.FALSE.equals(isNew)) {
        log.info("Skipping duplicate event: {}", event.eventId());
        return;
    }
    
    notificationService.sendClaimSubmittedNotification(event.payload());
}
```

### 3.4 Sync vs Async Operation Map

**Rule**: Never mix sync and async logic. If a user action triggers both a sync response and async side effects, complete the sync path first, then publish to Kafka.

| Operation | Protocol | Reason |
|-----------|----------|--------|
| Claim submission → return claim ID | **Sync REST** | User waits for confirmation |
| Policy validation on submit | **Sync (in-process)** | Must succeed before claim created |
| Document upload to storage | **Async (pre-signed URL)** | Bypasses API; no blocking |
| Notification delivery (email/SMS) | **Async Kafka** | User must not wait for delivery |
| Audit log write | **Async Kafka** | Never blocks a business operation |
| Claim status propagation | **Async Kafka** | Fan-out to multiple consumers |
| Report generation | **Async (scheduled batch)** | Pre-aggregated, never on-demand |
| Payment confirmation to user | **Sync REST** | User waits for payment result |
| Surveyor assignment | **Async Kafka** | Workflow engine processes in background |
| Workshop notification of approval | **Async Kafka** | Non-blocking side effect |

---

## 4. Database Standards

### 4.1 N+1 Query Prevention

**Rule**: Never call a repository method inside a loop. Use `@EntityGraph`, `JOIN FETCH`, or `@BatchSize`.

```java
// ❌ N+1 problem — 1 query for list + N queries for documents
List<Claim> claims = claimRepository.findByCustomerId(customerId);
claims.forEach(c -> c.getDocuments().size()); // Each triggers a query

// ✅ Fix — fetch in one query using @EntityGraph
@EntityGraph(attributePaths = {"documents", "assignments"})
List<Claim> findByCustomerIdWithDocuments(UUID customerId);

// ✅ Fix — JOIN FETCH in JPQL for specific use cases
@Query("SELECT c FROM ClaimEntity c LEFT JOIN FETCH c.documents WHERE c.customerId = :id")
List<ClaimEntity> findByCustomerIdFetchDocuments(@Param("id") UUID id);

// ✅ Fix — @BatchSize for collections that don't always need loading
@OneToMany(mappedBy = "claim", fetch = FetchType.LAZY)
@BatchSize(size = 25)
private List<DocumentEntity> documents;
```

### 4.2 Required Indexes

Define these explicitly in schema init scripts:

```sql
-- Claims — most queried fields
CREATE INDEX CONCURRENTLY idx_claims_customer_id       ON claims.claims(customer_id);
CREATE INDEX CONCURRENTLY idx_claims_status_date       ON claims.claims(status, created_at);
CREATE INDEX CONCURRENTLY idx_claims_policy_number     ON claims.claims(policy_number);
CREATE INDEX CONCURRENTLY idx_claims_natural_key       ON claims.claims(policy_number, incident_date, vehicle_registration);
CREATE INDEX CONCURRENTLY idx_claims_assigned_surveyor ON claims.claims(assigned_surveyor_id) WHERE status = 'ASSIGNED';

-- Documents
CREATE INDEX CONCURRENTLY idx_documents_claim_id ON documents.documents(claim_id);

-- Audit log — time-range queries
CREATE INDEX CONCURRENTLY idx_audit_entity        ON audit.audit_log(entity_type, entity_id);
CREATE INDEX CONCURRENTLY idx_audit_user_time     ON audit.audit_log(user_id, occurred_at DESC);

-- Notifications
CREATE INDEX CONCURRENTLY idx_notifications_recipient ON notifications.notification_log(recipient_id, sent_at DESC);
```

### 4.3 Schema Normalization Rules

- Claim status is stored as `VARCHAR` enum — never as integer codes
- All monetary values as `DECIMAL(12,2)` — never `FLOAT` or `DOUBLE`
- All IDs as `UUID` (not `BIGINT` auto-increment) — globally unique, microservice-safe
- `created_at` and `updated_at` on every entity — managed by `@PrePersist` / `@PreUpdate`
- No nullable foreign keys in core claim lifecycle tables — use explicit `NOT NULL`

---

## 5. Caching Standards (Redis)

### 5.1 Cache Key Naming Convention

```
Format: {entity}:{id}[:{qualifier}]

claim:{claimId}                        ← Single claim
policy:{policyNumber}                  ← Policy reference data
user:{userId}                          ← User profile + roles
workshop:nearby:{zipCode}              ← Workshop list by location
report:kpi:{regionId}:{yyyyMM}         ← Report snapshot
idempotency:{idempotencyKey}           ← Payment idempotency store
kafka:processed:{eventId}              ← Consumer dedup store
rate:limit:{userId}:{endpoint}         ← Rate limiter
```

### 5.2 Cache-Aside Pattern + Explicit Eviction

```java
@Service
@RequiredArgsConstructor
public class PolicyCacheService {
    
    // Cache on read (TTL 15 min — policy data changes rarely)
    @Cacheable(value = "policy", key = "#policyNumber", unless = "#result == null")
    public PolicyDto getPolicy(String policyNumber) {
        return policyServicePort.findByNumber(policyNumber);
    }
    
    // Evict on update — never serve stale data after a write
    @CacheEvict(value = "policy", key = "#policyNumber")
    public void invalidatePolicyCache(String policyNumber) {
        // Called after any policy update from PMS webhook
    }
}
```

### 5.3 What MUST NOT Be Cached

| Data | Why Never Cache |
|------|----------------|
| Current claim status | Customer checking status must see latest; stale status is a support call |
| Payment amounts pending settlement | Financial data — must be transactionally consistent |
| Audit log entries | Append-only; never read from cache |
| User session JWT validity | Token expiry must be checked live against Keycloak |
| Fraud flags | Real-time decision — never serve a cached "not fraud" result |

### 5.4 Cache Hit Ratio Target

- **Target**: `> 85%` cache hit ratio
- **Measured via**: Redis `INFO stats` → `keyspace_hits / (keyspace_hits + keyspace_misses)`
- **Exposed via**: Custom Micrometer gauge in `CacheMetricsConfig`
- **Alert threshold**: `< 75%` triggers cache strategy review

---

## 6. Security Standards

### 6.1 PII Field Masking on API Responses

Sensitive fields are masked at the DTO serialization layer — business logic never changes.

```java
// Custom Jackson serializer for SSN masking
public class MaskedSsnSerializer extends JsonSerializer<String> {
    @Override
    public void serialize(String ssn, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        // Show only last 4: ***-**-1234
        gen.writeString("***-**-" + ssn.substring(ssn.length() - 4));
    }
}

// Apply to DTO field
public record CustomerProfileResponse(
    UUID id,
    String fullName,
    
    @JsonSerialize(using = MaskedSsnSerializer.class)
    String ssn,                          // ← Masked in all API responses
    
    @JsonSerialize(using = MaskedBankAccountSerializer.class)
    String bankAccountNumber,            // ← Masked: ****6789
    
    String email                         // ← Full — needed for communications
) {}
```

**PII Masking Rules:**
| Field | Display Format |
|-------|---------------|
| SSN / Tax ID | `***-**-1234` (last 4) |
| Bank account | `****6789` (last 4) |
| Phone number | `+1 ***-***-7890` (last 4) |
| Driver's license | `****-4321` (last 4) |
| Email | Full — needed for comms |
| Password | Never in response (not even hashed) |

### 6.2 Input Validation — Required on Every Endpoint

```java
// Every @RequestBody must have @Valid
@PostMapping
public ResponseEntity<ApiResponse<ClaimResponse>> submitClaim(
        @Valid @RequestBody ClaimSubmissionRequest request) { ... }

// DTO with full validation annotations
public record ClaimSubmissionRequest(
    @NotBlank(message = "Policy number is required")
    @Pattern(regexp = "^[A-Z]{3}-\\d{8}$", message = "Invalid policy number format")
    String policyNumber,
    
    @NotNull(message = "Incident date is required")
    @PastOrPresent(message = "Incident date cannot be in the future")
    LocalDate incidentDate,
    
    @NotBlank String vehicleRegistration,
    
    @NotNull ClaimType claimType,
    
    @Size(max = 2000, message = "Description cannot exceed 2000 characters")
    String description
) {}
```

---

## 7. Observability Standards

### 7.1 Structured Logging with Correlation ID

```java
// CorrelationIdFilter — sets MDC at start of every request
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = Optional
            .ofNullable(request.getHeader("X-Correlation-ID"))
            .orElse(UUID.randomUUID().toString());
        
        MDC.put("correlationId", correlationId);
        MDC.put("userId", extractUserIdFromToken(request));
        response.setHeader("X-Correlation-ID", correlationId);
        
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

Logback pattern in `application.yml`:
```yaml
logging:
  pattern:
    console: "%d{ISO8601} [%X{correlationId:-no-correlation}] [%X{userId:-anonymous}] %-5level %logger{36} - %msg%n"
```

### 7.2 Liveness vs Readiness Health Probes (Separate Endpoints)

**Liveness** (`/actuator/health/liveness`): Is the JVM alive? Fails → container restart.  
**Readiness** (`/actuator/health/readiness`): Can the app serve traffic? Fails → remove from load balancer (no restart).

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true          # Enables /actuator/health/liveness and /readiness
      show-details: always
      group:
        readiness:
          include: db, redis, kafka    # Readiness depends on all dependencies
        liveness:
          include: ping               # Liveness is just "is JVM alive"
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
```

**Docker Compose health check using readiness probe:**
```yaml
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8090/actuator/health/readiness"]
  interval: 10s
  timeout: 5s
  retries: 5
  start_period: 30s
```

### 7.3 Enhanced Audit Event Structure

```java
public record AuditEvent(
    String eventId,           // UUID — primary key
    String correlationId,     // ties to originating request
    String userId,            // who performed the action
    String userRole,          // role at time of action
    String action,            // e.g. "CLAIM_APPROVED", "STATUS_CHANGED"
    String entityType,        // e.g. "Claim", "Payment"
    String entityId,          // e.g. the claimId
    String oldValue,          // JSON snapshot of previous state (for change tracking)
    String newValue,          // JSON snapshot of new state
    String ipAddress,         // request origin IP (fraud investigation)
    String userAgent,         // browser/client type (fraud investigation)
    String sessionId,         // Keycloak session ID
    Instant occurredAt        // event time
) {}
```

### 7.4 Prometheus Alerting Rules

Add to `infra/monitoring/prometheus-alerts.yml`:
```yaml
groups:
  - name: eclaims.rules
    rules:
      - alert: HighResponseTime
        expr: histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m])) > 5
        for: 2m
        annotations:
          summary: "99th percentile response time exceeds 5 seconds"

      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05
        for: 1m
        annotations:
          summary: "Error rate > 5% over last minute"

      - alert: LowCacheHitRatio
        expr: redis_cache_hit_ratio < 0.75
        for: 5m
        annotations:
          summary: "Redis cache hit ratio below 75% threshold"

      - alert: KafkaConsumerLag
        expr: kafka_consumer_lag_sum > 1000
        for: 3m
        annotations:
          summary: "Kafka consumer lag exceeding 1000 messages"
```

---

## 8. Granular Performance Targets by Operation

Replaces the single `< 5000ms` blanket target with operation-specific SLAs (all satisfy the assignment's 99% < 5000ms mandate):

| Operation | p95 Target | p99 Target | Strategy |
|-----------|-----------|-----------|---------|
| Customer portal — read (view claim status) | `< 800ms` | `< 1200ms` | Redis cache + indexed query |
| Claim submission (write) | `< 1500ms` | `< 2500ms` | Async doc upload; sync claim record only |
| Document upload (10MB) | `< 3000ms` | `< 5000ms` | Pre-signed S3/local URL; direct upload |
| Payment processing | `< 2000ms` | `< 4000ms` | Idempotency store; Stripe async confirm |
| Internal portal — claims queue | `< 500ms` | `< 1000ms` | Paginated + cached |
| Report generation (simple KPI) | `< 2000ms` | `< 4000ms` | Pre-aggregated read model |
| Report generation (complex) | `< 10s` | `< 20s` | Async batch; cached snapshots |
| Notification delivery | `< 30s` | `< 60s` | Kafka consumer lag monitoring |
| Workshop search by location | `< 500ms` | `< 1000ms` | Cached by zip code |

---

## 9. GDPR / CCPA — PII Anonymisation on Deletion Request

Add `ClaimDataRetentionService` to the `claims` module:

```java
@Service
@RequiredArgsConstructor
public class ClaimDataRetentionService {

    /**
     * CCPA/GDPR right-to-erasure handler.
     * Anonymises PII on closed/settled claims.
     * The claim record is retained (regulatory requirement),
     * but the person behind it is anonymised.
     */
    @Transactional
    public void anonymiseCustomerData(UUID customerId) {
        List<ClaimEntity> closedClaims = claimRepository
            .findByCustomerIdAndStatusIn(customerId,
                List.of(ClaimStatus.SETTLED, ClaimStatus.ARCHIVED));
        
        closedClaims.forEach(claim -> {
            claim.anonymisePii();       // Replaces PII with ANONYMISED_{uuid}
            auditPublisher.publish(buildAnonymisationAuditEvent(claim, customerId));
        });
        
        claimRepository.saveAll(closedClaims);
    }
}
```

**Rule**: Active claims (not yet settled) cannot be anonymised — regulatory hold. Document this explicitly.

---

## 10. Frontend Standards (React)

### 10.1 API Service Layer Separation

All API calls go through dedicated service files — never inline in components or hooks:

```
src/services/
├── claimsService.ts         ← All /api/v1/claims/* calls
├── documentsService.ts      ← All /api/v1/documents/* calls
├── workshopsService.ts      ← All /api/v1/workshops/* calls
├── paymentsService.ts       ← All /api/v1/payments/* calls
├── notificationsService.ts  ← All /api/v1/notifications/* calls
└── reportingService.ts      ← All /api/v1/reports/* calls
```

Each service file:
```typescript
// services/claimsService.ts
import { httpClient } from '@/infra/httpClient';
import type { ClaimSubmissionRequest, ClaimResponse } from '@/types/claims';

export const claimsService = {
    submit: (payload: ClaimSubmissionRequest) =>
        httpClient.post<ClaimResponse>('/claims', payload),
    
    getById: (claimId: string) =>
        httpClient.get<ClaimResponse>(`/claims/${claimId}`),
    
    listMyClaims: (page = 0, size = 20) =>
        httpClient.get<PagedResponse<ClaimResponse>>(`/claims?page=${page}&size=${size}`)
};
```

React Query hooks wrap the service:
```typescript
// hooks/useClaimDetails.ts
export const useClaimDetails = (claimId: string) =>
    useQuery({
        queryKey: ['claim', claimId],
        queryFn: () => claimsService.getById(claimId),
        staleTime: 30_000  // 30s — claim status could change
    });
```

### 10.2 PWA (Offline-First) for Customer Portal

The customer portal should function as a PWA so customers on poor mobile connections can view their last-known claim status:

```typescript
// vite.config.ts — add PWA plugin
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      workbox: {
        runtimeCaching: [
          {
            urlPattern: /\/api\/v1\/claims\/.*/,
            handler: 'StaleWhileRevalidate',    // Show cached, revalidate in background
            options: { cacheName: 'claims-cache', expiration: { maxAgeSeconds: 3600 } }
          }
        ]
      },
      manifest: {
        name: 'eClaims',
        short_name: 'eClaims',
        theme_color: '#1565C0',
        icons: [{ src: '/icon-192.png', sizes: '192x192', type: 'image/png' }]
      }
    })
  ]
});
```

**Offline capabilities (Phase 1 consideration):**
- View last-fetched claim status offline
- Queue document uploads when offline; sync when reconnected (Background Sync API)
- Show offline banner when network unavailable

### 10.3 Frontend State Management

- **React Query (TanStack)** for all server state (API data, caching, background refresh)
- **Zustand** for UI state only (selected claim, active filters, modal open/close)
- **No Redux** — Redux Toolkit is overkill for this scope; React Query + Zustand covers all cases
- **Rule**: Never put server-fetched data into Zustand — that's React Query's job

### 10.4 Husky Pre-Commit Hooks

Add to `eclaims-frontend/package.json`:
```json
{
  "lint-staged": {
    "*.{ts,tsx}": ["eslint --fix", "prettier --write"],
    "*.{json,css,md}": ["prettier --write"]
  },
  "husky": {
    "hooks": {
      "pre-commit": "lint-staged && tsc --noEmit"
    }
  }
}
```

---

## 11. Load Testing

### K6 Claim Submission Flow

Add `infra/load-tests/claim-submission.js`:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '1m', target: 50 },    // Ramp up to 50 users
    { duration: '3m', target: 100 },   // Sustain 100 concurrent users
    { duration: '2m', target: 200 },   // Spike to 200
    { duration: '1m', target: 0 },     // Ramp down
  ],
  thresholds: {
    'http_req_duration{name:submitClaim}': ['p99<2500'],   // Claim submission p99 < 2.5s
    'http_req_duration{name:viewClaim}': ['p99<1200'],     // View claim p99 < 1.2s
    'http_req_failed': ['rate<0.01'],                       // < 1% error rate
  }
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090/api/v1';

export default function () {
  // Login and get token (or use pre-generated token for load test)
  const token = __ENV.LOAD_TEST_TOKEN;
  const headers = { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };

  // Submit claim
  const claimPayload = JSON.stringify({
    policyNumber: `POL-${Math.floor(Math.random() * 1e8).toString().padStart(8, '0')}`,
    incidentDate: '2026-04-28',
    vehicleRegistration: `TN${Math.floor(Math.random() * 100)}-${Math.floor(Math.random() * 9999)}`,
    claimType: 'COLLISION',
    description: 'Vehicle collision at highway'
  });

  const submitRes = http.post(`${BASE_URL}/claims`, claimPayload, {
    headers,
    tags: { name: 'submitClaim' }
  });

  check(submitRes, {
    'claim created': (r) => r.status === 201 || r.status === 200,
    'has claimId': (r) => JSON.parse(r.body).data?.claimId !== undefined
  });

  const claimId = JSON.parse(submitRes.body).data?.claimId;
  if (!claimId) return;

  // View claim status
  const viewRes = http.get(`${BASE_URL}/claims/${claimId}`, {
    headers,
    tags: { name: 'viewClaim' }
  });
  check(viewRes, { 'claim found': (r) => r.status === 200 });

  sleep(1);
}
```

Run with: `k6 run --env BASE_URL=http://localhost:8090/api/v1 infra/load-tests/claim-submission.js`

---

## 12. Summary — All Enhancements at a Glance

| # | Enhancement | Where It Lives | Source |
|---|------------|---------------|--------|
| 1 | Entity-DTO separation hard rule | Backend coding standards | Prompt analysis |
| 2 | Lombok usage guidelines | Backend coding standards | Prompt analysis |
| 3 | Idempotency — claim natural key + payment idempotency header | `claims` + `payments` modules | Prompt analysis |
| 4 | Standard Kafka event envelope (`eventId`, `causationId`, `version`) | `shared/contracts/events` | Prompt analysis |
| 5 | Sync vs async operation map | Architecture design rule | Prompt analysis |
| 6 | Redis key naming + `@CacheEvict` + what NOT to cache | Caching strategy | Prompt analysis |
| 7 | PII field masking on DTO serialization | Security + DTO layer | Prompt analysis |
| 8 | N+1 prevention (`@EntityGraph`, `JOIN FETCH`, `@BatchSize`) | Data access coding standards | Prompt analysis |
| 9 | PWA offline-first for customer portal | Frontend plan | Prompt analysis |
| 10 | Java 21 Virtual Threads enabled | `app/eclaims-api/application.yml` | Reference docs |
| 11 | Liveness vs readiness health probes | Observability | Reference docs |
| 12 | Enhanced audit event + `oldValue/newValue/ipAddress/userAgent` | `shared/kernel/audit` | Reference docs |
| 13 | Granular performance targets per operation | NFR strategy | Reference docs |
| 14 | K6 load testing script | `infra/load-tests/` | Reference docs |
| 15 | GDPR/CCPA PII anonymisation on deletion | `claims` module | Reference docs |
| 16 | Husky pre-commit hooks | Frontend tooling | Reference docs |
| 17 | 85%+ cache hit ratio as measurable NFR metric | Caching + observability | Reference docs |
