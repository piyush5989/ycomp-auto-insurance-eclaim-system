# Meeting Presentation Script: Multi-Region Deployment Architecture (05-deployment-diagram)

- **Target Diagram**: `ycomp-auto-insurance-eclaim-system/design-documents/deployment-diagram.svg`
- **Target Audience**: Infrastructure Directors, Cloud Security Architects, Principal DevOps Engineers, C-Suite Technical Stakeholders
- **Presenter Role**: Lead Enterprise Solutions Architect
- **Presentation Objective**: Explain the physical cloud infrastructure topology on AWS, detailing multi-AZ active-active deployment in primary Region A (us-east-1), warm standby disaster recovery in Region B (us-west-2), zero-trust network zoning, automated self-healing, and RTO/RPO targets.

---

## 1. Opening Narrative: The Story Behind the Diagram

"Good afternoon, leaders and technical team members.

We have spent our previous sessions exploring our system context, microservices design, event streaming, and scalability strategy. Now, we arrive at the physical bedrock of our platform: *'Where does the software actually run, how is it secured from hackers, and what happens when the cloud data center itself catches fire?'*

Let me take you back to a real-world nightmare scenario that occurs in enterprise IT.

It is 2:15 AM on a Wednesday. In Northern Virginia, an errant construction backhoe cuts through a bundle of primary transatlantic fiber cables alongside a highway. Within ten minutes, power transformers in a major cloud data center malfunction. Status dashboards turn bright red: *Amazon Web Services us-east-1 is experiencing degraded networking, lost power, and elevated API error rates.*

If you are running a casual social app, you put up a polite message on social media and tell your users to come back tomorrow.

If you are YCompany, serving **200 million policyholders across the United States**, an unmanaged regional outage is a catastrophe:
- An insured family stranded on a dark interstate at 2:30 AM cannot request emergency roadside assistance.
- Body shops across the nation cannot release repaired vehicles because authorization APIs are dead.
- Adjusters cannot disburse emergency living expense cheques to displaced homeowners after a flood.
- State insurance commissioners launch immediate regulatory investigations into breach of statutory service standards.

Our assignment requirements give us three non-negotiable mandates:
- **eClaims environment should be up and running 24x7. In case of any crashes, the system should again restart on its own.**
- **The system should be reliable and store, backup, and recover system data in a distributed environment.**
- **Disaster Recovery Targets: RTO < 1 hour, RPO < 15 minutes, with 99.99% availability.**

This diagram - our **Multi-Region Deployment Architecture** - is the proof of how we fulfill those commitments. 

It maps out our physical AWS infrastructure topology across two separate regions: our **Primary Active Region in us-east-1 (N. Virginia)** and our **Passive Warm Standby DR Region in us-west-2 (Oregon)**.

Let us walk through the architecture zone by zone."

---

## 2. Step-by-Step Diagram Walkthrough & Conceptual Terms

