# Incident Management Flow - Implementation Complete

## ✅ What Has Been Implemented

### 1. Journey Completion UI (Customer Portal)

**File:** `eclaims-frontend/src/portals/customer/pages/ClaimDetailPage.tsx`

**Changes:**
- Journey progress now shows "✅ Claim Setup Complete" when all steps are done
- Success message displayed: "Your vehicle is at the workshop. A surveyor has been assigned and will inspect it soon."
- Journey progress card is hidden once claim moves to ASSIGNED or beyond

**Logic:**
```typescript
const journeyComplete = [
  'ASSIGNED', 'UNDER_SURVEY', 'SURVEYED', 'UNDER_ADJUDICATION',
  'APPROVED', 'REJECTED', 'PAYMENT_INITIATED', 'SETTLED'
].includes(claim.status)
```

---

### 2. Auto-Assign Adjustor When Survey Completes

**File:** `AutoAssignmentService.java`

**New Features:**
- Listens to `claim.status.changed` events
- When status becomes `SURVEYED`, automatically assigns an adjustor
- Uses load balancing (selects adjustor with lowest workload)
- Publishes `adjustor.assigned` event
- Sends notification to assigned adjustor

**Flow:**
```
Surveyor submits assessment
↓
Claim status → SURVEYED
↓
Publish: claim.status.changed event
↓
AutoAssignmentService.handleClaimEvents()
↓
Detects newStatus = "SURVEYED"
↓
autoAssignAdjustor(claimId, correlationId)
  ├─ Find all active adjustors
  ├─ Load balance: Select one with lowest workload
  ├─ Create assignment
  ├─ Log: ✅ ADJUSTOR AUTO-ASSIGNED
  ↓
Publish: adjustor.assigned event
Publish: notification.requested event (to adjustor)
```

---

## Complete Incident Management Flow

### End-to-End Process

```
1️⃣ CUSTOMER SUBMITS CLAIM
   Status: SUBMITTED
   ↓

2️⃣ CUSTOMER SELECTS WORKSHOP
   Status: WORKSHOP_SELECTED
   ↓

3️⃣ CUSTOMER DROPS OFF VEHICLE ⭐
   Status: VEHICLE_AT_WORKSHOP
   ↓
   📤 Event: vehicle.droppedoff
   ↓
   🔄 AutoAssignmentService.handleClaimEvents()
   ├─ Auto-assigns SURVEYOR based on workshop ZIP
   ├─ Log: ✅ SURVEYOR AUTO-ASSIGNED
   ├─ Publish: surveyor.assigned event
   └─ Publish: notification to surveyor
   ↓
   Status: ASSIGNED
   ↓
   📬 Surveyor receives notification: "New assignment at workshop XYZ"

4️⃣ SURVEYOR STARTS SURVEY
   Navigate to: /internal/surveyor/my-assignments
   Click: "Start Assessment" button
   ↓
   PATCH /api/v1/claims/{id}/status
   { "targetStatus": "UNDER_SURVEY" }
   ↓
   Status: UNDER_SURVEY

5️⃣ SURVEYOR SUBMITS ASSESSMENT ⭐
   Enter: Assessed Amount + Damage Notes
   Click: "Submit Assessment" button
   ↓
   PATCH /api/v1/claims/{id}/status
   {
     "targetStatus": "SURVEYED",
     "amount": 5000.00,
     "reason": "Front bumper damaged, needs replacement..."
   }
   ↓
   Status: SURVEYED
   ↓
   📤 Event: claim.status.changed (newStatus: SURVEYED)
   ↓
   🔄 AutoAssignmentService.handleClaimEvents()
   ├─ Detects newStatus = "SURVEYED"
   ├─ Auto-assigns ADJUSTOR with lowest workload
   ├─ Log: ✅ ADJUSTOR AUTO-ASSIGNED (after survey completed)
   ├─ Publish: adjustor.assigned event
   └─ Publish: notification to adjustor
   ↓
   Status: UNDER_ADJUDICATION
   ↓
   📬 Adjustor receives notification: "Survey completed for claim XYZ. Please review and adjudicate."

6️⃣ ADJUSTOR REVIEWS & ADJUDICATES
   Navigate to: /internal/claims-queue
   Filter: Status = UNDER_ADJUDICATION
   Click: Claim to review
   ↓
   Review survey assessment, photos, documents
   ↓
   Decision: Approve or Reject
   ↓
   If APPROVE:
     POST /api/v1/claims/{id}/approve
     { "approvedAmount": 4800.00 }
     Status → APPROVED
   ↓
   If REJECT:
     POST /api/v1/claims/{id}/reject
     { "rejectionReason": "Pre-existing damage not covered" }
     Status → REJECTED

7️⃣ PAYMENT PROCESSING
   If APPROVED:
     Status → PAYMENT_INITIATED
     ↓
     Payment processed
     ↓
     Status → SETTLED
```

