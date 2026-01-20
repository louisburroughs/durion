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

### Title
[FRONTEND] [STORY] Fulfillment: View & Print Pick List / Pick Tasks for Workorder (Generated on Reservation Confirmation)

### Primary Persona
Dispatcher (primary); Mechanic (secondary consumer on mobile)

### Business Value
Provide a deterministic, actionable pick list for a work order so shop staff can pull correct parts quickly, reduce picking errors, and improve on-time job starts.

---

## 2. Story Intent

### As a / I want / So that
**As a** Dispatcher,  
**I want** to view a generated pick list (pick tasks) for a work order, ordered by storage layout, with a printable and mobile-friendly view,  
**so that** Mechanics know what to pull and when, without manual coordination.

### In-scope
- Frontend screens to:
  - Locate and open the pick list associated with a work order
  - Display pick tasks (product, qty, suggested storage location, priority, due time, status)
  - Show list status (Draft/ReadyToPick/…)
  - Print-friendly rendering and mobile-friendly rendering
- Frontend calls to backend services to load pick list + tasks (and optionally trigger generation if allowed by backend contract)
- Empty/needs-review handling for tasks without actionable locations

### Out-of-scope
- Generating pick lists purely in the frontend (must be backend/inventory domain)
- Route optimization beyond deterministic sort keys
- Picking execution flows (mark picked, partial picks, substitutions), unless explicitly provided by backend contract
- Inventory allocation/reservation logic

---

## 3. Actors & Stakeholders
- **Dispatcher:** initiates/monitors fulfillment readiness; prints pick list.
- **Mechanic:** consumes pick tasks on a mobile device; may print if needed.
- **Inventory Domain (backend):** system of record for pick list/task generation, ordering, and suggested locations.
- **Work Execution Domain (backend):** system of record for work order context (priority, scheduled start, due time).
- **Shop Manager (optional stakeholder):** operational oversight.

---

## 4. Preconditions & Dependencies
1. A **Work Order** exists (workOrderId known).
2. Parts reservation has been **confirmed** (trigger for backend generation).
3. Backend provides an API to retrieve the pick list and tasks for a work order (contract TBD; see Open Questions).
4. Storage locations have deterministic layout sort fields available to the backend; frontend relies on backend-provided `sortOrder` or equivalent.
5. AuthN is working; AuthZ rules for viewing pick lists are defined (permission(s) TBD).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From a Work Order screen: “Pick List” action/tab (preferred).
- Direct route by work order: `/fulfillment/workorders/:workOrderId/pick-list`
- Direct route by pick list id (optional if backend supports): `/fulfillment/pick-lists/:pickListId`

### Screens to create/modify (Moqui)
Create/modify Moqui screens (names indicative; align to repo conventions):
1. **WorkOrderDetail** (existing, in WorkExec area): add navigation link/button “Pick List”
2. **PickListView** (new): view pick list header + tasks table/list
3. **PickListPrint** (new): print-optimized screen/view (minimal chrome)
4. **PickListMobile** (optional new): mobile-optimized layout (could be same screen with responsive behavior)

### Navigation context
- Breadcrumb: Work Orders → Work Order {id or reference} → Pick List
- Keep workOrder context visible (work order reference, priority, scheduledStart/dueAt if provided)

### User workflows
**Happy path**
1. Dispatcher opens Work Order.
2. Clicks “Pick List”.
3. System loads pick list for workOrderId.
4. If pick list exists and status is `ReadyToPick`, show tasks ordered as provided.
5. Dispatcher clicks “Print” to open print view and prints.

**Alternate path: pick list not yet created**
- If backend indicates “not found / not generated yet”, UI shows “Pick list not available yet” and a refresh action.
- If backend supports manual “Generate/Rebuild” (unknown), show a button gated by permission and confirmation (TBD).

**Alternate path: Draft / NeedsReview**
- If pick list status is `Draft` and tasks include `NeedsReview`, show callout listing issues and highlight those tasks.

---

## 6. Functional Behavior

