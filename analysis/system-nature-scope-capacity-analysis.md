# eClaims Processing System - Nature, Scope, Capacity, and Read/Write Profile Analysis

## 1. Executive Summary: Is the System Read-Heavy or Write-Heavy?

### The Architectural Verdict
By pure raw HTTP request volume, the eClaims system is **Read-Dominant (roughly 75:25 to 80:20 read-to-write ratio)**. However, from a systems engineering, persistence footprint, state machine complexity, and consistency perspective, it is a **Write-Intensive, Workflow-Centric Transactional System**.

### Why Raw Numbers Show Read Dominance
1. **Customer Status Inquiries & Anxiety-Driven Polling**: An auto insurance claim is a high-stress event. A customer whose vehicle was damaged checks the mobile app or web portal 2 to 5 times daily over the 10 to 14 day repair lifecycle. That equates to 20 to 50 read requests per claim just from the customer side.
2. **Operational Queues & Staff Dashboards**: 
   - Incident / Case Managers constantly monitor triage queues and assignment status.
   - Adjusters repeatedly query their workbench, view claim histories, examine uploaded damage photos, and compare line items against policy coverage.
   - Field surveyors pull assigned inspection lists and reference vehicle specs.
   - Partner workshops refresh work order backlogs and payment reconciliation screens.
3. **Directory and Geolocation Lookups**: Searching for partner repair shops and rental agencies based on customer zip code or geolocation generates repeated cacheable reads.
4. **Management Reporting & Analytics**: Executive dashboards, regional throughput monitors, fraud detection queries, and ageing matrices aggregate large volumes of claims data.

### Why the System Must Be Engineered Around Writes (Write-Intensive Reality)
1. **Complex Multi-Step State Transitions**: Unlike a social network where a write is a simple database insert/update, an insurance claim write represents a high-integrity business transaction:
   - First Notice of Loss (FNOL) intake with incident data and coverage validation.
   - Automated workforce assignment (matching surveyor geolocations, adjuster workloads).
   - Surveyor field assessment submission with itemized damage estimates.
   - Adjuster claim adjudication and payout approval.
   - Workshop work order generation, repair milestone updates, and final billing.
   - Payment execution and deductible settlement.
2. **Heavy Media & Document Ingestion**: Each claim involves uploading 5 to 10 high-resolution damage photographs, police reports, and PDF repair estimates. In terms of network bandwidth and disk I/O, write volume is enormous (gigabytes per hour, petabytes across regulatory retention lifecycles).
3. **Strict ACID Consistency and Zero-Data-Loss Requirement**: Financial payouts, coverage approvals, and settlement amounts cannot tolerate eventual inconsistency or dropped writes. Non-repudiation is mandatory.
4. **Heavy Write Side-Effects (Event Fan-Out)**: Every single state write triggers cascading asynchronous actions:
   - Notification dispatch (SMS, email, push to multiple parties).
   - Event streaming for fraud scoring and audit compliance.
   - Document archiving into an immutable document management system.

**Architectural Recommendation**: Implement **Command Query Responsibility Segregation (CQRS)** with an **Event-Driven Architecture**:
- **Command Side (Writes)**: Optimized for strong transactional consistency, state machine enforcement, validation, and domain event publishing.
- **Query Side (Reads)**: Optimized for low latency, utilizing read replicas, caching tiers (Redis), and Elasticsearch/OpenSearch for operational queues and multi-attribute search.

---

## 2. Nature of the System

### Core Architectural Characteristics
1. **Multi-Sided Enterprise Collaboration Platform**:
   - The platform acts as a unified digital clearinghouse coordinating five distinct stakeholder groups:
     - Policyholders / Customers (Self-service FNOL, tracking, payments)
     - Field Surveyors (Mobile/web damage assessments, repair estimates)
     - Claims Adjusters (Adjudication, valuation, approval/rejection)
     - Case / Incident Managers (Triage, queue oversight, reassignment overrides)
     - 3rd-Party Partner Ecosystem (Repair workshops, car rental providers)
     - Auditors & Compliance Officers (Immutable audit trails, regulatory reporting)