```
+-------------------------------------------------------------------------------------------------------+
|                                    GLOBAL EDGE & INGRESS TIER                                         |
|  - Route 53: Anycast DNS with automated health check failover                                         |
|  - CloudFront: Global CDN terminating TLS 1.3 at hundreds of edge locations                           |
|  - AWS WAF & Shield Advanced: Perimeter DDoS mitigation & OWASP Top 10 rule enforcement               |
+----------------------------------------------------+--------------------------------------------------+
                                                     |
                         Primary Active Route (100%) |  Standby Route (Failover via Route 53 Health Check)
                                                     v
+----------------------------------------------------+--------------------------------------------------+
|      PRIMARY REGION: us-east-1 (N. VIRGINIA)       |        DR REGION: us-west-2 (OREGON - PASSIVE)   |
|      Active-Active across 3 Availability Zones     |        Warm Standby (Pilot Light Cluster)        |
+----------------------------------------------------+--------------------------------------------------+
| [1. PUBLIC SUBNETS (DMZ) - 10.0.0.0/20]            | [1. PUBLIC SUBNETS (DMZ)]                        |
|  - Internet Gateway (IGW) for Ingress/Egress       |  - Internet Gateway & Route Tables               |
|  - Public Application Load Balancer (ALB)          |  - Pre-warmed Standby ALB                        |
|  - NAT Gateways (1 per AZ for high availability)   |  - NAT Gateways                                  |
|  - AWS Systems Manager (SSM) Bastionless Access    |                                                  |
+----------------------------------------------------+--------------------------------------------------+
| [2. PRIVATE APP SUBNETS - 10.0.16.0/20]            | [2. DR PRIVATE APP SUBNETS]                      |
|  - Presentation: S3-hosted React Portals           |  - Pre-warmed ECS Fargate Tasks (Min capacity)   |
|  - API Gateway & Identity: Cognito + Keycloak      |  - Step-scaling policies for instant ramp-up     |
|  - ECS Fargate Microservices:                      |                                                  |
|      * Claims Svc (3-20 tasks)                     |                                                  |
|      * Workflow / Camunda 8                        |                                                  |
|      * Fraud Detect (2-10 tasks)                   |                                                  |
|      * Document Svc (OCR + Textract)               |                                                  |
|      * Payment Svc (Stripe + ACH)                  |                                                  |
|      * Reporting (Redshift + QuickSight)           |                                                  |
|      * Notifications (NestJS)                      |                                                  |
+----------------------------------------------------+--------------------------------------------------+
| [3. PRIVATE DATA SUBNETS - 10.0.32.0/20]           | [3. DR PRIVATE DATA SUBNETS]                     |
|  - Aurora PostgreSQL (1 Primary Writer + 3 Readers)|  - Aurora Global Database (<1s replication lag)  |
|  - ElastiCache Redis Cluster (Multi-AZ)            |  - Amazon MSK DR (MirrorMaker 2 active sync)     |
|  - MemoryDB for Redis (Strict Idempotency)         |  - S3 Cross-Region Replication (CRR)             |
|  - Amazon S3 + Object Lock (WORM 7yr Compliance)   |  - DynamoDB Global Tables (Bi-directional sync)  |
|  - Amazon MSK (Kafka - 6 brokers, RF=3)            |                                                  |
|  - OpenSearch (Full-Text Search & Log Analytics)   |                                                  |
+----------------------------------------------------+--------------------------------------------------+
| [4. SECURITY & OBSERVABILITY (24x7)]               | [4. BACKUP & RECOVERY TARGETS]                   |
|  - AWS KMS (Envelope Encryption)                   |  - RTO: 1 hour (Region Failover Target)          |
|  - Secrets Manager (Automated Credential Rotation) |  - RPO: 15 min (Maximum Tolerable Data Loss)     |
|  - CloudWatch + Prometheus + X-Ray Tracing + ELK   |  - Availability: 99.99% Enterprise SLA           |
|  - PagerDuty 24x7 Escalation Policies              |  - AWS Backup: 7-year immutable snapshots        |
+----------------------------------------------------+--------------------------------------------------+
```

---

### Zone 1: Global Edge & Ingress

"Look at the very top of the diagram. 

Every user request, whether from a customer on a smartphone, an adjuster on the internal portal, or a partner workshop mechanic on a tablet, enters through our **Global Edge Tier**:
- **Amazon Route 53**: Provides Anycast DNS resolution with continuous synthetic health checks. During normal operations, 100% of global DNS traffic routes to `us-east-1`.
- **Amazon CloudFront**: Caches static assets at hundreds of edge locations, terminating TLS 1.3 connections close to the user to minimize round-trip handshake latency.
- **AWS WAF & Shield Advanced**: Provides automated protection against volumetric DDoS attacks and inspects incoming payloads for OWASP Top 10 vulnerabilities (SQL injection, cross-site scripting) before traffic enters our cloud perimeter."

---

### Zone 2: Network Zoning and Subnet Isolation (Defense-in-Depth)

"Now, look inside our Primary Region `us-east-1` (VPC CIDR `10.0.0.0/16`). Notice the strict horizontal color coding:

#### 1. Public Subnets (DMZ) - Green
- This is our Demilitarized Zone. The only components here are the **Internet Gateway**, our **Public Application Load Balancers (ALB)**, and **NAT Gateways** (one per Availability Zone).
- **Crucial Security Rule**: No application server, database, or cache container ever has a public IP address. Even our administrators do not use SSH bastions; we use **AWS Systems Manager (SSM) Session Manager**, which establishes encrypted terminal tunnels without opening inbound ports.