### Triggers (frontend)
- Route navigation to PickListView with `workOrderId` (required).
- User-initiated refresh.
- User selects Print/Mobile view.

### UI actions
- **Load pick list** on screen entry.
- **Refresh**: re-fetch pick list and tasks.
- **Print**: open print route/view (new tab optional) using the already-loaded data or re-fetch for latest.
- **Filter/Find** within tasks (safe UI ergonomics only; does not change backend sort).

### State changes (frontend-local)
- Loading states: `idle → loading → loaded | error`
- No domain state changes unless backend supports transitions (not defined here).

### Service interactions
- Call backend to:
  - fetch pick list by `workOrderId` (primary)
  - fetch tasks for pick list (may be included in same response)
  - optionally fetch work order summary (if needed for header, and not included)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `workOrderId` must be present in route params; otherwise show 400-style page message “Missing work order id” and do not call backend.
- If a task quantity is non-positive (should not happen), display “Invalid quantity” and flag row as error (display-only; do not correct).

### Enable/disable rules
- “Print” enabled only when pick list is loaded successfully.
- If pick list status is `Draft`, printing is allowed **only if business permits** (unclear). Default UI behavior: allow printing but show warning banner “Draft pick list—may be incomplete.” (requires confirmation; see Open Questions if printing Draft is disallowed).

### Visibility rules
- If any task `status = NeedsReview`, show a top-level warning summary and visually mark those tasks.
- If no tasks: show empty state “No pick tasks available yet.”

### Error messaging expectations
- 401: redirect to login (project standard)
- 403: show “You don’t have access to view pick lists for this work order.”
- 404 (no pick list): show empty state “Pick list not generated yet.”
- 409: show “Pick list changed; refresh to see latest.”
- 5xx/network: show retryable error with “Retry” action.

---

## 8. Data Requirements

### Entities involved (frontend perspective; SoR is backend)
- `PickList`
- `PickTask`
- `StorageLocation` (as embedded info on task, or referenced)
- `WorkOrder` summary (optional display context)

### Fields (types, required, defaults)
**PickList (required to render header)**
- `pickListId` (UUID, required)
- `workOrderId` (UUID, required)
- `status` (enum string; required)
- `createdAt` (timestamp; required for display)
- Optional but useful:
  - `workOrderPriority` (int)
  - `scheduledStartAt` (timestamp)
  - `dueAt` (timestamp)

**PickTask (required per row/item)**
- `pickTaskId` (UUID, required)
- `pickListId` (UUID, required)
- `productId` (UUID, required)
- `productDisplayName` (string, strongly preferred for UI; otherwise frontend needs product lookup → clarification)
- `quantityRequired` (number/decimal; required)
- `uom` (string; optional but important if fractional quantities exist → clarification)
- `suggestedLocationId` (UUID; nullable if NeedsReview)
- `suggestedLocationCode` (string; nullable)
- `priority` (int; required)
- `dueAt` (timestamp; required)
- `sortOrder` (int; required if backend pre-sorts; otherwise frontend sorts by provided layout fields → clarification)
- `status` (enum string; required)

**Storage location layout fields**
- Prefer backend-provided `sortOrder` and already-sorted tasks.
- If not, frontend requires:
  - `zoneOrder` (int)
  - `aisleOrder` (string/int)
  - `rackOrder` (int)
  - `binOrder` (int)
  - `locationCode` (string)

### Read-only vs editable
- All fields are **read-only** in this story (view/print only).

### Derived/calculated fields (frontend)
- “Overdue” indicator if `now > dueAt` and task not complete (only if task completion states exist; otherwise skip).
- Group headers by Zone/Aisle (optional UI; safe ergonomics).

---

## 9. Service Contracts (Frontend Perspective)

> Backend endpoints are not provided in the inputs; contracts below are **required** for buildability and must align to backend implementation. Until confirmed, story remains blocked.

### Load/view calls
1. **Get pick list by work order**
   - `GET /api/pick-lists/by-workorder/{workOrderId}`
   - Response: `PickList` including `tasks[]` OR a link to tasks endpoint
   - Errors:
     - 404 if not generated yet
     - 403 if unauthorized

