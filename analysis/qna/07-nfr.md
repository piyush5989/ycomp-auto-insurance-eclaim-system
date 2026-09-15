# Q&A Discussion Script: Category 7 - NFR (Non-Functional Requirements)

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: What exactly does p99 mean?

**Spoken Answer:**
"P99, or the **99th percentile latency**, is a statistical measure that means **99% of all requests completed in less than the specified duration, while only the slowest 1% exceeded it**.

For example, our assignment explicitly mandates: *'The offered solution must complete 99% of provided services in less than 5000 milliseconds over both peak and non-peak hours.'*
If our platform handles 1,000,000 API requests over an hour, a p99 of under 5,000ms guarantees that at least 990,000 requests finished in under 5 seconds. 

In enterprise architecture, p99 is the gold standard for measuring customer experience because it captures the long-tail latency that frustrated users actually experience, whereas averages hide poor performance."

**Technical Bullets:**
- Definition: Value below which 99% of observations fall in a sorted frequency distribution.
- Formula: Rank \(R = \lceil 0.99 \times N \rceil\); value at index \(R\) is the p99 latency.
- Mandate: Assignment requirement specifies 99% < 5,000ms SLA across peak and non-peak.

---

### Q2: Why p99 rather than average?

**Spoken Answer:**
"We measure p99 rather than the average (mean) because **averages lie in distributed software systems**.

Consider this real-world example:
Suppose we have 100 requests. 99 of those requests take 50 milliseconds each, but 1 request gets stuck behind a database lock or garbage collection pause and takes 20,000 milliseconds (20 seconds).
If you calculate the average, it is approximately 249 milliseconds. A dashboard showing an average of 249ms looks wonderful to management. But that one user had a horrific 20-second freeze, their mobile app timed out, and they abandoned their claim submission.

In an insurance platform with millions of claims, relying on averages hides micro-outages, database connection pool starvation, thread contention, and network packet loss. P99 forces our engineering team to design for the worst-case long-tail scenarios."

**Technical Bullets:**
- The Flaw of Averages: Heavily skewed by distribution shape (bimodal distributions in I/O systems).
- Long Tail Latency: Captures JVM GC pauses, cold starts, connection pool queueing, and disk I/O waits.
- Industry Standard: Amazon, Google, and enterprise financial platforms mandate p95/p99/p99.9 SLAs.

---

### Q3: If p99 is 4.8 seconds, what does that tell you?

**Spoken Answer:**
"If our monitoring dashboard shows a p99 of 4.8 seconds against a 5.0-second SLA, it tells me that **we are on the verge of an SLA breach and our system is experiencing significant architectural distress**.

Even though 4.8 seconds technically passes the 5.0-second SLA, an architect sees the warning lights:
1. Dangerously Thin Headroom: We have only a 200ms margin of safety. A minor 5% increase in traffic or a momentary cloud network jitter will push p99 past 5.0 seconds into contractual breach.
2. Underlying Degradation: Normal microservice REST requests (persisting a claim and publishing an outbox event) should complete in 150 to 300 milliseconds. If p99 has inflated to 4.8 seconds, it indicates that down in the 95th to 99th percentile, requests are queueing up - likely due to database connection pool exhaustion, slow sequential table scans, or unmanaged thread pool saturation.
3. Call to Action: It triggers immediate profiling of slow queries, checking Aurora CPU and read replica lag, and verifying ECS container autoscaling triggers."

**Technical Bullets:**
- Status: Statistically compliant, but operationally high-risk.
- Root Cause Indicators: Thread pool queueing, HikariCP pool starvation, un-indexed database joins, or external vendor API latency.
- Action: Automated alert triggers at p99 > 3,500ms (70% of SLA limit) to investigate before breaching 5,000ms.

---

### Q4: What about the remaining 1%?

**Spoken Answer:**
"The remaining 1% represents the extreme tail latency of our distribution - the outliers between p99 and p100 (maximum latency).

