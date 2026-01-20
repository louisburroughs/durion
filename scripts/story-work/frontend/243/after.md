STOP: Clarification required before finalization

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

---

## 1. Story Header

**Title:** Fulfillment: Issue/Consume Picked Items to Workorder (Frontend, Moqui)

**Primary Persona:** Parts Manager / Technician (POS user issuing parts to a workorder)

**Business Value:** Ensure picked parts are formally consumed against a workorder so on-hand inventory is decremented, inventory ledger is updated, and downstream job costing/accounting is accurate and auditable.

---

## 2. Story Intent

**As a** Parts Manager or Technician  
**I want** to review picked items for a workorder and submit a “Consume/Issue” action for specified quantities  
**So that** inventory is decremented and the workorder is accurately charged with consumed parts.

### In-scope
- Moqui UI flow to:
  - load a workorder’s picked items
  - enter/confirm consumption quantities (up to picked quantity)
  - submit consumption to backend
  - show success/failure outcomes and updated state
- Frontend validation aligned to backend rules (no over-consumption, only picked items, etc.)
- Basic audit visibility in UI (who/when consumed; at minimum confirmation + timestamp returned)

### Out-of-scope
- Picking/staging parts for a workorder (assumed already done elsewhere)
- Returning consumed items to stock (handled by separate “Return to Stock” flow/story)
- Editing inventory costs (standard/last/average) and cost policy decisions
- Implementing backend inventory ledger/event emission (frontend only calls services)

---

## 3. Actors & Stakeholders
- **Primary users:** Parts Manager, Technician
- **Secondary users:** Service Advisor (visibility), Inventory Manager (accuracy), Accounting (downstream consumption/cost attribution)
- **Systems:** Inventory service (authoritative consumption), Workorder service (workorder state/identity)

---

## 4. Preconditions & Dependencies

### Preconditions
- Workorder exists and is in a backend-allowed state for consumption (exact states TBD; backend example suggests “In Progress” allowed, “Completed/On Hold/Cancelled” not allowed).
- One or more items have been “Picked” for the workorder (backend concept: picked items associated to workorder).
- User is authenticated and has permission to consume inventory for a workorder (exact permission string TBD).

### Dependencies
- Backend endpoint to load “picked items for workorder” (contract TBD).
- Backend endpoint to consume picked items: backend reference proposes `POST /v1/inventory/consume`.
- Consistent identifiers:
  - `workorderId`
  - `pickedItemId` (or equivalent line id)
  - `sku` (if required)
- Error response format (field-level vs general errors) to map into Moqui forms.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Workorder detail screen: action button/link “Consume Picked Items”
- Optional: from Fulfillment/Picking module menu, search/select workorder then “Consume”

### Screens to create/modify
1. **Modify**: `WorkorderDetail` screen  
   - Add action: `Consume Picked Items` visible when workorder state allows consumption AND picked items exist (visibility depends on load API; if unknown, show and handle backend denial gracefully).
2. **Create**: `WorkorderConsumePickedItems` screen (new)
   - Shows list of picked items with:
     - SKU / description (if available)
     - picked quantity
     - already consumed quantity (if available)
     - remaining consumable quantity (derived if available)
     - input: quantity to consume now (default behavior defined below)
   - Submit action “Consume”
   - Cancel/back to workorder

### Navigation context
- Route pattern (proposed): `/workorders/:workorderId/fulfillment/consume-picked`
- Breadcrumb: Workorders → Workorder #{id} → Fulfillment → Consume Picked Items

### User workflows
**Happy path**
1. User opens workorder → Consume Picked Items
2. Screen loads picked items
3. User confirms quantities (often “consume all remaining”)
4. User submits
5. UI shows success confirmation + returns to workorder detail (or stays and reloads list showing updated quantities/states)

**Alternate paths**
- Consume partial quantities for some lines, leave others as 0
- Backend rejects due to invalid workorder state or stale picked quantities → UI shows error and reload option

---

## 6. Functional Behavior