2. **(If tasks not embedded) Get pick tasks**
   - `GET /api/pick-lists/{pickListId}/tasks`
   - Response: `PickTask[]` already sorted by `sortOrder`

3. **(Optional) Work order summary**
   - `GET /api/workorders/{workOrderId}/summary`

### Create/update calls
- None (unless backend supports manual generation; see Submit/transition)

### Submit/transition calls (only if backend supports)
- Optional manual trigger:
  - `POST /api/pick-lists/generate`
  - Body: `{ workOrderId }`
  - Response: `{ pickListId }`
  - Permission required (TBD)

### Error handling expectations (Moqui UI)
- Map HTTP errors to user messages per section 7.
- Preserve backend validation messages for 400 in a details panel.

---

## 10. State Model & Transitions

### Allowed states (displayed)
PickList `status` (from backend story reference; confirm frontend enums):
- `Draft`
- `ReadyToPick`
- `InProgress` (may exist)
- Other statuses unknown (must display generically if new)

PickTask `status`:
- `Pending`
- `NeedsReview`
- `Picked` (may exist)
- Other statuses unknown

### Role-based transitions
- None implemented in frontend in this story (view/print only).
- UI must not expose “mark picked” or “start picking” actions unless a separate story defines the transitions and permissions.

### UI behavior per state
- `Draft`: show banner “Draft: some tasks may require review”; highlight NeedsReview tasks.
- `ReadyToPick`: normal display; emphasize due times/priority.
- Unknown status: display as text badge without special behavior.

---

## 11. Alternate / Error Flows

1. **Pick list not found (not generated yet)**
   - Show empty state with:
     - Explanation: “Pick list is created when reservation is confirmed.”
     - Actions: Refresh; Back to Work Order
     - If manual generate exists: show “Generate pick list” button (permission-gated)

2. **Tasks include NeedsReview**
   - Show warning summary
   - For each NeedsReview task: show missing fields (e.g., “No actionable location”)

3. **Concurrency / stale data**
   - If backend returns 409 or indicates version mismatch:
     - Show “Pick list updated; refresh”
     - Provide refresh action

4. **Unauthorized**
   - 403: show access denied, do not reveal task details
   - Ensure print route also enforces same checks (no cached rendering if unauthorized)

5. **Network/server errors**
   - Show retry, preserve correlation/request id if returned in headers/body

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View ReadyToPick pick list for a work order
**Given** I am authenticated as a Dispatcher with permission to view pick lists  
**And** a work order `{workOrderId}` has an associated pick list in status `ReadyToPick`  
**When** I navigate to `/fulfillment/workorders/{workOrderId}/pick-list`  
**Then** the system displays the pick list header (pickListId, status, createdAt)  
**And** displays all pick tasks including product, quantity, suggested location, priority, dueAt, and task status  
**And** tasks are presented in the backend-provided deterministic order (by `sortOrder`)

### Scenario 2: Pick list not generated yet
**Given** I am authenticated as a Dispatcher  
**And** `{workOrderId}` has no pick list generated yet  
**When** I open the Pick List screen for `{workOrderId}`  
**Then** I see an empty state indicating the pick list is not available yet  
**And** I can click “Refresh” to retry loading

### Scenario 3: NeedsReview tasks are highlighted
**Given** I am authenticated as a Dispatcher  
**And** the pick list for `{workOrderId}` is in status `Draft`  
**And** at least one pick task has status `NeedsReview`  
**When** I view the pick list  
**Then** I see a warning banner indicating some tasks need review  
**And** the `NeedsReview` tasks are visually distinguished  
**And** each such task shows missing actionable information (e.g., no suggested location)

### Scenario 4: Print view renders pick list content
**Given** I have successfully loaded a pick list for `{workOrderId}`  
**When** I click “Print”  
**Then** the system opens a print-optimized view containing the same pick list header and tasks  
**And** the print view does not include application navigation chrome  
**And** the tasks appear in the same deterministic order