---

## Database Changes

### New Tables (Already Created)

**workflow.adjustors**
```sql
CREATE TABLE workflow.adjustors (
    id      UUID        PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    email   VARCHAR(255) NOT NULL,
    region  VARCHAR(50),
    active  BOOLEAN     NOT NULL DEFAULT TRUE,
    field_office VARCHAR(100),
    service_areas TEXT
);
```

**Seed Data:**
```sql
INSERT INTO workflow.adjustors VALUES
    ('b2c3d4e5-...-001', 'David Adjustor',   'adjustor1@eclaims.test', 'EAST', TRUE, 'Boston Office', ...),
    ('b2c3d4e5-...-002', 'Emma Adjustor',    'adjustor2@eclaims.test', 'WEST', TRUE, 'San Francisco Office', ...),
    ('b2c3d4e5-...-003', 'Frank Adjustor',   'adjustor3@eclaims.test', 'EAST', TRUE, 'New York Office', ...);
```

---

## New Backend Files Created

1. **AdjustorEntity.java**
   - JPA entity for adjustors table
   - Fields: id, name, email, region, active, fieldOffice, serviceAreas

2. **AdjustorJpaRepository.java**
   - Repository for adjustor queries
   - Methods: `findByActiveTrue()`, `findByActiveTrueAndRegion(String)`

---

## Updated Backend Files

### AutoAssignmentService.java

**Key Changes:**

1. **Renamed Method:**
   ```java
   // OLD: handleVehicleDroppedOff()
   // NEW: handleClaimEvents()  // Handles multiple event types
   ```

2. **Dual Event Handling:**
   ```java
   @KafkaListener(topics = "claim-events", groupId = "workflow-service")
   public void handleClaimEvents(DomainEvent<?> event) {
       // Handle vehicle drop-off → Assign surveyor
       if ("vehicle.droppedoff".equals(event.eventType())) { ... }
       
       // Handle survey completed → Assign adjustor
       if ("claim.status.changed".equals(event.eventType())) {
           if ("SURVEYED".equals(payload.newStatus())) {
               autoAssignAdjustor(claimId, correlationId);
           }
       }
   }
   ```

3. **New Methods:**
   - `autoAssignAdjustor()` - Assigns adjustor with load balancing
   - `publishAdjustorAssignedEvent()` - Publishes adjustor.assigned
   - `publishNotificationForAdjustor()` - Notifies adjustor
   - `publishAdjustorEscalationEvent()` - Escalates if no adjustor available

---

## Event Flow

### Surveyor Assignment (Vehicle Drop-Off)
```
Event: vehicle.droppedoff
↓
AutoAssignmentService.handleClaimEvents()
↓
if (event.eventType === "vehicle.droppedoff"):
    autoAssignBasedOnDropOff()
    ├─ Find surveyors covering workshop ZIP
    ├─ Select one with lowest workload
    ├─ Create assignment
    └─ Publish: surveyor.assigned + notification
```

### Adjustor Assignment (Survey Complete)
```
Event: claim.status.changed
Payload: { newStatus: "SURVEYED" }
↓
AutoAssignmentService.handleClaimEvents()
↓
if (event.eventType === "claim.status.changed"):
    if (payload.newStatus === "SURVEYED"):
        autoAssignAdjustor()
        ├─ Find all active adjustors
        ├─ Select one with lowest workload
        ├─ Create assignment
        └─ Publish: adjustor.assigned + notification
```

---

## Logging

### Surveyor Assignment Logs
```log
[corr-xyz] 🚗 Vehicle dropped off for claim abc-123 - NOW triggering surveyor auto-assignment
[corr-xyz] 🔍 Finding surveyors covering workshop ZIP: 02101, State: MA
[corr-xyz] ✓ Found 2 candidate surveyor(s)
[corr-xyz] ✅ SURVEYOR AUTO-ASSIGNED (after vehicle drop-off) | Claim: abc-123 | Surveyor: Alice | Workshop ZIP: 02101 | Current workload: 3
[corr-xyz] 📤 Publishing surveyor.assigned event for claim abc-123
[corr-xyz] 🔔 Publishing notification for surveyor Alice Surveyor - Vehicle ready for inspection
```

### Adjustor Assignment Logs
```log
[corr-xyz] 📋 Survey completed for claim abc-123 - NOW triggering adjustor auto-assignment
[corr-xyz] 🔍 Finding available adjustor for claim abc-123
[corr-xyz] ✓ Found 3 active adjustor(s)
[corr-xyz] ✅ ADJUSTOR AUTO-ASSIGNED (after survey completed) | Claim: abc-123 | Adjustor: David (b2c3d4e5-...-001) | Current workload: 5 active claims
[corr-xyz] 📤 Publishing adjustor.assigned event for claim abc-123 (trigger: survey completed)
[corr-xyz] 🔔 Publishing notification for adjustor David Adjustor - Survey completed, ready for adjudication
```