2. **State-Machine Driven Core**:
   - A claim is not a static database row; it is a long-running, distributed workflow:
     `DRAFT -> SUBMITTED (FNOL) -> ASSIGNED -> UNDER_SURVEY -> ASSESSMENT_SUBMITTED -> UNDER_ADJUDICATION -> APPROVED / REJECTED -> REPAIR_IN_PROGRESS -> REPAIR_COMPLETED -> PAYMENT_SETTLED -> CLOSED`
   - Strict invariants govern state transitions (e.g., an adjuster cannot adjudicate before surveyor assessment is submitted, unless supervisor override is invoked).
3. **Geo-Aware Workforce Orchestration**:
   - Auto-assignment logic dynamically matches field surveyors based on their service territory and proximity to the vehicle's repair workshop location, while balancing adjuster caseloads.
4. **Strict Auditability and Non-Repudiation**:
   - Every status transition, line-item adjustment, supervisor override, and customer message must be cryptographically auditable for state insurance commissioner audits and legal dispute resolutions.
5. **Hybrid Synchronous / Asynchronous Execution**:
   - Synchronous: User-facing operations (FNOL submission receipt, status queries, payments) with p99 < 5000 ms SLA.
   - Asynchronous: Media processing/OCR, notifications, fraud evaluation rules, automated assignments, and document archival.

---

## 3. Scope of Work

### In-Scope Functional Modules
1. **Customer Experience Portal & Mobile App**:
   - Policy-based authentication and onboarding.
   - Digital FNOL submission with photos and police report upload.
   - Real-time claim status tracking and push/SMS/email alerts.
   - In-network partner workshop locator (by zip code/GPS) and appointment booking.
   - Car rental partner vehicle selection based on policy entitlement.
   - Electronic payment of deductibles and repair dues.
2. **Incident & Workflow Management Engine**:
   - Rule-based automated assignment of adjusters and surveyors based on territory and workload.
   - Manual delegation and supervisor override capabilities for case managers.
   - SLA tracking and escalation triggers for stalled claims.
3. **Surveyor & Adjuster Workbench**:
   - Surveyor mobile/web digital assessment form (itemized damage, labor/parts estimate).
   - Adjuster adjudication cockpit (policy coverage verification, deductible calculation, approval workflow).
4. **3rd-Party Partner Integration (Workshops & Rentals)**:
   - Workshop portal for work order acceptance, photo updates, repair progress logs, and delivery date adjustments.
   - Final repair bill submission and electronic claim settlement tracking.
   - Rental car provider reservation and status sync.
5. **Document Management & Archival (DMS)**:
   - Centralized, encrypted storage for photos, estimates, invoices, and police reports.
   - Metadata indexing, access control, and 7-year regulatory retention lifecycle.
6. **Communication & Notification System**:
   - Multi-channel notification pipeline (SMS, email, push notification).
   - Full archival of customer and provider correspondence.
7. **Reporting & Business Intelligence**:
   - Case Manager operational reporting (workload, pending actions).
   - Regional Manager dashboards (turnaround time, claim volumes, paid amounts).
   - Executive & Compliance reports (cross-region performance, fraud analytics, ageing matrices).

### Explicit Out-of-Scope Boundaries
1. **Core Policy Sales & Underwriting**:
   - Policy generation, underwriting risk rating, and insurance sales are pre-existing systems. eClaims interfaces with them via read-only REST/gRPC policy validation contracts.
2. **Core General Ledger / Banking Engine**:
   - Payment gateway integration processes electronic customer payments and workshop disbursements, but core treasury banking and financial books remain in the company's enterprise ERP.
3. **Multi-Lingual Localization**:
   - As stipulated in the requirements, the system operates exclusively in English.

---

## 4. Capacity and Sizing Estimation (Back-of-the-Envelope)

