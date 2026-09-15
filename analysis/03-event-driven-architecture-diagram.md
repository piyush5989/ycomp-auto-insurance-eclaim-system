# Meeting Presentation Script: Event-Driven Architecture (03-event-driven-architecture-diagram)

- **Target Diagram**: `ycomp-auto-insurance-eclaim-system/design-documents/event-driven-architecture.svg`
- **Target Audience**: Technical Leads, Integration Architects, Senior Backend Engineers, QA Leads
- **Presenter Role**: Lead Enterprise Solutions Architect
- **Presentation Objective**: Explain how asynchronous event streaming via Apache Kafka decouples core business writes from multi-channel notifications, audit compliance, WebSocket updates, and business intelligence.

---

## 1. Opening Narrative: The Story Behind the Diagram

"Good afternoon, team. 

In our earlier discussions, we established that our eClaims platform is fundamentally a **write-intensive workflow engine**. Every claim requires approximately twenty distinct write operations across its lifecycle - First Notice of Loss (FNOL), auto-assignment, damage assessment, adjuster approval, repair milestones, customer deductible payment, and workshop settlement.

Now, let me share a real scenario that illustrates why this diagram exists.

Picture a traditional, tightly-coupled enterprise architecture. A customer submits a claim on their mobile app. The server code starts running synchronously:
- It writes the claim to the database.
- It immediately tries to call an external SMS vendor API to text the customer.
- It tries to generate a PDF summary and connect to an email server.
- It tries to calculate an initial fraud score.
- It writes an audit log to a compliance server.

Now imagine that the telecom SMS vendor is experiencing network congestion and takes three seconds to respond. Or suppose our fraud analysis microservice is rebooting. 

In a synchronous world, the customer's phone sits there with a spinning progress wheel for five or six seconds before throwing a timeout error. The customer thinks their claim failed, so they hit 'Submit' two more times. Behind the scenes, three duplicate claims are created in the database, three separate adjusters are notified, and our support team spends hours untangling the mess.

We call this **the synchronous waiting game**. When you bind your critical database writes to peripheral side-effects, your platform becomes as fragile as its slowest external vendor.

This diagram right here - our **Event-Driven Architecture** - is how we permanently break that cycle. 

It illustrates our asynchronous nervous system. By placing Amazon MSK (managed Apache Kafka) in the center, we decouple the act of recording a business event from the downstream actions that react to that event.

Let us walk through the four vertical stages shown on this diagram: Core Services on the left, Event Streaming in the center, Event Consumers, and Delivery Channels on the right."

---

## 2. Step-by-Step Diagram Walkthrough & Conceptual Terms

```
+-------------------------------------------------------------------------------------------------------+
|                                  EVENT-DRIVEN ARCHITECTURE PIPELINE                                   |
+-------------------------------------------------------------------------------------------------------+

 [1. CORE SERVICES (PRODUCERS)]
  - Claims Service: Submit & update claims (FNOL, State Transitions)
  - Workflow Service: Orchestration & escalations (Auto-assignment, SLA timers)
  - Workshop Service: Repair work orders (Parts ordered, repair stages, billing)
  - Payment Service: Disbursements & refunds (Deductibles, partner settlements)
  - Document Service: Upload, OCR & validation (Textract processing, metadata)
  - Fraud Service: ML risk scoring (Anomaly detection, duplicate flags)
                                                   |
                                                   v (Transactional Outbox Pattern)
 [2. EVENT STREAMING TIER (AMAZON MSK)]
  - Kafka Topics: claim-events, workflow-events, audit-events, payment-events, repair-events
  - Core Function: Durable message buffering, strict FIFO ordering per partition key
  - Resilience: Dead Letter Queues (DLQ), Avro/JSON Schema Registry, backoff retries
                                                   |
                                                   v (Independent Consumer Groups)
 [3. EVENT CONSUMERS]
  - Multi-Channel Delivery: Pulls status change events and formats messages
  - Dashboards & BI: Updates real-time turnaround metrics and regional counters
  - Immutable Log & Auditing: Writes event payloads to compliance storage
  - Live Status Push: Routes real-time UI events to active WebSocket sessions
  - Policy Validation: Async post-checks and fraud surveillance
                                                   |
                                                   v
 [4. DELIVERY CHANNELS & DESTINATIONS]
  - Amazon SES & SendGrid: High-deliverability transactional emails
  - Amazon SNS & Twilio: Global cellular SMS alerts
  - Firebase Cloud Messaging (FCM): Native iOS and Android mobile push alerts
  - Amazon API Gateway WebSocket: Sub-second live UI browser progress bar sync
  - Amazon S3 + Object Lock: WORM compliant, tamper-proof 7-year audit repository
                                                   |
                                                   v
 [5. RECIPIENTS]
  - 200M+ Policyholders | Adjustors, Surveyors, Managers | Workshops & Rentals | C-Suite
```

