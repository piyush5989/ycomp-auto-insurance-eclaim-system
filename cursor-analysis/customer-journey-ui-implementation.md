# Customer Journey UI Implementation

## ✅ What Has Been Implemented

### 1. Workshop Selection Page

**File:** `eclaims-frontend/src/portals/customer/pages/SelectWorkshopPage.tsx`

**Features:**
- Search workshops by ZIP code or city
- Display partner and external workshops
- Show workshop details (name, address, phone, distance)
- Highlight partner workshops with badge
- Select workshop and proceed to drop-off
- Responsive design with radio button selection

**Route:** `/customer/claims/:claimId/select-workshop`

**API Endpoint:** `POST /api/v1/claims/{claimId}/select-workshop`

**User Flow:**
1. Customer navigates from claim detail page
2. Enters ZIP code or city to search workshops
3. Browses available partner workshops
4. Selects preferred workshop
5. Confirms selection → Redirected to vehicle drop-off page

---

### 2. Vehicle Drop-Off Confirmation Page

**File:** `eclaims-frontend/src/portals/customer/pages/VehicleDropOffPage.tsx`

**Features:**
- Record current mileage (optional)
- Select fuel level (Full, 3/4, 1/2, 1/4, Empty)
- Checkbox for photo confirmation
- Add drop-off notes (optional, 500 char max)
- Info banner explaining what happens next
- Form validation and error handling

**Route:** `/customer/claims/:claimId/vehicle-dropoff`

**API Endpoint:** `POST /api/v1/claims/{claimId}/vehicle-dropoff`

**Request Payload:**
```json
{
  "mileage": 45000,
  "fuelLevel": "THREE_QUARTERS",
  "dropOffNotes": "Vehicle keys left with receptionist",
  "photosUploaded": true
}
```

**User Flow:**
1. Customer confirms workshop selection
2. Fills in vehicle condition details
3. Confirms drop-off
4. **⭐ This triggers surveyor auto-assignment on backend**
5. Redirected to rental vehicle page (optional step)

---

### 3. Rental Vehicle Selection Page

**File:** `eclaims-frontend/src/portals/customer/pages/RentalVehiclePage.tsx`

**Features:**
- Select rental duration (1-30 days)
- Browse available rental vehicles
- View vehicle details (make, model, year, seats, transmission, fuel type)
- See daily rate and calculated total cost
- Reserve vehicle or skip this step
- Provider information displayed

**Route:** `/customer/claims/:claimId/rental-vehicle`

**API Endpoints:**
- `GET /api/v1/rentals/vehicles?availableOnly=true`
- `POST /api/v1/rentals/reserve`

**Request Payload (Reserve):**
```json
{
  "claimId": "uuid",
  "vehicleId": "uuid",
  "rentalDays": 7
}
```

**User Flow:**
1. Customer completes vehicle drop-off
2. Optionally selects rental duration
3. Browses available vehicles
4. Selects vehicle → Reserves
5. OR clicks "Skip This Step"
6. Returns to claim detail page

---

### 4. Claim Detail Page - Journey Progress

**File:** `eclaims-frontend/src/portals/customer/pages/ClaimDetailPage.tsx` (Updated)

**New Features Added:**

#### Journey Progress Indicator
- Visual step-by-step progress bar
- Shows 3 steps: Select Workshop → Drop Off Vehicle → Rental (Optional)
- Color-coded status:
  - ✅ Completed: Green
  - 🔵 Current: Primary blue with ring
  - ⚪ Pending: Gray
- Dynamic display based on claim status

#### Action Cards
Contextual action cards appear based on claim status:

**Status: SUBMITTED**
```
┌─────────────────────────────────────────┐
│ Next Step: Select Repair Workshop      │
│ [🏢 Select Repair Workshop]            │
└─────────────────────────────────────────┘
```

**Status: WORKSHOP_SELECTED**
```
┌─────────────────────────────────────────┐
│ Next Step: Confirm Vehicle Drop-Off    │
│ [🚗 Confirm Vehicle Drop-Off]          │
└─────────────────────────────────────────┘
```

**Status: VEHICLE_AT_WORKSHOP or ASSIGNED**
```
┌─────────────────────────────────────────┐
│ Optional: Get Rental Vehicle           │
│ [🔑 Get Rental Vehicle]  [Skip]        │
└─────────────────────────────────────────┘
```

---

### 5. Updated Routes

**File:** `eclaims-frontend/src/portals/customer/CustomerPortalRoutes.tsx`

**New Routes Added:**
```tsx
<Route path="claims/:claimId/select-workshop"  element={<SelectWorkshopPage />} />
<Route path="claims/:claimId/vehicle-dropoff"  element={<VehicleDropOffPage />} />
<Route path="claims/:claimId/rental-vehicle"   element={<RentalVehiclePage />} />
```

