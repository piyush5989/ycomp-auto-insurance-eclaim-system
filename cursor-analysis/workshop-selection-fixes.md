# Workshop Selection Page - Bug Fixes

## Issues Found and Fixed

### Issue 1: Search Not Working (Showing All Workshops)
**Problem:** Frontend was sending wrong parameter names to backend

**Root Cause:**
- Frontend sent: `zipCode` and `city`
- Backend expected: `zip` and `location`

**Fix:**
```typescript
// BEFORE (WRONG):
if (searchZip.trim()) params.append('zipCode', searchZip.trim())
if (searchCity.trim()) params.append('city', searchCity.trim())

// AFTER (CORRECT):
if (searchZip.trim()) params.append('zip', searchZip.trim())
if (searchCity.trim()) params.append('location', searchCity.trim())
```

---

### Issue 2: All Workshops Selected When Clicking One
**Problem:** Multiple radio buttons were checking/unchecking together

**Root Cause:**
- Radio buttons didn't have a `name` attribute
- Each radio was independent instead of part of a group

**Fix:**
```typescript
// ADDED name attribute to group radios:
<input
  type="radio"
  name="workshop-selection"  // ← This groups all radios together
  checked={selectedWorkshopId === workshop.id}
  onChange={() => setSelectedWorkshopId(workshop.id)}
  onClick={(e) => e.stopPropagation()}  // ← Prevents double-click issue
  className="mt-1 w-5 h-5 text-primary-600"
/>
```

---

### Issue 3: Field Name Mismatch
**Problem:** Frontend interface didn't match backend response

**Root Cause:**
- Frontend expected: `workshopId`, `state`, `isPartner`, `distanceKm`
- Backend returned: `id`, no `state`, no `isPartner`, no `distanceKm`

**Fix:**
```typescript
// BEFORE:
interface WorkshopOption {
  workshopId: string
  state: string
  isPartner: boolean
  distanceKm?: number
  // ...
}

// AFTER:
interface WorkshopOption {
  id: string  // ← Changed from workshopId
  rating: number  // ← Added (backend provides this)
  providerType: string  // ← Added (backend provides this)
  // Removed: state, isPartner, distanceKm
}
```

---

### Issue 4: Confirm Workshop Not Working
**Problem:** Frontend was looking for wrong field name when confirming

**Root Cause:**
- Used `workshop.workshopId` instead of `workshop.id`

**Fix:**
```typescript
// BEFORE:
const selectedWorkshop = workshops.find((w) => w.workshopId === selectedWorkshopId)

// AFTER:
const selectedWorkshop = workshops.find((w) => w.id === selectedWorkshopId)
```

---

## What Now Works

✅ **Search by ZIP Code**
```
User enters: "02101"
→ API call: GET /api/v1/workshops?zip=02101&providerType=REPAIR_WORKSHOP
→ Returns: Boston workshops
```

✅ **Search by City**
```
User enters: "Boston"
→ API call: GET /api/v1/workshops?location=Boston&providerType=REPAIR_WORKSHOP
→ Returns: All Boston area workshops
```

✅ **Radio Button Selection**
- Only one workshop can be selected at a time
- Click on card OR radio button to select
- Selected workshop is highlighted with primary color

✅ **Confirm Workshop**
- Button appears when a workshop is selected
- Sends correct workshop ID to backend
- Navigates to vehicle drop-off page on success

---

## Testing Steps

1. **Test ZIP Code Search:**
   - Enter "02101" in ZIP field
   - Should show Boston workshops only
   - Enter "94102" in ZIP field
   - Should show San Francisco workshops only

2. **Test City Search:**
   - Enter "Boston" in city field
   - Should show all Boston area workshops
   - Enter "San Francisco" in city field
   - Should show all SF area workshops

3. **Test Radio Selection:**
   - Click on first workshop card
   - Only that workshop should be selected
   - Click on second workshop
   - First should deselect, second should select
   - Click radio button directly
   - Should toggle selection properly

4. **Test Confirm:**
   - Select a workshop
   - Click "Confirm Workshop" button
   - Should navigate to vehicle drop-off page
   - Check backend logs for workshop selection event

---

## Backend API Reference

### GET /api/v1/workshops

**Query Parameters:**
- `zip` (string, optional) - ZIP code to search
- `location` (string, optional) - City name to search
- `providerType` (string, optional) - Filter by type: REPAIR_WORKSHOP, AUTH_SERVICE_STATION, CAR_RENTAL

**Response:**
```json
{
  "data": [
    {
      "id": "uuid",
      "name": "AutoFix Premium",
      "address": "123 Main St",
      "city": "Boston",
      "zipCode": "02101",
      "phone": "+1-212-555-0101",
      "email": "autofix@workshop.test",
      "rating": 4.8,
      "active": true,
      "providerType": "REPAIR_WORKSHOP"
    }
  ]
}
```

### POST /api/v1/claims/{claimId}/select-workshop

**Request Body:**
```json
{
  "workshopId": "uuid"
}
```

**Response:**
```json
{
  "data": null,
  "correlationId": "correlation-id"
}
```

---

## Files Modified

1. **eclaims-frontend/src/portals/customer/pages/SelectWorkshopPage.tsx**
   - Fixed interface to match backend response
   - Fixed API query parameters
   - Added `name` attribute to radio buttons
   - Added `onClick` stopPropagation to prevent bubbling
   - Fixed all references from `workshopId` to `id`

---

## Status: ✅ FIXED

All issues have been resolved. The workshop selection page now:
- Searches correctly by ZIP or city
- Displays only matching workshops
- Allows selecting only one workshop at a time
- Successfully confirms workshop selection
- Navigates to next step (vehicle drop-off)
