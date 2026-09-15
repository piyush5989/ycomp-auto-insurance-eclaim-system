# Q&A Discussion Script: Category 3 - Camunda / Workflow

This document is designed as a direct, spoken meeting script that can be referenced while presenting to an architecture review board, technical steering committee, or panel.

Every question has:
- Spoken Answer: Exactly what to say in the meeting (crisp, professional, authoritative).
- Technical Bullets: Concrete technical facts, trade-offs, and metrics.
- Follow-up Defense: The counter-argument to keep in mind if challenged.

---

### Q1: Why Camunda?

**Spoken Answer:**
"We selected Camunda 8 because insurance claims processing is fundamentally a **long-running, human-in-the-loop business orchestration process** that spans days or weeks, rather than a quick milliseconds-long technical pipeline.

An auto claim involves complex parallel forks: while the vehicle is being towed and inspected by a surveyor, the customer is selecting a rental car, and the claims adjuster is reviewing policy limits. If a surveyor does not respond within 48 hours, statutory insurance regulations require business escalation.

Camunda gives us three decisive enterprise capabilities:
1. ISO-Standard BPMN 2.0 Visual Workflows: Non-technical stakeholders (claims directors and compliance auditors) can inspect, audit, and understand the workflow diagrams directly.
2. Cloud-Native Zeebe Engine: Camunda 8 runs on the Zeebe distributed engine, which uses event-sourcing and Raft consensus instead of heavy relational database locks, scaling effortlessly to millions of concurrent process instances.
3. Native SLA and Escalation Timers: Non-interrupting boundary timer events allow us to trigger automated escalations without writing fragile cron jobs or custom database polling scripts."

**Technical Bullets:**
- Standard: BPMN 2.0 visual specification (eliminates gap between business analysts and code).
- Engine: Zeebe distributed workflow engine (Raft-based partition brokers, horizontal scalability).
- SLA Management: Native BPMN timer events for 24h/48h regulatory SLA tracking.
- Visibility: Camunda Operate provides real-time visual heatmaps of claim bottlenecks.

---

### Q2: Why can't Claims Service maintain the claim state itself?

**Spoken Answer:**
"The Claims Service *does* maintain the core lifecycle state of the claim aggregate. But maintaining state is very different from orchestrating a multi-week business workflow.

If you try to maintain the entire workflow inside the Claims Service, you end up writing what we call **hardcoded spaghetti logic**:
- You write cron jobs that poll the database every five minutes searching for claims where `surveyor_assigned_at < NOW() - INTERVAL '48 HOURS'`.
- You write nested `if/else` state transition trees across thousands of lines of code.
- When the business wants to introduce a new rule - like 'If repair estimate > $5,000, require a senior supervisor approval before workshop authorization' - you have to modify core Java code, run regression tests, and execute a full production deployment.

By delegating process orchestration to Camunda, the Claims Service stays focused on its core responsibility: validating business invariants and persisting claim records. Camunda manages the sequence, timers, and routing rules."

**Technical Bullets:**
- Anti-Pattern: Database polling cron jobs (`SELECT * WHERE status = 'ASSIGNED' AND created_at < ...`) degrade DB performance.
- Inversion of Control: Camunda orchestrates *when* tasks happen; microservices execute *how* tasks happen.
- Maintainability: Business rule changes (e.g., adding an approval threshold) happen in BPMN without recompiling Java microservices.

---

### Q3: What belongs in Camunda versus Claims Service?

**Spoken Answer:**
"We maintain a clean and rigid separation between Orchestration and Domain Logic:

What belongs in **Camunda**:
- Process Sequence and Flow: What step comes after First Notice of Loss (FNOL).
- SLA Timers and Escalations: 'Wait 48 hours for surveyor assessment; if no response, escalate to Case Manager.'
- Branching and Decision Gateways: 'If damage > $10,000, fork to Special Investigation Unit (SIU) for fraud review; else route to fast-track adjuster.'
- Human Task Lifecycles: Managing task assignment tokens for adjusters and surveyors.

What belongs in **Claims Service**:
- Authoritative Domain Entity State: Persisting the `Claim` record, financial ledger, and history in PostgreSQL.
- Business Validation Invariants: Checking if the policy was active on the date of loss; verifying deductible calculations.
- Data Ownership: Storing customer notes, police report metadata, and audit records.
- API Contracts: Exposing REST endpoints to frontend portals and mobile apps."

