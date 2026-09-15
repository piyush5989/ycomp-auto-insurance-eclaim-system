# eClaims Modernisation - Design Documents & Diagrams Alignment Assessment

| Metadata | Details |
|---|---|
| Document Type | Architectural Review & Diagram Alignment Audit |
| System | YCompany eClaims Processing System |
| Review Standard | `assignment.txt` & `system-nature-scope-capacity-analysis.md` |
| Scale Context | 200 Million Policyholders across US Geographies |
| Author | Senior Solution Architect |
| Date | September 2026 |

---

## 1. Executive Summary

This evaluation conducts a rigorous architectural cross-check of all system design diagrams and supporting specifications against:
1. **The Business & Functional Specifications** defined in `assignment.txt`.
2. **The Nature, Scope, and Capacity Profile** established in `system-nature-scope-capacity-analysis.md` (200M customers, 12M annual claims, 40k-200k daily claims, 250 write TPS, 2,500 read QPS, 1.43 PB 7-year storage lifecycle, and CQRS/Event-driven write-read decoupling).

### Summary Alignment Scorecard

| Diagram / Deliverable | Source Artifact | Assignment.txt Alignment | Capacity & Nature Alignment | Overall Rating | Key Action Item |
|---|---|---|---|---|---|
| **1. System Context Architecture** | `context-diagram.mmd` / `.svg` | Moderate (80%) | High (90%) | **Minor Revision Needed** | Move Workshop Partner outside internal boundary; add Car Rental Partner & External Workshops |
| **2. Solution Architecture** | `solution-architecture.mmd` / `.svg` | High (90%) | High (88%) | **Minor Revision Needed** | Add Rentals Service; update document upload to direct S3 pre-signed pattern |
| **3. Claims State Machine** | `claim-states-diagram.svg` | High (88%) | High (92%) | **Minor Revision Needed** | Add Case Manager supervisor override, reassignment loop, and Fraud Hold state |
| **4. Scalability Strategy** | `scalibility-strategy.svg` | High (95%) | Very High (95%) | **Aligned** | Add S3 Lifecycle tiering (Hot -> IA -> Glacier) for 1.43 PB 7-year retention |
| **5. Event-Driven Architecture** | `event-driven-architecture.svg` | Very High (96%) | Very High (98%) | **Strongly Aligned** | Highly consistent with async write fan-out and decoupling requirements |
| **6. Multi-Region Deployment** | `deployment-diagram.mmd` / `.svg` | High (90%) | High (90%) | **Aligned with Caveat** | Harmonize ECS Fargate (Phase 1) vs EKS (Phase 2) label with Solution Architecture |
| **7. Enterprise CI/CD Pipeline** | `ci-cd-architecture.mmd` / `.svg` | Very High (95%) | Very High (95%) | **Strongly Aligned** | Directly validates < 5000ms SLA and 200M load simulation in QA gate |

---

## 2. Diagram-by-Diagram Detailed Assessment

### 2.1 System Context Architecture (`context-diagram.mmd` / `.svg`)

#### Diagram Overview
Depicts external actors, YCompany internal organizational boundaries, the core eClaims system, and third-party dependencies (Policy Management System, Stripe, Notification gateways).

#### Alignment Strengths
- Accurately captures all internal human roles specified in Section 3 of `assignment.txt`: Case Manager, Surveyor, Adjustor, Auditor, Regional Manager, and Top Management.
- Correctly links policy validation to the external Policy Management System (PMS) and outbound notifications to SES/Twilio/FCM.
- Reflects the electronic payment integration for customer and workshop payments.

#### Gaps and Discrepancies
1. **Misplaced Boundary for Partner Workshops**:
   - In `context-diagram.mmd`, `PARTNERS["Workshop Partner"]` is placed inside the `subgraph INTERNAL["YCompany Internal Users"]`.
   - Per `assignment.txt` Section 3 ("3rd Party integrations"), repair workshops are external commercial entities accessing a dedicated 3rd-Party Portal, not internal YCompany employees.
2. **Missing Car Rental Partner Dependency**:
   - `assignment.txt` explicitly requires: *"user should be able to select a rental vehicle from the Car Rental partner from customer site itself"*.
   - The context diagram omits the Car Rental Partner integration entirely from both the actor side and the external systems side.
3. **Missing External (Out-of-Network) Workshops**:
   - `assignment.txt` specifies: *"Customer can choose from partner workshop or any external workshop."* The diagram only accounts for partner workshops.

#### Recommended Corrections
- Relocate `Workshop Partner` and add `Car Rental Partner` under an `External Service Providers / Partners` boundary on the left.
- Add `External Rental Partner API` under `External Dependencies` on the right.

---

### 2.2 Solution Architecture (`solution-architecture.mmd` / `.svg`)

#### Diagram Overview
Displays the multi-tier enterprise architecture across Internet/Edge, Load Balancing, Presentation Layer, API Gateway & Identity, Microservices, Data & Messaging, External Systems, and Observability.

