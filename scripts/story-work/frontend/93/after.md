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

### Title
[FRONTEND] Fulfillment: Reserve/Allocate Stock to Workorder Lines (SOFT/HARD) + View/Promote/Cancel

### Primary Persona
- **Parts Manager / Service Advisor** (human user) interacting in POS UI
- **Work Execution System** (system actor) as the source of workorder line context

### Business Value
- Ensure required parts for a job are predictably available by creating and managing inventory reservations per workorder line, with clear visibility into allocated vs backordered quantities and explicit promotion to HARD when operational commitment occurs.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Parts Manager (or authorized user),
- **I want** to view reservation status for each workorder line and take explicit actions to reserve (SOFT), promote to HARD, or cancel reservations,
- **So that** parts are held for the job at the right time, ATP impact is controlled, and shortages/backorders are visible and auditable.

### In-scope
- Moqui screens/forms to:
  - Display reservation + allocation details for a selected workorder line.
  - Trigger **upsert reservation** (create/update SOFT allocations) for a workorder line.
  - Trigger **promote SOFT → HARD** (with permission) and reflect resulting ATP impact.
  - Trigger **cancel reservation** for a workorder line.
- UI presentation of outcomes:
  - Reservation status (`PENDING`, `FULFILLED`, `PARTIALLY_FULFILLED`, `BACKORDERED`, `CANCELLED`)
  - Required vs allocated vs backordered quantities
  - Allocation rows including state (`SOFT`/`HARD`) and location
- Frontend error handling + audit visibility (read-only) for reservation/allocation history.

### Out-of-scope
- Creating pick tasks and operational picking workflows (only show returned pick-task references if backend provides them).
- Any inventory valuation/costing logic.
- Defining or changing Work Order lifecycle/state machine (owned by Work Execution).
- Automated/time-based promotions (explicitly disallowed).
- Substitution/backorder fulfillment policy (only reflect statuses returned by backend).

---

## 3. Actors & Stakeholders
- **Parts Manager (primary UI user):** initiates reserve/promote/cancel and reviews shortages.
- **Service Advisor:** views status to communicate ETA/availability to customer.
- **Technician (optional):** may view reservation status while preparing job (no privileged actions unless granted).
- **Inventory System (SoR):** authoritative source for reservations, allocations, ATP calculation.
- **Work Execution System (external SoR for workorders):** provides workorder line identifiers and required quantities; triggers may originate from workorder workflow.

---

## 4. Preconditions & Dependencies
- A workorder and one or more workorder lines exist in Work Execution with a stable identifier (**WorkOrderLineID**).
- Each workorder line references a SKU/product that exists in the Product catalog and is recognized by Inventory.
- Frontend can obtain:
  - `workOrderId` and `workOrderLineId`
  - `sku` (or productId mapped to sku) and `requiredQty`
  - current workorder line state (at minimum whether cancelled)
- Backend endpoints/services exist (or will exist) to:
  - Get reservation by workorder line reference
  - Upsert reservation (idempotent)
  - Promote allocations to HARD (permission-guarded)
  - Cancel reservation
  - Return audit/history information (or at least updated timestamps/actor)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Work Order detail** screen (Work Execution area) → action “Inventory Reservation” on each workorder line.
- Optional: From **Inventory** module search → “Reservation Lookup” by WorkOrderLineID (only if product requires it; otherwise out-of-scope).

### Screens to create/modify
1. **Modify Work Order Line screen** (existing, Workexec-owned)  
   - Add navigation/action to Reservation screen with parameters: `workOrderId`, `workOrderLineId`, `sku`, `requiredQty`.
2. **New/updated Inventory Reservation screen** (Inventory-owned)
   - Screen ID suggestion: `Inventory/Reservation/WorkOrderLineReservation.xml` (final path must match repo conventions).

### Navigation context
- Breadcrumb: Work Order → Line → Reservation
- Return link to Work Order detail with preserved scroll/line focus (if supported by existing patterns).

### User workflows
**Happy path (create/update SOFT reservation)**
1. User opens reservation screen for a workorder line.
2. UI loads existing reservation (if any) and availability summary.
3. User clicks “Reserve (SOFT)” or “Update Reservation” (if required qty changed).
4. UI shows updated reservation status + allocation list.

**Happy path (promote to HARD)**
1. User with permission clicks “Promote to HARD”.
2. UI confirms action (explicit, irreversible in effect on ATP).
3. UI shows allocations now `HARD`, status updated, and displays any audit fields returned.

**Happy path (cancel)**
1. User clicks “Cancel Reservation”.
2. UI confirms.
3. UI shows reservation status `CANCELLED` and allocations removed/marked released per backend response.