**Technical Bullets:**
- Camunda = Orchestration (The Conductor): Knows the sheet music, sequence, and timing.
- Claims Service = Domain Execution (The Musician): Knows how to play the instrument and store the notes.

---

### Q4: Who owns the authoritative claim status?

**Spoken Answer:**
"The **Claims Service is the single authoritative source of truth for claim status**, NOT Camunda.

Camunda tracks the *process token* (e.g., token is currently waiting at user task `ReviewAssessment`), but the official business state of the claim (e.g., `SUBMITTED`, `ASSIGNED`, `ASSESSMENT_COMPLETED`, `APPROVED`, `SETTLED`) is stored in the `claims.claims` table in Amazon Aurora PostgreSQL.

If Camunda's database were wiped out entirely, our business records remain intact. The Claims Service exposes the status to the customer portal, enforces database constraints, and publishes status change events to Kafka. Camunda simply commands the Claims Service to transition status when workflow milestones are reached."

**Technical Bullets:**
- Source of Truth: `claims.claims.status` in PostgreSQL.
- Camunda Role: Holds the workflow instance pointer (`processInstanceKey`), not the core financial/legal record.
- Disaster Decoupling: If workflow engine is lost, claim records and legal audits remain 100% intact.

---

### Q5: How do BPMN workflows interact with microservices?

**Spoken Answer:**
"BPMN workflows interact with our microservices using **Asynchronous Job Workers (External Task Pattern)** over gRPC, rather than making blocking HTTP calls from the workflow engine.

Here is the exact interaction loop:
1. Camunda advances a process instance to a Service Task (e.g., `type: assign-surveyor`).
2. Instead of Camunda making an outbound HTTP POST to the Workflow Service, our `Workflow Service` runs a long-polling **Zeebe Job Worker** in Java.
3. The job worker pulls the task over a multiplexed, bi-directional gRPC stream.
4. The worker executes the local business logic (queries available surveyors by zip code and assigns surveyor Sarah).
5. The worker sends back a gRPC completion command (`client.newCompleteCommand(job.getKey()).send()`) along with process variables.
6. Camunda receives the completion and advances the process token to the next BPMN step."

**Technical Bullets:**
- Pattern: External Worker / Job Worker pattern (pull-based via gRPC).
- Protocol: gRPC / HTTP/2 bi-directional streaming (low latency, high throughput).
- Advantage: No firewall pinholes; microservices pull work from Camunda; Camunda never needs to know the IP addresses of microservice containers.

---

### Q6: What is a Camunda job worker?

**Spoken Answer:**
"A Camunda job worker is a lightweight client application - in our case, a Spring Boot bean using the `spring-zeebe-starter` library - that continuously polls the Camunda Zeebe broker for available tasks of a specific job type.

In our code, a developer simply annotates a Java method:
`@JobWorker(type = 'calculate-deductible', autoComplete = true)`
`public Map<String, Object> calculateDeductible(final ActivatedJob job) { ... }`

The worker receives the job payload containing process variables (like `policyNumber` and `vehicleType`), runs domain calculations, updates the database, and returns the result. If the container running the worker crashes, Camunda's lock expires and the job is automatically handed to another healthy worker container."

**Technical Bullets:**
- Library: `io.camunda:spring-zeebe-starter`.
- Annotation: `@JobWorker(type = "...", maxJobsActive = 32)`.
- Threading: Integrated with Java 21 Virtual Threads for non-blocking task execution.

---

### Q7: What happens if a job worker fails?

**Spoken Answer:**
"When a job worker fails - whether due to a JVM crash, an unhandled exception, or a network timeout - Camunda's distributed architecture guarantees that the workflow task is never lost.

Here is the exact failure behavior:
1. When a worker activates a job, it acquires a **time-bound lock** (default: 300 seconds).
2. If the worker container crashes physically, it stops sending heartbeats. Once the lock timeout elapses, the Zeebe broker releases the lock and marks the job as available again. Another healthy worker instance immediately picks it up.
3. If the worker encounters an application exception (e.g., database temporarily unreachable), the worker code catches it and calls `client.newFailCommand(job.getKey()).retries(job.getRetries() - 1).send()`.
4. If retries reach zero, Camunda raises an **Incident** in the workflow engine. The process instance pauses at that exact step without crashing, and an alert appears on the Camunda Operate dashboard for operations to inspect."