In our architecture, we do not ignore the remaining 1%; we manage it through strict defensive engineering:
1. Hard Client and Gateway Timeouts: We configure a **hard timeout of 8.0 seconds** at Amazon API Gateway and ALB. No client request is ever permitted to hang indefinitely; if a request does not complete within 8 seconds, it is terminated with an HTTP 504 Gateway Timeout.
2. Circuit Breakers: Calls to external third-party systems (like Stripe or the legacy Policy Core) are wrapped with Resilience4j circuit breakers with a 2-second execution timeout.
3. Asynchronous Shedding: Operations that inherently take longer than 5 seconds - such as high-resolution photo OCR via AWS Textract or heavy monthly reporting - are strictly prohibited from synchronous API request paths. They are offloaded to background Kafka workers so they never touch client-facing request latencies."

**Technical Bullets:**
- Hard Upper Bound: ALB / API Gateway 8-second client connection timeout.
- Circuit Breaker: Resilience4j caps external dependency waits at 2,000ms.
- Isolation: Heavy tasks (OCR, batch reports) offloaded to asynchronous background jobs.

---

### Q5: Where exactly do you measure latency?

**Spoken Answer:**
"We measure latency at three distinct observation boundaries to gain complete visibility into where time is spent:

1. Boundary 1: Edge / User Experience Latency (At CloudFront and API Gateway):
This measures the **Total Round-Trip Time (RTT)** from the moment the user's mobile app emits the first byte over cellular networks until the last byte of the response returns. This includes DNS lookup, TLS handshake, internet transit, and backend processing.
2. Boundary 2: Application Ingress Latency (At Spring Boot Controller / Filter):
Measured by Micrometer / Spring Actuator. This isolates our backend server processing time from public internet network latency.
3. Boundary 3: Downstream Dependency Latency (At Repositories and HTTP Clients):
Measured via OpenTelemetry distributed tracing spans, capturing individual database query execution times, Redis cache round-trips, and external Stripe API durations."

**Technical Bullets:**
- Tier 1: CloudFront / API Gateway `IntegrationLatency` and `Latency` metrics.
- Tier 2: Spring Boot `@Timed` filter metrics emitted to CloudWatch / Prometheus.
- Tier 3: OpenTelemetry distributed trace spans in AWS X-Ray / Datadog.

---

### Q6: Does database latency count?

**Spoken Answer:**
"**Yes, absolutely.** Database latency is an integral component of the synchronous client request path.

When a customer submits a claim via `POST /api/v1/claims`, the Spring Boot backend must begin a database transaction, execute the insert into `claims.claims`, write to `claims.outbox`, and commit the transaction before returning the HTTP 201 response. 

If the database takes 400 milliseconds to acquire a connection and execute the write, that 400ms is directly added to the user's perceived latency. 
That is precisely why we keep our database writes hyper-optimized: using HikariCP connection pooling, indexed foreign keys, and small, lean outbox payloads so our database transaction time remains **under 30 milliseconds**."

**Technical Bullets:**
- Contribution: DB execution time + connection acquisition wait time directly contribute to API response time.
- Optimization Target: DB latency p99 < 30ms for OLTP writes; < 10ms for indexed reads.
- Metric: HikariCP `ConnectionAcquireTime` and Aurora `CommitLatency`.

---

### Q7: Does a third-party API call count?

**Spoken Answer:**
"If a third-party API call is executed synchronously inside the HTTP request-response thread, **yes, it counts completely toward your latency SLA**. And that is the biggest trap in software architecture!

If your controller calls the external SMS provider or fraud API synchronously, and that provider takes 3.5 seconds to reply, your API's latency is at least 3.5 seconds. If that provider experiences an outage, your p99 breaches the SLA immediately.

**That is why in our architecture, we strictly BAN third-party API calls from synchronous customer request paths!**
- SMS and Email: Sent asynchronously by background Kafka consumers.
- Document OCR: Executed asynchronously by S3 event-driven workers.
- The ONLY synchronous external API is the Stripe payment gateway during deductible checkout, which is guarded by a Resilience4j circuit breaker with a hard 2.0-second timeout."

**Technical Bullets:**
- Rule: Zero third-party blocking calls on primary write endpoints.
- Decoupling: Use Transactional Outbox + Kafka for 99% of downstream external communications.
- Exception: Payment Gateway protected by 2-second timeout and fallback handling.

---

### Q8: How would you load-test the system?

