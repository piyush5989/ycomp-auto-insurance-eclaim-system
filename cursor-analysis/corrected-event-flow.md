# ✅ CORRECTED Event Flow - Workshop-Based Surveyor Assignment

## Critical Correction

**SURVEYOR ASSIGNMENT TRIGGER: `vehicle.droppedoff` event (NOT `workshop.selected`)**

---

## Why This Matters

### ❌ Wrong Approach (Initially Implemented)
```
workshop.selected → Immediately assign surveyor
```

**Problems:**
- Surveyor assigned before vehicle arrives at workshop
- Wastes surveyor time if customer delays drop-off
- Not operationally efficient
- Surveyor might arrive before vehicle

### ✅ Correct Approach (Now Fixed)
```
vehicle.droppedoff → NOW assign surveyor
```

**Benefits:**
- Surveyor assigned only when vehicle is physically present
- Operational efficiency - surveyor knows vehicle is ready
- No wasted trips
- Clear trigger: "Vehicle is here, send surveyor"

---

## Complete Corrected Event Flow

### Step 1: Claim Submission
```
Customer submits claim
↓
POST /api/v1/claims
↓
Claim aggregate created with status: SUBMITTED
↓
📤 Publish: claim.created event → Kafka
↓
Log: "[corr-xyz] Claim abc-123 created successfully"
```

**NO surveyor assignment yet** ✅

---

### Step 2: Workshop Selection
```
Customer selects workshop
↓
POST /api/v1/claims/{claimId}/select-workshop
{
  "workshopId": "w1w1w1w1-..."
}
↓
WorkshopApplicationService.selectWorkshopForClaim()
  ├─ Update claim.workshopId = "w1w1w1w1-..."
  ├─ Update claim.status = WORKSHOP_SELECTED (new status)
  ↓
📤 Publish: workshop.selected event → Kafka
{
  "claimId": "abc-123",
  "workshopId": "w1w1w1w1-...",
  "workshopName": "Boston Auto Repair",
  "workshopZipCode": "02101",
  "workshopState": "MA",
  "isPartnerWorkshop": true,
  "customerId": "customer1",
  "policyNumber": "POL-12345"
}
↓
Log: "[corr-xyz] 🏪 Workshop selected for claim abc-123: Boston Auto Repair (ZIP: 02101)"
↓
📤 Publish: notification.requested → topic: notification-events
  ├─ To Workshop: "Claim abc-123 assigned to your facility. Awaiting vehicle drop-off."
  └─ To Customer: "Workshop selected. Please drop off your vehicle to begin survey process."
```

**STILL NO surveyor assignment** ✅  
**Claim status: WORKSHOP_SELECTED**

---

### Step 3: Vehicle Drop-Off (KEY TRIGGER)
```
Customer drops off vehicle at workshop
↓
POST /api/v1/claims/{claimId}/vehicle-dropoff
{
  "dropOffNotes": "Vehicle delivered at 9:00 AM",
  "mileage": 45000,
  "fuelLevel": "THREE_QUARTERS",
  "photosUploaded": true,
  "expectedPickupAt": "2026-05-05T10:00:00Z"
}
↓
WorkshopApplicationService.confirmVehicleDropOff()
  ├─ Create record in workshops.vehicle_dropoffs
  ├─ Update claim.status = VEHICLE_AT_WORKSHOP
  ↓
📤 Publish: vehicle.droppedoff event → Kafka ⭐ ASSIGNMENT TRIGGER
{
  "claimId": "abc-123",
  "workshopId": "w1w1w1w1-...",
  "workshopName": "Boston Auto Repair",
  "workshopZipCode": "02101",
  "workshopState": "MA",
  "workshopLatitude": 42.3601,
  "workshopLongitude": -71.0589,
  "dropOffId": "drop-uuid",
  "droppedOffAt": "2026-05-01T09:00:00Z",
  "mileage": 45000,
  "fuelLevel": "THREE_QUARTERS",
  "photosUploaded": true,
  "confirmedBy": "customer1",
  "customerId": "customer1",
  "policyNumber": "POL-12345",
  "vehicleRegistration": "MA-REG-001"
}
↓
Log: "[corr-xyz] 🚗 Vehicle dropped off at workshop for claim abc-123 (mileage: 45000, fuel: THREE_QUARTERS)"
```