### Scenario 5: Unauthorized access is blocked
**Given** I am authenticated without permission to view pick lists for `{workOrderId}`  
**When** I navigate to the pick list route for `{workOrderId}`  
**Then** the system displays an access denied message  
**And** no pick list/task data is rendered

### Scenario 6: Backend error shows retryable message
**Given** I am authenticated as a Dispatcher  
**And** the backend returns a 5xx error when loading the pick list  
**When** I open the pick list screen  
**Then** I see an error state with a “Retry” action  
**When** I click “Retry” and the backend succeeds  
**Then** the pick list is displayed

---

## 13. Audit & Observability

### User-visible audit data
- Display `createdAt` and current `status` for the pick list.
- If backend provides status history, show a “History” section (otherwise omit; do not invent).

### Status history
- Not required unless backend supplies an endpoint/fields (Open Question).

### Traceability expectations
- Frontend should include correlation/request id in error details when available (header `X-Correlation-Id` or response body field; confirm convention).
- Log client-side navigation + load failures via existing frontend logging hooks (project standard).

---

## 14. Non-Functional UI Requirements
- **Performance:** initial load should render skeleton/loading state immediately; target < 2s perceived load on typical task list sizes (exact SLA not defined).
- **Accessibility:** keyboard navigable; sufficient contrast; print view readable.
- **Responsiveness:** usable on mobile screens; tasks list should adapt (stacked rows or condensed columns).
- **i18n/timezone:** display timestamps (`dueAt`, `createdAt`) in user/site timezone (confirm app standard); do not change backend times.
- **Printing:** CSS `@media print` styling to avoid cut-off columns and ensure page breaks behave sensibly.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide standard empty-state with explanation + refresh/back actions; safe because it does not alter domain behavior. Impacted sections: UX Summary, Alternate/Error Flows.
- SD-UI-LOADING-SKELETON: Use skeleton/loading indicator during async fetch; safe UI ergonomics. Impacted sections: Functional Behavior, Non-Functional.
- SD-ERR-MAP-HTTP: Standard mapping of 401/403/404/409/5xx to UI states; safe because it is generic transport handling. Impacted sections: Business Rules, Error Flows, Service Contracts.
- SD-UI-RESPONSIVE: Responsive layout adaptations for mobile/print without changing data; safe. Impacted sections: UX Summary, Non-Functional.

---

## 16. Open Questions
1. **Backend API contract:** What are the exact Moqui service endpoints (path, parameters) to:
   - fetch pick list by `workOrderId`
   - fetch pick tasks (embedded vs separate)
   - and do responses include `productDisplayName`, `uom`, and `sortOrder`?
2. **Manual generation:** If a pick list does not exist yet, should the frontend provide a “Generate pick list” action, or is generation strictly event-driven only?
3. **Authorization model:** What permission(s)/roles control:
   - viewing pick lists
   - printing pick lists
   - (if applicable) generating/regenerating pick lists
4. **Printing Draft lists:** Is printing allowed when `PickList.status = Draft` (e.g., due to `NeedsReview` tasks), or must printing be blocked?
5. **Task ordering source of truth:** Will backend always return tasks already ordered deterministically (recommended), or must frontend implement sorting using location fields?
6. **Work order context fields:** Should the pick list view display `scheduledStartAt`, `dueAt`, and `workOrderPriority`—and which service supplies them (inventory response vs workexec summary)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Fulfillment: Create Pick List / Pick Tasks for Workorder — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/92


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Fulfillment: Create Pick List / Pick Tasks for Workorder
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/92
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Fulfillment: Create Pick List / Pick Tasks for Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want a pick list so that mechanics know what to pull for a workorder.

## Details
- Pick tasks include product, qty, suggested storage locations, priority, and due time.

## Acceptance Criteria
- Pick tasks generated when reservation confirmed.
- Sorted by route/location.
- Printable or mobile view.

## Integrations
- Workexec provides workorder context; shopmgr may surface to mechanics.

## Data / Entities
- PickTask, PickList, RouteHint

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*

====================================================================================================