**Spoken Answer:**
"We execute load testing using a modern, distributed open-source load-testing framework: **Grafana k6 (or Apache JMeter) orchestrated via distributed AWS ECS tasks**.

Our load testing methodology follows four progressive phases:
1. Baseline Smoke Testing: 100 virtual users (VUs) to verify end-to-end functionality, token generation, and database integrity.
2. Stress Testing: Ramping traffic steadily up to our target peak capacity (250 write TPS and 2,500 read QPS) over a 2-hour window to verify that p99 remains below 5,000ms.
3. Spike / Surge Testing: Simulating a severe weather catastrophe. We inject an instant 5x spike in traffic within 60 seconds (jumping from 20 TPS to 250+ TPS) to validate whether ECS Fargate step-scaling policies and Aurora connection pools react without dropped packets.
4. Soak / Endurance Testing: Running 1.5x average load continuously for 24 hours to detect memory leaks, JVM heap bloat, database dead tuple accumulation, and connection pool leaks."

**Technical Bullets:**
- Tooling: Grafana k6 running in containerized distributed execution mode.
- Test Scenarios: Smoke test, Stress test, Surge/Spike test, 24-hour Soak test.
- Automation: Performance test regression suite runs automatically in staging before production releases.

---

### Q9: How many concurrent users would you test?

**Spoken Answer:**
"We load test for a target concurrency of **15,000 to 20,000 concurrent active virtual users (VUs)**.

In load testing terminology, 'concurrent virtual users' does not mean passive idle browsers; it means actively executing user sessions generating requests with realistic think-time (5 to 10 seconds between clicks).

Testing 20,000 active concurrent VUs with realistic think times generates:
- Approximately **2,000 to 2,500 Read QPS** against our edge, cache, and database replicas.
- Approximately **200 to 250 Write TPS** against our core Claims, Workshop, and Payment services.
This load matches our worst-case catastrophe surge scenario across 200 million policyholders."

**Technical Bullets:**
- Target VUs: 20,000 concurrent active virtual users.
- Throughput Generated: ~250 write TPS and ~2,500 read QPS.
- Think Time: Modeled using truncated normal distribution (mean: 6s, std-dev: 2s) to mimic human interaction.

---

### Q10: How did you derive that number?

**Spoken Answer:**
"We derived our concurrency numbers directly from first-principles insurance actuarial mathematics and Little's Law:

1. Total Population: 200 million policyholders.
2. Annual Incident Rate: Auto insurance industry claims frequency averages 6% per year, yielding **12 million claims annually**.
3. Baseline Daily Volume: Across 250 business days, that is an average of **48,000 claims/day**.
4. Catastrophe Surge Multiplier: During a severe multi-state blizzard or hurricane, claims intake spikes 4x to 5x normal volume in the affected geographic zone, reaching **200,000 claims in a single day**.
5. Peak Window: Most claims are filed during a concentrated 8-hour daytime window (28,800 seconds). 
200,000 claims / 28,800 seconds = **~7 FNOL writes per second baseline**, with peak burst clusters reaching **50 to 100 new claim submissions per second**.
6. Full Lifecycle Factor: Each claim generates ~5 downstream lifecycle writes (surveyor notes, repair work orders, payments), yielding **~250 total write TPS**.
7. Read-to-Write Ratio: A standard 10:1 ratio yields **~2,500 read QPS**.
8. Little's Law (\(L = \lambda \times W\)): At an arrival rate of \(\lambda = 2,750\) requests/second and an average session interaction time \(W = 7\) seconds, the number of active concurrent users \(L\) is \(2,750 \times 7 \approx 19,250\) concurrent users. We round this to **20,000 concurrent users**."

**Technical Bullets:**
- Actuarial Base: 200M users * 6% frequency = 12M claims/yr.
- Surge Peak: 200,000 claims/day / 28,800 sec = ~7 FNOL writes/sec, clustering to 50-100/sec.
- Total Traffic: 250 write TPS, 2,500 read QPS.
- Concurrency: Little's Law calculation yields ~20,000 concurrent VUs.

---

### Q11: What happens when traffic suddenly becomes 3x normal?

**Spoken Answer:**
"When traffic abruptly surges to 3x normal within minutes, our architecture handles the surge through a coordinated four-tier response without dropping requests:

1. Edge Absorption: CloudFront and AWS WAF absorb static asset requests and block any illegitimate scraping or bot traffic.
2. In-Flight Backpressure via Kafka: Client writes write to PostgreSQL and outbox in 150ms. Downstream processing (SMS alerts, PDF generation) is decoupled by Kafka, which easily buffers thousands of messages per second without dropping a single byte.
3. ECS Fargate Target Tracking Auto-Scaling: Container tasks scale out horizontally based on CPU (>70%) and ALB request count per target. A cluster running 8 tasks expands to 24 tasks within 2 to 3 minutes.
4. Database Stability via PgBouncer / Connection Pool Capping: Application containers cap their database connections at 20. The database writer node does not experience a connection storm; queries wait in the lightweight connection pool queue for a few milliseconds, keeping database CPU stable below 80%."

**Technical Bullets:**
- Ingress: ALB distributes load across expanding container fleet.
- Compute: ECS Fargate step-scaling adds tasks in increments of +50% under rapid CPU growth.
- Database: HikariCP connection limits prevent database collapse; Aurora auto-scales read replicas.
- Message Queue: Kafka acts as an elastic shock absorber.

---

### Q12: What are your autoscaling metrics?

**Spoken Answer:**
"We avoid relying on a single simplistic metric. We employ a **composite multi-metric auto-scaling strategy** tailored to each service's operational profile:

1. CPU Utilization (Target: 70%): Core indicator of computational and garbage collection load for our Spring Boot microservices.
2. ALB Request Count Per Target: Sized at 1,000 requests per container task per minute. This scales the application tier based directly on incoming HTTP traffic volume *before* CPU has time to spike.
3. Kafka Consumer Lag (For Asynchronous Workers): Worker tasks (like Notification Service and Event Ingestion) scale based on the metric `ConsumerLag`. If lag exceeds 2,000 messages or `EstimatedTimeLag` exceeds 30 seconds, worker containers scale out horizontally up to 32 tasks (matching partition count).
4. Aurora Read Replica Auto-Scaling: Read replicas scale out based on `CPUUtilization > 65%` or `DatabaseConnections > 800`."

**Technical Bullets:**
- App Tier: `TargetTrackingScaling` on `ECSServiceAverageCPUUtilization: 70%` and `ALBRequestCountPerTarget`.
- Consumer Tier: CloudWatch custom metric scaling on `AWS/Kafka ConsumerLag`.
- Database Tier: Aurora Auto Scaling on `RDSReaderAverageCPUUtilization: 65%`.

---

### Q13: Why CPU >70%?

**Spoken Answer:**
"We set our auto-scaling target threshold at 70% CPU because it provides the **optimal balance between cloud cost efficiency and safety headroom**.

Why not 50%? Sizing at 50% CPU results in severe over-provisioning and doubles our monthly AWS Fargate bill during normal operations.
Why not 85% or 90%? In Java applications running on the JVM, once CPU utilization crosses 80%, garbage collection pauses become longer and more frequent. Furthermore, launching a new ECS Fargate container, running the JVM warmup, and passing the ALB health check takes approximately 60 to 90 seconds. 

If you wait until 85% CPU to trigger scaling, a sudden incoming traffic spike will push existing containers to 100% CPU before the new tasks can become healthy, resulting in request queueing, connection timeouts, and SLA breaches. Sizing at 70% leaves a 30% buffer to absorb traffic while new containers initialize."

**Technical Bullets:**
- Safety Margin: 30% headroom accommodates traffic during the 60-90s container startup and health check window.
- JVM Dynamics: Prevents GC thrashing and thread starvation that occurs above 80% CPU.
- Cost Optimization: Maximizes container utilization without risking availability.

---

### Q14: Would CPU alone be sufficient?

**Spoken Answer:**
"**No, CPU alone is completely insufficient for modern microservices and event-driven architectures.**