**Technical Bullets:**
- Locking: Zeebe job lock duration (e.g., 5 minutes); auto-released on worker death.
- Failure Command: `FailJobCommand` decrements retries and attaches error backtrace.
- Incident Management: Zero retries creates an Incident; process halts safely until manual or automated resolution.

---

### Q8: How do retries work?

**Spoken Answer:**
"Retries in Camunda are configured at two levels: in the BPMN model definition and in the worker code.

In the BPMN definition:
We define the retry count (e.g., `retries = 3`) and a retry backoff duration: `backoff = PT10S, PT1M, PT5M` (10 seconds, 1 minute, 5 minutes).

When an error occurs:
- Attempt 1 fails: Camunda waits 10 seconds before offering the job to workers again.
- Attempt 2 fails: Camunda waits 1 minute.
- Attempt 3 fails: Camunda waits 5 minutes.
- If all 3 attempts fail, an Incident is raised.

Once the underlying issue is resolved (for example, a downstream workshop API service is brought back online), an administrator can click 'Retry Incident' in the Camunda Operate UI (or invoke the Zeebe REST/gRPC API), and the workflow resumes from that exact failed task without needing to restart the claim from scratch."

**Technical Bullets:**
- BPMN Config: `zeebe:taskDefinition retries="3"` and ISO 8601 duration backoff.
- Granularity: Retries apply strictly to the individual failed task, not the whole workflow.
- Operator Recovery: Single-click batch incident retry via Camunda Operate.

---

### Q9: How do you implement escalation? (Example: Surveyor hasn't completed an assessment within 48 hours. How does your architecture handle it?)

**Spoken Answer:**
"Let me walk you through how we implement the 48-hour surveyor SLA escalation.

In our BPMN process model, the `Perform Damage Assessment` task has a **Non-Interrupting Boundary Timer Event** attached to it, configured with the ISO 8601 duration `PT48H` (48 hours).

Here is what happens in production:
1. When a claim enters the assessment stage, Camunda starts the 48-hour countdown timer.
2. If the surveyor inspects the car and submits their report at hour 24, the assessment task completes. The timer is canceled automatically, and the process advances to adjuster review.
3. But suppose 48 hours pass and the surveyor has not submitted the assessment. The timer fires automatically.
4. Because it is non-interrupting, the original surveyor can still submit their report, but a parallel escalation branch is spawned immediately.
5. The escalation branch triggers an automated job worker that:
   - Publishes an `EscalationTriggered` event to Kafka.
   - Dispatches a high-priority push notification and email to the Regional Case Manager's portal.
   - Re-evaluates surveyor availability in that zip code and suggests alternative field staff.
   - Logs an SLA breach warning in our audit and reporting matrix.
6. The Case Manager can open the portal, review the stale claim, and click 'Delegate' to reassign the claim to another surveyor with a single click."

**Technical Bullets:**
- BPMN Element: Boundary Non-Interrupting Timer Event (`cancelActivity = false`).
- Duration: `PT48H` (or dynamically calculated from business calendar excluding weekends).
- Action: Emits Kafka event -> Notification Service alerts Case Manager -> UI enables delegation.

---

### Q10: How do you change a workflow when thousands of claims are already using the old workflow?

**Spoken Answer:**
"This is a common challenge in insurance, where claims take weeks to settle. You cannot simply wipe out in-flight workflows when deploying a new process.

We manage this using **Process Versioning and Instance Migration**:

1. Default Behavior (Safe Isolation): When we deploy an updated BPMN file (e.g., adding an EV battery inspection step), Camunda automatically assigns it a new version number (e.g., Version 2). 
All new claims submitted from that moment forward instantiate Version 2. 
Crucially, all existing 50,000 in-flight claims continue executing on **Version 1** until they naturally reach settlement and close. Both versions run simultaneously on the same cluster without interference.

2. Process Instance Migration (For Critical Compliance Changes): If a federal insurance regulation requires an immediate change that *must* apply to existing claims, we use Camunda's **Process Instance Migration API**. We define a migration mapping plan (e.g., Map Task `V1_Review` to `V2_EnhancedReview`) and execute the migration in batches."

