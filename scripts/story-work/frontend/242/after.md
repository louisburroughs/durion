## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

STOP: Clarification required before finalization

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Fulfillment: Return Unused Items to Stock with Reason

**Primary Persona:** Warehouse Manager (primary); Service Advisor (secondary)

**Business Value:** Enables returning unused, saleable items from completed/closed work orders back into inventory with a mandatory reason per item, improving inventory accuracy and traceability for operations and downstream accounting.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Warehouse Manager or Service Advisor,  
- **I want to** return unused items from a completed/closed work order back into stock while providing a reason for each returned item,  
- **So that** inventory on-hand is accurate, returned items are available for future work, and returns are auditable with standardized reason codes.

### In-scope
- A Moqui/Vue/Quasar UI flow to:
  - Select a completed/closed work order eligible for returns
  - Display items consumed by that work order and the max-returnable quantities
  - Enter return quantities (<= consumed and > 0) for one or more items
  - Select a **mandatory** return reason per returned line
  - Submit a single atomic “return to stock” transaction
  - Display a success confirmation including a return transaction identifier
- Frontend validation and server error handling consistent with backend rules
- Read-only visibility of key audit fields for the created return record (as returned by API)

### Out-of-scope
- Financial reconciliation / accounting adjustments beyond emitting/consuming the return result (Accounting consumes emitted event; frontend does not implement accounting)
- Defining or managing reason codes (assumed preconfigured)
- Changing work order state or consumed quantities from this screen
- Any costing/valuation calculations (Inventory domain remains authoritative; frontend only displays returned values)

---

## 3. Actors & Stakeholders
- **Warehouse Manager (Actor):** Processes return-to-stock transactions.
- **Service Advisor (Actor):** May process returns for their work orders.
- **Inventory System (System):** Validates eligibility, enforces rules, posts ledger entries, updates on-hand.
- **Accounting (Stakeholder):** Depends on accurate inventory and emitted events (not directly interacted with by UI).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend.
- User has permission to create returns (backend permission example given: `inventory:return:create`; final permission string must match backend).
- Target work order exists and is in **Completed** or **Closed** state.
- Work order has at least one **consumed** item line eligible for return (may be zero; UI must handle empty state).
- Backend provides a non-empty list of valid return reason codes.

### Dependencies (Blocking where unclear)
- Backend API contract for:
  - Loading work order consumed items and max-returnable quantities
  - Listing return reason codes
  - Submitting return transaction and receiving `inventoryReturnId` (or equivalent)
- Routing/location context to determine **which stock location** receives returned items (see Open Questions)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From a Work Order detail view (Completed/Closed): action button/link **“Return Items to Stock”**
- Optional: from an Inventory/Fulfillment menu: “Returns” → “Return from Work Order” (only if consistent with existing navigation; otherwise keep to Work Order detail entry point)

### Screens to create/modify (Moqui)
1. **Modify Work Order Detail screen** to include the entry action when eligible:
   - Show action only when work order state ∈ {Completed, Closed} and user has permission.
2. **New screen:** `apps/pos/screen/workorder/ReturnItems.xml` (name indicative; final path must match repo conventions)
   - Parameters: `workOrderId` required
   - Loads:
     - work order header (id, state)
     - consumed items list with consumed qty and returnable qty
     - reason codes list
   - Form/table to select return quantities + reasons per line
   - Submit transition to a service call (REST or Moqui service façade)

### Navigation context
- Breadcrumb/back navigation returns to Work Order detail.
- On successful submission, navigate to:
  - Either a “Return Confirmation” subview, **or**
  - Back to Work Order detail with a visible confirmation banner including return transaction id.
  (Final decision depends on repo pattern; see Open Questions if unclear.)

### User workflows
**Happy path**
1. User opens a completed/closed work order.
2. Clicks “Return Items to Stock”.
3. Sees a list of consumed items with maximum returnable quantity.
4. Enters return quantity for one or more items and selects a reason per selected item.
5. Confirms submission.
6. Sees success message with return transaction id.

**Alternate paths**
- Work order has no consumed items → empty state; no submit allowed.
- User tries to return > allowed → inline validation errors.
- User omits a reason for a returned line → inline validation errors.
- Backend rejects due to state change or permissions → show message and disable submit.

