# Q&A Discussion Script: Category 8 - Availability

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Multi-AZ vs Multi-Region?

**Spoken Answer:**
"Multi-AZ and Multi-Region address fundamentally different disaster scenarios at vastly different costs and operational complexities:

**Multi-AZ (High Availability within a Single Region)**:
- Deployed across 3 distinct, physically isolated Availability Zones (data centers) in a single AWS region (e.g., `us-east-1a`, `us-east-1b`, `us-east-1c`).
- Connected by ultra-low-latency dedicated fiber (<1-2 milliseconds).
- Enables **synchronous replication** for databases and automated, zero-data-loss failover in under 30 seconds if a physical data center loses power or catches fire.
- Multi-AZ is our day-to-day High Availability (HA) foundation.

**Multi-Region (Disaster Recovery across Geographic Regions)**:
- Deployed across two geographically distant AWS regions: Primary Region A in Northern Virginia (`us-east-1`) and DR Region B in Oregon (`us-west-2`), separated by 2,500 miles.
- Used for catastrophic disasters: total regional AWS fiber severance, nationwide power grid failure, or regional regulatory seizure.
- Uses **asynchronous replication** due to the speed of light (transcontinental network latency is ~60-80ms)."

**Technical Bullets:**
- Multi-AZ: Latency <2ms, Synchronous quorum, Automatic failover <30s, Protects against single data center failure.
- Multi-Region: Latency 60-80ms, Asynchronous replication, Protects against catastrophic regional disaster.
- Cost Ratio: Multi-AZ adds ~20-30% overhead; Active-Active Multi-Region doubles infrastructure and operational costs.

---

### Q2: HA vs DR?

**Spoken Answer:**
"High Availability (HA) and Disaster Recovery (DR) represent two distinct lines of defense in enterprise resiliency:

**High Availability (HA) - Keeping the System Running Continuously**:
- Focuses on absorbing localized, routine hardware and infrastructure failures without human intervention and without service interruption.
- Examples in our platform: An ECS Fargate task crashing and auto-restarting; an Aurora database node failing and failing over to a standby replica in AZ-2; an ALB routing traffic around a degraded zone.
- Target: 99.99% uptime during standard operations.

**Disaster Recovery (DR) - Restoring the System after Catastrophic Loss**:
- Focuses on business continuity when an entire production environment or geographical region is completely destroyed or incapacitated.
- Involves disaster runbooks, DNS traffic failover (Route 53), database replication promotion, and restoring operations within agreed business tolerances (RTO and RPO)."

**Technical Bullets:**
- HA: Operational resilience, automated, zero-downtime, localized failure domain (AZ level).
- DR: Business continuity, catastrophic event, cross-region failover, governed by RTO/RPO.

---

### Q3: RTO vs RPO?

**Spoken Answer:**
"RTO and RPO are the two core business metrics that define an enterprise disaster recovery contract:

**RTO (Recovery Time Objective) - How long can you afford to be down?**:
- The maximum acceptable duration of time between the declared disaster and the complete restoration of business service.
- It answers: *'How fast can we bring the platform back online?'*
- Our assignment and architecture target an **RTO of < 1 hour**.

**RPO (Recovery Point Objective) - How much data can you afford to lose?**:
- The maximum acceptable age of data that can be lost when a disaster strikes.
- It answers: *'How many minutes of committed transactions are we willing to re-create or lose?'*
- Our target is an **RPO of < 15 minutes**."

**Technical Bullets:**
- RTO: Time to restore service (Target: < 1 hour / 60 minutes).
- RPO: Data loss window measured in time (Target: < 15 minutes).
- Business Driver: Regulatory mandates from State Insurance Commissioners requiring continuous policyholder service access.

---

### Q4: Your RTO is <1 hour. What does that mean?

**Spoken Answer:**
"An RTO of less than 1 hour means that if an asteroid physically obliterates the entire Northern Virginia AWS region (`us-east-1`) at 2:00 PM, **our eClaims platform will be fully operational, accepting customer claims, and processing repair orders in our secondary Oregon region (`us-west-2`) before 3:00 PM**.

