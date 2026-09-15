# Q&A Discussion Script: Category 10 - Architect-Level Hard Questions

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: "If I gave you half the budget, what would you remove?"

**Spoken Answer:**
"If you cut my budget from $2.0M to $1.0M, I would protect the core transactional engine and cut peripheral convenience layers:

1. Defer the Dedicated Native Mobile App (React Native):
Instead of building and maintaining separate iOS and Android native apps, I would launch the Customer Portal strictly as a high-quality, mobile-responsive **Progressive Web App (PWA)**. That immediately saves 6 months of mobile specialist engineering and app store release cycles (~$150K).
2. Consolidate from 8 Microservices to a 3-Service Modular Architecture:
I would merge Claims, Workflow, and Incident Management into a single 'Claims Core' deployable, merge Workshop and Notification into an 'Operations' deployable, and keep Payment separate strictly for PCI-DSS compliance. This cuts inter-service plumbing, Kafka connector overhead, and dev resources (~$250K).
3. Replace Camunda 8 SaaS with Embedded State Machine / Spring StateMachine:
For Day 1, execute claim status transitions via Spring StateMachine in code, deferring external BPMN engine licensing and complex worker infrastructure (~$100K).
4. Outsource Deductibles to Standard Stripe Hosted Checkout:
Eliminate custom deductible billing ledgers and UI; redirect policyholders to Stripe's hosted checkout page (~$80K).
5. Compress Phase 1 and Governance:
Use pre-built architecture patterns and reduce project management from full-time to part-time (~$120K).

What remains intact: Aurora PostgreSQL, S3 document storage, secure Cognito auth, and the core claim intake and approval workflow. The business can still process digital claims."

**Technical Bullets:**
- Cut 1: Native Mobile -> Responsive Web PWA (Saves ~$150K).
- Cut 2: 8 Services -> 3 Consolidated Services (Saves ~$250K).
- Cut 3: External BPMN Engine -> Embedded Spring StateMachine (Saves ~$100K).
- Cut 4: Custom Payment UI -> Off-the-shelf Stripe Hosted Checkout (Saves ~$80K).
- Retained Core: Relational ACID integrity, document upload to S3, secure customer auth.

---

### Q2: "Which three architectural decisions are hardest to reverse?"

**Spoken Answer:**
"The three architectural decisions that represent the highest **one-way door risks** in this platform are:

1. **The Choice of Primary Relational Storage (Aurora PostgreSQL) over NoSQL**:
Deciding on relational ACID persistence vs NoSQL (DynamoDB/Cassandra) is nearly impossible to reverse once millions of claims, relational policies, and financial ledgers are written. Reversing this requires rewriting every JPA entity, data model, indexing strategy, and reporting query across the entire enterprise.
2. **Domain Boundary Context Mapping (Service Data Ownership)**:
How we draw the boundary lines between Claims, Workforce, and Workshop. Once databases are partitioned into separate schemas with private tables and event contracts, merging them back together or redrawing lines causes massive refactoring across APIs, Kafka schemas, and team ownership.
3. **The Dual Identity Provider Architecture (Cognito for Customers, Keycloak for Staff)**:
Separating consumer identities from enterprise corporate staff at the gateway level is deeply embedded into token validation filters, JWT claim mappings, and session revocation models. Migrating 200 million policyholders from Cognito to another IAM or vice-versa requires coordinating credential resets and auth pipeline rewires."

**Technical Bullets:**
- Decision 1: Relational ACID Core (PostgreSQL) vs Distributed NoSQL.
- Decision 2: Domain Boundary Context Map (bounded context separation).
- Decision 3: Dual IdP Security Architecture (Cognito + Keycloak routing).
- Classification: Type 1 'One-Way Door' architectural decisions per Jeff Bezos's framework.

---

### Q3: "Which component is the biggest single point of failure?"

**Spoken Answer:**
"The biggest physical single point of failure in our primary region is the **Aurora PostgreSQL Primary Writer Node**.