---

## 6. Functional Behavior

### Triggers
- User clicks “Return Items to Stock” action from a work order.
- Screen load triggers data fetch for work order eligibility, consumed items, and reason codes.
- Submit triggers a single backend “return to stock” request.

### UI actions
- Per line item:
  - Quantity input (integer, min 0; only >0 lines are considered “selected” unless UI includes explicit checkbox)
  - Reason dropdown (required when quantity > 0)
- Submit button:
  - Disabled until at least one line has quantity > 0 and all such lines have reasons selected
- Cancel button:
  - Returns to prior screen without changes

### State changes (frontend)
- Local form state:
  - `returnLines[]` derived from consumed items
  - `dirty` tracking for unsaved changes prompt (optional safe default; see Applied Safe Defaults)
- Post-submit:
  - Clear form state and show confirmation
  - Refresh work order view data if returning to detail (to reflect updated availability/return history if shown)

### Service interactions (frontend perspective)
- `GET` load: work order + consumed items (+ optionally existing returns summary)
- `GET` load: reason codes
- `POST` submit: create return transaction with lines
- Handle backend validation errors and map to line-level messages where possible

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Work order state must be **Completed** or **Closed**:
  - If not, UI must not allow starting the flow (hide entry action).
  - If state changes between entry and submit, backend will reject; UI shows error and guides user back.
- Return quantity rules per line:
  - Must be an integer
  - Must be **> 0** to be included in submission
  - Must be **<= maxReturnableQty** (typically consumed minus previously returned; depends on backend)
- Return reason rules:
  - **Mandatory** for every line with quantity > 0
  - Selected from a predefined list of reason codes (no free-text unless backend supports; not assumed)

### Enable/disable rules
- Submit disabled until:
  - At least one line has quantity > 0
  - All selected lines have a reason
  - No validation errors present
  - Not currently submitting
- Quantity input disabled for lines with `maxReturnableQty = 0`
- Entire form read-only if user lacks permission (or block access with 403 handling)

### Visibility rules
- Show “max returnable” and “consumed qty” read-only per item.
- Show a banner if there are no eligible items.
- Show an error banner for authorization failure and hide form.

### Error messaging expectations
- Inline per-line errors for:
  - quantity exceeds max returnable
  - missing reason
- Page-level errors for:
  - work order not eligible
  - network/server failure
  - unauthorized (403)
- Messages should be actionable, e.g.:
  - “Return quantity cannot exceed the returnable quantity (X).”
  - “Select a return reason.”
  - “Returns can only be processed for completed or closed work orders.”

---

## 8. Data Requirements

### Entities involved (conceptual; frontend consumes via API)
- WorkOrder (external/domain-owned; referenced)
- Consumed work order items (work order fulfillment/parts consumption records)
- InventoryReturn (created by backend)
- InventoryLedger entries (backend side-effect; UI does not create directly)
- ReturnReasonCode (lookup list)

### Fields (type, required, defaults)

**Screen input model (frontend)**
- `workOrderId` (string/uuid, required; route param)
- `locationId` (string/uuid, required?) — **unclear** how chosen; see Open Questions
- `returnLines[]`:
  - `sku` (string, required, read-only)
  - `productId` (string/uuid, optional read-only if provided)
  - `description` (string, optional read-only)
  - `consumedQty` (integer, read-only)
  - `maxReturnableQty` (integer, read-only)
  - `quantityReturned` (integer, editable; default 0)
  - `returnReasonCode` (string/enum, editable; required when quantityReturned > 0)

**Response fields to display on success**
- `inventoryReturnId` (string/uuid, required)
- `processedAt` (timestamp UTC, required)
- `processedByUserId` (string/uuid, optional display if provided)
- returned line summary (sku, qty, reason)

### Read-only vs editable by state/role
- Editable only if:
  - work order eligible
  - user has permission
- Always read-only:
  - consumedQty, maxReturnableQty, sku/description
- Post-submission: form becomes read-only or navigates away

### Derived/calculated fields (frontend)
- `selectedLines = returnLines.filter(l => l.quantityReturned > 0)`
- client-side validation flags based on `maxReturnableQty`
- Submit payload derived from `selectedLines`

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is referenced from backend story #177 but exact endpoints are TBD. Frontend must be implemented to align with actual backend routes once confirmed.