Relying exclusively on CPU creates two critical failure modes:
1. The I/O-Bound Lockup: A service might be waiting on slow external HTTP calls or locked database connections. The CPU remains low (at 20%), but all application threads are blocked and incoming requests are timing out. A CPU-only metric would see 20% and never scale out the cluster! That is why we include `ALBRequestCountPerTarget` and HTTP 5xx error rates.
2. The Asynchronous Worker Blind Spot: Kafka consumer workers poll messages in batches. When a massive backlog of 500,000 messages builds up in Kafka, the consumer container might process them steadily at 60% CPU without spiking. A CPU-only autoscaler would leave only 2 worker pods running, causing customer SMS alerts to be delayed by hours! That is why asynchronous workers must scale based on **Kafka Consumer Lag**."

**Technical Bullets:**
- Blind Spot 1: I/O blocking where threads saturate while CPU stays idle.
- Blind Spot 2: Asynchronous batch processing where consumer lag grows without CPU spikes.
- Multi-Metric Solution: CPU + Memory + ALB Request Count + Kafka Consumer Lag.

---

### Q15: Could Kafka consumer lag drive autoscaling?

**Spoken Answer:**
"**Yes, and for our asynchronous event worker fleet, it is our PRIMARY auto-scaling metric.**

Here is how we implement it:
1. Amazon MSK emits consumer group metrics to CloudWatch, including `SumOffsetLag` and `MaxOffsetLag`.
2. We create an AWS CloudWatch Alarm that triggers when `MaxOffsetLag > 2000` for 2 consecutive evaluation periods (2 minutes).
3. The alarm invokes an Application Auto Scaling policy on the Notification or Reporting ECS Service, scaling task count out in steps: e.g., +4 tasks if lag > 2,000; +8 tasks if lag > 10,000.
4. Scale Ceiling: We configure a strict upper limit of **32 tasks**, because our core topics have 32 partitions. Sizing beyond 32 tasks would result in idle containers sitting with zero assigned partitions, wasting money."

**Technical Bullets:**
- Metric: `AWS/Kafka MaxOffsetLag`.
- Hard Ceiling: Capped at partition count (\(N = 32\)).
- Cooldown: 5-minute scale-in cooldown to prevent container thrashing.

---

### Q16: How do you prevent an autoscaling storm?

**Spoken Answer:**
"An autoscaling storm occurs when a momentary traffic spike triggers rapid scale-out, followed immediately by scale-in, causing containers to bounce continuously; or worse, when hundreds of new containers launch simultaneously and overwhelm the database connection pool.

We prevent autoscaling storms using four architectural guardrails:
1. Scale-In and Scale-Out Cooldown Timers: We enforce an aggressive **scale-out cooldown of 60 seconds** (add capacity quickly), but a conservative **scale-in cooldown of 300 seconds (5 minutes)**. This ensures capacity remains available to absorb follow-up traffic waves before terminating instances.
2. Step-Scaling with Percentage Caps: We scale in steps (+25% or +50% of current fleet) rather than allowing the cluster to double or quadruple in a single evaluation.
3. Database Connection Pool Quotas: Because each container is hard-capped at 20 HikariCP connections, scaling from 10 tasks to 30 tasks increases DB connections from 200 to 600, which is well within our Aurora instance limit (max_connections = 2,000).
4. Circuit Breakers: If downstream database latency spikes, we prevent autoscaling from launching more containers that would simply add fuel to the fire."

**Technical Bullets:**
- Cooldowns: Scale-out cooldown = 60s; Scale-in cooldown = 300s (hysteresis).
- Step Scaling: Granular percentage step adjustments prevent over-provisioning.
- Connection Bounds: Max container ceiling enforces database connection safety.

---

### Q17: What data should Redis cache?

**Spoken Answer:**
"Redis should cache data that exhibits **high read frequency, low write frequency, high computation cost, and tolerance for eventual consistency**:

1. Reference / Master Data:
   - Certified Repair Workshop listings and geo-coordinates by zip code.
   - Vehicle make/model/year lookup directories.
2. Read-Heavy Policy Snapshots:
   - Policyholder active coverage details and deductible tiers (cached for 15 minutes upon login to spare the legacy Policy core).
3. Pre-Calculated Operational Metrics:
   - Regional triage queue totals, supervisor summary cards, and adjuster workload counts (5-minute TTL).
4. Short-Lived Security & Idempotency Tokens:
   - Client idempotency submission keys (24-hour TTL).
   - Real-time user revocation timestamps for JWT blacklisting."