While our application tier runs across dozens of stateless ECS Fargate containers, our edge is distributed across hundreds of CloudFront PoPs, and our MSK Kafka cluster spans 6 distributed brokers:
- There is **only ONE physical compute instance acting as the primary Aurora database writer** at any given millisecond.
- If that primary writer host experiences hardware failure or memory corruption, all write transactions (claim submissions, adjuster approvals, payment commits) stall until Aurora's multi-AZ automated failover detects the failure and promotes a read replica to master.

We mitigate this SPOF by:
1. Multi-AZ Aurora Storage Quorum: Underneath compute, storage is striped 6-way across 3 AZs; storage never fails with the compute node.
2. Automated Failover in <30 seconds: Aurora promotes a replica in under 30 seconds automatically.
3. Transactional Outbox & Kafka Buffering: Write bursts during failover queue up gracefully in client retries or Kafka."

**Technical Bullets:**
- Component: Aurora PostgreSQL Primary Writer Node.
- Nature: Asymmetric master in a master-replica relational architecture.
- Mitigation: Aurora multi-AZ storage striping + 30-second automated failover to read replica.

---

### Q4: "Which part of your architecture worries you most?"

**Spoken Answer:**
"The part of the architecture that keeps me up at night is **Third-Party Workshop System Integration and Partner Availability**.

Everything inside our AWS VPC is under our engineering control: our containers auto-scale, our database has replicas, our Kafka brokers have replication factors of 3. 

External repair body shops are completely outside our control:
- In-network body shops range from high-tech national chains (like Caliber Collision) with modern APIs to small independent body shops using legacy Windows desktop estimating software.
- If an external workshop management system goes down, or if their webhooks fail to acknowledge our status updates, repair work orders stall, vehicle release dates get delayed, and customers blame YCompany.

We defensively engineer around this anxiety by:
1. Providing our own self-service Web Partner Portal so mechanics can work directly in a browser without needing API integrations.
2. Building an **Anti-Corruption Layer (ACL)** and dead-letter queue buffering for all incoming third-party workshop estimate payloads.
3. Decoupling shop updates via asynchronous events so workshop latency never blocks our core claims pipeline."

**Technical Bullets:**
- Worry: Heterogeneous third-party partner integration reliability and data quality.
- Blast Radius: Can stall vehicle repair completion and customer delivery dates.
- Defenses: Anti-Corruption Layer, fallback React Partner Portal, asynchronous DLQ processing.

---

### Q5: "What did you deliberately NOT solve?"

**Spoken Answer:**
"Great architects know that what you choose NOT to build is just as important as what you build. We deliberately declared four areas out of scope for this initial platform release:

1. Core Policy Administration System (PMS) Modernization:
We did not attempt to rewrite YCompany's legacy underwriting, premium billing, and policy issuance core. We treat the PMS strictly as an external upstream source of truth accessed via read-only APIs and Redis caching.
2. Fully Automated Straight-Through Processing (STP) AI Adjudication:
We deliberately did NOT build an automated AI bot that approves and pays claims without human review. In auto insurance, regulatory liability and fraud risks require human adjusters to approve payouts over statutory thresholds. We provide AI scoring as an advisory tool for human adjusters, not an autonomous decider.
3. Active-Active Multi-Region Database Writes:
We deliberately did NOT implement multi-master active-active databases across Virginia and Oregon, avoiding distributed consensus latency and split-brain risks in favor of a clean, robust Active-Passive DR topology.
4. Car Rental Vehicle Fleet Inventory Management:
We provide an appointment and reservation booking integration stub with Enterprise/Hertz, rather than building an entire rental car fleet logistics management system."

**Technical Bullets:**
- Deliberate Non-Goal 1: Legacy Core PMS rewrite (bounded context protection).
- Deliberate Non-Goal 2: Fully autonomous straight-through AI claim settlement (human-in-the-loop regulatory guardrails).
- Deliberate Non-Goal 3: Multi-master active-active relational database writes.
- Deliberate Non-Goal 4: Third-party rental fleet ERP software.

---

### Q6: "Where have you traded consistency for availability?"