---

### Step 4: AUTO-ASSIGNMENT (Surveyor) ⭐
```
📥 Kafka Listener: AutoAssignmentService.handleVehicleDroppedOff()
↓
Listens to event type: "vehicle.droppedoff"
↓
Consumes: vehicle.droppedoff event
↓
Log: "[corr-xyz] 🚗 Vehicle dropped off for claim abc-123 - NOW triggering surveyor auto-assignment for workshop inspection"
↓
autoAssignBasedOnDropOff(payload, correlationId)
  ├─ Extract workshop ZIP: 02101, State: MA
  ├─ Log: "[corr-xyz] 🔍 Finding surveyors covering workshop ZIP: 02101, State: MA (vehicle is NOW at workshop 'Boston Auto Repair', ready for inspection)"
  ├─ Query: surveyors WHERE active=true AND covers_zip('02101')
  ├─ Found: [Alice (load=3), Carol (load=5)]
  ├─ Select: Alice (lowest workload)
  ├─ Create assignment record
  ├─ Log: "[corr-xyz] ✅ SURVEYOR AUTO-ASSIGNED (after vehicle drop-off) | Claim: abc-123 | Surveyor: Alice Surveyor | Workshop ZIP: 02101 | Current workload: 3 active assignments"
  ↓
📤 Publish: surveyor.assigned event → topic: claim-events
{
  "claimId": "abc-123",
  "surveyorId": "a1b2c3d4-...-001",
  "surveyorName": "Alice Surveyor",
  "workshopId": "w1w1w1w1-...",
  "workshopZipCode": "02101",
  "assignmentTrigger": "VEHICLE_DROPPED_OFF"
}
↓
📤 Publish: notification.requested → topic: notification-events
{
  "recipientId": "a1b2c3d4-...-001",
  "recipientType": "SURVEYOR",
  "notificationType": "SURVEYOR_ASSIGNED",
  "channel": "IN_APP",
  "subject": "New Survey Assignment - Vehicle Ready",
  "message": "You have been assigned to survey claim abc-123. Vehicle is now at workshop (ZIP: 02101) and ready for inspection."
}
↓
Log: "[corr-xyz] 📤 Publishing surveyor.assigned event for claim abc-123 (trigger: vehicle drop-off confirmed)"
Log: "[corr-xyz] 🔔 Publishing notification for surveyor Alice Surveyor - Vehicle ready for inspection"
```

**Claim status updated: VEHICLE_AT_WORKSHOP → ASSIGNED**

---

## Event Timeline Summary

| Step | Event | Surveyor Status | Vehicle Status | Claim Status |
|------|-------|----------------|----------------|--------------|
| 1 | `claim.created` | Not assigned ⏸️ | Not at workshop | SUBMITTED |
| 2 | `workshop.selected` | Not assigned ⏸️ | Not at workshop | WORKSHOP_SELECTED |
| 3 | `vehicle.droppedoff` | ⭐ **Assignment triggered** | ✅ At workshop | VEHICLE_AT_WORKSHOP |
| 4 | `surveyor.assigned` | ✅ Assigned | ✅ At workshop | ASSIGNED |
| 5 | Surveyor visits workshop | In progress | ✅ At workshop | UNDER_SURVEY |

---

## Kafka Topics

### Topic: claim-events