### Triggers
- Entering `WorkorderConsumePickedItems` screen triggers load of workorder header (optional) + picked items list.
- Clicking `Consume` triggers consumption submit.

### UI actions
- Per picked line:
  - numeric input `qtyToConsume`
  - convenience action “Max” sets to remaining picked qty (if remaining known; else sets to picked qty)
- Global:
  - “Consume” submits only lines with `qtyToConsume > 0`
  - “Consume All” (optional) sets all lines to max and submits (if included, must be explicitly implemented)

### State changes (frontend-visible)
- After successful submit:
  - show confirmation summary: number of lines consumed, total quantity
  - refresh picked list to reflect new statuses/remaining quantities
  - optionally navigate back to workorder detail

### Service interactions
- Load picked items for workorder (read)
- Submit consumption (write)
- On submit success: reload picked items (read) to ensure UI consistency

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `qtyToConsume` must be:
  - numeric
  - > 0 to be included in request
  - integer vs decimal **(Open Question; inventory quantities might be fractional depending on UOM)**
- Must not exceed picked quantity (or remaining consumable quantity if provided)
- Must not allow consuming lines not in “Picked” status (if status exposed in UI)

### Enable/disable rules
- Disable “Consume” button when:
  - no lines with `qtyToConsume > 0`
  - any line has validation error
  - submit in progress (prevent double-submit)
- Disable quantity input for lines not eligible (e.g., already consumed/cancelled) if status provided.

### Visibility rules
- Show the screen only when user can access workorder; otherwise show unauthorized.
- If backend indicates workorder state disallows consumption, show read-only message and hide submit.

### Error messaging expectations
- Map backend errors:
  - `400` validation errors: highlight offending line(s) when possible; otherwise show banner with message
  - `403` forbidden: show “You don’t have permission to consume inventory.”
  - `404` not found: “Workorder not found” or “Picked item not found”
  - `409` conflict: show “Workorder is not in a state that allows consumption” OR “Picked quantities changed; please reload”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- Workorder (read-only identity/state)
- PickedItem (picked line associated to workorder)
- Consumption submission payload (not necessarily persisted in frontend)
- Optional: consumption result / ledger summary (read-only response)

### Fields (type, required, defaults)
**Workorder (read)**
- `workorderId` (string, required)
- `status` (string, required for gating if provided)

**PickedItem (read)**
- `pickedItemId` (string, required)
- `sku` (string, required if backend needs it for submit; otherwise display-only)
- `productName/description` (string, optional)
- `qtyPicked` (number, required)
- `qtyConsumed` (number, optional)
- `status` (enum/string, optional but useful: Picked/Consumed/etc.)
- `uom` (string, optional; impacts validation)

**UI Input (editable)**
- `qtyToConsume` (number, default: 0; see Applied Safe Defaults)

**Derived/calculated fields (frontend)**
- `qtyRemaining = qtyPicked - qtyConsumed` (only if `qtyConsumed` available; else not derived)
- `lineEligible = (status == Picked)` if status available

### Read-only vs editable by state/role
- Workorder fields read-only
- Picked item identity fields read-only
- Quantity input editable only when:
  - user has permission (if permission surfaced; otherwise backend enforces)
  - workorder is in allowed state (if known)
  - line eligible (if status known)

---

## 9. Service Contracts (Frontend Perspective)

> Backend contracts are not fully authoritative in provided inputs; below are **proposed** contracts derived from backend reference and must be confirmed.

### Load/view calls
1. **Get picked items for workorder**
   - Proposed: `GET /v1/workorders/{workorderId}/picked-items`
   - Response: array of PickedItem with fields listed above
   - Errors: 403/404

2. **(Optional) Get workorder header**
   - Proposed: `GET /v1/workorders/{workorderId}`
   - Used to display status and guard actions

### Create/update calls
- none (frontend only submits consumption)