---

## Complete User Journey Flow

### Step-by-Step Customer Experience

```
1️⃣ SUBMIT CLAIM
   ↓
   POST /api/v1/claims
   ↓
   Claim Status: SUBMITTED
   ↓
   Customer sees: "Next Step: Select Repair Workshop"

2️⃣ SELECT WORKSHOP
   ↓
   Navigate to: /customer/claims/{id}/select-workshop
   ↓
   Search workshops by ZIP/city
   ↓
   Select partner workshop
   ↓
   POST /api/v1/claims/{id}/select-workshop
   ↓
   Claim Status: WORKSHOP_SELECTED
   ↓
   ⚠️  NO surveyor assigned yet!
   ↓
   Customer sees: "Next Step: Confirm Vehicle Drop-Off"

3️⃣ DROP OFF VEHICLE ⭐ ASSIGNMENT TRIGGER
   ↓
   Navigate to: /customer/claims/{id}/vehicle-dropoff
   ↓
   Enter mileage, fuel level, notes
   ↓
   POST /api/v1/claims/{id}/vehicle-dropoff
   ↓
   Backend publishes: vehicle.droppedoff event
   ↓
   ⭐ AutoAssignmentService TRIGGERS
   ↓
   Surveyor auto-assigned based on workshop ZIP
   ↓
   Claim Status: VEHICLE_AT_WORKSHOP → ASSIGNED
   ↓
   Customer sees: "Optional: Get Rental Vehicle"

4️⃣ RENTAL VEHICLE (OPTIONAL)
   ↓
   Navigate to: /customer/claims/{id}/rental-vehicle
   ↓
   Select rental duration
   ↓
   Browse available vehicles
   ↓
   POST /api/v1/rentals/reserve
   ↓
   Rental reservation created
   ↓
   Customer redirected to claim details

5️⃣ CLAIM PROCESSING
   ↓
   Surveyor receives notification
   ↓
   Surveyor visits workshop
   ↓
   Surveyor submits assessment
   ↓
   Adjustor reviews and adjudicates
   ↓
   Customer receives approval/rejection
```

---

## Visual Design Elements

### Color Scheme
- **Step 1 (Workshop Selection):** Blue (`bg-blue-50`, `border-blue-200`)
- **Step 2 (Vehicle Drop-Off):** Green (`bg-green-50`, `border-green-200`)
- **Step 3 (Rental Vehicle):** Purple (`bg-purple-50`, `border-purple-200`)

### Icons Used
- **Workshop:** `<Building2 />`
- **Vehicle:** `<Car />`
- **Rental:** `<KeyRound />`
- **Location:** `<MapPin />`
- **Phone:** `<Phone />`
- **Success:** `<CheckCircle2 />`
- **Warning:** `<AlertTriangle />`

### UI Components
- Cards with hover effects
- Radio button selection for workshops/vehicles
- Loading spinners for async operations
- Form validation with error messages
- Responsive grid layouts (2 columns on desktop)
- Breadcrumb navigation (back arrow)
- Step indicators with visual progress

---

## Backend API Requirements

### ✅ Already Implemented
1. `POST /api/v1/claims/{claimId}/select-workshop`
   - Handler: `WorkshopController.selectWorkshop()`
   - Publishes: `workshop.selected` event

2. `POST /api/v1/claims/{claimId}/vehicle-dropoff`
   - Handler: `WorkshopController.confirmVehicleDropOff()`
   - Publishes: `vehicle.droppedoff` event
   - ⭐ **Triggers surveyor assignment**

### 🔧 TODO: Rental Endpoints

Need to create:

1. **List Available Rental Vehicles**
   ```
   GET /api/v1/rentals/vehicles?availableOnly=true
   ```
   **Response:**
   ```json
   {
     "data": [
       {
         "vehicleId": "uuid",
         "vehicleType": "SEDAN",
         "make": "Honda",
         "model": "Accord",
         "year": 2024,
         "seatingCapacity": 5,
         "transmissionType": "AUTOMATIC",
         "fuelType": "GASOLINE",
         "dailyRate": 45.00,
         "available": true,
         "providerId": "uuid",
         "providerName": "Enterprise Rent-A-Car"
       }
     ]
   }
   ```

2. **Reserve Rental Vehicle**
   ```
   POST /api/v1/rentals/reserve
   ```
   **Request:**
   ```json
   {
     "claimId": "uuid",
     "vehicleId": "uuid",
     "rentalDays": 7
   }
   ```
   **Response:**
   ```json
   {
     "data": {
       "reservationId": "uuid",
       "claimId": "uuid",
       "vehicleId": "uuid",
       "dailyRate": 45.00,
       "totalCost": 315.00,
       "reservationStart": "2026-05-01T10:00:00Z",
       "reservationEnd": "2026-05-08T10:00:00Z",
       "status": "RESERVED"
     }
   }
   ```