#### 2. Private App Subnets - Blue
- All ECS Fargate microservice containers live here. 
- Inbound traffic is accepted exclusively from the Public ALB on port 8080 over TLS.
- Outbound traffic to third-party APIs (Stripe, Twilio, SendGrid) routes strictly through the NAT Gateways with static Elastic IP addresses, allowing external vendors to whitelist our egress IPs.

#### 3. Private Data Subnets - Purple (Zero Internet Access)
- Contains our stateful engines: Aurora PostgreSQL, ElastiCache Redis, Amazon MSK Kafka brokers, and OpenSearch.
- **Absolute Isolation**: These subnets have **zero route to the internet** - no NAT Gateway, no Internet Gateway. 
- How do microservices connect to AWS managed services like S3 or Secrets Manager? Through **AWS PrivateLink (VPC Endpoints)**. Traffic never leaves the private AWS fiber backbone."

---

### Zone 3: Container Orchestration & Automated Self-Healing

"Look at the container orchestration block in the Private App Subnet:
- Microservices run as **AWS ECS Fargate tasks** across three separate physical Availability Zones (AZ-a, AZ-b, AZ-c).
- **Automated Self-Healing**: Every container exposes Spring Boot Actuator `/actuator/health/liveness` probes. If a JVM experiences an unrecoverable memory leak, deadlock, or crash, ECS detects the probe failure, instantly terminates the unhealthy container, and launches a fresh container in seconds without human intervention.
- **Auto-Scaling**: Tasks scale out elastically based on CPU and memory thresholds (e.g., Claims Service scales from 3 tasks up to 20 tasks; Fraud Detection scales from 2 to 10 tasks) to absorb localized storm spikes."

---

### Zone 4: Data Tier & Disaster Recovery Synchronization

"Now, look at how data replicates between `us-east-1` (Virginia) and `us-west-2` (Oregon):
1. **Amazon Aurora Global Database**: The Aurora PostgreSQL Primary Writer in Virginia streams storage-level Write-Ahead Log (WAL) records directly to the read replica in Oregon via dedicated AWS backbone infrastructure. **Replication lag is under 1 second**.
2. **Amazon MSK MirrorMaker 2**: Kafka topics, offsets, and partition states are actively mirrored between the Virginia and Oregon Kafka clusters.
3. **Amazon S3 Cross-Region Replication (CRR)**: Every accident photo, estimate, and police report uploaded to S3 in Virginia is replicated to the Oregon bucket within seconds, protected by Object Lock in Compliance Mode.
4. **AWS DynamoDB Global Tables**: Provides multi-region active replication for distributed session states and real-time user presences."

---

## 3. Clear Conceptual Explanations of Key Diagram Terms

| Term in Diagram | Plain-Language Meaning | Technical / Business Significance |
|---|---|---|
| **RTO (Recovery Time Objective)** | The maximum acceptable duration of system downtime after a disaster (Target: 1 hour). | Dictates how fast our engineering team and automation must promote the DR region and restore live operations. |
| **RPO (Recovery Point Objective)** | The maximum acceptable data loss measured in time (Target: 15 minutes). | Dictates how frequently data must replicate; Aurora Global (<1s) and S3 CRR easily beat this 15-minute target. |
| **Active-Active (Multi-AZ) vs Active-Passive (Multi-Region)** | Active-Active within a region across 3 data centers; Active-Passive between regions (Virginia active, Oregon warm standby). | Provides 99.99% local availability without incurring cross-country network latency penalties on transactional writes. |
| **VPC Endpoints (AWS PrivateLink)** | Private network interfaces connecting your VPC directly to AWS services without traversing the public internet. | Guarantees compliance; ensures sensitive insurance data and customer PII never touch the public internet. |
| **MirrorMaker 2** | A distributed utility that mirrors topics, message offsets, and configurations between separate Apache Kafka clusters. | Ensures that in the event of regional failover, event consumers in Oregon resume processing without missing or duplicating messages. |