### Submit/transition calls
1. **Consume picked items**
   - Proposed: `POST /v1/inventory/consume`
   - Request body (from backend reference):
     ```json
     {
       "workorderId": "wo-12345",
       "items": [
         { "pickedItemId": "pick-abc-789", "sku": "SKU-OILFILTER-A", "quantity": 1 }
       ]
     }
     ```
   - Frontend rules:
     - include only items with `quantity > 0`
     - send `pickedItemId` always; include `sku` only if required by backend (Open Question)
   - Success:
     - `200 OK` with summary payload (Open Question: response schema)
   - Errors:
     - `400` validation details
     - `403` forbidden
     - `409` conflict (invalid state / concurrency)

### Error handling expectations
- Moqui screen actions should:
  - capture HTTP status + message
  - bind field errors to form/list rows when backend provides item-level error identifiers (Open Question)
  - show a retry/reload option on 409 conflict

---

## 10. State Model & Transitions

### Allowed states (relevant to this screen)
**Workorder states**
- Allowed: at least `In Progress` (from backend reference)
- Disallowed: `Completed`, `On Hold`, `Cancelled` (from backend reference)
- **Exact enumeration and mapping is unknown (Open Question).**

**Picked item states**
- `Picked` → `Consumed` on successful consume
- Consumption is immutable; reversal requires separate “Return” transaction (out-of-scope)

### Role-based transitions
- Users with appropriate inventory fulfillment permission can submit consumption.
- Others can view but cannot submit (or receive 403 on submit).
- **Exact permission name(s) for frontend gating unknown (Open Question).**

### UI behavior per state
- If workorder disallowed: show message + disable submit.
- If line not Picked: disable its input and do not submit it.
- If all lines ineligible: show empty/none-eligible state.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Quantity entered > picked/remaining:
  - show inline error on that line
  - disable submit until corrected
- Quantity <= 0:
  - treated as “not selected”; no error unless user typed negative (then show error)

### Concurrency conflicts
- Picked quantities changed since load:
  - backend returns 409 or 400 with detail
  - UI shows conflict message and offers “Reload picked items”
  - after reload, preserve user-entered quantities where still valid; otherwise reset to 0 (needs confirmation; Open Question)

### Unauthorized access
- User opens screen:
  - if load endpoints return 403: show “Not authorized” and link back
- User submits and gets 403:
  - show error; keep inputs but disable further submit until reload/login

### Empty states
- No picked items:
  - show “No picked items available to consume for this workorder.”
  - provide navigation back to workorder

---

## 12. Acceptance Criteria

### Scenario 1: Load consume screen with picked items
**Given** I am an authenticated user with access to workorder `WO-123`  
**And** `WO-123` has 2 picked items associated to it  
**When** I navigate to `/workorders/WO-123/fulfillment/consume-picked`  
**Then** the system displays the picked items list including picked quantity for each line  
**And** each line has an input to enter a consume quantity  
**And** the Consume action is disabled until at least one line has a quantity > 0

### Scenario 2: Successfully consume all picked quantities
**Given** workorder `WO-123` is in an allowed state for consumption  
**And** item line `PICK-1` has `qtyPicked = 2` and is eligible to consume  
**When** I enter `qtyToConsume = 2` for `PICK-1` and click “Consume”  
**Then** the frontend submits a consumption request containing `workorderId = WO-123` and `pickedItemId = PICK-1` with quantity 2  
**And** I see a success confirmation  
**And** the picked items list is reloaded after success

### Scenario 3: Prevent consuming more than picked (client-side)
**Given** item line `PICK-1` shows `qtyPicked = 1`  
**When** I enter `qtyToConsume = 2`  
**Then** the line shows a validation error indicating the quantity exceeds picked quantity  
**And** the Consume action remains disabled

### Scenario 4: Backend rejects because workorder is not consumable (409)
**Given** workorder `WO-999` is `Completed` (or another disallowed state)  
**When** I attempt to consume any picked item quantities and click “Consume”  
**Then** the backend responds with HTTP `409`  
**And** the frontend displays an error message that the workorder is not in a valid state for consumption  
**And** no success confirmation is shown  
**And** the user can click “Reload” to refresh workorder/items