**Technical Bullets:**
- Characteristics: High read-to-write ratio (>10:1), bounded dataset size, non-volatile.
- Key Formats: Structured key naming with explicit namespaces (e.g., `eclaims:workshops:zip:75001`).
- Eviction Policy: `volatile-lru` (evicts keys with an expire set using least recently used algorithm).

---

### Q18: What data should never be cached?

**Spoken Answer:**
"We strictly forbid caching four categories of data in Redis:

1. Primary Financial Transaction State:
   - The authoritative payment ledger, deductible receipt balances, and bank disbursement records. These must reside strictly in ACID-compliant Aurora PostgreSQL or durable MemoryDB.
2. Unencrypted Sensitive PII:
   - Plaintext Social Security numbers, driver license numbers, and banking details. Redis is an in-memory datastore; placing unencrypted PII in cache creates unnecessary compliance exposure.
3. Rapidly Mutating Highly-Contended Entity State:
   - In-flight claim records that are actively undergoing concurrent adjustments by multiple staff members. Caching dynamic aggregates leads to cache synchronization bugs and dirty reads.
4. Large Binary Files:
   - Accident photos, PDF estimates, and police reports. Redis is designed for small key-value strings and JSON objects; storing 5MB photos in Redis causes memory fragmentation and severe network interface saturation."

**Technical Bullets:**
- Prohibited: Financial ledgers, unencrypted PII, high-velocity mutable entities, and binary blobs.
- Anti-Pattern: Using Redis as a document store or file store; use Amazon S3 instead.

---

### Q19: How do you prevent cache stampede?

**Spoken Answer:**
"A cache stampede (or thundering herd) occurs when a popular cached key expires, and hundreds or thousands of concurrent requests simultaneously get a cache miss and all bombard the database with the exact same expensive query.

We prevent cache stampedes using two techniques:

1. Distributed Mutex Locking (Redlock / Atomic Key Lock):
When an application thread encounters a cache miss, it does NOT immediately query the database. Instead, it attempts to acquire an atomic distributed lock in Redis:
`SET lock:workshop:75001 "1" NX EX 10`.
- The single thread that acquires the lock queries the database, repopulates the Redis cache, and releases the lock.
- All other 999 threads fail to acquire the lock, sleep for 50 milliseconds, and then read the freshly populated cache! The database receives exactly ONE query instead of 1,000.

2. Probabilistic Early Expiration (XFetch Algorithm) & Jitter:
We add random jitter to TTLs (`TTL = base_ttl + random_between(0, 60s)`) so that multiple related keys never expire at the exact same millisecond."

**Technical Bullets:**
- Defense 1: Distributed Mutex via `SETNX` lock pattern.
- Defense 2: Probabilistic early recomputation or background cache refresh before TTL expires.
- Defense 3: Jittered TTL distribution eliminates synchronized expiration waterfalls.

---

### Q20: What happens when Redis goes down?

**Spoken Answer:**
"In our architecture, **Redis is treated as an operational cache, NOT a single point of failure.** If the entire Redis cluster goes down:

1. High Availability Resilience First: Our Amazon ElastiCache Redis deployment is configured as a **Multi-AZ cluster with Automatic Failover**. If the primary node crashes, AWS promotes a read replica to master in under 30 seconds.
2. Graceful Application Degradation (Cache-Aside Fallback):
In our Spring Boot code, all Redis operations are wrapped in `try/catch` blocks or use Resilience4j circuit breakers:
- If a `RedisConnectionException` occurs, the application logs a warning, falls back gracefully, and queries the database read replica directly.
- The user experience does NOT crash; response times increase slightly from 5ms to 35ms, but all business operations remain 100% functional.
3. Circuit Breaker Protection: To prevent the database from being crushed by the sudden flood of cache misses, the circuit breaker enables local in-memory Guava / Caffeine caches as a temporary buffer until Redis connectivity is restored."

**Technical Bullets:**
- High Availability: Multi-AZ with automated replica promotion (<30s).
- Fallback: Non-blocking fallback to Aurora Read Replicas on cache errors.
- Circuit Breaker: Resilience4j opens on Redis failure to shed load and use local JVM fallback caches.
