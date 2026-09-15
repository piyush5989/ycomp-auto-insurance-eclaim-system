# Meeting Presentation Script: Solution Architecture (02-solution-architecture-diagram)

- **Target Diagram**: `ycomp-auto-insurance-eclaim-system/design-documents/solution-architecture.svg`
- **Target Audience**: Technical Steering Committee, Enterprise Architecture Review Board, Engineering Directors
- **Presenter Role**: Lead Enterprise Solutions Architect
- **Presentation Objective**: Explain the multi-tier target solution architecture, demonstrating how user requests traverse from edge security to microservices, data persistence, and external partner networks.

---

## 1. Opening Narrative: The Story Behind the Diagram

"Welcome, colleagues. 

In our previous session, we walked through the System Context Architecture and defined who our stakeholders are and where our system boundaries lie. Now, the natural question every senior engineer and technology leader in this room is asking is: *'How do we actually build and run this at scale?'*

Let us be completely transparent about the engineering challenge we face. 

We have to serve **200 million policyholders** who access our platform on mobile devices and browsers from across the country. If a blizzard blankets the Midwest, twenty thousand drivers might reach for their phones at the exact same hour to report frozen engines and fender benders. If our system makes them wait ten seconds for a screen to load, or if an image upload fails halfway through, they lose trust immediately.

At the exact same time, we have hundreds of claims adjusters and fraud investigators who need lightning-fast search capabilities across millions of claims. And we have partner repair workshops who need to update work orders, request parts approvals, and receive payments.

In the past, organizations tried to solve this with a single monolithic application connecting to a single massive database. But we know what happened: one slow report run by an executive locked the entire database, preventing customers from submitting claims. A change in the workshop module required redeploying the entire codebase, bringing down the customer portal.

This diagram - our **Solution Architecture** - shows how we break those bottlenecks. It represents our end-to-end multi-tier architecture, designed from the ground up for high concurrency, fault isolation, and sub-second response times.

Let us walk down through the architectural layers together, starting from the Internet and Edge at the top, down to our Microservices, Data tier, and Integrations."

---

## 2. Step-by-Step Diagram Walkthrough & Conceptual Terms

```
+-------------------------------------------------------------------------------------------------------+
|                                    SOLUTION ARCHITECTURE TIERS                                        |
+-------------------------------------------------------------------------------------------------------+

 [1. INTERNET & EDGE INFRASTRUCTURE]
  - DNS (Route 53): Latency-based Anycast routing & health failover
  - CDN (CloudFront): Global edge distribution of static web/mobile assets
  - Web Application Firewall (AWS WAF): DDoS mitigation & OWASP Top 10 rule enforcement
  - Application Load Balancer (ALB): HTTPS / TLS 1.3 termination, path routing, health checks
                                                   |
                                                   v
 [2. PRESENTATION LAYER]
  - Customer Portal (React Web SPA) & Mobile Apps (React Native)
  - Internal Portal (React Admin for Adjusters, Case Managers, Auditors)
  - Partner Portal (Workshop repair updates & Car Rental vehicle coordination)
                                                   |
                                                   v
 [3. API GATEWAY & IDENTITY TIER]
  - Amazon API Gateway: Rate limiting (10K RPS burst), path routing, request validation
  - Dual Identity Tier:
      * AWS Cognito: Cost-effective consumer identity for 200M+ policyholders with MFA
      * Keycloak Cluster: Fine-grained UMA 2.0 RBAC and AD federation for internal staff & partners
                                                   |
                                                   v
 [4. MICROSERVICES ARCHITECTURE (SPRING BOOT 3 + JAVA 21)]
  Core Business Services:
  - Claims Service (FNOL intake, lifecycle state machine, policy checks)
  - Workflow Service (Camunda BPM orchestration, auto-assignment, escalation timers)
  - Assessment Service (Surveyor damage evaluations, adjuster approvals)
  - Fraud Detection (Rules engine + ML hooks for suspicious claims)
  Supporting Services:
  - Document Service (Pre-signed S3 uploads, Textract OCR, archival)
  - Repair Tracking (Workshop work orders, car rental coordination)
  - Payment Service (Stripe Connect marketplace payouts, deductible capture)
  - Notification Service (Asynchronous multi-channel email/SMS/push delivery)
  - Reporting Service (Turnaround KPIs, fraud ageing matrix, regional dashboards)
                                                   |
                                                   v
 [5. DATA & MESSAGING LAYER]
  - Primary Database: Amazon Aurora PostgreSQL (Multi-AZ with read replicas)
  - Distributed Cache: Amazon ElastiCache Redis (Session cache & idempotency tokens)
  - Document Storage: Amazon S3 + Object Lock (WORM compliance, 7-year retention)
  - Event Streaming: Amazon MSK (Managed Apache Kafka backbone)
                                                   |
                                                   v
 [6. EXTERNAL INTEGRATIONS & OBSERVABILITY]
  - Integrations: Legacy Policy Core (REST), Stripe (PCI-DSS), Workshop & Rental APIs, SES/Twilio/FCM
  - Observability: CloudWatch, Prometheus, OpenTelemetry distributed tracing, PagerDuty 24x7
```