**Spoken Answer:**
"We explicitly traded consistency for availability in three distinct subsystems following the CAP Theorem (AP over CP):

1. **Customer Real-Time Status Dashboards**:
When an adjuster approves a claim in New York, the customer's mobile app in California receives the update via Kafka and WebSocket within 500 milliseconds. We accept a momentary window of eventual consistency (sub-second lag) so that the primary approval transaction returns in 180ms without waiting for real-time edge caches to synchronize.
2. **Executive Reporting and BI Dashboards**:
Executive KPI dashboards query Amazon Redshift and read-model materialized tables updated asynchronously via Kafka. If an executive refreshes their screen, the claim counts may lag the live production database by 30 to 60 seconds. We trade real-time consistency for 100% database availability on the transactional writer.
3. **Notification Multi-Channel Dispatch**:
SMS and email notifications are delivered asynchronously. If the Twilio cellular network is congested, the SMS may arrive 2 minutes after the database state commits. We never block the customer's HTTP submission thread waiting for telecom network acknowledgments."

**Technical Bullets:**
- Subsystem 1: UI Status synchronization via Kafka/WebSockets (eventual consistency window <500ms).
- Subsystem 2: OLAP / BI reporting dashboards (bounded staleness 30-60s).
- Subsystem 3: Multi-channel SMS/Email delivery (asynchronous decoupled dispatch).

---

### Q7: "Where is eventual consistency acceptable and where is it unacceptable?"

**Spoken Answer:**
"We draw a razor-sharp line across the platform regarding eventual consistency:

**Where Eventual Consistency is 100% ACCEPTABLE**:
- Downstream Notifications (SMS, Email, Mobile Push alerts).
- Search indexing into OpenSearch.
- Executive analytics, fraud aggregation metrics, and regional KPI reports.
- Workshop directory zip code caches (a 1-hour stale workshop listing causes zero harm).
- UI progress bar updates.

**Where Eventual Consistency is ABSOLUTELY UNACCEPTABLE (Strict ACID Consistency Enforced)**:
- Financial Payouts and Deductible Collections: When charging a customer deductible or disbursing $15,000 to a repair shop, the ledger transaction, balance deduction, and idempotency lock must be strictly ACID consistent. Double-charges or phantom disbursements are catastrophic.
- Claim Aggregate Lifecycle Transitions: A claim cannot be in `APPROVED` and `REJECTED` status at the same time. Row-level optimistic locking enforces strict serialization.
- Regulatory WORM Document Anchoring: Document audit records must be durably committed before evidence can be certified."

**Technical Bullets:**
- Acceptable: UI push, reporting OLAP, notifications, search indices, reference caching.
- Unacceptable: Financial ledger balances, claim state machine transitions, compliance audit trail.
- Architectural Mechanism: Local ACID transactions in Aurora + MemoryDB for finance; Kafka for async propagation everywhere else.

---

### Q8: "What architecture decision would you change if the customer base were 2M instead of 200M?"

**Spoken Answer:**
"If YCompany had 2 million policyholders instead of 200 million (a 99% reduction in volume), I would make three dramatic simplifications:

1. **Eliminate the Dual-IdP Architecture**:
I would eliminate AWS Cognito entirely and use **Keycloak (or a single Auth0/Okta tenant) for EVERYONE** - customers, staff, and workshops alike. At 2M policyholders, the active daily user base is small enough that a standard clustered Keycloak database easily manages consumer accounts alongside enterprise RBAC without performance bottlenecks.
2. **Consolidate to a Clean Modular Monolith on ECS Fargate**:
Instead of 8 independent microservices, I would build a clean Spring Boot modular monolith. At 2M policyholders, average claim volume is only ~400 claims per day (~0.05 writes/sec). Operating 8 microservices, 8 CI/CD pipelines, and Kafka brokers for 400 claims/day is severe over-engineering.
3. **Replace Apache Kafka with Amazon SQS and SNS**:
Eliminate Amazon MSK's 6-broker cluster. Simple SQS queues and SNS topics provide all the asynchronous decoupling needed at a fraction of the cost ($5/month vs $1,500/month for MSK)."

