# Workshop-Based Surveyor Assignment - Implementation Guide

**Enterprise-Grade Customer Journey with Event-Driven Architecture**

---

## Overview

This document outlines the complete implementation of workshop-based surveyor assignment with proper event-driven architecture, Kafka messaging, and comprehensive logging/notifications.

---

## ✅ What Has Been Implemented

### 1. Database Schema (15_workshop_and_rental_enhancements.sql)

**New Tables:**
- `workshops.vehicle_dropoffs` - Vehicle drop-off tracking
- `rentals.rental_vehicles` - Rental vehicle inventory
- `rentals.rental_providers` - Partner rental companies
- `rentals.rental_reservations` - Customer rental bookings
- `notifications.notifications` - Multi-channel notification queue
- `workflow.surveyor_coverage` - ZIP code coverage for surveyors

**Enhanced Tables:**
- Added lat/lng/zip_code/state/territory_code to workshops
- Seeded demo data for Boston and San Francisco regions

### 2. Event Payloads (Kafka Contracts)

**New Events:**
- `WorkshopSelectedPayload` - When customer selects workshop
- `VehicleDroppedOffPayload` - When vehicle arrives at workshop
- `RentalVehicleReservedPayload` - When customer books rental
- `NotificationRequestedPayload` - Generic notification trigger

### 3. Updated Auto-Assignment Logic

**File:** `AutoAssignmentService.java`

**Key Changes:**
- Now listens to `workshop.selected` event (not `claim.created`)
- Assignment based on **workshop ZIP code** (where car is)
- Enterprise-grade logging with emojis for easy tracking:
  - 🔍 Searching for surveyors
  - ✓ Found candidates
  - ✅ Assignment successful
  - ❌ No coverage (escalation)
  - ⚠️  Warnings
  - 📤 Publishing events
  - 🔔 Notifications

**Assignment Algorithm:**
```
1. Extract workshop ZIP code from event
2. Find surveyors covering that ZIP5
3. If none, fallback to ZIP3 (broader area)
4. If still none, escalate to case managers
5. Among candidates, select one with lowest workload
6. Create assignment
7. Publish surveyor.assigned event
8. Publish notification for surveyor
```

**Sample Log Output:**
```
[corr-123] 🔍 Finding surveyors covering workshop ZIP: 02101, State: MA
[corr-123] ✓ Found 2 candidate surveyor(s) for workshop ZIP 02101
[corr-123]   Surveyor Alice Surveyor (ID: a1b2c3d4-...-001) workload: 3 active assignments
[corr-123]   Surveyor Carol Surveyor (ID: a1b2c3d4-...-003) workload: 5 active assignments
[corr-123] ✅ SURVEYOR AUTO-ASSIGNED | Claim: abc-123 | Surveyor: a1b2c3d4-...-001 (Alice Surveyor) | Workshop ZIP: 02101 | Current workload: 3 active assignments | Selection reason: Lowest workload in coverage area
[corr-123] 📤 Publishing surveyor.assigned event for claim abc-123
[corr-123] 🔔 Publishing notification for surveyor Alice Surveyor - New assignment
```

### 4. Workshop Controller APIs

**New Endpoints:**
- `POST /api/v1/claims/{claimId}/select-workshop` - Customer selects workshop
- `POST /api/v1/claims/{claimId}/vehicle-dropoff` - Confirm drop-off

---

## 🔄 Complete Event Flow (Kafka Architecture)

### Step 1: Claim Submission
```
Customer submits claim
↓
POST /api/v1/claims
↓
ClaimApplicationService.submitClaim()
↓
Claim aggregate created with status: SUBMITTED
↓
📤 Publish: claim.created event → Kafka topic: claim-events
↓
Log: "[corr-xyz] Claim abc-123 created successfully"
```

### Step 2: Workshop Selection
```
Customer selects workshop
↓
POST /api/v1/claims/{claimId}/select-workshop
↓
WorkshopApplicationService.selectWorkshopForClaim()
↓
Update claim.workshopId
↓
📤 Publish: workshop.selected event → Kafka topic: claim-events
{
  "claimId": "abc-123",
  "workshopId": "w1w1w1w1-...",
  "workshopName": "Boston Auto Repair",
  "workshopZipCode": "02101",
  "workshopState": "MA",
  "workshopLatitude": 42.3601,
  "workshopLongitude": -71.0589,
  "isPartnerWorkshop": true,
  "customerId": "customer1",
  "policyNumber": "POL-12345"
}
↓
Log: "[corr-xyz] 🏪 Workshop selected for claim abc-123: Boston Auto Repair (ZIP: 02101)"
↓
📤 Publish: notification.requested → topic: notification-events
{
  "recipientId": "workshop-id",
  "recipientType": "WORKSHOP",
  "notificationType": "CLAIM_ASSIGNED_TO_WORKSHOP",
  "channel": "EMAIL",
  "subject": "New Claim Assignment",
  "message": "Claim abc-123 has been assigned to your facility..."
}
```