---

### Layer 1: Internet & Edge Infrastructure

"Look at the top of the diagram. Every incoming request from the public internet first hits our Edge Infrastructure:
- **DNS (Amazon Route 53)**: Provides latency-based global Anycast routing. It routes a user in New York to the nearest AWS edge location while continuously checking server health. If a data center degrades, DNS shifts traffic in 60 seconds.
- **CDN (Amazon CloudFront)**: Our Content Delivery Network. It caches static assets (React bundles, CSS, icons, partner workshop zip code directories) at hundreds of edge points of presence across the United States. This offloads up to 70% of static read traffic before it ever touches our backend servers.
- **Web Application Firewall (AWS WAF)**: Inspects every HTTP packet at the edge. It automatically blocks SQL injection, cross-site scripting (XSS), credential stuffing attacks, and volumetric DDoS attacks using AWS Shield Advanced.
- **Application Load Balancer (ALB)**: Terminates TLS 1.3 encryption, validates certificates, and distributes clean traffic across our container clusters in multiple Availability Zones."

---

### Layer 2: Presentation Layer (Portals)

"Directly beneath our ingress tier is the Presentation Layer. Notice how we have tailored the interfaces to the specific audience:
- **Customer Portal & Mobile App**: Built with React and React Native. Focused on simplicity, guided FNOL wizard flows, photo capture, and a visual progress bar.
- **Internal Portal**: A data-dense React administrative interface built for speed, allowing adjusters and surveyors to compare damage photos side-by-side with policy limits.
- **Partner Portal**: Dedicated views for auto repair body shops to manage repair orders and for car rental providers to coordinate replacement vehicle reservations."

---

### Layer 3: API Gateway & Dual Identity Strategy

"This is one of the most critical architectural innovations in our design:
- **Amazon API Gateway**: Acts as our front door. It enforces rate limiting (preventing any single IP from overwhelming the system), routes REST paths (`/api/v1/claims`, `/api/v1/workshops`), and terminates WebSocket connections for real-time customer push updates.
- **Dual Identity Providers (Cognito + Keycloak)**:
  - *Why two identity providers?* Because managing 200 million consumer accounts in an enterprise IAM tool like Keycloak or Okta would be prohibitively expensive in licensing and database sizing.
  - We use **AWS Cognito** for our 200M external policyholders - it provides pay-per-active-user economics, self-registration, and built-in SMS MFA.
  - We use a clustered **Keycloak 24** deployment for internal employees and partner workshops. Keycloak provides complex role hierarchies, User-Managed Access (UMA 2.0), Active Directory federation, and allows operations to reconfigure role permissions without deploying new code."

---

### Layer 4: Microservices Architecture (The Core Engine)

"Beneath the gateway sits our application runtime: **Spring Boot 3.2 on Java 21**. 

By utilizing Java 21 Virtual Threads (Project Loom), our microservices can handle tens of thousands of concurrent I/O-bound requests (waiting for databases or external webhooks) without exhausting operating system thread pools.

Notice how we have componentized our business into autonomous services:
1. **Claims Service**: Owns the core claim entity, policy pre-checks, and the primary lifecycle state machine.
2. **Workflow Service**: Integrates with **Camunda 8 (BPMN)** to orchestrate long-running asynchronous workflows, such as automated surveyor assignments based on territory and SLA escalation timers (alerting supervisors if a claim sits unassigned for over 48 hours).
3. **Assessment Service**: Manages line-item damage appraisals uploaded by field surveyors and routes approvals to claims adjusters.
4. **Fraud Detection Service**: Runs real-time rule checks during claim intake (duplicate VIN checks, date anomalies) and connects via ML hooks to Amazon SageMaker for anomaly scoring.
5. **Supporting Services**: 
   - `Document Service` (manages pre-signed S3 upload tokens and runs AWS Textract for OCR of police reports).
   - `Repair Tracking Service` (tracks body shop repair stages and rental car coordination).
   - `Payment Service` (manages customer deductible charges and workshop disbursements via Stripe Connect).
   - `Notification Service` (consumes Kafka events and fans out email, SMS, and push notifications).
   - `Reporting Service` (aggregates turnaround KPIs, ageing matrices, and executive dashboards)."

---

### Layer 5: Data & Messaging Backbone

