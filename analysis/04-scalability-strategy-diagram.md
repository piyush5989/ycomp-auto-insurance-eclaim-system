# Meeting Presentation Script: Scalability & Performance Strategy (04-scalability-strategy-diagram)

- **Target Diagram**: `ycomp-auto-insurance-eclaim-system/design-documents/scalibility-strategy.svg`
- **Target Audience**: Infrastructure Architects, Performance Engineers, Database Administrators, Engineering Leadership
- **Presenter Role**: Lead Enterprise Solutions Architect
- **Presentation Objective**: Explain the multi-dimensional scalability strategy that allows eClaims to maintain sub-second response times and meet the 99% < 5,000ms SLA across 200 million policyholders, even during localized catastrophe surges.

---

## 1. Opening Narrative: The Story Behind the Diagram

"Good morning, colleagues.

Let us start this session with a sobering question that every enterprise architect must answer: *'What happens when the weather goes bad?'*

On an average Tuesday in June, YCompany processes around 35,000 claims spread peacefully across all fifty states. Our servers run at 15% CPU utilization. Database query execution times hover around thirty milliseconds. Everything looks green and calm on our operational dashboards.

Then, winter arrives in the Northeast. A severe polar vortex and ice storm sweeps from Ohio to Maine. Within six hours, hundreds of thousands of vehicles are stranded in ditches with frozen transmissions, shattered tail lights, and bumper collisions. 

Over one hundred thousand stressed policyholders open their mobile app at the exact same moment.

In the legacy YCompany setup, this kind of surge was fatal:
- Thread pools locked up.
- The single relational database hit 100% CPU due to table scans on unpartitioned tables.
- Media uploads of accident photos filled up web server disk space.
- The portal crashed, leaving stranded drivers with blank screens and generating a public relations crisis.

Our assignment requirements are unambiguous on this point:
- **System must have flexible design and be able to handle increased load in the future.**
- **It must support both on-premise and cloud-based deployment with auto-scaling.**
- **The offered solution must complete 99% of provided services in less than 5,000 milliseconds, over both peak and non-peak hours.**

This diagram - our **Scalability and Performance Strategy** - is our coordinated blueprint to guarantee that our platform never bends, degrades, or crashes under storm surges. 

Notice that scalability is not treated as a single magic setting. It is engineered as four coordinated pillars that merge into a single target: Horizontal Scaling, Caching Strategy, Asynchronous Processing, and Database Optimization."

---

## 2. Step-by-Step Diagram Walkthrough & Conceptual Terms

```
+-------------------------------------------------------------------------------------------------------+
|                                    SCALABILITY & PERFORMANCE BLUEPRINT                                |
+-------------------------------------------------------------------------------------------------------+

 [1. HORIZONTAL SCALING]
  - ECS Fargate Auto-Scaling: Target Tracking policy (Scale out when CPU > 70%)
  - Kafka Partition Scaling: Scale consumer instances dynamically across 32 topic partitions
  - RDS Read Replicas: Route all heavy reporting & analytical queries away from the Primary writer
                                                   |
 [2. CACHING STRATEGY]                             |
  - Redis - Policy & User Data (TTL: 15 min): Instant lookup of active customer coverage
  - Redis - Report Dashboards (TTL: 5 min): Pre-calculated regional KPIs & triage queue totals
  - CloudFront CDN: Edge caching of static frontend UI assets and regional directory lookups
                                                   |
 [3. ASYNC PROCESSING]                             +---> [MERGED STRATEGY]
  - Pre-Signed S3 URLs: Direct-to-S3 photo uploads bypass API Gateway & container memory                   |
  - Kafka Status Propagation: Non-blocking write events decouple backend workflows                        |
  - Scheduled Batch Jobs: Spring Batch offloads heavy report generation to background                     |
                                                   |                                                       v
 [4. DATABASE OPTIMISATION]                        |                                            +---------------------+
  - Table Partitioning: Claims partitioned by year + region                                     | PERFORMANCE TARGET: |
  - Composite Indexes: Targeted B-trees on (claim_id, status, region)                           | 99% of requests     |
  - HikariCP Connection Pooling: Capped at 20 connections per service pod                       | < 5000ms SLA        |
                                                                                                +---------------------+
```

---