### Step 3: Auto-Assignment (Surveyor)
```
📥 Kafka Listener: AutoAssignmentService.handleWorkshopSelected()
↓
Consumes: workshop.selected event
↓
Log: "[corr-xyz] Workshop selected for claim abc-123 - Auto-assigning surveyor based on workshop location (ZIP: 02101, State: MA)"
↓
autoAssignBasedOnWorkshop()
  ├─ Log: "[corr-xyz] 🔍 Finding surveyors covering workshop ZIP: 02101, State: MA"
  ├─ Query: surveyors WHERE active=true AND covers_zip('02101')
  ├─ Found: [Alice (load=3), Carol (load=5)]
  ├─ Select: Alice (lowest load)
  ├─ Create assignment record
  ├─ Log: "[corr-xyz] ✅ SURVEYOR AUTO-ASSIGNED | Claim: abc-123 | Surveyor: Alice Surveyor | Workshop ZIP: 02101 | Current workload: 3"
  ↓
📤 Publish: surveyor.assigned event → topic: claim-events
{
  "claimId": "abc-123",
  "surveyorId": "a1b2c3d4-...-001",
  "surveyorName": "Alice Surveyor",
  "workshopId": "w1w1w1w1-...",
  "workshopZipCode": "02101"
}
↓
📤 Publish: notification.requested → topic: notification-events
{
  "recipientId": "a1b2c3d4-...-001",
  "recipientType": "SURVEYOR",
  "notificationType": "SURVEYOR_ASSIGNED",
  "channel": "IN_APP",
  "subject": "New Survey Assignment",
  "message": "You have been assigned to survey claim abc-123 at workshop Boston Auto Repair (ZIP: 02101)"
}
↓
Log: "[corr-xyz] 📤 Publishing surveyor.assigned event for claim abc-123"
Log: "[corr-xyz] 🔔 Publishing notification for surveyor Alice Surveyor - New assignment"
```

### Step 4: Claim Status Update
```
📥 Kafka Listener: ClaimEventListener (in claims module)
↓
Consumes: surveyor.assigned event
↓
Update claim.status = ASSIGNED
Update claim.assignedSurveyorId = "a1b2c3d4-...-001"
↓
Log: "[corr-xyz] ✅ Claim abc-123 status updated to ASSIGNED"
↓
📤 Publish: claim.status.changed event
{
  "claimId": "abc-123",
  "previousStatus": "SUBMITTED",
  "newStatus": "ASSIGNED",
  "performedBy": "SYSTEM",
  "reason": "Surveyor auto-assigned based on workshop location"
}
```

### Step 5: Notifications Distribution
```
📥 Kafka Listener: NotificationService.handleNotificationRequested()
↓
Consumes: notification.requested event
↓
For each notification:
  ├─ Store in notifications.notifications table
  ├─ Route by channel:
  │    ├─ EMAIL → Send via SMTP
  │    ├─ SMS → Send via Twilio/AWS SNS
  │    └─ IN_APP → Update unread count in Redis
  ├─ Mark as SENT
  ↓
Log: "[corr-xyz] 📧 Email sent to surveyor Alice Surveyor - New assignment notification"
Log: "[corr-xyz] 🔔 In-app notification created for surveyor a1b2c3d4-...-001"
```

### Step 6: Vehicle Drop-Off
```
Customer confirms vehicle drop-off
↓
POST /api/v1/claims/{claimId}/vehicle-dropoff
↓
WorkshopApplicationService.confirmVehicleDropOff()
↓
Create record in workshops.vehicle_dropoffs
↓
Log: "[corr-xyz] 🚗 Vehicle dropped off at workshop for claim abc-123"
↓
📤 Publish: vehicle.droppedoff event
{
  "claimId": "abc-123",
  "workshopId": "w1w1w1w1-...",
  "dropOffId": "drop-uuid",
  "droppedOffAt": "2026-05-01T01:30:00Z",
  "mileage": 45000,
  "fuelLevel": "THREE_QUARTERS",
  "photosUploaded": true
}
↓
📤 Publish: notification.requested (to surveyor)
"Vehicle has been dropped off. You can now schedule your inspection."
```