**Technical Bullets:**
- IdP: Single Keycloak or managed Auth0 instance for all users.
- Architecture: Spring Boot Modular Monolith on ECS Fargate.
- Messaging: Amazon SNS + SQS instead of Amazon MSK Kafka cluster.
- Cost Savings: Infrastructure bill drops from ~$12,000/month to ~$1,200/month.

---

### Q9: "What if there are only 5,000 claims/day instead of 50,000?"

**Spoken Answer:**
"At 5,000 claims per day, our write throughput averages only **0.17 writes per second (peaking at 2 to 3 TPS)**.

Under that volume:
1. Database Right-Sizing: We would scale down our Amazon Aurora PostgreSQL cluster from high-memory instances (`db.r6g.2xlarge`) to small, cost-effective instances (`db.r6g.large`) or Aurora Serverless v2, saving thousands of dollars monthly.
2. Eliminate Dedicated OLAP Redshift Cluster: At 5,000 claims/day, annual claim volume is under 1.8 million records. A single Aurora read replica with proper indexing can easily handle executive reporting queries; Amazon Redshift is not required until year 4 or 5.
3. Downsize Kafka Brokers: Scale Amazon MSK to a 3-broker `kafka.m5.large` cluster or switch to Amazon EventBridge + SQS to eliminate idle broker compute costs.
4. Scale Down ECS Container Fleet: Run baseline container tasks at 2 tasks per service across 2 AZs, letting auto-scaling handle rare weather spikes."

**Technical Bullets:**
- Throughput Reality: 5,000 claims/day = ~0.17 write TPS baseline, peak ~3 TPS.
- Database: Aurora Serverless v2 scales down to 0.5 ACUs during idle nights.
- Analytics: Run reporting directly against Aurora Read Replicas; defer Redshift.
- Cost Impact: Reduces monthly AWS infrastructure spend by ~65%.

---

### Q10: "What if YCompany says AWS is prohibited and everything must run on-prem?"

**Spoken Answer:**
"Because our target architecture strictly adheres to **Cloud-Neutral, Open-Source Component Foundations**, migrating from AWS to an on-premise private enterprise data center is an infrastructure orchestration task, requiring zero rewrites of our application code.

Here is our 1-to-1 open-source mapping for on-premise deployment:
- Compute: AWS ECS Fargate -> **Red Hat OpenShift / Kubernetes (EKS Anywhere or Rancher)** running our identical Docker container images.
- Relational Database: AWS Aurora PostgreSQL -> **PostgreSQL 16 High Availability Cluster managed by Crunchy Data / CloudNativePG Operator** on Ceph / SAN storage.
- Object Storage: Amazon S3 -> **MinIO Enterprise Object Store** with WORM Object Locking enabled on local disk arrays.
- Event Streaming: Amazon MSK -> **Apache Kafka (Strimzi Operator) on OpenShift** or Redpanda enterprise cluster.
- Identity: AWS Cognito -> **Keycloak Realm Expansion** or corporate PingFederate / Microsoft Active Directory.
- Caching: ElastiCache Redis -> **Clustered Redis Enterprise on Kubernetes**.
- WAF / Load Balancing: AWS WAF + ALB -> **F5 BIG-IP / NGINX Plus / Envoy Ingress Gateway**.

Our Spring Boot microservices use standard Spring Data JPA, Spring Kafka, and AWS S3 SDK (which natively connects to MinIO via endpoint overrides). Not a single line of business Java code changes."

**Technical Bullets:**
- Architecture: 100% containerized, open-standard compliant.
- On-Prem Mapping: OpenShift + CloudNativePG Postgres + MinIO + Strimzi Kafka + Keycloak.
- Code Impact: ZERO lines of Java domain logic changed; configuration properties update endpoints.

---

### Q11: "How cloud-neutral is this architecture really?"

**Spoken Answer:**
"Our architecture is **90% Cloud-Neutral at the application and data layer, with deliberate cloud-native coupling restricted to the infrastructure hosting layer**.