---

## Claim Status Flow (Updated)

| Status | Meaning | Next Action |
|--------|---------|-------------|
| `SUBMITTED` | Claim created | Customer selects workshop |
| `WORKSHOP_SELECTED` | Workshop assigned | Customer drops off vehicle |
| `VEHICLE_AT_WORKSHOP` | Vehicle physically at workshop | ⭐ Surveyor assignment triggered |
| `ASSIGNED` | Surveyor assigned | Surveyor schedules inspection |
| `UNDER_SURVEY` | Surveyor inspecting | Surveyor submits assessment |
| `SURVEYED` | Survey complete | Adjustor reviews |
| `UNDER_ADJUDICATION` | Adjustor processing | Adjustor approves/rejects |
| `APPROVED` | Claim approved | Payment processing |
| `REJECTED` | Claim rejected | Customer notified |
| `PAYMENT_INITIATED` | Payment sent | Awaiting confirmation |
| `SETTLED` | Claim closed | Complete |

---

## Responsive Design

All pages are mobile-responsive:
- **Desktop:** 2-column grid for workshop/vehicle cards
- **Tablet:** 2-column grid maintains
- **Mobile:** Single column stack
- **Navigation:** Hamburger menu (if implemented in layout)
- **Forms:** Full-width inputs on mobile

---

## Accessibility Features

- ✅ `aria-label` attributes on all interactive elements
- ✅ Keyboard navigation support
- ✅ Focus states on buttons and inputs
- ✅ Semantic HTML (proper heading hierarchy)
- ✅ Color contrast meets WCAG AA standards
- ✅ Form validation with screen-reader friendly errors

---

## Testing Checklist

### Manual Testing Steps

1. **Workshop Selection**
   - [ ] Search by ZIP code returns results
   - [ ] Search by city returns results
   - [ ] Partner badge displays correctly
   - [ ] Radio button selection works
   - [ ] Confirm button disabled until selection
   - [ ] Navigation to drop-off page after confirmation

2. **Vehicle Drop-Off**
   - [ ] Mileage input accepts valid numbers
   - [ ] Fuel level buttons toggle correctly
   - [ ] Photo checkbox works
   - [ ] Notes textarea has character counter
   - [ ] Form validation works (invalid mileage)
   - [ ] Success redirects to rental page

3. **Rental Vehicle**
   - [ ] Rental days input accepts 1-30
   - [ ] Vehicle cards display correctly
   - [ ] Selection updates total cost
   - [ ] Reserve button works
   - [ ] Skip button returns to claim detail
   - [ ] Loading states display during API calls

4. **Claim Detail Progress**
   - [ ] Progress bar shows correct step
   - [ ] Action cards appear based on status
   - [ ] Navigation buttons work
   - [ ] Visual states (completed/current/pending) accurate

---

## Performance Considerations

- **Query caching:** Workshop and vehicle queries cached for 5 minutes
- **Image optimization:** Not applicable (no image uploads in journey)
- **Lazy loading:** Pages loaded on-demand via React Router
- **API batching:** Not needed (independent API calls)
- **Error boundaries:** Should add for production

---

## Future Enhancements

### Phase 2
1. **Map Integration**
   - Show workshops on interactive map
   - Calculate distance from customer location
   - Get directions to workshop

2. **Real-time Notifications**
   - WebSocket updates when surveyor assigned
   - Push notifications for status changes
   - SMS notifications at each step

3. **Photo Upload**
   - Upload vehicle photos during drop-off
   - Image compression and optimization
   - Gallery view of uploaded photos

4. **Rental Vehicle Images**
   - Show photos of rental vehicles
   - Virtual showroom
   - 360° views

5. **Workshop Reviews**
   - Customer ratings and reviews
   - Workshop response times
   - Quality metrics

### Phase 3
1. **Live Chat with Workshop**
   - In-app messaging
   - Chat history
   - File sharing

2. **Appointment Scheduling**
   - Book drop-off time slot
   - Calendar integration
   - Reminder notifications

3. **Digital Signature**
   - Sign drop-off form digitally
   - Electronic acknowledgment
   - PDF receipt generation

---

## Status: ✅ COMPLETE

All UI pages for the customer journey have been implemented and integrated.

**Next Steps:**
1. Create rental vehicle backend endpoints (RentalController)
2. Test end-to-end flow with real data
3. Add error boundaries for production
4. Implement analytics tracking for journey steps