**Events in order:**
1. `claim.created` - Customer submits claim
2. `workshop.selected` - Customer picks workshop (NO assignment yet)
3. `vehicle.droppedoff` - **⭐ TRIGGERS surveyor assignment**
4. `surveyor.assigned` - System assigns surveyor based on workshop location
5. `survey.completed` - Surveyor submits assessment
6. `claim.status.changed` - Various status updates

**Consumers:**
- `AutoAssignmentService` listens ONLY to `vehicle.droppedoff` ✅
- `ClaimEventListener` updates claim status on various events
- `NotificationService` sends notifications on all events
- `ReportingService` updates KPI metrics

---

## Log Output Example (Corrected)

```log
[2026-05-01 09:00:00] [corr-abc-123] INFO  - Claim abc-123 created successfully
[2026-05-01 09:00:00] [corr-abc-123] 📤    - Publishing claim.created event

[2026-05-01 09:05:30] [corr-abc-123] INFO  - 🏪 Workshop selected for claim abc-123: Boston Auto Repair (ZIP: 02101)
[2026-05-01 09:05:30] [corr-abc-123] 📤    - Publishing workshop.selected event
[2026-05-01 09:05:30] [corr-abc-123] 🔔    - Notification sent to workshop: Awaiting vehicle drop-off

⏸️ NO SURVEYOR ASSIGNMENT YET - Waiting for vehicle drop-off...

[2026-05-01 10:15:45] [corr-abc-123] INFO  - 🚗 Vehicle dropped off at workshop for claim abc-123
[2026-05-01 10:15:45] [corr-abc-123] 📤    - Publishing vehicle.droppedoff event

⭐ NOW TRIGGERING SURVEYOR ASSIGNMENT ⭐

[2026-05-01 10:15:46] [corr-abc-123] INFO  - 🚗 Vehicle dropped off for claim abc-123 - NOW triggering surveyor auto-assignment for workshop inspection
[2026-05-01 10:15:46] [corr-abc-123] INFO  - 🔍 Finding surveyors covering workshop ZIP: 02101, State: MA (vehicle is NOW at workshop 'Boston Auto Repair', ready for inspection)
[2026-05-01 10:15:46] [corr-abc-123] INFO  - ✓ Found 2 candidate surveyor(s) for workshop ZIP 02101
[2026-05-01 10:15:46] [corr-abc-123] DEBUG -   Surveyor Alice Surveyor (ID: a1b2c3d4-...-001) workload: 3 active assignments
[2026-05-01 10:15:46] [corr-abc-123] DEBUG -   Surveyor Carol Surveyor (ID: a1b2c3d4-...-003) workload: 5 active assignments
[2026-05-01 10:15:46] [corr-abc-123] INFO  - ✅ SURVEYOR AUTO-ASSIGNED (after vehicle drop-off) | Claim: abc-123 | Surveyor: a1b2c3d4-...-001 (Alice Surveyor) | Workshop ZIP: 02101 | Current workload: 3 active assignments | Selection reason: Lowest workload in coverage area
[2026-05-01 10:15:46] [corr-abc-123] 📤    - Publishing surveyor.assigned event for claim abc-123 (trigger: vehicle drop-off confirmed)
[2026-05-01 10:15:46] [corr-abc-123] 🔔    - Publishing notification for surveyor Alice Surveyor - Vehicle ready for inspection
```

---

## Code Changes Made

### 1. Updated Kafka Listener

**Before (Wrong):**
```java
@KafkaListener(topics = "claim-events", ...)
public void handleWorkshopSelected(DomainEvent<?> event) {
    if (!"workshop.selected".equals(event.eventType())) return;
    // Assignment logic...
}
```

**After (Correct):**
```java
@KafkaListener(topics = "claim-events", ...)
public void handleVehicleDroppedOff(DomainEvent<?> event) {
    if (!"vehicle.droppedoff".equals(event.eventType())) return;  // ✅ Correct trigger
    // Assignment logic ONLY after vehicle is at workshop...
}
```

### 2. Enhanced VehicleDroppedOffPayload