---

## Notifications

### Surveyor Notification
```json
{
  "claimId": "abc-123",
  "recipientId": "surveyor-uuid",
  "recipientType": "SURVEYOR",
  "notificationType": "SURVEYOR_ASSIGNED",
  "channel": "IN_APP",
  "subject": "New Survey Assignment - Vehicle Ready",
  "message": "You have been assigned to survey claim abc-123. Vehicle is now at workshop (ZIP: 02101) and ready for inspection."
}
```

### Adjustor Notification
```json
{
  "claimId": "abc-123",
  "recipientId": "adjustor-uuid",
  "recipientType": "ADJUSTOR",
  "notificationType": "ADJUSTOR_ASSIGNED",
  "channel": "IN_APP",
  "subject": "New Claim for Adjudication",
  "message": "Survey has been completed for claim abc-123. Please review and adjudicate."
}
```

---

## Testing Steps

### 1. Test Surveyor Assignment
```bash
# Submit claim → Select workshop → Drop off vehicle
POST /api/v1/claims/{id}/vehicle-dropoff
{
  "mileage": 45000,
  "fuelLevel": "HALF",
  "photosUploaded": true
}

# Check logs:
✅ SURVEYOR AUTO-ASSIGNED (after vehicle drop-off)

# Check database:
SELECT * FROM workflow.assignments WHERE claim_id = '{id}';
# Should have surveyor assignment

# Check surveyor portal:
Navigate to: /internal/surveyor/my-assignments
# Should see the claim listed
```

### 2. Test Adjustor Assignment
```bash
# Surveyor submits assessment
PATCH /api/v1/claims/{id}/status
{
  "targetStatus": "SURVEYED",
  "amount": 5000.00,
  "reason": "Front bumper damaged..."
}

# Check logs:
📋 Survey completed for claim {id} - NOW triggering adjustor auto-assignment
✅ ADJUSTOR AUTO-ASSIGNED (after survey completed)

# Check database:
SELECT * FROM workflow.assignments WHERE claim_id = '{id}';
# Should have TWO assignments: surveyor + adjustor

# Check claim status:
GET /api/v1/claims/{id}
# Status should be: UNDER_ADJUDICATION
```

---

## Frontend Improvements

### Customer Portal

**Before:**
- Journey progress always showing "Complete Your Claim Journey"
- No indication when setup is done

**After:**
- Shows "✅ Claim Setup Complete" when ASSIGNED or beyond
- Green success message: "Your vehicle is at the workshop. A surveyor has been assigned and will inspect it soon."
- Journey progress hidden once claim moves to processing stages

---

## What Happens Next

After adjustor is assigned:

1. **Adjustor Logs In:**
   - Navigate to `/internal/claims-queue`
   - Filter by Status: UNDER_ADJUDICATION
   - See claim in their queue

2. **Adjustor Reviews Claim:**
   - Click claim to view details
   - See surveyor's assessment amount
   - See damage notes
   - Review uploaded documents/photos

3. **Adjustor Makes Decision:**
   - **Approve:** Set approved amount, claim → APPROVED
   - **Reject:** Provide rejection reason, claim → REJECTED

4. **Customer Notified:**
   - Email/SMS notification about decision
   - Can log in to see status

5. **Payment Processing:**
   - If approved → PAYMENT_INITIATED → SETTLED

---

## Status: ✅ COMPLETE

All incident management features are now implemented:

✅ Customer submits claim  
✅ Customer selects workshop  
✅ Customer drops off vehicle  
✅ **Surveyor auto-assigned based on workshop location**  
✅ Surveyor receives notification  
✅ **Surveyor can submit assessment via website**  
✅ **Adjustor auto-assigned when survey complete**  
✅ **Adjustor receives notification**  
✅ Journey progress shows completion status  
✅ All events published to Kafka  
✅ Comprehensive logging with emojis  

---

## Next Steps (Optional Enhancements)

1. **Adjustor Dashboard:**
   - Create dedicated adjustor pages
   - My assigned claims queue
   - Quick approve/reject interface

2. **Real-time Notifications:**
   - WebSocket for instant updates
   - Browser push notifications
   - SMS notifications

3. **Email Integration:**
   - Send email to surveyor when assigned
   - Send email to adjustor when assessment ready
   - Send email to customer on approval/rejection

4. **Mobile App:**
   - Surveyor mobile app for field work
   - Photo upload from mobile
   - Digital signature on assessment

5. **Analytics:**
   - Average survey turnaround time
   - Adjustor performance metrics
   - Approval/rejection rates