---

### Stage 1: Core Services (Domain Event Producers)

"Look at the first column on the left. These are our transactional microservices. 

Notice what their primary responsibility is: executing domain logic and recording state. 
- When a customer submits a claim, the `Claims Service` validates policy rules and writes the record to PostgreSQL.
- Crucially, it does **not** make HTTP calls to Twilio or email servers.
- Instead, it writes a `ClaimSubmittedEvent` to an internal transactional outbox table in the same database transaction.
- A Change-Data-Capture connector streams that event into Kafka.

Because the service only writes to its local database, the HTTP response returns to the customer's phone in **less than 200 milliseconds**. The customer sees an instant confirmation: *'Claim #CLM-2026-004812 Submitted.'* The primary write path is fast, reliable, and completely immune to external outages."

---

### Stage 2: Event Streaming Tier (Amazon MSK / Apache Kafka)

"In the second column sits **Amazon MSK**, our managed Kafka cluster. 

Why Kafka instead of a traditional message queue like RabbitMQ or Amazon SQS?
1. **Durable Commit Log**: Kafka does not delete messages when consumed; it retains them on disk for seven days. If a downstream notification worker crashes, it resumes reading right from its last saved offset without losing a single message.
2. **Total Ordering per Claim**: In auto claims, order matters. You cannot process a `ClaimApproved` event before a `ClaimSubmitted` event. By using the `claimId` as the Kafka message partition key, Kafka guarantees that all events for a given claim land on the exact same partition and are processed in strict chronological order.
3. **Partition Scalability**: We distribute topics across 32 partitions. This allows multiple consumer instances to read different claims in parallel, easily absorbing up to 250 write TPS during storm surges."

---

### Stage 3: Event Consumers (Autonomous Processing)

"In the third column, look at our Event Consumers. Notice how each consumer group is completely isolated:

#### 1. Multi-Channel Delivery Consumer
Listens for state changes (e.g., `ClaimAssigned`, `AssessmentSubmitted`, `RepairCompleted`). It hydrates the customer contact details, formats localized text, and routes the message to the appropriate delivery channel.

#### 2. Live Status via WebSocket
When an adjuster clicks 'Approve', this consumer receives the event and pushes a lightweight JSON frame over an active WebSocket connection managed by Amazon API Gateway. The customer's mobile app screen automatically updates its progress indicator from 'Under Review' to 'Approved' in real time, without the customer ever hitting refresh.

#### 3. Immutable Log & Real-Time Auditing
Consumes every single write event across all topics. It packages the event metadata (userId, timestamp, client IP, payload SHA-256 hash) and writes it into compliance storage.

#### 4. Dashboards & BI Consumer
Instead of running heavy SQL `COUNT(*)` and aggregation queries against our transactional claims database, this consumer updates pre-aggregated summary counters in Redis and our reporting tables. When an executive opens the regional dashboard, the numbers load instantly."

---

### Stage 4: Delivery Channels & Recipients

"Finally, in the fourth and fifth columns, we see the delivery channels routing to our recipients:
- **SES & SendGrid**: Dispatches formal PDF letters and legal notifications.
- **SNS & Twilio**: Dispatches immediate SMS alerts with tracking links.
- **Firebase FCM**: Updates native mobile app badges and push banners.
- **API Gateway WebSockets**: Powers live interactive dashboards for workshops, adjusters, and policyholders.
- **S3 + Object Lock**: Stores our tamper-proof audit trail for seven years to satisfy insurance commission mandates."

