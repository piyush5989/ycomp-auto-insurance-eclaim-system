# YCompany eClaims Modernisation - Client Demo Agenda

**Target Duration:** 45-60 Minutes
**Audience:** Technical Leadership, Business Stakeholders, Evaluators

> **Presenter Note:** Use this document as your guide to drive the conversation. Keep the detailed design documents available as an appendix to dive into *only* if stakeholders ask specific deep-dive questions.

---

## 1. The Context & Vision (5-7 mins)
*Setting the stage: Why we are here and what business value we are delivering.*

- **The Current Pain:** 
  - Manual, paper-based workflow resulting in 45-60 day settlement times.
  - No real-time visibility for customers.
  - Reactive fraud detection.
- **The Vision:** 
  - A cloud-native, event-driven digital claims platform built for scale.
  - Serving 200M+ policyholders across US geographies.
- **The Target Business Impact:** 
  - Settle claims in 10-15 days.
  - Target an 85% reduction in processing costs.
  - Proactive, ML-assisted fraud detection.

*(If available, briefly show the High-Level System Architecture Diagram here to set a mental model).*

---

## 2. Live POC Demonstration (15-20 mins)
*Show, don't just tell. Walk through the working code to prove execution capability.*

### A. The Customer Experience (Frontend)
- **Action:** Walk through submitting a new claim via the React frontend.
- **Highlight:** Clean UX, step-by-step validation, and immediate feedback.

### B. Under the Hood (Backend Execution)
- **Action:** Show the backend logs/database entries as the claim is processed.
- **Senior Engineering Highlights to Mention:**
  - **Modular Monolith Pattern:** Explain that the POC uses a Modular Monolith (Clean Architecture/Hexagonal). It provides the perfect balance for Phase 1—proving domain logic without distributed system overhead—while making future microservice extraction trivial.
  - **Event-Driven Core:** Mention that saving a claim triggers asynchronous events (via Kafka/Redpanda) to drive workflow and notifications without blocking the user.
  - **Enterprise Resilience:** Briefly point out centralized error handling (`GlobalExceptionHandler`) and **Idempotency** in payments (crucial to ensure we never double-pay a claim).

---

## 3. Scaling to Production: Enterprise Architecture (10 mins)
*Transition from the POC to the 200M+ user production reality.*

- **The Transition:** "The POC proves the business logic. Here is how we scale it for 200M users and 4-10M annual claims."
- **Key Architectural Decisions (The 'Why'):**
  - **Identity Strategy:** We split Identity. AWS Cognito for our 200M customers (zero ops overhead, massive scale) and Keycloak for internal staff (handling complex, fine-grained RBAC).
  - **Database & Storage:** Amazon Aurora PostgreSQL Multi-AZ for 99.99% transactional HA, paired with AWS S3 Object Lock to guarantee WORM (Write Once Read Many) compliance for 7-year document retention.
  - **Event Streaming:** Migrating to Amazon MSK (Managed Kafka) to act as the durable backbone for the microservices.
- **Meeting the NFRs:** 
  - Ensuring < 5000ms latency via ElastiCache (Redis) and CloudFront CDN.
  - OWASP compliance enforced at the edge via AWS WAF.

---

## 4. Execution Plan & ROI (5 mins)
*How we get there and what it costs.*

- **Phased Rollout Strategy:**
  - **Phase 1 (Months 1-6):** Production Web Launch (lifting the modular monolith to AWS ECS Fargate).
  - **Phase 2 (Months 7-12):** Microservice Extraction (scaling out independent services).
  - **Phase 3 (Months 13-18):** Advanced Capabilities (AWS Textract for OCR, SageMaker for Fraud ML).
- **Realistic Costing:** 
  - Infrastructure sized honestly for 4-10M annual claims (typical 2-5% industry claim rate).
  - Phase 1 infrastructure OPEX estimated at a highly efficient ~$48K-$99K/year.
  - Total implementation team: 12-15 professionals over 18 months.

---

## 5. Q&A / Deep Dives (10-15 mins)
*Open the floor. Use the detailed markdown documents (`techstack-dar.md`, `project-estimation-deliverable.md`, `solution-approach.md`) as references to answer specific technical or budgeting questions.*

**Potential Pivot Points for Q&A:**
- *"How do you handle a scenario where Kafka goes down?"* -> Pivot to DR/Resiliency plan.
- *"Why not serverless (Lambda) for everything?"* -> Pivot to the DAR comparison matrices.
- *"How do you guarantee insurance regulatory compliance?"* -> Pivot to S3 Object Lock and Audit logging NFRs.
