# Meeting Presentation Script: System Context Architecture (01-context-diagram)

- **Target Diagram**: `ycomp-auto-insurance-eclaim-system/design-documents/context-diagram.svg`
- **Target Audience**: Executive Stakeholders, Technical Architecture Board, Engineering Leads
- **Presenter Role**: Lead Enterprise Solutions Architect
- **Presentation Objective**: Establish clear business and technical boundaries, define external vs internal trust zones, and clarify enterprise integrations for the eClaims ecosystem.

---

## 1. Opening Narrative: The Story Behind the Diagram

"Good morning, everyone. Thanks for your time today.

Before we dive into technical components, let us look at the reality that brought us all into this room. 

Imagine you are one of YCompany's 200 million policyholders. You are driving home on a Friday evening, and another vehicle skids into your rear bumper. You are shaken, your car is leaking fluid, and you need help.

In our legacy world, this is where your nightmare would actually begin. You had to call a phone support desk on Monday morning, wait on hold, fill out physical paper forms, and mail in police report copies. You then waited days for an adjuster or field surveyor to inspect the vehicle. The surveyor wrote physical notes on a clipboard and faxed them to a regional processing center. Our partner repair shops refused to touch the car until they received a physical confirmation, and when settlement finally happened weeks later, YCompany printed a paper cheque and mailed it via postal service.

If you asked our customer service team, 'What is the status of my claim?', nobody could give you an immediate answer because the files lived in physical folders across disparate regional offices. And if our executive leadership asked, 'Which regions have the highest claim settlement delays or potential fraud leakage?', gathering that data took weeks of spreadsheet consolidation.

This diagram right here - our **System Context Architecture** - was drawn to fix that fundamental breakdown. 

This is our 30,000-foot view. It answers three vital questions without getting bogged down in internal code:
1. Who interacts with our system, and through what human roles?
2. What belongs inside the eClaims platform boundary?
3. Which external systems must we integrate with, and what are those contracts?

Let us walk through the three main zones on this canvas: the Actors on the left, the eClaims Core Platform in the center, and our External Dependencies on the right."

---

## 2. Step-by-Step Diagram Walkthrough & Conceptual Terms

### Zone 1: Human Actors and Boundaries (Left Hand Side)

"Let us start on the left with our human users. Notice how we have categorized them into distinct groups:

#### 1. Customer / Policyholder
This is our external consumer. They represent the largest volume of users - up to 200 million policyholders across the United States. Their interaction is self-service:
- Submitting First Notice of Loss (FNOL) with photos and accident descriptions.
- Tracking live status so they never have to call a helpdesk to ask where their claim stands.
- Paying deductibles electronically right from their phone or browser.

#### 2. Operations Team (Case Manager, Surveyor, Adjustor)
These are our internal operational specialists:
- **Case Manager**: Acts as the triage lead and supervisor. They monitor the regional claim queues, ensure claims are assigned properly, reassign work if someone is on leave, and hold supervisor override authority when an unusual claim requires executive sign-off.
- **Surveyor**: The field specialist who physically or digitally inspects the damaged vehicle at the repair facility. They upload itemized damage assessments, labor hour estimates, and replacement parts calculations.
- **Adjustor**: The financial and legal adjudicator. They review the surveyor's assessment against the customer's policy terms, evaluate deductibles, check coverage limits, and approve or reject the final claim payout amount.

#### 3. Management & Audit (Auditor, Regional Manager, Top Management)
These roles do not process claims day-to-day; they govern and steer the organization:
- **Auditor**: Needs read-only, non-repudiable access across all historical transitions, communications, and financial approvals to satisfy state insurance commissioner audits.
- **Regional Manager**: Focuses on operational efficiency metrics within their geographic territory - average claim processing turnaround time (TAT), payout volumes, and team workload balance.
- **Top Management**: C-suite leadership needing cross-region business intelligence - identifying loss ratios, catastrophe storm impact across states, and fraud trends.

#### 4. Workshop Partner
Notice the Workshop Partner here. In our business model, partner repair facilities log in to receive initial accident details, upload repair work orders, log milestone progress (parts ordered, painting, reassembly), and submit final invoices. 
*(Architectural Note for Technical Members: In our upcoming diagram revision, we are refining this boundary to place Workshop Partners and Car Rental Providers in an external partner boundary rather than inside internal company users, enforcing zero-trust API isolation).*

---

### Zone 2: The eClaims Core Platform (Center)

"In the center sits the **eClaims System (Core Platform)**. 

Notice what it is responsible for:
- Orchestrating the end-to-end claims lifecycle.
- Enforcing strict state machine transitions so an adjuster cannot approve a claim before an assessment exists.
- Providing document management, audit logging, and reporting.

Notice also what eClaims is **NOT** doing: it is not attempting to replace our corporate general ledger, nor is it attempting to re-write our 20-year-old policy underwriting system. That brings us to the right side of the diagram."

---

### Zone 3: External Dependencies (Right Hand Side)

"On the right, we have three external systems that eClaims integrates with via clean, authenticated API contracts:

#### 1. Policy Management System (PMS)
- **Concept**: This is YCompany's existing system of record for policy sales, underwriting actuarial tables, and premium billing.
- **Contract**: eClaims connects via read-only REST/gRPC interfaces. When a customer enters their policy number during claim intake, eClaims validates whether the policy is active, checks vehicle registration match, and retrieves coverage limits and deductible amounts. eClaims never writes policy updates back to PMS.

#### 2. Payment Gateway (Stripe Connect)
- **Concept**: A PCI-DSS Level 1 compliant financial processor.
- **Contract**: Instead of storing credit cards or bank account numbers inside our databases, eClaims delegates payment processing. It handles two flows:
  1. *Inbound*: Collecting deductible payments electronically from customers via web/mobile.
  2. *Outbound*: Disbursing approved claim settlements directly to partner workshop bank accounts via automated ACH transfers.

#### 3. Notification Services (SES / Twilio / FCM)
- **Concept**: Multi-channel delivery infrastructure.
- **Contract**: When a claim transitions state, eClaims emits notifications:
  - **AWS SES**: Transactional emails containing formal claim approval letters and PDF summaries.
  - **Twilio / Amazon SNS**: Immediate SMS text alerts with tracking URLs.
  - **Firebase Cloud Messaging (FCM) / Apple APNs**: Native mobile push notifications updating the user's progress bar."

---

## 3. Clear Conceptual Explanations of Key Diagram Terms

| Term in Diagram | Plain-Language Meaning | Technical / Business Significance |
|---|---|---|
| **FNOL (First Notice of Loss)** | The initial report submitted by an insured party following an accident or damage. | Sets the entire legal and operational claims process in motion; must capture date, location, and photos immediately. |
| **Surveyor vs Adjustor** | Surveyor assesses physical vehicular damage; Adjustor adjudicates financial and legal coverage liability. | Implements regulatory segregation of duties, preventing fraud and single-point financial sign-offs. |
| **Supervisor Override** | The governed ability of a Case Manager to override a surveyor or adjuster decision. | Ensures high-value or disputed claims do not stall in queues when values exceed individual adjuster limits. |
| **Trust Boundary** | The boundary separating entities with different privilege levels (external public internet vs internal enterprise network). | Governs authentication strategies: public users use consumer identity (Cognito); internal staff use enterprise RBAC (Keycloak). |
| **Non-Repudiation** | The guarantee that an actor cannot deny having performed an action. | Achieved by signing every write with user ID, role, client IP, and immutable audit event logging. |

---

## 4. Expected Technical Questions & Answers (Meeting Panel)

### Q1: Why did we keep the legacy Policy Management System (PMS) outside the eClaims boundary rather than consolidating everything into one modern database?
**Answer**:
"Consolidating policy sales, underwriting actuarial tables, and claims processing into one system sounds attractive initially, but in enterprise architecture, that is a known anti-pattern called 'scope expansion.' YCompany's PMS handles rate filings across fifty state insurance commissions and recurring premium billing. Merging that into this project would turn an 18-month claims modernization into a multi-year, high-risk ERP replacement. By keeping PMS external and interacting via a well-defined `PolicyServicePort`, eClaims remains completely autonomous and resilient."

### Q2: How do we prevent partner repair workshops from gaining unauthorized access to internal claims data or other workshops' invoices?
**Answer**:
"We enforce multi-tenant authorization through Role-Based Access Control (RBAC) and data-level scoping:
1. Every API call requires an OAuth2/OIDC Bearer token issued by our identity tier.
2. The token contains the caller's role (`ROLE_WORKSHOP`) and tenant identifier (`workshop_id`).
3. Spring Security method-level annotations (`@PreAuthorize`) verify that workshop callers can only invoke workshop-specific endpoints.
4. In the database layer, queries are filtered strictly by `WHERE workshop_id = :authenticatedWorkshopId`. A workshop can never query, view, or even know about claims assigned to another repair facility."

### Q3: If third-party notification services (like Twilio or SES) experience an outage, will customer claim submissions fail?
**Answer**:
"No, absolutely not. That is one of our primary architectural guardrails. All communications are completely decoupled from the synchronous claim submission path using an asynchronous event-driven model. When a claim is submitted, the record is committed to our database and an event is published to our internal event stream. The Notification Service consumes that event in the background. If Twilio fails or slows down, the message sits safely in our durable retry queue. The customer receives an immediate HTTP 201 Created confirmation in under 300 milliseconds regardless of external vendor health."

### Q4: Why is Stripe Connect suggested for workshop disbursements rather than our traditional batch ACH file transfers?
**Answer**:
"Traditional batch ACH transfers take 3 to 5 business days and require manual reconciliation by our accounting clerks. Partner workshops cited delayed payments as their number one complaint in our problem statement. Stripe Connect provides programmable marketplace disbursements with instant verification and direct bank routing. However, for audit and treasury compliance, the actual ledger authorization is still synchronized back to YCompany's enterprise ERP nightly."