During that 60-minute window, our automated and engineer-guided DR sequence executes:
1. Minute 0-10: Automated Route 53 health check alarms fire; Incident Commander declares regional failover.
2. Minute 10-25: Aurora Global Database in `us-west-2` is promoted to Primary read-write cluster (takes <5 minutes).
3. Minute 25-45: ECS Fargate container fleets in Oregon scale out from Pilot Light baseline (2 tasks) to full production capacity (40+ tasks).
4. Minute 45-55: Route 53 DNS records switch 100% of public traffic to Oregon ALB; synthetic health checks verify endpoints.
5. Minute 55: The platform is open for business."

**Technical Bullets:**
- Commitment: Complete service restoration in secondary region within 60 minutes of disaster declaration.
- Automated Orchestration: Terraform / AWS Systems Manager automation scripts execute region failover.
- Validation: Verified by biannual automated DR simulation drills.

---

### Q5: Your RPO is <15 minutes. What does that mean?

**Spoken Answer:**
"An RPO of less than 15 minutes means that in the worst imaginable sudden regional catastrophe where primary data centers evaporate instantly, **the absolute maximum data loss that YCompany can suffer is 15 minutes of recent transactions**.

In practice, because we use **Amazon Aurora Global Database**, cross-region physical storage replication latency is typically **under 1 second**. Under normal conditions, zero data is lost. 
The 15-minute RPO is our contractual upper bound that accounts for extreme disaster scenarios: for example, if the cross-country network link was experiencing packet congestion right before the primary region failed, any transactions committed in the last few seconds that had not yet crossed the country to Oregon would be lost. 

Customer records filed 16 minutes prior to the disaster are 100% guaranteed to be safe and available in the DR region."

**Technical Bullets:**
- Contractual Maximum: Zero data older than 15 minutes can ever be lost.
- Real-World Performance: Aurora Global Database physical replication lag is typically <1 second.
- S3 Replication: S3 Cross-Region Replication (CRR) backed by RTC (Replication Time Control) guarantees 99.99% of objects replicate within 15 minutes.

---

### Q6: Why active-passive rather than active-active?

**Spoken Answer:**
"We chose **Active-Passive (Warm Standby / Pilot Light in Region B)** over Active-Active for three reasons: **data consistency physics, operational complexity, and cloud economics**.

1. The CAP Theorem & Speed of Light: In an Active-Active setup across Virginia and Oregon, if a customer updates their claim in Virginia while a claims adjuster updates the estimate in Oregon, you have a multi-master distributed write conflict. Resolving concurrent writes across 2,500 miles requires distributed consensus (like Spanner or CockroachDB) which adds 70ms of synchronous latency to every write, violating our sub-second response times.
2. Cloud Infrastructure Cost: A fully active multi-region deployment requires running duplicate, high-capacity container fleets, duplicate Kafka clusters, and duplicate database writer tiers 24x7x365, more than doubling our monthly cloud bill.
3. Operational Sanity: Active-Passive gives us a single, authoritative Primary writer node during normal operations, eliminating distributed split-brain scenarios while comfortably meeting our RTO < 1 hour and RPO < 15 min SLAs."

**Technical Bullets:**
- Physics: Speed of light across continental US = ~60ms RTT; synchronous multi-region writes degrade user latency.
- Cost Ratio: Active-Passive costs ~25-30% of Primary region cost; Active-Active costs 200-250%.
- Data Integrity: Single primary writer avoids multi-master conflict resolution (Last-Write-Wins data loss).

---

### Q7: What happens when us-east-1 fails completely?

**Spoken Answer:**
"When `us-east-1` experiences a total regional blackout, our disaster recovery plan transitions our warm standby pilot light in `us-west-2` into full production:

1. Automated Detection: Amazon Route 53 Anycast health checks continuously probe our public endpoints in `us-east-1`. When endpoints fail from 3 independent global probing locations for 3 consecutive intervals, CloudWatch raises a P1 Critical Alarm.
2. Disaster Declaration: The Incident Commander triggers the AWS Systems Manager automated failover runbook.
3. Database Promotion: Amazon Aurora Global Database in `us-west-2` is detached from replication and promoted to an independent standalone read-write cluster (completes in under 2 minutes).
4. Container Fleet Ramp-Up: ECS Fargate in `us-west-2`, which runs in a 'pilot light' state (2 tasks per service), executes an auto-scaling step-scaling command to scale out to full production capacity (20-40 tasks per service).
5. Traffic Shift: Route 53 health check failover automatically routes 100% of global DNS queries to the `us-west-2` Application Load Balancer.
6. Public Resumption: Traffic enters Oregon, where microservices connect to the newly promoted local Aurora database and local Redis caches."

**Technical Bullets:**
- Trigger: Route 53 synthetic health check failure (3 consecutive probes, 15s interval).
- DB Action: `rds:FailoverGlobalCluster` promotes secondary cluster in <2 minutes.
- Compute Action: ECS Fargate scales from pilot light (16 tasks total) to full capacity (120+ tasks).
- Total Execution Time: ~35-45 minutes (well within our 60-minute RTO).

---

### Q8: How does Route 53 detect the failure?

**Spoken Answer:**
"Route 53 detects regional failure through **Global Distributed Synthetic Health Checks**:

1. Multi-Region Probers: AWS Route 53 operates external health checkers across multiple geographical locations worldwide (e.g., London, Tokyo, California, Ireland).
2. Synthetic Endpoint Probing: Every 10 seconds, these probers send HTTPS GET requests to our dedicated health endpoint: `https://api.eclaims.ycompany.com/actuator/health`.
3. Deep Health Verification: This `/actuator/health` endpoint is not a dumb ping; it verifies that the container can successfully query the local database and ping the local cache.
4. Failure Threshold: If more than 18% of global probers report that the endpoint returned non-200 status codes or timed out for 3 consecutive evaluation cycles (30 seconds total), Route 53 marks the primary region as **UNHEALTHY**.
5. DNS Failover: Route 53's failover routing policy immediately stops resolving DNS queries to the `us-east-1` ALB and begins returning the IP addresses of the `us-west-2` ALB with a 60-second DNS TTL."

**Technical Bullets:**
- Protocol: HTTPS synthetic probe every 10s (fast interval).
- Quorum: Requires consensus from health checkers across >= 3 global AWS regions.
- DNS TTL: Configured with a 60-second TTL to ensure global ISP DNS caches purge quickly.

---

### Q9: What happens to Aurora?

**Spoken Answer:**
"In our architecture, we deploy **Amazon Aurora Global Database**:

During normal operations:
- The Aurora cluster in `us-east-1` is the **Primary Cluster**, handling all write transactions and replicating locally across 3 Availability Zones.
- The Aurora cluster in `us-west-2` is a **Secondary Read Cluster**. Aurora's dedicated storage hardware layer continuously replicates storage blocks across the AWS dedicated fiber network with typical replication latency of **less than 1 second**.

When `us-east-1` fails:
1. The secondary cluster in `us-west-2` is promoted to a standalone primary cluster using the AWS CLI or RDS Console:
   `aws rds failover-global-cluster --global-cluster-identifier eclaims-global-db --target-db-cluster-identifier eclaims-db-oregon`.
2. Aurora breaks the replication link and enables write operations on the Oregon cluster in **under 2 minutes**.
3. Microservices in `us-west-2` update their JDBC connection pools to point to the local Oregon cluster writer endpoint.
4. Because replication is continuous, virtually zero data loss occurs."

**Technical Bullets:**
- Engine: Aurora Global Database (storage-level physical replication).
- Replication Lag: Typically < 1 second under normal conditions.
- Promotion Time: < 2 minutes to convert secondary cluster to read-write primary.

---

### Q10: What happens to S3?

**Spoken Answer:**
"Our Amazon S3 architecture utilizes **S3 Cross-Region Replication (S3 CRR) backed by S3 Replication Time Control (S3 RTC)**:

1. Automatic Cross-Country Replication:
Every accident photo, PDF estimate, and police report uploaded to our primary bucket (`eclaims-docs-us-east-1`) is asynchronously replicated across the country to our standby bucket (`eclaims-docs-us-west-2`).
2. S3 Replication Time Control (RTC) SLA:
We enable S3 RTC, which provides an AWS enterprise SLA guaranteeing that **99.99% of all new uploaded objects replicate to the secondary region within 15 minutes**, directly fulfilling our RPO < 15 minutes requirement.
3. Object Lock Parity:
The destination bucket in Oregon is also configured with **S3 Object Lock Compliance Mode**. The 7-year WORM compliance retention dates and legal holds are replicated identically, ensuring that regulatory evidence remains tamper-proof even in the DR region."

**Technical Bullets:**
- Feature: S3 Cross-Region Replication (CRR) with S3 RTC enabled.
- SLA: AWS guarantees 99.99% of objects replicate within 15 minutes.
- Encryption: Re-encrypted at destination using Oregon AWS KMS Customer Master Key.

---

### Q11: What happens to Kafka?

**Spoken Answer:**
"In our primary region, Amazon MSK operates a 6-broker Kafka cluster spread across 3 AZs. For cross-region disaster recovery, we maintain an independent Amazon MSK cluster in `us-west-2` synchronized via **Apache Kafka MirrorMaker 2 (MM2)**.

During normal operations:
- MirrorMaker 2 continuously consumes messages from all core topics in Virginia (`claim-events`, `repair-events`) and replicates them to corresponding topics in Oregon.
- MM2 preserves message payloads, record headers, and timestamps.

When `us-east-1` fails:
1. Microservices launched in Oregon configure their Kafka bootstrap servers to point to the local Oregon MSK cluster.
2. As customers submit new claims and adjusters approve repairs in Oregon, new events flow seamlessly into the Oregon Kafka topics.
3. Note on Offsets: Because cross-cluster offset numbers can drift, MirrorMaker 2 continuously emits offset mapping topics (`mm2-offset-syncs`), allowing consumers in the DR region to translate consumer group offsets accurately."

**Technical Bullets:**
- Tooling: Apache Kafka MirrorMaker 2 (run via Amazon MSK Connect).
- Topics: Bi-directional replication enabled or active-passive topic mirroring.
- Offset Translation: MM2 automatically translates consumer group committed offsets.

---

### Q12: How does MirrorMaker 2 replicate Kafka?

**Spoken Answer:**
"MirrorMaker 2 (MM2) is built on top of the Kafka Connect framework and provides enterprise multi-cluster synchronization:

1. Architecture: MM2 runs as a set of managed connectors inside Amazon MSK Connect.
2. MirrorSourceConnector: Acts as a standard consumer in the source cluster (`us-east-1`), reads messages from source topics, and acts as a producer to write them into the destination cluster (`us-west-2`).
3. Topic Naming: MM2 can replicate topics directly or use source prefixes (e.g., `us-east-1.claim-events`) to prevent cyclic replication loops in multi-region topologies.
4. Checkpoint & Offset Sync: MM2 runs a `MirrorCheckpointConnector` that tracks consumer group committed offsets between both clusters. It periodically emits offset sync records that translate source topic offsets into equivalent destination topic offsets, allowing consumer applications to resume reading without processing massive backlogs of duplicate messages."

**Technical Bullets:**
- Framework: Kafka Connect distributed runtime.
- Components: `MirrorSourceConnector` (data), `MirrorCheckpointConnector` (consumer offsets), `MirrorHeartbeatConnector` (liveness).
- Deduplication: Employs record timestamp alignment to map offsets.

---

### Q13: What if replication lag exceeds 15 minutes?

**Spoken Answer:**
"If network congestion or massive traffic spikes cause cross-region replication lag to exceed 14 minutes, it threatens our contractual RPO SLA. We have automated monitoring and alerting to handle this:

1. Early Warning Alarms: CloudWatch alarms monitor `AuroraGlobalDBReplicationLag` and S3 `ReplicationLatency`. If replication lag crosses **5 minutes**, a P2 High ticket is cut. If it crosses **10 minutes**, an automated P1 Incident is raised.
2. Remediation During Degradation:
   - AWS Network Diagnostics: We verify AWS backbone health and Direct Connect / VPC peering saturation.
   - Resource Throttling: If batch analytics or bulk reporting ETL jobs are consuming replication bandwidth, our data pipelines automatically pause bulk replication streams to prioritize transactional `claim-events` and Aurora write streams.