### Pillar 1: Horizontal Scaling

"Let us examine the top-left box: **Horizontal Scaling**.
- **ECS Fargate Auto-Scaling (CPU > 70%)**: We do not size our server fleet for the worst-case storm 365 days a year - that would waste millions of dollars in cloud spend. Instead, our containers run on serverless AWS ECS Fargate. When CPU utilization exceeds 70%, or when incoming request counts surge, ECS automatically scales our service tasks from a baseline of 4 tasks up to 60 tasks in minutes.
- **Kafka Partition Scaling**: In event-driven systems, the consumer group is the unit of scale. We provision 32 partitions per core topic. Under baseline load, 4 worker pods process multiple partitions. When ingestion spikes, our consumer cluster scales out to 32 pods, assigning exactly one partition per container for maximum parallel throughput.
- **RDS Read Replicas**: In our capacity analysis, we established that reads outnumber writes by approximately 4 to 1. By deploying 3 dedicated Aurora PostgreSQL read replicas, we isolate read traffic. When hundreds of adjusters refresh their triage queues, those queries run against read replicas, leaving the primary database node 100% free to process financial write transactions."

---

### Pillar 2: Caching Strategy

"Now look at the top-right box: **Caching Strategy**.
- **Redis for Policy & User Reference Data (TTL: 15 min)**: When John files a claim, we must verify that his policy is active. Instead of making an expensive network call to the legacy Policy Administration System (PMS) every time John refreshes his screen, we cache the validated policy snapshot in Amazon ElastiCache Redis for 15 minutes.
- **Redis for Report Dashboards (TTL: 5 min)**: Regional managers love refreshing their dashboards on Monday mornings to view weekly claim volumes and turnaround times. Running those aggregate queries in real time would lock database rows. We pre-calculate and cache those summary metrics in Redis with a 5-minute TTL.
- **CloudFront CDN Edge Caching**: Our React web application bundles, mobile assets, CSS, and zip code directory lookups for partner repair shops are cached at AWS edge locations nationwide. When a customer in Boston searches for nearby body shops, that request is served directly from a Boston edge server in under 20 milliseconds."

---

### Pillar 3: Asynchronous Processing

"Look at the bottom-left box: **Async Processing**.
- **Pre-Signed S3 URLs (Removes the API Choke Point)**: In an auto claim, every customer uploads five to ten high-resolution accident photos (totaling 15MB to 30MB per claim). At 40,000 claims a day, streaming that binary data through our microservice containers would consume hundreds of gigabytes of RAM and trigger JVM garbage collection freezes. By generating pre-signed S3 URLs, the client uploads photos directly to S3. Our containers only process lightweight 2KB JSON metadata requests.
- **Kafka Claim Status Propagation**: When an adjuster approves a claim, that status change is written to PostgreSQL, an event is published to Kafka, and the HTTP call returns in 180ms. The heavy downstream work (generating PDF approval letters, sending emails, updating workshop queues) happens asynchronously.
- **Spring Batch for Report Generation**: Heavy monthly management reports and insurance commission audit exports run as scheduled Spring Batch jobs during off-peak hours against read replicas, completely isolated from customer traffic."

---

### Pillar 4: Database Optimization

"Finally, look at the bottom-right box: **Database Optimization**.
- **Table Partitioning by Year + Region**: At 12 million claims a year, a single unpartitioned table would reach 60 million rows after five years. A standard B-tree index would exceed available RAM, causing Postgres to constantly swap index blocks to disk. We partition the `claims` table hierarchically: first by year (`claims_2026`), and then by geographic region (`claims_2026_us_east`). When an adjuster queries active claims in New York, PostgreSQL uses **partition pruning** to scan only the small sub-table. The remaining 90% of the database is never touched.
- **Composite Indexes**: We maintain specialized multi-column indexes on `(claim_id, status, region)` to turn costly sequential table scans into lightning-fast index seeks.
- **HikariCP Connection Pooling**: Java 21 Virtual Threads allow a single server to handle 50,000 concurrent web requests. But if that server tries to open 50,000 connections to PostgreSQL, the database crashes. We enforce a strict connection pool ceiling of **20 connections per service pod** using HikariCP, multiplexed via PgBouncer."

---

## 3. Clear Conceptual Explanations of Key Diagram Terms