### Step 7: Rental Vehicle (Optional)
```
Customer selects rental vehicle
↓
POST /api/v1/rentals/reserve
↓
RentalApplicationService.reserveVehicle()
↓
Create record in rentals.rental_reservations
Update rental_vehicles.availability_status = RESERVED
↓
Log: "[corr-xyz] 🚙 Rental vehicle reserved for claim abc-123"
↓
📤 Publish: rental.reserved event
{
  "claimId": "abc-123",
  "reservationId": "res-uuid",
  "vehicleType": "SUV",
  "dailyRate": 65.00,
  "reservationStart": "2026-05-01T01:30:00Z"
}
↓
📤 Publish: notification.requested (to customer)
"Your rental vehicle (Honda CR-V) is reserved and ready for pickup."
```

---

## 📊 Kafka Topics Architecture

### Topic: claim-events
**Events:**
- `claim.created`
- `claim.status.changed`
- `workshop.selected` ⭐ NEW
- `surveyor.assigned` ⭐ NEW
- `vehicle.droppedoff` ⭐ NEW
- `claim.escalated`

**Consumers:**
- workflow-service (AutoAssignmentService)
- claims-service (status updates)
- notifications-service
- reporting-service

### Topic: notification-events
**Events:**
- `notification.requested` ⭐ NEW

**Consumers:**
- notifications-service (NotificationService)

### Topic: audit-events
**Events:**
- All events also published here for audit trail

**Consumers:**
- audit-service (writes to audit.audit_log)

---

## 🔔 Notification Architecture

### Recipients & Notification Types

| Recipient | Event | Channel | Message |
|-----------|-------|---------|---------|
| **Customer** | claim.created | EMAIL, SMS | "Claim abc-123 submitted successfully" |
| **Customer** | workshop.selected | IN_APP | "Workshop selected. Please drop off vehicle." |
| **Customer** | surveyor.assigned | EMAIL, IN_APP | "Surveyor assigned to your claim" |
| **Customer** | vehicle.droppedoff | IN_APP | "Drop-off confirmed. Survey will be scheduled." |
| **Customer** | claim.status.changed | EMAIL, SMS, IN_APP | "Claim status updated to {status}" |
| **Surveyor** | surveyor.assigned | EMAIL, IN_APP | "New survey assignment at {workshop}" |
| **Surveyor** | vehicle.droppedoff | IN_APP | "Vehicle dropped off. Ready for inspection." |
| **Workshop** | workshop.selected | EMAIL | "New claim assigned to your facility" |
| **Workshop** | surveyor.assigned | IN_APP | "Surveyor {name} assigned for claim {id}" |
| **Adjustor** | survey.completed | EMAIL, IN_APP | "Survey completed. Ready for adjudication." |
| **Case Manager** | claim.escalated | EMAIL, IN_APP | "No surveyor coverage. Manual assignment needed." |

### Notification Storage (notifications.notifications)

```sql
id: uuid
recipient_id: "customer1" or "surveyor-id"
recipient_type: CUSTOMER | SURVEYOR | ADJUSTOR | WORKSHOP | CASE_MANAGER
notification_type: CLAIM_SUBMITTED | SURVEYOR_ASSIGNED | etc.
channel: EMAIL | SMS | IN_APP
subject: "New Survey Assignment"
message: "You have been assigned..."
claim_id: uuid (for filtering)
status: PENDING | SENT | FAILED | READ
sent_at: timestamp
read_at: timestamp
metadata: jsonb (extra context)
created_at: timestamp
```

---

## 🎯 Customer Portal UI Flow

### Step-by-Step Wizard