### Scale Foundations (Derived from 200M Customer Base)
- **Total Customer Base**: 200,000,000 policyholders.
- **Auto Insurance Industry Claim Frequency**: 5% to 7% of insured vehicles file a claim annually. Using **6% average annual claim frequency**:
  - **Annual Claims Volume**: 200,000,000 * 0.06 = **12,000,000 claims / year**.
  - **Monthly Volume**: ~1,000,000 claims / month.
  - **Daily Volume (Average)**: ~33,000 to 40,000 claims / day.
  - **Peak Surge Factor**: Severe weather events (hailstorms, blizzards, hurricanes) produce regional surges of 3x to 5x.
  - **Peak Daily Volume**: **150,000 to 200,000 claims / day**.

### Transactional Throughput Calculations (TPS / QPS)
1. **First Notice of Loss (FNOL) Ingestion**:
   - Average Day: 40,000 claims / (12 business hours * 3,600s) ≈ **0.93 FNOL / sec**.
   - Peak Day: 200,000 claims / (12 business hours * 3,600s) ≈ **4.63 FNOL / sec**.
   - Peak Burst (Localized catastrophe surge, 1-hour window): **25 to 50 FNOL / sec**.
2. **Overall Write Transaction Throughput**:
   - Average lifecycle write events per claim: ~20 distinct write operations (FNOL creation, document uploads, auto-assignment, surveyor report, adjuster valuation, 4-6 repair status updates, payment, notes, audit logs).
   - Total Daily Writes: 40,000 * 20 = 800,000 write transactions / day.
   - Average Write TPS: ~18 to 25 writes / sec.
   - Peak Write TPS: **100 to 250 writes / sec**.
3. **Read Request Throughput (QPS)**:
   - Average active claims in flight (assuming 14-day average turnaround): 40,000 * 14 = ~560,000 active concurrent claims.
   - Customer status views: 560,000 active claims * 2 views/day = 1,120,000 views/day.
   - Staff queue queries (adjusters, surveyors, workshops refreshing queues): ~3,000,000 queries/day.
   - Partner directory & rental search queries: ~500,000 queries/day.
   - Total Daily Reads: ~4,620,000 to 8,000,000 reads / day.
   - Average Read QPS: ~100 to 180 QPS.
   - Peak Read QPS (morning dashboard logins + customer surge during catastrophic events): **1,000 to 2,500 QPS**.

### Storage & Media Capacity Requirements
1. **Unstructured Data (Document Management System / Object Storage)**:
   - Per claim media: 5 high-res photos (3 MB each) + 2 PDF documents/estimates (1 MB each) ≈ **17 MB per claim**.
   - Daily media intake: 40,000 claims * 17 MB ≈ **680 GB / day**.
   - Annual media intake: 12,000,000 claims * 17 MB ≈ **204 TB / year**.
   - 7-year regulatory retention requirement: 204 TB * 7 ≈ **1.43 Petabytes**.
   - Storage Strategy: Hot tier (S3 Standard / Blob Storage) for active claims (first 60 days) -> Infrequent Access (S3 Standard-IA) for 1 year -> Glacier / Deep Archive for years 2 through 7.
2. **Structured Transactional Data (Relational Database)**:
   - Relational record footprint per claim (metadata, assignments, line items, audit logs, notifications): ~50 KB per claim.
   - Annual database growth: 12,000,000 * 50 KB ≈ **600 GB / year**.
   - 5-year relational retention: ~3.0 TB. Highly manageable with modern partitioned PostgreSQL / Aurora clusters with read replicas.

### Non-Functional Requirements (NFR) Alignment
- **Latency / Performance**: 99% of requests completed in < 5,000 ms (p99 < 5.0s), with standard transactional endpoints targeting p95 < 500 ms and cached read endpoints targeting p95 < 100 ms.
- **Availability**: 99.95% uptime (24x7 operation with multi-AZ redundancy and automated self-healing restart).
- **Scalability**: Horizontal auto-scaling of stateless application pods based on CPU/memory thresholds and queue depth.