| Term in Diagram | Plain-Language Meaning | Technical / Business Significance |
|---|---|---|
| **Partition Pruning** | The query engine's ability to skip entire database partitions that do not match the query filter. | Keeps database queries under 10ms even when the overall database holds tens of millions of historical claims. |
| **Pre-Signed S3 URL** | A temporary, cryptographically signed web address granting client permission to upload a specific file directly to cloud storage. | Bypasses the application server entirely, eliminating memory and network bandwidth bottlenecks during multi-megabyte photo uploads. |
| **Read Replica Lag** | The small delay (usually under 100 milliseconds) for updates to replicate from the Primary database node to reader nodes. | Requires architects to ensure critical write-then-read operations hit the primary node to prevent reading stale state. |
| **Target Tracking Auto-Scaling** | An elastic scaling policy that adds or removes server containers dynamically to maintain a specific metric (e.g., 70% CPU). | Balances cloud cost with performance; scales up instantly during catastrophe surges and scales down during calm nights. |
| **Composite Index** | An index created on two or more columns of a database table simultaneously. | Optimizes multi-attribute filtering (e.g., searching for all 'APPROVED' claims in 'REGION_EAST' created this week). |

---

## 4. Expected Technical Questions & Answers (Meeting Panel)

### Q1: With read replicas in place, what happens if a customer submits a claim and immediately refreshes their browser before the read replica has replicated the new record?
**Answer**:
"This is the classic 'read-your-own-writes' eventual consistency challenge. We solve this through two mechanisms:
1. **Frontend Optimistic UI Update**: When the customer submits the claim, the API returns the created claim aggregate in the HTTP 201 response body. The React client immediately writes this payload into its local state cache (TanStack Query / Redux), rendering the new claim instantly without needing a re-query.
2. **Session Sticky Routing**: For critical subsequent queries within the first 5 seconds of a write, the API Gateway inspects a `X-Write-Timestamp` header. If the write occurred within the replication window, the query is routed to the Aurora Primary node rather than a read replica, ensuring strict read-your-own-writes consistency."

### Q2: Why did we choose table partitioning by year and region rather than by customer ID?
**Answer**:
"In an auto insurance claims system, query access patterns are driven by **operational lifecycle and geography**, not customer ID:
- Adjusters and surveyors work within assigned geographic territories (e.g., Chicago North office covers Illinois zip codes).
- Reporting and regulatory filings are structured by region and calendar year.
- Policyholders file a claim once every 3 to 5 years, so customer-centric partitioning would create millions of tiny, inefficient partitions.
Partitioning by Year and Region aligns perfectly with our query access patterns, enabling PostgreSQL partition pruning to eliminate over 90% of data blocks from disk scans on every single operational query."

### Q3: How do we prevent connection pool exhaustion when our Spring Boot services auto-scale from 4 tasks up to 60 tasks during a catastrophe?
**Answer**:
"If 60 container tasks each opened 50 database connections, that would total 3,000 connections, which would overwhelm Aurora PostgreSQL's memory. We solve this using a two-tier connection strategy:
1. We configure **HikariCP** inside each Spring Boot container with a lean pool of 15 to 20 connections per pod.
2. We deploy **PgBouncer** in transaction pooling mode between our application containers and Aurora.
3. PgBouncer multiplexes thousands of virtual client transactions across a fixed, highly optimized pool of 150 server connections on the Aurora Primary node. Connections are held only for the microsecond duration of an individual SQL query, allowing our container fleet to scale out without risking database connection rejection."

### Q4: How does our scalability strategy ensure compliance with the 99% < 5,000ms SLA during peak hours?
**Answer**:
"Our end-to-end latency budget is designed with massive headroom:
- DNS and Edge CDN routing: ~20ms
- API Gateway token validation and rate limit check: ~15ms
- Spring Boot processing with Virtual Threads: ~30ms
- Partitioned PostgreSQL single-row indexed write: ~25ms
- Transactional Outbox event insert: ~15ms
Total synchronous request time: **~105 milliseconds**.
Even under 5x catastrophe surges with network jitter and database queueing, our p99 response time peaks around **450 milliseconds**. This gives us more than a 10x safety margin below our 5,000 millisecond NFR ceiling."