---

## 3. Clear Conceptual Explanations of Key Diagram Terms

| Term in Diagram | Plain-Language Meaning | Technical / Business Significance |
|---|---|---|
| **Transactional Outbox Pattern** | Writing domain data and the corresponding event to the same database in a single atomic transaction. | Eliminates the dual-write problem, guaranteeing zero lost events even if the application crashes immediately after saving. |
| **Partition Key Ordering** | Using a specific attribute (like `claimId`) to route related messages to the same Kafka partition. | Ensures events for a single claim are processed in strict first-in-first-out (FIFO) order, preventing race conditions. |
| **Idempotent Consumer** | A consumer designed to produce the exact same outcome even if it receives the same message multiple times. | Prevents duplicate actions (like charging a deductible twice or sending duplicate SMS alerts) during network retries. |
| **WebSocket Real-Time Push** | A persistent, bi-directional network connection between a browser/app and the server. | Eliminates polling; enables sub-second UI progress bar updates across 200M policyholders and partner workshops. |
| **Dead Letter Queue (DLQ)** | A dedicated error topic where unprocessable messages are parked for analysis. | Prevents malformed messages from blocking the entire event processing pipeline, ensuring continuous operation. |

---

## 4. Expected Technical Questions & Answers (Meeting Panel)

### Q1: What happens if Kafka goes down? Will customer claim submissions fail?
**Answer**:
"No. Because we use the Transactional Outbox Pattern, microservices write incoming claims to their local PostgreSQL database and the `outbox_events` table in the same ACID transaction. If Amazon MSK experiences a temporary hiccup, the claim is already safely committed to disk. As soon as Kafka recovers, our Change-Data-Capture connector resumes streaming outbox records from where it left off. In addition, Amazon MSK is deployed in a Multi-AZ cluster with a replication factor of 3 across separate data centers, providing 99.95% availability."

### Q2: Because Kafka guarantees at-least-once delivery, how do we guarantee that a customer is not charged their deductible twice?
**Answer**:
"We implement the **Idempotent Consumer Pattern** using Redis and PostgreSQL unique constraints:
1. Every payment event carries a unique `eventId` and `idempotencyKey`.
2. When the `Payment Service` consumes a `PaymentInitiated` event, it attempts to insert the key into Redis using `SETNX` with a 24-hour expiration.
3. If the key already exists, the consumer knows the event was already processed, commits the Kafka offset immediately, and skips execution.
4. Furthermore, the database table `payments.transactions` enforces a `UNIQUE(idempotency_key)` constraint. Even if an edge case bypasses Redis, the database rejects the second write, making double charges mathematically impossible."

### Q3: If a regional storm creates a sudden backlog of 100,000 claim notifications, how does the system prevent notification workers from crashing?
**Answer**:
"We utilize **Kubernetes Event-driven Autoscaling (KEDA)** tied to Kafka consumer group lag:
- Under normal conditions, four notification worker containers process messages comfortably.
- If a storm hits and the unconsumed message count exceeds 500 messages per partition, KEDA automatically spins up additional consumer containers, scaling up to the total number of partitions (32 instances).
- If external vendors like Twilio throttle us due to carrier rate limits, failed messages are rerouted to a retry topic with exponential backoff (`claim-events-retry-5m`), allowing the primary topic to continue flowing without head-of-line blocking."

### Q4: Why did we select WebSockets for real-time customer tracking instead of traditional mobile polling?
**Answer**:
"With 560,000 active claims in flight across 200 million policyholders, if every customer app polled our backend servers every 15 seconds to check their claim status, our API Gateway would be bombarded by over 37,000 read queries every single second just to return 'no change.' 
By using **API Gateway WebSockets**, the client opens a single persistent connection. Our servers send data only when a state transition actually happens. This eliminates over 95% of unnecessary API polling traffic, saving significant server compute and database I/O costs."