#### Alignment Strengths
- **Dual Identity Tier**: Cognito for 200M customer scale and Keycloak for internal staff fine-grained RBAC aligns with the high-concurrency external vs strict-permission internal profile.
- **Event Backbone (Amazon MSK)**: Matches the write-heavy, event-carried state transfer pattern required for asynchronous decoupling.
- **Aurora PostgreSQL Multi-AZ + Read Replicas**: Directly addresses the read-heavy nature of customer status tracking and staff dashboard queries.
- **Presentation Layer Separation**: Clearly divides Customer Web, Mobile App (React Native), Internal Admin, and Workshop Portal.

#### Gaps and Discrepancies
1. **Missing Rental Service in Microservices**:
   - The microservices box shows: Claims, Workflow, Document, Payment, Reporting, and Notification.
   - It omits the `Rental Service` (which exists in the repository as `modules/rentals` and in `assignment.txt` requirements).
2. **Document Upload Data Flow Inefficiency**:
   - In `solution-architecture.mmd`, `documentService -->|"Uploads Files"| fileStorage`.
   - As calculated in the capacity analysis, daily media ingestion is ~680 GB/day (peaking up to 3.4 TB/day during catastrophes). Streaming multi-megabyte binary photos through the Document Service container creates memory and bandwidth saturation.
   - Per `scalibility-strategy.svg` and standard cloud best practices, the Document Service should issue pre-signed S3 URLs, allowing client portals to upload directly to S3.
3. **Search / Operational Queue Read Acceleration**:
   - With 560,000 active concurrent claims and thousands of internal staff filtering queues, an Elasticsearch / OpenSearch cluster or Aurora read-replica caching layer is essential to avoid query contention on the transactional database.

#### Recommended Corrections
- Add `Rental Service (Partner Integration)` into the `SERVICES` subgraph.
- Clarify the document upload arrow as: `PRESENTATION -->|"Pre-signed direct upload"| fileStorage` and `documentService -->|"Metadata & OCR"| fileStorage`.

---

### 2.3 Claims Lifecycle State Machine (`claim-states-diagram.svg`)

#### Diagram Overview
Visualizes the linear and branching states of an auto insurance claim from submission to closure and DMS archival.

#### Alignment Strengths
- Sequence perfectly reflects the core business flow in `assignment.txt`:
  `Start -> Draft -> Submitted -> Assigned -> UnderSurvey -> AssessmentSubmitted -> UnderAdjudication -> Approved / Rejected -> InRepair -> RepairComplete -> PaymentPending -> Settled -> Archived -> End`.
- Distinguishes between assessment (surveyor) and adjudication (adjuster), enforcing segregation of duties.
- Integrates workshop repair completion and electronic payment settlement before final archival.

#### Gaps and Discrepancies
1. **Missing Case Manager Supervisor Override Flow**:
   - `assignment.txt` Section 3 explicitly notes: *"Case Manager should be able to view the complete details of the case and make adjustment to claims which can override the Adjustor and surveyor."*
   - The diagram does not show an override bypass path where a Case Manager directly adjusts/approves a claim.
2. **Missing Reassignment / Delegation Loop**:
   - `assignment.txt`: *"In case Surveyor or Adjustor is not available for claims processing the case manager should be able to delegate the claim processing to other Surveyor or Adjustor."*
   - The state machine lacks a self-transition or `REASSIGNED` sub-state from `ASSIGNED` / `UNDER_SURVEY`.
3. **Missing Fraud Investigation Hold State**:
   - Fraud detection and the ageing matrix are prominent requirements in `assignment.txt` and `nfr-summary.md`. A flagged claim must transition to an `INVESTIGATION_HOLD` / `SIU_REVIEW` state rather than proceeding directly to normal approval.

#### Recommended Corrections
- Add transition branches for `Supervisor Override` (Case Manager -> Approved), `Reassignment` (Assigned -> Reassigned), and `Fraud Review` (Submitted -> Fraud Hold -> Under Adjudication).

---

### 2.4 Scalability Strategy Diagram (`scalibility-strategy.svg`)

#### Diagram Overview
Covers horizontal scaling, multi-layer caching, asynchronous decoupling, and database optimization techniques.

#### Alignment Strengths
- **Database Partitioning**: Specifies `Partitioning claims table by year + region`. This is essential for handling 12,000,000 claims/year and preventing index degradation.
- **Pre-signed S3 Uploads**: Explicitly states `Document upload via pre-signed S3 URLs (removes API bottleneck)`. This directly resolves the capacity concern of 680 GB daily photo uploads.
- **Multi-Level Caching**: Highlights Redis caching for policy/user reference data (TTL 15m) and dashboard metrics (TTL 5m), plus CloudFront edge caching, matching the 2,500 QPS read peak.
- **Kafka Partition Scaling**: Aligns with the 250 write TPS and async claim status propagation.

#### Minor Enhancement Opportunity
- **Storage Tiering Lifecycle**: At 204 TB/year and 1.43 PB over 7 years, storage costs will dominate without lifecycle rules. Add an explicit mention of S3 Intelligent Tiering or S3 Glacier Deep Archive transitions (e.g., Hot S3 0-60 days -> S3-IA 60-365 days -> Glacier Years 2-7).

---