**Technical Bullets:**
- Versioning Rule: New version deployment does NOT mutate active running instances.
- Dual-Execution: Version 1 and Version 2 execute concurrently in production.
- Migration API: Camunda `MigrateProcessInstance` API maps tokens from V1 activity IDs to V2 activity IDs.

---

### Q11: How do you version BPMN definitions?

**Spoken Answer:**
"We version BPMN definitions through a combination of Git source control, CI/CD automated deployment, and Camunda's internal semantic versioning.

1. Source Control: BPMN XML files live in our Git repository alongside the service code (e.g., `/src/main/resources/bpmn/auto-claim-lifecycle.bpmn`).
2. Deployment Pipeline: During our GitHub Actions CI/CD deployment, our deployment script hashes the BPMN file. If changes exist, it invokes the Zeebe CLI/REST API: `zbctl deploy auto-claim-lifecycle.bpmn`.
3. Engine Versioning: Camunda assigns an auto-incrementing integer version (1, 2, 3...) keyed on the `bpmnProcessId` (e.g., `process-auto-claim`). The broker maintains the complete definition history in its Raft log."

**Technical Bullets:**
- Identifiers: `bpmnProcessId` remains constant (e.g., `process_claim_v1`); Camunda increments `version: 1 -> 2 -> 3`.
- CI/CD Gate: Automated BPMN linting using `bpmn-lint` in CI pipeline checks for unconnected nodes and syntax errors.

---

### Q12: Can different claims simultaneously run different workflow versions?

**Spoken Answer:**
"Yes, absolutely. That is a native core capability of Camunda.

In our production system, if Claim #101 was submitted in May under Workflow Version 1, its process token remains pinned to Version 1 throughout its lifecycle. When Claim #502 is submitted in June after a deployment, it runs under Workflow Version 2.

Our Spring Boot job workers are designed to be backward compatible. When a worker activates a job, it inspects the process variables. If a variable introduced in Version 2 is absent, the worker falls back gracefully to default logic. This allows YCompany to roll out gradual workflow enhancements without risky big-bang data migrations."

**Technical Bullets:**
- Architecture: Multi-version concurrent execution.
- Token Isolation: Process instance is permanently bound to the deployment key it started with.
- Worker Design: Defensive coding handles optional process variables across version boundaries.

---

### Q13: What happens if Camunda becomes unavailable?

**Spoken Answer:**
"Because our microservices communicate with Camunda asynchronously via job workers and Kafka, **temporary Camunda downtime does NOT bring down our customer-facing portals or stop claim submissions.**

Here is the exact resilience path:
1. FNOL Ingestion Continues: When a customer submits a claim, the Claims Service writes the record to PostgreSQL and emits `ClaimCreated` to Kafka.
2. Outbox Buffering: The message sits safely buffered in Kafka.
3. Workflow Service Polling: A workflow bridge consumer attempts to start the process instance in Camunda. If Camunda is unreachable, the consumer retries with exponential backoff.
4. Active Workers Pause: Running job workers simply wait for the gRPC stream to reconnect; no application memory is leaked.
5. Recovery: Once Camunda recovers, the workflow bridge consumer drains the Kafka queue, instantiates the pending workflow instances, and normal processing resumes. 
The customer's primary experience (submitting a claim and receiving their Claim ID) is completely unaffected."

**Technical Bullets:**
- Decoupling Layer: Kafka buffers incoming workflow initiation triggers during Camunda outages.
- Worker Behavior: Zeebe Java client handles automatic reconnects with jittered backoff.
- Zero Loss: Outbox and Kafka ensure zero workflow trigger events are lost.

---

### Q14: Why Camunda instead of AWS Step Functions?

**Spoken Answer:**
"While AWS Step Functions is a capable cloud-native orchestrator, it has significant limitations for complex insurance business workflows:

1. Business Readability and BPMN: Step Functions uses JSON/YAML-based Amazon States Language (ASL). Business analysts and compliance auditors cannot read raw ASL JSON. Camunda uses standard BPMN 2.0, providing visual flowcharts that business executives can inspect directly.
2. Human Task Management: Step Functions requires custom API plumbing and DynamoDB tokens for human approval steps. Camunda has built-in Tasklist, user assignment, task delegation, and candidate groups.
3. Cost at Scale: Step Functions Standard charges $25 per million state transitions. In a complex claim with 30 state transitions, 12 million claims a year generate 360 million transitions ($9,000/month just for workflow state transitions). Camunda's predictable node/instance pricing is much more cost-effective.
4. Portability: Step Functions tightly binds YCompany to AWS. Camunda can run on AWS, Azure, GCP, or on-premise OpenShift clusters."

**Technical Bullets:**
- Modeling: BPMN 2.0 visual standard vs AWS ASL JSON.
- Human Tasks: Native task management vs custom manual plumbing in Step Functions.
- Lock-in: Open standard vs proprietary AWS cloud lock-in.

---

### Q15: Why Camunda instead of Temporal?

**Spoken Answer:**
"Temporal is an exceptional developer-centric workflow engine, but in an enterprise insurance company, **the workflow is owned collaboratively by software engineers, compliance officers, and business claims directors.**

Temporal enforces 'Workflows as Code' (writing Java or Go code to define workflows). There is no graphical model. If the Chief Claims Officer asks: *'Show me the exact escalation path when an adjuster exceeds their $20,000 approval limit,'* you have to open an IDE and walk them through Java code.

With Camunda, the BPMN diagram is both the documentation and the executable code. Business stakeholders, auditors, and technical leads review the identical diagram. Camunda also provides out-of-the-box UI tooling (Camunda Operate for operations, Tasklist for end-user task queues, and Optimize for process bottleneck analytics) which Temporal requires you to build or license separately."

**Technical Bullets:**
- Audience: Camunda bridges business + engineering; Temporal is strictly code-first for engineers.
- UI Suite: Camunda includes Operate (monitoring), Tasklist (human tasks), Optimize (BI); Temporal requires third-party or custom UI.
- Standard: ISO BPMN 2.0 vs proprietary SDK APIs.

---

### Q16: Why SaaS instead of self-hosted?

**Spoken Answer:**
"For our production launch, we evaluated both options. Choosing **Camunda 8 SaaS** drastically reduces operational overhead for our 12-person team.

Self-hosting Camunda 8 requires operating an internal Zeebe cluster, an Elasticsearch/OpenSearch cluster for data export, an identity management server, and a web application fleet, all backed by multi-AZ Raft consensus replication. That alone requires 1 to 2 dedicated cluster reliability engineers.

With Camunda 8 SaaS, Camunda manages cluster scaling, high-availability backups, Zeebe broker patching, and multi-region failover under a 99.9% enterprise SLA. Our team simply connects over secure gRPC using API client credentials. 
However, because our workers use standard Zeebe gRPC clients, we retain the option to migrate to a self-hosted Helm deployment on AWS EKS or on-premise at any time with zero code changes."

**Technical Bullets:**
- Team Focus: 12-person team cannot afford dedicated Zeebe/Elasticsearch cluster reliability engineering.
- SLA: Managed 99.9% availability backed by Camunda engineering.
- Fallback Path: Zeebe client code is identical whether connecting to Camunda SaaS or self-hosted Helm chart.

---

### Q17: Does Camunda become a vendor lock-in risk?

**Spoken Answer:**
"Camunda presents minimal vendor lock-in risk compared to proprietary cloud services like AWS Step Functions, for three specific reasons:

1. Open Standard Modeling: The process models are standard BPMN 2.0 XML files. They can be parsed, viewed, and migrated to any other BPMN-compliant engine (such as Flowable or jBPM) without redesigning business logic.
2. External Worker Pattern: Our microservice code contains zero Camunda-proprietary database schemas or runtime dependencies. Our services simply implement standard Java interfaces that consume JSON payloads.
3. Clean Hexagonal Architecture: In our Spring Boot services, the Camunda client lives entirely behind an outbound port interface (`WorkflowPort`). If YCompany ever decided to replace Camunda with another engine, only the adapter implementation class changes; zero domain logic in the Claims Service is touched."

**Technical Bullets:**
- Portability: BPMN 2.0 XML is an ISO/IEC 19510 standard.
- Architecture: Hexagonal ports and adapters isolate engine specifics from core business logic.
- Self-Hosting Option: Can switch from Camunda SaaS to Camunda Self-Hosted open-source / commercial on-prem anytime.