Let me break down our layers honestly:
- Application Layer (100% Cloud-Neutral): Spring Boot 3.2, Java 21, React, React Native, Camunda BPMN models, and Liquibase database scripts are completely standard open-source technologies. They run identically on AWS, Azure, Google Cloud, or bare metal.
- Messaging & Caching Layer (100% Cloud-Neutral): Kafka client protocols and Redis wire protocols are universal. Switching from MSK to Confluent or Azure Event Hubs requires changing only a bootstrap URL.
- Storage & Identity Layer (85% Cloud-Neutral): S3 API is the de-facto industry standard (supported by MinIO, Google Cloud Storage, and Azure Blob). 
- Where we deliberately embraced AWS Lock-in: Amazon Aurora's shared distributed storage engine and AWS Cognito. We accepted this managed lock-in because building equivalent distributed storage and consumer auth from scratch on bare VMs costs millions of dollars in maintenance."

**Technical Bullets:**
- Portability Score: ~90% portable across clouds.
- App Tier: OCI Docker containers run anywhere.
- Persistence Tier: Standard PostgreSQL SQL dialects; zero proprietary extensions.
- Intentional Lock-in: Aurora distributed storage + Cognito consumer directory.

---

### Q12: "Which AWS services create the strongest vendor lock-in?"

**Spoken Answer:**
"The services creating the strongest vendor lock-in in our architecture, ranked from highest to lowest:

1. **Amazon Cognito (Highest Lock-in)**:
Cognito stores user passwords as salted hashes that AWS will NOT export due to security policies. If we migrate away from Cognito, we can export user profiles and emails, but all 200 million policyholders would be forced to reset their passwords on their next login, creating a significant customer experience friction.
2. **Amazon Aurora Distributed Storage Architecture**:
Aurora uses a proprietary AWS storage engine separating compute and storage. While the SQL interface is standard PostgreSQL, its sub-20ms multi-AZ replication, 1-second cross-region replication, and storage auto-expansion do not exist out of the box in self-hosted Postgres without complex extensions.
3. **AWS WAF and API Gateway**:
Routing rules, rate-limiting policies, and Web ACL rule groups are defined in proprietary AWS formats that must be re-authored in Envoy, Kong, or Cloudflare if moving away.
4. **Lowest Lock-in**: ECS Fargate, MSK, and ElastiCache. These run standard Docker, Kafka, and Redis; migration is trivial."

**Technical Bullets:**
- Rank 1: Cognito (password hash export restrictions cause forced user password resets).
- Rank 2: Aurora Storage Layer (proprietary distributed storage architecture).
- Rank 3: API Gateway / WAF ACL definitions.
- Rank 4: ECS / MSK / Redis (Standard open-source wire protocols).

---

### Q13: "Why are you using both Kafka and Camunda? Aren't they solving overlapping orchestration problems?"

**Spoken Answer:**
"This is a common question, but **Kafka and Camunda solve fundamentally different, complementary problems in enterprise systems**:

**Apache Kafka is an Event Streaming Backbone (Choreography - The Nervous System)**:
- High-throughput, distributed, append-only commit log.
- Answers: *'What just happened in the past?'* (`ClaimCreated`, `PaymentSettled`).
- Producers publish events and have zero awareness of who is listening.
- Handles millions of transient, sub-second technical messages.
- Does NOT maintain visual process state, human task queues, or business escalation timers.

**Camunda 8 is a Business Process Orchestrator (Orchestration - The Brain)**:
- Long-running, human-in-the-loop stateful workflow engine.
- Answers: *'What business step should happen next, who is responsible, and what happens if they don't finish in 48 hours?'*
- Tracks claims that remain open for 20 days across multiple humans (customer, surveyor, adjuster).
- Provides visual BPMN heatmaps for business directors.

They work together beautifully: Kafka transports the events between microservices; Camunda consumes key lifecycle events to advance the multi-week human workflow."