### 2.5 Event-Driven Architecture (`event-driven-architecture.svg`)

#### Diagram Overview
Illustrates core services producing domain events to an event streaming layer, consumed by downstream delivery channels, audit logs, and external consumers.

#### Alignment Strengths
- **Write Side-Effects Isolation**: Directly fulfills the capacity analysis recommendation by offloading notification fan-out, audit trail recording, real-time WebSocket pushes, and BI metrics ingestion from the critical write path.
- **Stakeholder Inclusivity**: Correctly identifies all recipients: 200M+ policyholders, adjustors, surveyors, managers, workshops, rental providers, and executive reporting.
- **Audit & WORM Compliance**: Connects immutable event streams directly to S3 + Object Lock for 7-year regulatory retention.

#### Minor Enhancement Opportunity
- Indicate Dead Letter Queues (DLQ) and Idempotent Consumer patterns on critical paths like Payment and Workshop Settlement events to prevent duplicate disbursements.

---

### 2.6 Multi-Region Deployment Architecture (`deployment-diagram.mmd` / `.svg`)

#### Diagram Overview
Maps AWS infrastructure topology across active Region A (`us-east-1`) and passive DR Region B (`us-west-2`), detailing ingress, compute, caching, streaming, data storage, and observability.

#### Alignment Strengths
- **High Availability & DR**: Implements active-passive failover with Route 53 (60s TTL), RDS PostgreSQL streaming replication, MSK cross-region mirroring, and S3 Cross-Region Replication, satisfying the RTO < 1h / RPO < 15m requirement.
- **Enterprise Observability**: Dedicated Prometheus, Grafana, and ELK stacks in both regions guarantee full operational visibility and debugging support.
- **Security Isolation**: Clear separation into Public Subnet (ALB), Private Subnet (Compute/Kafka/Cache), and Restricted Data Zone (RDS/S3).

#### Gaps and Discrepancies
1. **ECS Fargate vs EKS Discrepancy**:
   - `solution-architecture.mmd` and `ci-cd-architecture.mmd` designate compute as **AWS ECS Fargate**.
   - `deployment-diagram.mmd` labels compute as **Amazon EKS Cluster A / B**.
   - While `solution-approach.md` describes this as a Phase 1 (ECS Fargate) to Phase 2 (EKS) migration, the diagram itself should clarify `Amazon EKS (Target / Phase 2)` or reflect ECS Fargate to prevent client confusion during senior stakeholder reviews.

---

### 2.7 Enterprise CI/CD Pipeline Architecture (`ci-cd-architecture.mmd` / `.svg`)

#### Diagram Overview
Covers source code management, AWS-native DevSecOps build pipeline, container security, quality gates, multi-environment deployment, and infrastructure as code.

#### Alignment Strengths
- **Direct NFR Validation**: The QA/UAT stage explicitly incorporates: `UAT + Performance Testing: 5000ms SLA Validation, 200M User Load Simulation`. This directly traces back to the primary performance NFR in Section 5.2 of `assignment.txt`.
- **Security Standards**: Integrates SonarQube, OWASP dependency scanning, TruffleHog secrets detection, and Trivy container vulnerability scanning, satisfying OWASP Top 10 compliance.
- **Zero-Downtime Releases**: Blue-Green deployment with automated rollback on latency degradation (> 5000ms) or 5xx error spikes ensures 24x7 system availability.

---

## 3. Synthesis & Actionable Recommendations

### Prioritized Roadmap for Diagram Adjustments

| Priority | Artifact | Adjustment Required | Rationale |
|---|---|---|---|
| **P1** | `context-diagram.mmd` | Move Workshop Partner outside internal boundary; add Car Rental Partner & External Workshops | Critical business boundary correctness per `assignment.txt` Section 3 |
| **P2** | `solution-architecture.mmd` | Add Rentals Service to microservices; indicate S3 pre-signed direct upload pattern | Prevents 680 GB/day API container bottleneck identified in capacity analysis |
| **P3** | `claim-states-diagram.svg` | Add branches for Case Manager supervisor override, reassignment, and fraud hold | Reflects core functional override and fraud requirements from `assignment.txt` |
| **P4** | `deployment-diagram.mmd` | Add annotation: `Amazon EKS (Phase 2 Target) / ECS Fargate (Phase 1)` | Resolves terminology inconsistency between solution architecture and deployment diagrams |
| **P5** | `scalibility-strategy.svg` | Add explicit 7-year S3 Lifecycle tiering (Hot -> IA -> Glacier) | Validates financial and capacity feasibility for 1.43 PB document storage |

---

## 4. Conclusion

The existing design diagrams exhibit **high technical maturity and strong alignment (88% to 96%)** with both the business requirements in `assignment.txt` and the architectural scale requirements derived in `system-nature-scope-capacity-analysis.md`.

The primary strengths lie in the **event-driven asynchronous decoupling, database partitioning, multi-tier caching, and robust DevSecOps pipeline**. The few identified gaps (boundary classification of workshops, missing rental integrations, and state machine override branches) are straightforward adjustments that will elevate the deliverables to a flawless Senior Staff / Principal Architect standard.