"At the foundation sits our persistence and event backbone:
- **Primary Database (Amazon Aurora PostgreSQL)**: Multi-AZ transactional engine with automated failover and up to 15 auto-scaling read replicas. Each microservice owns its isolated PostgreSQL schema (`claims`, `workflow`, `workshops`, etc.) to prevent tight database coupling.
- **Distributed Cache (Amazon ElastiCache Redis)**: Caches active policy data, workshop directory lookups, and short-lived idempotency tokens to eliminate duplicate payments.
- **Document Storage (Amazon S3 + Object Lock)**: Stores all photos, PDF estimates, and police reports with Write-Once-Read-Many (WORM) compliance, enforcing the mandatory 7-year insurance retention policy.
- **Event Streaming (Amazon MSK)**: Managed Apache Kafka cluster serving as our event backbone, decoupling state changes from downstream notifications and audit logs."

---

## 3. Clear Conceptual Explanations of Key Diagram Terms

| Term in Diagram | Plain-Language Meaning | Technical / Business Significance |
|---|---|---|
| **Anycast Routing (Route 53)** | Routing network traffic to the geographically nearest server using a single shared IP address. | Minimizes network latency for users across the country and provides instant regional failover if a primary cloud region fails. |
| **Project Loom / Virtual Threads** | Lightweight threads managed by the Java virtual machine rather than the operating system. | Enables Spring Boot to process thousands of concurrent web requests with minimal RAM, preventing server thread-pool crashes during traffic surges. |
| **Camunda 8 (BPM Orchestration)** | An enterprise workflow engine executing visual BPMN 2.0 diagrams. | Automates long-running business processes, timer escalations, and SLA alerts without hardcoding timer loops in application code. |
| **WORM Storage (Object Lock)** | 'Write Once, Read Many' - data written to storage cannot be modified, overwritten, or deleted by anyone, including administrators. | Essential for legal compliance with US insurance regulations and state insurance commissioner fraud audits. |
| **UMA 2.0 (User-Managed Access)** | An OAuth2-based standard for fine-grained, policy-driven authorization. | Enables Keycloak to manage dynamic, role-based permission delegations (e.g., a Case Manager delegating work to a peer) without code changes. |

---

## 4. Expected Technical Questions & Answers (Meeting Panel)

### Q1: Why did you choose a microservices architecture instead of keeping the existing monolithic application structure?
**Answer**:
"A monolithic structure creates tight coupling across domains that evolve at completely different speeds. For example, our reporting queries and fraud analysis algorithms are compute-heavy and read-intensive, while our claims intake path is write-intensive and requires sub-second SLAs. In a monolith, an unindexed reporting query run by regional management can lock database tables, slowing down customer claim submissions. Componentizing into autonomous services with isolated database schemas allows us to scale the Claims Service independently during a storm surge while leaving the rest of the system undisturbed."

### Q2: How does the system handle communication between microservices? Is it synchronous REST or asynchronous messaging?
**Answer**:
"We follow a strict hybrid principle:
- **Synchronous REST (over mTLS)** is reserved exclusively for immediate queries where the caller cannot proceed without the result (e.g., the Claims Service verifying active policy coverage with PMS).
- **Asynchronous Messaging (via Amazon MSK / Kafka)** is used for all state changes and cross-service side-effects. When an adjuster approves a claim, the Claims Service writes the approval to its local database and publishes a `ClaimApproved` event to Kafka. The Workshop Service, Payment Service, and Notification Service consume that event independently. This ensures that if the notification system is temporarily degraded, the adjuster's approval succeeds without delay."

### Q3: How do you prevent binary document uploads from degrading API Gateway and microservice memory?
**Answer**:
"We implement the **Direct-to-S3 Pre-Signed URL Pattern**. The client never streams multi-megabyte photos or police report PDFs through the API Gateway or Spring Boot container memory. Instead, the client sends a small JSON metadata request (`POST /api/v1/documents/presigned-url`). The Document Service verifies permissions and returns a cryptographically signed, short-lived S3 upload URL. The client browser or mobile app uploads the binary directly to Amazon S3. Once S3 confirms receipt, an asynchronous event triggers AWS Textract OCR and malware scanning in the background. This completely eliminates media upload memory bottlenecks."

### Q4: How do we monitor system health to satisfy the NFR requirement of 24x7 uptime and automatic recovery?
**Answer**:
"We implement defense-in-depth observability:
1. Every container exposes Spring Boot Actuator `/actuator/health/liveness` and `/actuator/health/readiness` probes. If a container hangs or deadlocks, AWS ECS Fargate automatically terminates it and spins up a healthy replacement in seconds.
2. We use OpenTelemetry distributed tracing with AWS X-Ray to track requests end-to-end across services using a unique `traceId`.
3. CloudWatch and Prometheus collect p99 latency metrics at the ALB and API Gateway. If the p99 response time exceeds 4,000ms or 5xx error rates exceed 0.5%, CloudWatch alarms automatically page our on-call engineering team via PagerDuty."