**Technical Bullets:**
- Kafka = Choreography (Reactive event broadcast, high-throughput, technical decoupling).
- Camunda = Orchestration (Stateful business process, human tasks, visual BPMN, SLA timer escalations).
- Synergy: Microservices emit domain events to Kafka -> Workflow worker listens to Kafka -> Advances Camunda process token.

---

### Q14: "Why Kafka + EventBridge? Why not one?"

**Spoken Answer:**
"In our complete enterprise target design, **Kafka is our Internal High-Throughput Core Backbone**, while **Amazon EventBridge is our External SaaS & Cloud Integration Broker**.

Why not just Kafka?
Connecting dozens of third-party SaaS tools (such as Zendesk customer support, PagerDuty incident management, Salesforce CRM, or AWS GuardDuty security findings) directly into Kafka requires writing, operating, and maintaining dozens of custom Kafka Connect microservice bridges.

Why not just EventBridge?
As we covered earlier, EventBridge lacks high-throughput streaming, cannot guarantee strict FIFO per-claim ordering, has higher latency (50-200ms), and lacks deterministic offset rewind for historical event replays.

The Two-Tier Architecture:
- Kafka (Amazon MSK) handles all high-volume, ordered core transactional events (`claim-events`, `repair-events`, `audit-events`).
- EventBridge handles lightweight peripheral cloud events: AWS CloudWatch alarm routing, third-party SaaS webhooks, and automated DevOps security remediation triggers. 
Each technology is applied to its exact architectural sweet spot."

**Technical Bullets:**
- Kafka: Internal transactional spine (high throughput, strict ordering, low latency, replayability).
- EventBridge: External SaaS and AWS serverless routing (Zendesk, PagerDuty, AWS GuardDuty).
- Boundary: Clear separation between core domain events and infrastructure/SaaS integration events.

---

### Q15: "Why Aurora + DynamoDB + Redis + MemoryDB + Redshift + OpenSearch? Can you justify every datastore?"

**Spoken Answer:**
"Yes, I can justify every single datastore through **Polyglot Persistence**, where each engine is chosen strictly because using another engine for that specific workload degrades performance, compromises compliance, or inflates costs:

1. **Amazon Aurora PostgreSQL (Primary OLTP System of Record)**:
   - Justification: ACID relational transactions for claims, foreign key constraints between policies and repairs, and financial ledger integrity.
2. **ElastiCache Redis (In-Memory Operational Caching & Concurrency)**:
   - Justification: Sub-millisecond read caching for policy snapshots and zip codes; distributed mutex locking (`Redlock`) to prevent cache stampedes.
3. **Amazon MemoryDB for Redis (Financial Idempotency Ledger)**:
   - Justification: Multi-AZ transactional durability with Redis sub-millisecond speeds. Prevents double-charging customers during cloud failover without locking relational database rows.
4. **Amazon DynamoDB (High-Throughput Key-Value Lookups)**:
   - Justification: Ephemeral upload ticket handshakes and WebSocket connection session tables with native TTL auto-eviction.
5. **Amazon Redshift (OLAP Columnar Data Warehouse)**:
   - Justification: High-performance actuarial aggregations and monthly KPI reports scanning 50 million historical rows without polluting transactional Aurora buffer pools.
6. **Amazon OpenSearch (Full-Text Search & Log Analytics)**:
   - Justification: Fuzzy matching across unstructured claims text ('dented bumper near passenger door') and centralized ELK log troubleshooting.

Using PostgreSQL for full-text search across 60M rows degrades query latency; using PostgreSQL for OLAP locks transactional tables; and using Redis for financial durability risks lost writes. Each store performs its specialized duty."

**Technical Bullets:**
- Aurora: Relational ACID OLTP (System of record).
- Redis: Sub-ms caching & distributed locking (Read offload).
- MemoryDB: Multi-AZ durable in-memory transactions (Financial idempotency).
- DynamoDB: Key-value with native TTL (Upload tickets, WebSocket connections).
- Redshift: Columnar OLAP (Aggregations across millions of historical claims).
- OpenSearch: Inverted index full-text search & log analytics.