```
┌─────────────────────────────────────┐
│ Step 1: Submit Claim (✅ Complete)  │
├─────────────────────────────────────┤
│ • Policy number                     │
│ • Vehicle registration              │
│ • Incident details                  │
│ • Supporting documents              │
│ • Submit → Claim ID generated       │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Step 2: Select Workshop (⭐ NEW)    │
├─────────────────────────────────────┤
│ • Show map with partner workshops   │
│ • Filter by ZIP or city             │
│ • Display:                          │
│   - Workshop name                   │
│   - Address                         │
│   - Distance from you               │
│   - Rating (if available)           │
│   - "Partner" badge                 │
│ • Option: "Use external workshop"   │
│ • Select → Triggers surveyor assign │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Step 3: Drop Off Vehicle (⭐ NEW)   │
├─────────────────────────────────────┤
│ • Confirm arrival at workshop       │
│ • Enter mileage                     │
│ • Select fuel level                 │
│ • Add notes (damage, condition)     │
│ • Upload photos (optional)          │
│ • Expected pickup date              │
│ • Confirm → Surveyor notified       │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Step 4: Rental Vehicle (⭐ NEW OPT) │
├─────────────────────────────────────┤
│ • "Need a rental vehicle?" prompt   │
│ • Show available rentals near you   │
│ • Filter by category (SUV, Sedan)   │
│ • Display daily rate                │
│ • Select dates                      │
│ • Reserve → Confirmation email      │
│ • Skip → Continue to tracking       │
└─────────────────────────────────────┘
         ↓
┌─────────────────────────────────────┐
│ Step 5: Track Repair (Existing)    │
├─────────────────────────────────────┤
│ • View claim status                 │
│ • See assigned surveyor             │
│ • Track repair progress             │
│ • View work order                   │
│ • Receive status notifications      │
└─────────────────────────────────────┘
```

---

## 🖥️ Internal Portal Notifications

### Surveyor Portal (My Assignments)

**New Assignment Notification:**
```
┌──────────────────────────────────────────┐
│ 🔔 New Assignment                        │
├──────────────────────────────────────────┤
│ Claim: abc-123                           │
│ Workshop: Boston Auto Repair             │
│ Address: 123 Main St, Boston, MA 02101   │
│ Vehicle: Toyota Camry (REG-12345)        │
│ Drop-off: Confirmed (2026-05-01)         │
│                                          │
│ [View Details] [Navigate to Workshop]    │
└──────────────────────────────────────────┘
```

**Log on Surveyor Login:**
```
[2026-05-01 01:30:15] INFO  [Alice Surveyor logged in]
[2026-05-01 01:30:15] INFO  [Loading assignments for surveyor: a1b2c3d4-...-001]
[2026-05-01 01:30:15] INFO  [Found 3 active assignments]
[2026-05-01 01:30:15] INFO  [1 new unread notification]
```

### Case Manager Portal (Escalations)

**Escalation Notification:**
```
┌──────────────────────────────────────────┐
│ ⚠️  Manual Assignment Required           │
├──────────────────────────────────────────┤
│ Claim: xyz-789                           │
│ Workshop: Rural Auto Shop               │
│ ZIP: 12345 (No surveyor coverage)        │
│ Escalation Reason: No available surveyor │
│                                          │
│ [Assign Manually] [View Workshop]        │
└──────────────────────────────────────────┘
```

---

## 📝 Complete Implementation Checklist

### ✅ Completed
- [x] Database schema with workshops, rentals, notifications, surveyor coverage
- [x] Kafka event payloads (WorkshopSelected, VehicleDroppedOff, RentalReserved, NotificationRequested)
- [x] AutoAssignmentService updated to use workshop location
- [x] Enterprise-grade logging with emojis
- [x] ZIP5 → ZIP3 fallback logic
- [x] Escalation flow for no coverage
- [x] Workshop selection API endpoints

### 🚧 To Complete

**Backend:**
- [ ] WorkshopApplicationService methods implementation:
  - `selectWorkshopForClaim()` - Publish workshop.selected event
  - `confirmVehicleDropOff()` - Create dropoff record, publish event
- [ ] RentalApplicationService (new module or extend workshops):
  - `GET /api/v1/rentals/available?zip={zip}&category={category}`
  - `POST /api/v1/rentals/reserve`
- [ ] NotificationService (new module):
  - Kafka listener for notification.requested events
  - Multi-channel dispatcher (EMAIL, SMS, IN_APP)
  - Store in notifications.notifications table
- [ ] ClaimEventListener in claims module:
  - Listen for surveyor.assigned → update claim status to ASSIGNED
- [ ] Audit logging enhancement:
  - Capture assignment rationale (ZIP, workload, reason)

**Frontend:**
- [ ] Workshop selection UI (`SelectWorkshopPage.tsx`):
  - Map view with markers
  - List view with filters
  - Partner badge display
  - Distance calculation from customer location
- [ ] Vehicle drop-off form (`VehicleDropOffPage.tsx`):
  - Mileage input
  - Fuel level selector
  - Photo upload
  - Notes textarea
  - Expected pickup date picker