3. Worst-Case DR Decision: If the primary region fails while replication lag is at 20 minutes, the Incident Commander has the legal authority to execute failover. The business accepts the 20-minute gap, and once the primary region recovers, our data engineering team runs historical outbox reconciliation scripts to identify and recover missing transactions."

**Technical Bullets:**
- Alarms: CloudWatch alert at lag > 5 min (Warning); alert at lag > 10 min (Critical).
- Traffic Prioritization: QOS prioritization of transactional WAL streams over bulk ETL data.
- Reconciliation Runbook: Automated reconciliation script compares database outbox tables against recovered region storage.

---

### Q14: How frequently do you test DR?

**Spoken Answer:**
"In enterprise insurance, an untested disaster recovery plan is merely a fantasy. We conduct **rigorous, scheduled DR testing twice a year (Biannually)**:

Our DR testing program consists of three levels:
1. Monthly Automated Chaos Engineering (GameDays): Using AWS Fault Injection Simulator (FIS), we randomly inject single-AZ outages, kill primary Aurora database writer nodes, and sever network connections to test our Multi-AZ HA self-healing in our staging environment.
2. Biannual Full-Scale Regional Failover Drills: Every six months, our operations, security, and engineering teams execute a complete simulated failover from `us-east-1` to `us-west-2` during an off-peak maintenance window.
3. Verification Audit: We measure exact elapsed time against our RTO (<1 hour target) and audit test data against our RPO (<15 min target). The findings, timing logs, and remediation tickets are formally signed off by the Chief Information Security Officer and submitted to state insurance regulators."

**Technical Bullets:**
- Cadence: Biannual full regional failover; monthly automated AZ chaos drills.
- Tooling: AWS Fault Injection Simulator (FIS) and Chaos Mesh.
- Governance: Formal post-mortem report and compliance sign-off for state insurance audit.

---

### Q15: Multi-AZ database failure vs entire AWS region failure - explain both recovery paths.

**Spoken Answer:**
"Let me clearly contrast the two failure scenarios and their exact recovery paths:

**Scenario A: Multi-AZ Database Node Failure (Routine Local Event)**:
- Scope: The physical host running the primary Aurora writer node in `us-east-1a` suffers hardware failure or local power loss.
- Detection: Aurora storage quorum detects loss of heartbeat in under 5 seconds.
- Recovery Action: **100% Automated, Zero Human Intervention.** Aurora automatically promotes an existing read replica in `us-east-1b` to become the new primary writer.
- Client Impact: Database failover completes in **under 30 seconds**. The Aurora cluster endpoint automatically updates its CNAME record. Spring Boot's HikariCP connection pool drops stale connections, reconnects to the new writer, and continues processing. 
Zero data loss (RPO = 0); downtime is under 30 seconds.

**Scenario B: Entire AWS Region Failure (Catastrophic Regional Disaster)**:
- Scope: Complete loss of `us-east-1` (transatlantic fiber cuts, regional power grid collapse).
- Detection: Route 53 global health checks mark all primary endpoints unhealthy after 30 seconds.
- Recovery Action: **Human-Approved Automated Runbook Execution.** 
  1. Incident Commander approves region failover.
  2. Systems Manager runbook executes `aws rds failover-global-cluster`, detaching Oregon cluster in <2 minutes.
  3. ECS Fargate in Oregon scales out from 16 to 120 tasks (10 minutes).
  4. Route 53 shifts 100% of global DNS traffic to Oregon ALB.
- Client Impact: Total recovery completes in **under 45 minutes** (RTO < 1h); data loss is bounded to **under 1 second** of asynchronous replication lag (RPO < 15m)."

**Technical Bullets:**
- Multi-AZ: Local AZ failure -> Aurora automated promotion in <30s -> RPO = 0, RTO < 30s -> Zero human intervention.
- Multi-Region: Regional disaster -> Global Database promotion + Fargate scale-out + Route 53 DNS shift -> RTO < 45m, RPO < 1s -> Governed by Incident Commander approval.