### Scenario 5: Backend rejects due to item not associated/picked (400)
**Given** I am on the consume screen for `WO-123`  
**When** the backend responds `400 Bad Request` indicating a submitted line is not picked for `WO-123`  
**Then** the frontend displays the error message returned by the backend  
**And** the user remains on the consume screen with inputs preserved

### Scenario 6: Unauthorized submit (403)
**Given** I can view the workorder but do not have permission to consume inventory  
**When** I click “Consume”  
**Then** the backend responds with `403 Forbidden`  
**And** the frontend shows a permission error message  
**And** no inventory consumption success state is shown

---

## 13. Audit & Observability

### User-visible audit data
- On success, show:
  - confirmation timestamp (client time) and optionally server timestamp if returned
  - number of lines/total quantity consumed
- If backend returns consumption transaction id(s), display them (Open Question).

### Status history
- Not required to render full ledger, but screen should reflect post-consumption status via reload.

### Traceability expectations
- Frontend must include correlation/request id headers if project convention exists (Open Question; otherwise rely on standard HTTP tracing).
- Log (frontend console/logging facility) submit attempts and outcomes without leaking sensitive data.

---

## 14. Non-Functional UI Requirements
- **Performance:** initial load < 2s for up to 50 picked lines (pagination if larger; see safe defaults)
- **Accessibility:** WCAG 2.1 AA; keyboard-navigable table/list and inputs; error messages announced to screen readers
- **Responsiveness:** usable on tablet resolutions common in shop floors
- **i18n/timezone/currency:** quantities only; timestamps shown in user locale/timezone if displayed

---

## 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE**
  - **Assumed:** Explicit empty state messaging when no picked items exist.
  - **Why safe:** Pure UI ergonomics; does not change business rules.
  - **Impacted sections:** UX Summary, Alternate/Empty states.
- **SD-UX-PAGINATION-DEFAULT**
  - **Assumed:** Paginate picked items list at 50 rows/page if backend returns more.
  - **Why safe:** UI scalability only; does not affect domain logic.
  - **Impacted sections:** Non-Functional UI Requirements, UX Summary.
- **SD-ERR-HTTP-MAP**
  - **Assumed:** Map HTTP 400/403/404/409 to user-facing banner + inline field errors when identifiable.
  - **Why safe:** Standard error handling; aligns with backend status semantics without inventing policy.
  - **Impacted sections:** Business Rules (errors), Service Contracts, Alternate/Error flows.

---

## 16. Open Questions

1. What is the **authoritative frontend route/screen naming convention** in `durion-moqui-frontend` for workorder subflows (exact URL path, screen location, menu integration)?
2. What backend endpoint should the frontend use to **load picked items for a workorder** (path + response schema)? Is it owned by inventory service or workorder service?
3. For the consume submit call, is `sku` required in addition to `pickedItemId`, or is `pickedItemId` sufficient?
4. Are consumption quantities **integer-only** or can they be fractional (UOM-driven)? If fractional, what precision and step rules apply?
5. What are the exact **workorder statuses** that allow consumption, and what are their canonical string values?
6. What permission/role governs consumption (e.g., `inventory.consume`, `inventory.fulfillment.consume`)? Should the frontend gate the action based on permissions endpoint/claims, or rely solely on backend enforcement?
7. What is the expected **success response payload** from `POST /v1/inventory/consume` (transaction ids, updated lines, messages)? Should the UI show a receipt-like summary?
8. Concurrency handling: if reloading after a conflict, should the UI attempt to **preserve user-entered quantities** that remain valid, or reset all to 0?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Fulfillment: Issue/Consume Picked Items to Workorder — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/243

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Fulfillment: Issue/Consume Picked Items to Workorder  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/243  
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #236 - Fulfillment: Issue/Consume Picked Items to Workorder  
**URL**: https://github.com/louisburroughs/durion/issues/236  
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
*Generated by Missing Issues Audit System - 2025-12-26T17:36:31.497882803*

====================================================================================================