**Alternate paths**
- Partial allocation/backorder: show allocated qty < required qty and computed backorder qty; show status `PARTIALLY_FULFILLED` or `BACKORDERED`.
- Promotion failure due to insufficient ATP: show error and keep state unchanged in UI.
- Reservation doesn’t exist yet: show empty state with “Reserve (SOFT)” CTA.

---

## 6. Functional Behavior

### Triggers
- Screen load with `workOrderLineId` → fetch reservation view data.
- User actions:
  - Upsert reservation (SOFT allocations)
  - Promote to HARD
  - Cancel reservation
  - Refresh

### UI actions
- **Reserve (SOFT)/Update** button:
  - Sends idempotent upsert keyed by `workOrderLineId`.
  - Uses required quantity provided by workexec context unless user-edit is allowed (see Open Questions).
- **Promote to HARD** button:
  - Enabled only when reservation exists and at least one allocation is `SOFT` and user has permission.
- **Cancel Reservation** button:
  - Enabled when reservation exists and not already `CANCELLED`.

### State changes (frontend)
- Local UI state reflects backend authoritative values returned after each action.
- No client-side recomputation of ATP; display only what backend returns.

### Service interactions (Moqui)
- Use Moqui `transition` actions calling services (remote REST or local services depending on project conventions) for:
  - `loadReservationView`
  - `upsertReservation`
  - `promoteReservationHard`
  - `cancelReservation`

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Required identifiers must be present before enabling actions:
  - `workOrderLineId` required for all operations.
  - `sku` and `requiredQty` required for upsert.
- Quantity rules:
  - If `requiredQty <= 0`: treat as cancellation **only if** backend supports that behavior; otherwise block action and show validation error (see Open Questions).
- Promotion requires permission: `inventory.reserve.hard`.
- Manual edits to allocation rows (quantities/locations) are **not allowed** in UI unless backend explicitly supports it (not provided).

### Enable/disable rules
- “Reserve (SOFT)” enabled when:
  - `workOrderLineId` + `sku` present
  - `requiredQty > 0`
  - workorder line not cancelled (if known)
- “Promote to HARD” enabled when:
  - reservation status not `CANCELLED`
  - at least one allocation state = `SOFT`
  - user has permission `inventory.reserve.hard`
- “Cancel” enabled when:
  - reservation exists
  - status not `CANCELLED`

### Visibility rules
- Show “Backordered Qty” when `requiredQty > allocatedQty`.
- Show allocation table only when allocations exist.
- Show audit/history panel only when backend returns audit data.

### Error messaging expectations
- Display backend error `code` and human-readable `message` when present.
- Map common errors to actionable UI messages:
  - `SKU_NOT_FOUND` → “Part not found in inventory catalog.”
  - `INSUFFICIENT_ATP` → “Not enough available-to-promise to hard-reserve these parts.”
  - `INVALID_QUANTITY` → “Quantity must be greater than zero.”
  - `403` → “You don’t have permission to perform this action.”

---

## 8. Data Requirements

### Entities involved (frontend view)
- Reservation
- Allocation
- WorkorderLineRef (reference only; owned by workexec)
- Optional: Reservation/Allocation audit/history (if exposed)

### Fields (type, required, defaults)

**Workorder line context (input to screen)**
- `workOrderId` (string/UUID, optional but recommended for navigation)
- `workOrderLineId` (string/UUID, **required**)
- `sku` (string, **required** for upsert)
- `requiredQty` (integer, **required** for upsert)

**Reservation (read model)**
- `reservationId` (UUID, required if exists)
- `workOrderLineId` (string, required)
- `sku` (string, required)
- `requiredQuantity` (int, required)
- `allocatedQuantity` (int, required; backend-derived)
- `status` (enum, required)
- `createdAt`/`updatedAt` (timestamp, optional display)

**Allocation (read model rows)**
- `allocationId` (UUID, required)
- `reservationId` (UUID, required)
- `locationId` (string, optional display label if provided)
- `allocatedQuantity` (int, required)
- `allocationState` (`SOFT`|`HARD`, required)
- `status` (`ALLOCATED`|`PICKED`|`RELEASED`, optional if backend provides)
- `hardenedAt` (timestamp, optional)
- `hardenedBy` (string, optional)
- `hardenedReason` (string enum, optional)

**Derived/calculated (frontend-only display)**
- `backorderedQuantity = max(requiredQuantity - allocatedQuantity, 0)` (display only; does not drive logic)