**Added workshop details to event payload:**
```java
public record VehicleDroppedOffPayload(
    UUID claimId,
    UUID workshopId,
    String workshopName,        // ⭐ Added
    String workshopZipCode,     // ⭐ Added - needed for assignment
    String workshopState,       // ⭐ Added
    BigDecimal workshopLatitude,// ⭐ Added
    BigDecimal workshopLongitude,// ⭐ Added
    UUID dropOffId,
    Instant droppedOffAt,
    // ... other drop-off details
)
```

**Why:** Makes event self-contained - no need to query workshop service during assignment

### 3. Updated Method Signature

**Before:**
```java
private void autoAssignBasedOnWorkshop(WorkshopSelectedPayload payload, ...)
```

**After:**
```java
private void autoAssignBasedOnDropOff(VehicleDroppedOffPayload payload, ...)
```

---

## Testing the Corrected Flow

### Test Scenario: Happy Path

```bash
# Step 1: Submit claim
POST /api/v1/claims
# Expected: Claim created, NO surveyor assigned

# Step 2: Select workshop
POST /api/v1/claims/abc-123/select-workshop
{
  "workshopId": "w1w1w1w1-0000-0000-0000-000000000001"
}
# Expected: Workshop linked to claim, NO surveyor assigned yet

# Check Kafka - should see workshop.selected event
# Check DB - assignments table should be EMPTY for this claim

# Step 3: Drop off vehicle ⭐ ASSIGNMENT TRIGGER
POST /api/v1/claims/abc-123/vehicle-dropoff
{
  "mileage": 45000,
  "fuelLevel": "THREE_QUARTERS",
  "photosUploaded": true
}

# Expected:
# 1. vehicle.droppedoff event published
# 2. AutoAssignmentService triggered
# 3. Surveyor assignment created
# 4. surveyor.assigned event published
# 5. Notifications sent

# Verify:
SELECT * FROM workflow.assignments WHERE claim_id = 'abc-123';
# Should now have a record with assigned_at = timestamp of drop-off

# Check logs for:
# "🚗 Vehicle dropped off for claim abc-123 - NOW triggering surveyor auto-assignment"
# "✅ SURVEYOR AUTO-ASSIGNED (after vehicle drop-off)"
```

---

## Customer Journey (Corrected)

### What Customer Sees

**Step 1: Submit Claim**
```
✅ Claim submitted
Claim ID: ABC-123
Status: Submitted
```

**Step 2: Select Workshop**
```
✅ Workshop selected: Boston Auto Repair
📍 123 Main St, Boston, MA 02101
Status: Workshop Selected
⚠️  Next: Please drop off your vehicle
```

**Step 3: Drop Off Vehicle** ⭐
```
✅ Vehicle drop-off confirmed
Drop-off time: 10:15 AM
Status: Vehicle at Workshop
⏳ Surveyor assignment in progress...
```

**Step 4: Surveyor Assigned** (automatic, seconds later)
```
✅ Surveyor assigned: Alice Surveyor
Status: Assigned - Survey Scheduled
Expected survey: Within 24 hours
```

---

## Key Takeaways

### ✅ Correct Understanding
- Surveyor assignment happens AFTER vehicle drop-off
- Drop-off event includes workshop location (ZIP) for assignment
- Assignment based on workshop location (where car IS)
- Not based on incident location (where accident WAS)

### 🎯 Operational Benefits
- Surveyor knows vehicle is ready for inspection
- No wasted trips to workshop before vehicle arrives
- Clear trigger: "Vehicle here → send surveyor"
- Efficient resource utilization

### 📋 Event Sequence
1. `claim.created` - No assignment
2. `workshop.selected` - No assignment
3. `vehicle.droppedoff` - **⭐ Assignment triggered**
4. `surveyor.assigned` - Assignment complete

---

## Status: ✅ CORRECTED AND IMPLEMENTED

All code has been updated to reflect the correct event flow.