- [ ] Rental vehicle selection (`RentalSelectionPage.tsx`):
  - Available vehicles grid
  - Category filters
  - Date range picker
  - Price calculator
- [ ] Progress stepper component:
  - Shows current step (1/5, 2/5, etc.)
  - Mark completed steps with checkmarks
  - Disable future steps
- [ ] Notification bell icon:
  - Unread count badge
  - Dropdown with recent notifications
  - Mark as read functionality
- [ ] Surveyor portal enhancements:
  - Show workshop address on assignment card
  - "Navigate to Workshop" button (Google Maps link)
  - Vehicle drop-off status indicator

---

## 🔍 Testing the Flow

### 1. Submit Claim
```bash
POST http://localhost:8090/api/v1/claims
{
  "policyNumber": "POL-12345",
  "vehicleRegistration": "MA-REG-001",
  "incidentDate": "2026-04-30",
  "incidentLocation": "Boston, MA",
  "description": "Rear-end collision",
  "claimType": "COLLISION"
}

# Check logs for:
# [corr-xyz] Claim abc-123 created successfully
# 📤 Publishing claim.created event
```

### 2. Select Workshop
```bash
POST http://localhost:8090/api/v1/claims/abc-123/select-workshop
{
  "workshopId": "w1w1w1w1-0000-0000-0000-000000000001"
}

# Check logs for:
# [corr-xyz] 🏪 Workshop selected for claim abc-123
# [corr-xyz] 📤 Publishing workshop.selected event
# [corr-xyz] 🔍 Finding surveyors covering workshop ZIP: 02101
# [corr-xyz] ✅ SURVEYOR AUTO-ASSIGNED | Surveyor: Alice Surveyor
# [corr-xyz] 📤 Publishing surveyor.assigned event
# [corr-xyz] 🔔 Publishing notification for surveyor Alice Surveyor
```

### 3. Check Kafka Topics
```bash
# View workshop.selected event
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic claim-events \
  --from-beginning | grep "workshop.selected"

# View surveyor.assigned event
kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic claim-events \
  --from-beginning | grep "surveyor.assigned"
```

### 4. Check Database
```sql
-- Check assignment
SELECT * FROM workflow.assignments WHERE claim_id = 'abc-123';

-- Check notification
SELECT * FROM notifications.notifications WHERE claim_id = 'abc-123';

-- Check surveyor workload
SELECT s.name, COUNT(a.id) as active_assignments
FROM workflow.surveyors s
LEFT JOIN workflow.assignments a ON s.id = a.surveyor_id AND a.active = true
GROUP BY s.id, s.name;
```

---

## 🎓 Enterprise Patterns Used

1. **Event-Driven Architecture** - Loosely coupled services via Kafka
2. **Event Sourcing** - All state changes captured as events
3. **CQRS** - Command (write) and Query (read) separation
4. **Saga Pattern** - Multi-step workflow coordination via events
5. **Idempotency** - Event deduplication via Redis
6. **Audit Trail** - Immutable event log for compliance
7. **Structured Logging** - Correlation IDs + contextual information
8. **Domain Events** - Business events as first-class citizens
9. **Asynchronous Processing** - Non-blocking surveyor assignment
10. **Geospatial Matching** - ZIP code-based territory coverage

---

## 🚀 Next Steps

1. **Complete Backend Services**
   - Implement WorkshopApplicationService methods
   - Create RentalApplicationService
   - Create NotificationService with Kafka listener

2. **Build Frontend Wizard**
   - Workshop selection with map
   - Vehicle drop-off form
   - Rental vehicle selection
   - Progress stepper component

3. **Add Notification UI**
   - Bell icon with unread count
   - In-app notification panel
   - Mark as read functionality

4. **Testing**
   - Integration tests for event flow
   - E2E test: Submit claim → Select workshop → Auto-assign surveyor
   - Load test: 1000 concurrent workshop selections

5. **Monitoring**
   - Kafka lag monitoring
   - Assignment latency metrics
   - Notification delivery rates
   - Surveyor workload distribution

---

## 📞 Support

For questions about this implementation:
- Event flow: Check Kafka topics and correlation IDs in logs
- Assignment logic: See AutoAssignmentService logs with 🔍 ✅ emojis
- Notifications: Query notifications.notifications table
- Coverage issues: Check workflow.surveyor_coverage table

All logs use structured format with correlation IDs for distributed tracing.