### Read-only vs editable
- Editable:
  - None by default, except possibly `requiredQty` if product decides UI can override workexec’s required quantity (Open Question).
- Read-only:
  - All reservation/allocation fields; actions are via service calls.

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not fully defined in provided inputs; the following is the **minimum** the frontend needs. If backend differs, adjust to match actual Moqui services/endpoints.

### Load/view calls
- `GET Reservation by workOrderLineId`
  - Request: `{ workOrderLineId }`
  - Response: `{ reservation, allocations[], atpSummary? , auditHistory? }`

### Create/update calls
- `POST UpsertReservation`
  - Request: `{ workOrderLineId, sku, requiredQuantity, idempotencyKey? }`
  - Response: `{ reservation, allocations[] }`
  - Idempotency: backend must treat `workOrderLineId` as idempotency identifier.

### Submit/transition calls
- `POST PromoteReservationHard`
  - Request: `{ reservationId | workOrderLineId, reason: 'USER_ACTION' }`
  - Response: `{ reservation, allocations[] }`
  - Errors: `INSUFFICIENT_ATP`, `403` for missing permission.

- `POST CancelReservation`
  - Request: `{ reservationId | workOrderLineId }`
  - Response: `{ reservation(status=CANCELLED) }` and allocations removed or marked.

### Error handling expectations
- 400 validation errors return a structured payload with `errors[]` and optional `code`.
- 403 for permission failures.
- 404 when reservation not found on load (UI should treat as “no reservation exists” rather than hard error).

---

## 10. State Model & Transitions

### Allowed states (Reservation)
- `PENDING`
- `FULFILLED`
- `PARTIALLY_FULFILLED`
- `BACKORDERED`
- `CANCELLED`

### Allowed states (AllocationState)
- `SOFT`
- `HARD`

### Role-based transitions (UI-triggered)
- Any authorized inventory user:
  - None → (Upsert) → `PENDING/FULFILLED/PARTIALLY_FULFILLED/BACKORDERED` (backend decides)
  - Active → (Cancel) → `CANCELLED`
- User with `inventory.reserve.hard`:
  - AllocationState `SOFT` → (Promote) → `HARD`

### UI behavior per state
- `CANCELLED`: disable Promote/Reserve actions; show “Cancelled” banner.
- `BACKORDERED` / `PARTIALLY_FULFILLED`: show backordered quantity prominently; keep Reserve enabled (to retry/update) unless workorder cancelled.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing `workOrderLineId` → block screen actions and show “Missing work order line reference.”
- Missing `sku` or `requiredQty` on upsert → disable Reserve and show inline explanation.

### Concurrency conflicts
- If backend returns a conflict (409) due to concurrent update:
  - UI shows “Reservation was updated by another user/system. Refreshing…” and reloads view.

### Unauthorized access
- On 403:
  - Show error and keep UI state unchanged.
  - For Promote: hide button entirely if permission is known in session; otherwise show disabled with tooltip after first 403 (project convention-dependent).

### Empty states
- No reservation found:
  - Show “No reservation yet” and enable “Reserve (SOFT)”.

---

## 12. Acceptance Criteria

### Scenario 1: View existing reservation and allocations
**Given** a user opens the Reservation screen with a valid `workOrderLineId`  
**When** the screen loads  
**Then** the UI requests reservation details for that `workOrderLineId`  
**And** displays reservation `status`, `requiredQuantity`, `allocatedQuantity`, and allocation rows with `allocationState` and `allocatedQuantity`.

### Scenario 2: Create SOFT reservation (upsert) for a workorder line
**Given** no reservation exists for `workOrderLineId=WOL-1` and `sku=FLTR-01` with `requiredQty=5`  
**When** the user clicks “Reserve (SOFT)”  
**Then** the UI calls `UpsertReservation` with `{workOrderLineId: WOL-1, sku: FLTR-01, requiredQuantity: 5}`  
**And** on success displays a reservation with `allocatedQuantity=5` and allocations with `allocationState=SOFT`  
**And** the UI does not show any ATP reduction as a client-side calculation.

### Scenario 3: Update reservation quantity idempotently
**Given** an existing reservation for `workOrderLineId=WOL-1` exists  
**When** the user triggers Reserve again with the same inputs  
**Then** the UI receives the same reservation identity (`reservationId`) and does not show duplicate allocation rows (as returned by backend).

### Scenario 4: Promote SOFT to HARD (authorized)
**Given** a reservation exists with at least one allocation in `SOFT`  
**And** the user has permission `inventory.reserve.hard`  
**When** the user clicks “Promote to HARD” and confirms  
**Then** the UI calls `PromoteReservationHard`  
**And** on success displays allocations updated to `allocationState=HARD`  
**And** displays `hardenedAt`/`hardenedBy`/`hardenedReason` if provided.