### Load/view calls
1. **Load work order & returnable lines**
   - Proposed: `GET /v1/workorders/{workOrderId}/returnable-items`
   - Response shape (minimum needed):
     - workOrder: `{ workOrderId, status }`
     - location context (optional): `{ defaultReturnLocationId, locationName }`
     - items: `[ { sku, description, consumedQty, maxReturnableQty } ]`

2. **Load return reason codes**
   - Proposed: `GET /v1/inventory/return-reasons`
   - Response: `[ { code, label, active } ]` (active filtered server-side preferred)

### Create/update calls
- None (no draft saving assumed)

### Submit/transition calls
1. **Submit return transaction**
   - Proposed: `POST /v1/inventory/returns`
   - Request:
     ```json
     {
       "workOrderId": "uuid",
       "locationId": "uuid",
       "items": [
         { "sku": "SKU-123", "quantityReturned": 2, "returnReasonCode": "NOT_NEEDED" }
       ]
     }
     ```
   - Response (success 201/200):
     ```json
     {
       "inventoryReturnId": "uuid",
       "workOrderId": "uuid",
       "locationId": "uuid",
       "processedAt": "timestamp_utc",
       "processedByUserId": "uuid",
       "items": [
         { "sku": "SKU-123", "quantityReturned": 2, "returnReasonCode": "NOT_NEEDED" }
       ]
     }
     ```

### Error handling expectations
- `400` Validation errors:
  - Map field errors to line items if error payload identifies `sku` or line index.
- `403` Forbidden:
  - Show “You do not have permission to return items to stock.”
- `404` Not found:
  - Work order not found → show not found state and link back.
- `409` Conflict:
  - Work order state changed or concurrency issue → show conflict message and offer reload.
- `5xx`:
  - Show generic failure and allow retry; ensure idempotency guidance from backend (unknown; see Open Questions).

---

## 10. State Model & Transitions

### Allowed states (work order eligibility)
- Eligible: `Completed`, `Closed`
- Ineligible: any other state (e.g., Open/In Progress/Cancelled/etc.)

### Role-based transitions
- Not changing work order state.
- Return submission allowed only for roles with permission `inventory:return:create` (exact permission string TBD).

### UI behavior per state
- Work order state eligible:
  - Show entry point action
  - Allow form interaction
- Work order state ineligible:
  - Hide action; if user navigates directly to URL, show blocked message and no submit
- After successful return:
  - Show success confirmation; prevent duplicate submit via disabled button and request in-flight lock

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Quantity > maxReturnableQty:
  - Inline error on quantity field; submit disabled
- Missing reason when qty > 0:
  - Inline error on reason field; submit disabled
- No items selected (all qty 0):
  - Submit disabled; helper text “Enter a quantity to return.”

### Concurrency conflicts
- Work order transitions out of Completed/Closed after screen load:
  - Backend returns 409/400; UI shows banner “Work order is no longer eligible for returns. Reload to view current status.”
- Returnable quantities changed (another return processed elsewhere):
  - Backend rejects with validation; UI shows errors and offers “Reload returnable quantities”.

### Unauthorized access
- 403 on load or submit:
  - Show access denied state; hide form; provide navigation back

### Empty states
- No consumed/returnable items:
  - Show message “No consumed items are eligible to return to stock.”
  - Hide/disable submit

---

## 12. Acceptance Criteria

### Scenario 1: Successful return of unused items with reason
**Given** a work order in `Completed` state with a consumed line for `SKU-123` with `maxReturnableQty = 5`  
**And** the user has permission to create inventory returns  
**And** the return reason codes list includes `NOT_NEEDED`  
**When** the user opens “Return Items to Stock” for that work order  
**And** enters `quantityReturned = 2` for `SKU-123`  
**And** selects return reason `NOT_NEEDED` for `SKU-123`  
**And** submits the return  
**Then** the frontend sends a single return submission request containing `workOrderId`, `locationId`, and the selected line with quantity and reason  
**And** the UI displays a success confirmation containing `inventoryReturnId`  
**And** the submit control is disabled while the request is in flight to prevent duplicate submission.