---

## 4. Expected Technical Questions & Answers (Meeting Panel)

### Q1: Why did we choose an Active-Passive (Warm Standby) model across regions instead of an Active-Active multi-region database setup?
**Answer**:
"This was a deliberate design choice based on the physics of latency and regulatory financial consistency:
- **Speed-of-Light Latency**: Virginia and Oregon are approximately 2,400 miles apart. A synchronous round-trip database commit across that distance takes 70 to 80 milliseconds. Forcing every claims write transaction to wait for cross-country confirmation would destroy our p99 latency SLA.
- **Preventing Split-Brain Financial State**: Claims processing involves financial commitments and deductible settlements. In an Active-Active multi-master setup during a network partition, two adjusters in different regions could approve conflicting settlement payouts for the same claim.
- **Warm Standby Economics**: Active-Passive with Aurora Global Database delivers sub-second replication lag (<1s) and allows us to hit our RTO < 1 hour and RPO < 15 minutes requirements at less than half the infrastructure cost of Active-Active."

### Q2: What is the exact step-by-step failover procedure if an entire AWS region (us-east-1) goes offline?
**Answer**:
"Our disaster recovery runbook executes in four automated and verified stages:
1. **Detection (T+0 to T+5 min)**: Route 53 synthetic health checks fail three consecutive times across multiple global vantage points. PagerDuty immediately pages our Incident Commander.
2. **Database Promotion (T+5 to T+15 min)**: Our automated failover script runs `aws rds failover-global-cluster`, promoting the Aurora read replica in Oregon (`us-west-2`) to an independent Primary read-write cluster. Data loss is under 3 seconds of replication lag.
3. **Compute Scale-Out (T+15 to T+30 min)**: ECS Fargate auto-scaling policies in Oregon execute step-scaling, scaling out our pilot light containers (2 tasks per service) up to full production capacity (20 tasks per service).
4. **Traffic Cutover (T+30 to T+45 min)**: Route 53 shifts global DNS traffic to the Oregon Application Load Balancer. CloudFront edge caches seamlessly forward requests to Oregon.
Total recovery elapsed time: **45 minutes, comfortably under our 1-hour RTO SLA**."

### Q3: How do we prevent unauthorized internal access to databases when multiple microservices reside in the same VPC?
**Answer**:
"We implement a **Zero-Trust Network and Security Architecture**:
1. **Security Group Chaining**: The Aurora PostgreSQL security group allows inbound connections on port 5432 strictly from the security groups of the specific application services that need access (e.g., `sg-claims-service`). Direct database access from jump boxes or other subnets is blocked at the hypervisor level.
2. **Schema-Level Database Credentials**: Each service connects using a distinct PostgreSQL user with permissions restricted exclusively to its own schema (e.g., the `claims_user` cannot read or write to `workshops` tables).
3. **Automated Credential Rotation**: Database passwords and API keys are stored in AWS Secrets Manager and rotated automatically every 30 days without application downtime.
4. **mTLS Service-to-Service Encryption**: All internal microservice-to-microservice traffic is encrypted in transit using mutual TLS (mTLS) with short-lived X.509 certificates managed by AWS Certificate Manager (ACM)."

### Q4: How does the deployment architecture satisfy the requirement that confidential customer data is encrypted at rest and in compliance with insurance regulations?
**Answer**:
"We enforce **Multi-Layer Envelope Encryption using AWS Key Management Service (KMS)**:
- **Storage Volumes**: All Aurora database storage volumes, Redis caches, and EBS/EFS volumes are encrypted at rest using customer-managed AWS KMS keys (AES-256).
- **Document Storage**: Amazon S3 media buckets use Server-Side Encryption with KMS Customer Managed Keys (SSE-KMS). Furthermore, buckets enforce **S3 Object Lock in Compliance Mode**, preventing any document, photo, or invoice from being deleted or modified by anyone (including root accounts) for seven years.
- **Field-Level PII Encryption**: Highly sensitive customer fields (Social Security Numbers, bank account routing numbers) undergo application-level envelope encryption using AES-GCM before ever being written to SQL query buffers, ensuring that even database administrators cannot view plaintext sensitive data."