### Scenario 5: Promote fails due to insufficient ATP
**Given** a reservation exists with `SOFT` allocations  
**When** the user clicks “Promote to HARD”  
**And** the backend responds with error `INSUFFICIENT_ATP`  
**Then** the UI shows an error message indicating insufficient ATP  
**And** allocations remain displayed as `SOFT` (no optimistic change persists).

### Scenario 6: Cancel reservation
**Given** a reservation exists for `workOrderLineId=WOL-1`  
**When** the user clicks “Cancel Reservation” and confirms  
**Then** the UI calls `CancelReservation`  
**And** on success displays reservation status `CANCELLED`  
**And** disables Promote/Reserve actions for that reservation.

### Scenario 7: Unauthorized promote attempt
**Given** a user without `inventory.reserve.hard` permission views a reservation with `SOFT` allocations  
**When** the user attempts to promote (if button is visible)  
**Then** the backend returns 403  
**And** the UI shows a permission error  
**And** the UI prevents further promote attempts in the session (disable/hide per convention).

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided):
  - reservation `createdAt`, `updatedAt`
  - last action actor (user/system) and reason for hardening (for promotion)
  - optional “History” list of changes (state transitions, qty changes)

### Status history
- If backend provides events (e.g., `AllocationHardened`), show them in a read-only panel with timestamp and actor.

### Traceability expectations
- Frontend includes correlation ID header if project convention exists; otherwise ensure Moqui logs include screen transition + parameters (excluding sensitive data).

---

## 14. Non-Functional UI Requirements

- **Performance:** Reservation screen should render within 2s on typical network after data load; avoid N+1 calls (single view call preferred).
- **Accessibility:** Buttons and tables keyboard-navigable; confirmation dialogs accessible; status text not color-only.
- **Responsiveness:** Usable on tablet width; allocation table supports horizontal scroll if needed.
- **i18n/timezone:** Display timestamps in user locale/timezone per Moqui/Quasar defaults (no custom conversions unless project requires).
- **Security:** Do not expose hidden identifiers unnecessarily; rely on backend authorization; avoid logging SKUs/IDs in client console in production mode.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide a “No reservation exists yet” empty state with a primary CTA to “Reserve (SOFT)”. Qualifies as safe UX ergonomics. Impacted sections: UX Summary, Alternate/Empty states.
- SD-UX-CONFIRM-DESTRUCTIVE: Require confirmation dialog for “Promote to HARD” and “Cancel Reservation” actions. Qualifies as safe UX ergonomics to prevent accidental irreversible/impactful actions. Impacted sections: UX Summary, Functional Behavior, Acceptance Criteria.
- SD-ERR-STD-MAPPING: Standard mapping of HTTP 400/403/404/409 to inline/toast messages without inventing domain policy. Qualifies because it’s generic error handling. Impacted sections: Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Backend contract:** What are the exact Moqui service names or REST endpoints (paths, request/response schemas) for:
   - load reservation by `workOrderLineId`
   - upsert reservation
   - promote to HARD
   - cancel reservation  
   (Blocking: frontend cannot wire transitions reliably without these.)
2. **Workexec context source:** On the reservation screen, does the frontend receive `sku` and `requiredQty` from Work Execution UI state, or must it load workorder line details from a workexec service?
3. **Editability of required quantity:** Is `requiredQuantity` editable in the reservation UI, or is it strictly derived from the workorder line and only updated by workexec?
4. **Zero/negative quantity behavior:** Should frontend treat `requiredQty <= 0` as “cancel reservation” (as backend story suggests) or block as invalid input?
5. **Audit/history availability:** Will backend provide an audit/history feed for reservation/allocation changes? If yes, what schema and paging strategy?
6. **Permissions for reserve/cancel:** Besides `inventory.reserve.hard`, what permissions govern:
   - creating/updating SOFT reservations
   - cancelling reservations
   (Need exact permission strings to enforce UI affordances consistently.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/93

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/93  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **System**, I want to reserve stock for workorder lines so that parts are held for the job.

## Details  
- Soft allocation vs hard reservation.  
- Handle partial reservations and backorders.

## Acceptance Criteria  
- Reservation created/updated.  
- ATP reflects allocations.  
- Idempotent updates.  
- Audited.

## Integrations  
- Workexec requests reservation; inventory responds with allocations and pick tasks.

## Data / Entities  
- Reservation, Allocation, WorkorderLineRef

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