### Scenario 2: Attempt to return more than allowed (client-side)
**Given** a work order returnable line for `SKU-123` with `maxReturnableQty = 5`  
**When** the user enters `quantityReturned = 6`  
**Then** the UI shows an inline validation error indicating the quantity exceeds the returnable amount  
**And** the submit action is disabled.

### Scenario 3: Attempt to submit without a reason
**Given** a work order returnable line for `SKU-123` with `maxReturnableQty = 5`  
**When** the user enters `quantityReturned = 1` for `SKU-123`  
**And** does not select a return reason  
**Then** the UI shows an inline validation error for missing reason  
**And** the submit action is disabled  
**Or** if submit is attempted, the UI blocks submission and focuses the missing reason field.

### Scenario 4: Work order not eligible (state changed)
**Given** the user opens the return screen for a work order that is `Completed`  
**When** the work order becomes not `Completed` or `Closed` before submission  
**And** the user submits a return  
**Then** the backend responds with an error indicating the work order is not eligible  
**And** the UI displays a non-field error “Returns can only be processed for completed or closed work orders.”  
**And** the UI offers a reload/back action.

### Scenario 5: Unauthorized user
**Given** the user lacks permission to create inventory returns  
**When** the user navigates to the return screen URL  
**Then** the UI shows an access denied state  
**And** does not display an editable return form.

---

## 13. Audit & Observability

### User-visible audit data
- On success, show:
  - `inventoryReturnId`
  - `processedAt` (displayed in user locale, sourced from UTC)
  - Summary of returned items (sku, qty, reason)
- Optional (if backend provides): processed by user display name

### Status history / traceability expectations
- The return transaction must be traceable by `inventoryReturnId` for support.
- Frontend should include correlation/request id in logs if provided by API responses/headers (do not invent if not available).

---

## 14. Non-Functional UI Requirements
- **Performance:** Initial load should avoid N+1 calls; at most 2 calls (returnable items + reason codes). Cache reason codes in-session if consistent with app patterns.
- **Accessibility:** WCAG 2.1 AA; form fields have labels; errors are announced (aria-live) and associated with inputs.
- **Responsiveness:** Works on tablet widths typical for warehouse/parts counter.
- **i18n/timezone:** Display `processedAt` using user timezone; reason labels display from backend-provided label. Currency not applicable.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty state when no returnable items are available; qualifies as safe UI ergonomics. Impacted sections: UX Summary, Error Flows.
- SD-UX-INFLIGHT-GUARD: Disable submit button and show loading indicator during submit to prevent accidental double submission; safe because it does not change domain rules. Impacted sections: Functional Behavior, Acceptance Criteria.
- SD-ERR-STD-MAP: Map HTTP 400/403/404/409/5xx to standard inline/page error presentation; safe because it follows backend semantics without inventing policy. Impacted sections: Service Contracts, Alternate/Error Flows.

---

## 16. Open Questions

1. **Return location selection:** How is `locationId` determined for the return-to-stock transaction?
   - Always the work order’s site default stock location?
   - User-selectable location (dropdown) limited by site/permissions?
2. **API contract finalization:** What are the exact backend endpoints, request/response shapes, and error payload formats for:
   - fetching returnable/consumed items
   - fetching reason codes
   - submitting a return
3. **Max returnable calculation source:** Does the backend return `maxReturnableQty` already accounting for prior returns, or must the frontend compute it?
4. **Reason code model:** Are reason codes global, site-specific, or role-specific? Do they have `active` flags and display labels provided by backend?
5. **Idempotency/double-submit:** Does the backend provide an idempotency key mechanism for `POST /inventory/returns`? If yes, what header/field should frontend send?
6. **Navigation pattern:** After success, should the UI:
   - navigate back to work order detail with banner, or
   - show a dedicated confirmation/details page for the created `InventoryReturn`?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Fulfillment: Return Unused Items to Stock with Reason — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/242

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Fulfillment: Return Unused Items to Stock with Reason  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/242  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #237 - Fulfillment: Return Unused Items to Stock with Reason  
**URL**: https://github.com/louisburroughs/durion/issues/237  
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns

### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:36:28.875577242*

====================================================================================================