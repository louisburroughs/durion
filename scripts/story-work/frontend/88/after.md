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
[FRONTEND] [STORY] Allocations: Reallocate Reserved Stock When Schedule/Priority Changes (Deterministic + Audited)

### Primary Persona
Dispatcher

### Business Value
Keep inventory reservations aligned to the latest work schedule and priority so urgent jobs get stock first (without starving others), while making changes explainable (auditable) and keeping ATP accurate.

---

## 2. Story Intent

### As a / I want / So that
**As a** Dispatcher,  
**I want** the POS UI to trigger and display deterministic reallocation of reserved stock when a work order’s schedule, priority, or inventory-waiting status changes,  
**so that** I can trust reservations/ATP reflect operational reality and understand why allocations changed.

### In-scope
- A UI workflow to **initiate** reallocation for a specific stock item or work order context (manual trigger).
- A UI view to **review results** of the most recent reallocation:
  - which work orders gained/lost allocations
  - before/after reserved quantities
  - reason code and trigger metadata
  - ATP before/after (for impacted stock item(s))
- A UI view to **inspect allocation ordering inputs** (read-only): basePriority, effectivePriority, dueDateTime, waitingSince, scheduleStartTime, createdAt tie-breaker values (as available from backend).
- Frontend error handling for validation/permission/concurrency failures.

### Out-of-scope
- Implementing the allocation algorithm in the frontend (Inventory backend is SoR).
- Editing WorkExec priority or ShopMgr schedule fields (handled in their domains).
- Defining/altering policy values (agingGracePeriod/interval/step/maxEffectivePriority) unless backend exposes them read-only.
- Automatic reallocation triggers driven purely by frontend polling (backend event-driven triggers remain backend responsibility).

---

## 3. Actors & Stakeholders
- **Dispatcher (primary user):** wants allocations to reflect priorities/schedule; needs explanations.
- **Inventory Manager (secondary):** may review audit/reasons for stock movement decisions.
- **Technician / Shop floor user (indirect):** impacted by stock availability but not configuring policy here.
- **System (Inventory domain backend):** computes allocations, ATP, audit records (authoritative).
- **System (Work Execution / Shop Management):** sources schedule/priority changes (context only).

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui frontend.
- Backend provides endpoints/services to:
  - view allocations for a stock item and/or work order
  - request a reallocation (manual trigger) with a reason code
  - view reallocation audit entries / allocation change history
  - view ATP/onHand/allocated summary for the stock item
- Permissions exist and are enforced by backend for “reallocate allocations” and for “view allocation audit”.
- Data exists: at least one `stockItemId`, competing `workOrderId`s, and existing `Allocation` records.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an Inventory item detail screen: “Allocations” tab/panel → “Reallocate now”
- From a Work Order context (if present in frontend nav): “Inventory Allocations” panel → “Reallocate impacted items”
- From a global Inventory → Allocations search/list screen → select `stockItemId` → “Reallocate now”

### Screens to create/modify
1. **New/Modify Screen:** `Inventory/Allocation/AllocationList.xml` (or equivalent inventory allocations screen)
   - List allocations by `stockItemId` and show ATP summary.
2. **New Modal/Dialog Screen or Subscreen:** `Inventory/Allocation/ReallocateDialog.xml`
   - Confirm action and submit reason/notes (if supported).
3. **New/Modify Screen:** `Inventory/Allocation/AllocationAudit.xml`
   - Timeline/table of allocation change audits for a `stockItemId` (and optionally workOrderId filter).
4. **Optional Subscreen:** `Inventory/Allocation/ReallocationRunResult.xml`
   - Show “run” summary if backend returns a runId/result payload.

> Moqui pattern: use a screen with transitions invoking services; results displayed via context from service call or via redirect with parameters (e.g., runId).

### Navigation context
- Inventory top-level menu: `Inventory → Allocations`
- Within Stock Item detail: `Stock Item → Allocations`

### User workflows
**Happy path (manual reallocation):**
1. Dispatcher opens Allocations for a stock item.
2. Reviews current allocations and ATP.
3. Clicks “Reallocate now”.
4. Confirms reason (e.g., SYSTEM_REBALANCE or SCHEDULE_CHANGE if user is initiating due to known change).
5. UI submits reallocation request.
6. UI shows success banner and refreshes allocations + ATP + latest audit entries.

**Alternate path (view why allocations changed):**
1. Dispatcher opens Allocation Audit for a stock item.
2. Filters by reason code (e.g., PRIORITY_CHANGE).
3. Opens an audit row to see before/after allocation state and trigger metadata.

---

## 6. Functional Behavior

### Triggers (frontend)
- **Manual trigger** initiated by user action (“Reallocate now”).
- **Refresh trigger** after reallocation completes: reload allocations, ATP, and audit list.

> Non-manual triggers (priority/schedule events) are backend-driven; frontend must support reviewing their audit trails and seeing updated allocations.

### UI actions
- Search/select `stockItemId` (required to scope the reallocation).
- “Reallocate now” opens confirmation dialog:
  - shows impacted stock item identifier/name
  - shows warning: “This may change which work orders hold reservations”
  - collects required inputs for backend request (see Open Questions)
- After submit:
  - show spinner/progress until response
  - on success: show summary + refresh data panes
  - on failure: show error mapping (validation vs permission vs conflict)

### State changes (UI-local)
- Dialog open/close state
- Loading states for allocations/audit/ATP blocks
- Last run timestamp (derived from response or latest audit time)

### Service interactions
- Call backend service/API to initiate reallocation for `stockItemId` (and optionally `workOrderId`).
- Fetch updated:
  - allocations list
  - ATP summary
  - audit entries for the change (by stockItemId, optionally by runId or occurredAt since submit)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `stockItemId` must be present to run reallocation.
- Reason code must be provided **if backend requires it** (backend story says audit reason is required; manual trigger should supply `MANUAL_OVERRIDE` or `SYSTEM_REBALANCE`—see Open Questions).
- Quantity fields are read-only in this UI (no partial allocations or manual quantity edits in scope).

### Enable/disable rules
- “Reallocate now” button is disabled when:
  - user lacks permission (backend indicates 403; UI should hide/disable if permission info is available)
  - required `stockItemId` not selected
  - a reallocation request is in-flight (prevent double-submit)
- Audit view is disabled/hidden if user lacks audit permission.

### Visibility rules
- Show ATP summary for selected stock item:
  - onHand, totalAllocated, ATP (backend-provided preferred)
- Show a “No allocations” empty state if none exist.
- Show “No audit entries” if none exist.

### Error messaging expectations
- 400 validation error: show field-level message in dialog (e.g., missing reasonCode)
- 403 forbidden: “You don’t have permission to reallocate allocations.”
- 404 not found: “Stock item not found or no longer available.”
- 409 conflict: “Allocations changed while you were viewing. Refresh and try again.”
- 5xx: “Reallocation failed due to a server error. Try again later.” Include correlation/trace id if provided.

---

## 8. Data Requirements

### Entities involved (frontend view model)
- `Allocation`
- `AuditLog` / `ItemAllocationAudit` (name TBD)
- `StockItem` summary (for name/sku display)
- (Read-only) work order sorting inputs:
  - basePriority
  - effectivePriority
  - dueDateTime
  - waitingSince
  - scheduleStartTime
  - workOrderCreatedAt

### Fields (type, required, defaults)

**Allocation (display)**
- `allocationId` (UUID, required, read-only)
- `workOrderId` (UUID, required, read-only)
- `stockItemId` (UUID, required, read-only)
- `quantityReserved` (Decimal/Integer, required, read-only)
- `updatedAt` (Timestamp, read-only)

**ATP Summary (display)**
- `onHand` (Decimal, read-only)
- `totalAllocated` (Decimal, read-only)
- `availableToPromise` (Decimal, read-only)

**Audit (display)**
- `reasonCode` (enum string, required)
- `previousAllocationState` (JSON/string, required)
- `newAllocationState` (JSON/string, required)
- `triggeredBy` (`USER|SYSTEM`, required)
- `triggerReferenceId` (string, optional)
- `occurredAt` (timestamp, required)

**Reallocation request (input)**
- `stockItemId` (UUID, required)
- `reasonCode` (enum string, required if backend requires)
- `note` (string, optional) (only if backend supports)

### Read-only vs editable by state/role
- Dispatcher can **initiate** reallocation (permission-gated).
- Dispatcher can **view** allocations; audit view permission may be more restricted (TBD).
- No editing of allocation quantities or priorities in this story.

### Derived/calculated fields (UI)
- “Change direction” per work order: compare before/after allocations if response includes both; otherwise derive by comparing refreshed list vs previous client snapshot (marked as best-effort only).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contracts are not fully specified in provided inputs; below is the **required frontend-facing contract shape**. If backend differs, update this story or add adapter logic.

### Load/view calls
1. `GET /inventory/allocations?stockItemId=...`
   - Returns allocations list + (optional) sorting inputs per work order.
2. `GET /inventory/stockItem/atp?stockItemId=...`
   - Returns onHand/totalAllocated/ATP (preferred as a single object).
3. `GET /inventory/allocations/audit?stockItemId=...&from=...&to=...&reasonCode=...`
   - Returns audit entries sorted by occurredAt desc.

### Create/update calls
- none

### Submit/transition calls
1. `POST /inventory/allocations/reallocate`
   - Request: `{ stockItemId, reasonCode, note?, triggerReferenceId? }`
   - Response (minimum): `{ status: "SUCCESS", occurredAt, correlationId? }`
   - Response (preferred): `{ runId, impactedWorkOrders[], atpBefore, atpAfter, correlationId }`

### Error handling expectations
- 400: structured field errors `{ errors: [{field, message, code}] }`
- 403: forbidden
- 409: conflict (concurrency or stale inputs)
- 5xx: server error with `correlationId` echoed to UI if present

---

## 10. State Model & Transitions

### Allowed states
- No new domain state machine introduced in frontend.
- UI state:
  - `idle`
  - `loading`
  - `submittingReallocation`
  - `success`
  - `error`

### Role-based transitions
- Only users with backend permission may transition UI into “submittingReallocation” (button enabled).

### UI behavior per state
- `loading`: show skeleton/loading indicators for allocations/audit/ATP blocks
- `submittingReallocation`: disable inputs and show spinner on confirm button
- `success`: toast + refresh lists; optionally deep-link to audit entry/run
- `error`: show inline error in dialog or page-level alert depending on failure phase

---

## 11. Alternate / Error Flows

1. **Empty state: no allocations exist**
   - Show empty state with guidance: “No reservations for this stock item.”
   - Reallocate action still allowed only if backend supports reallocating including unallocated demand; otherwise disable and explain (Open Question).

2. **Validation failure (missing reasonCode)**
   - Keep dialog open, highlight reasonCode field, show backend message.

3. **Concurrency conflict (409)**
   - Show message and offer “Refresh allocations” action.

4. **Unauthorized (403)**
   - Hide/disable reallocate controls; show “Insufficient permissions” banner if user navigates directly.

5. **Backend returns success but audit not immediately visible**
   - After submit, refresh audit with retry/backoff (bounded: e.g., 3 attempts over ~2s) only if backend is eventually consistent; otherwise single refresh. (See Applied Safe Defaults.)

---

## 12. Acceptance Criteria

### Scenario 1: Dispatcher manually triggers reallocation for a stock item
**Given** I am logged in as a Dispatcher with permission to reallocate allocations  
**And** I am viewing the Allocations page for a selected stock item  
**When** I click “Reallocate now”  
**And** I confirm the action with a valid reason code  
**Then** the system submits a reallocation request for that stock item  
**And** I see a success confirmation  
**And** the allocations list refreshes and reflects the latest backend state  
**And** the ATP summary refreshes.

### Scenario 2: Deterministic outcome is visible via stable refreshed results
**Given** a stock item has competing work orders and limited on-hand quantity  
**When** I trigger reallocation twice without any intervening data changes  
**Then** the allocations shown after each run are identical  
**And** the audit shows two entries (or one idempotent entry if backend dedupes) with the supplied reason code.

### Scenario 3: Audit trail is viewable with reason and before/after state
**Given** allocations changed due to a schedule or priority change (system-triggered)  
**When** I open the Allocation Audit view for the stock item  
**Then** I can see an audit entry with a reason code (e.g., `SCHEDULE_CHANGE` or `PRIORITY_CHANGE`)  
**And** the entry displays before/after allocation state and occurredAt timestamp.

### Scenario 4: Full allocation only is reflected in UI
**Given** remaining available quantity is insufficient to fully satisfy a work order’s required quantity  
**When** I view allocations after a reallocation  
**Then** that work order shows no allocation for the stock item (quantityReserved absent/0 per backend contract)  
**And** the UI does not show any partial quantity reserved for that work order.

### Scenario 5: Permission enforcement on reallocation
**Given** I am logged in without permission to reallocate allocations  
**When** I navigate to the Allocations page  
**Then** I do not see (or cannot interact with) the “Reallocate now” action  
**And** if I attempt to call reallocation (direct URL/action), I receive a forbidden error and see an appropriate message.

### Scenario 6: Concurrency conflict handling
**Given** I am viewing allocations for a stock item  
**And** allocations change on the backend before I submit reallocation  
**When** I submit the reallocation request  
**And** the backend responds with HTTP 409 conflict  
**Then** the UI shows a conflict message  
**And** provides a one-click refresh to reload allocations/audit/ATP.

---

## 13. Audit & Observability

### User-visible audit data
- Allocation Audit screen shows:
  - reasonCode
  - triggeredBy
  - triggerReferenceId (if present)
  - occurredAt
  - before/after allocation state (render JSON in readable format)

### Status history
- Show audit list as immutable history (no edit/delete in UI).

### Traceability expectations
- Display backend-provided `correlationId` (or request id) on error states and optionally on success details for support troubleshooting.
- Frontend logs (console/dev logging per project convention) must not include sensitive data; include correlationId where available.

---

## 14. Non-Functional UI Requirements
- **Performance:** allocations/audit pages should load within 2 seconds for up to 200 allocation rows (pagination supported).
- **Accessibility:** all actions keyboard accessible; dialogs have focus trap; error messages announced via ARIA.
- **Responsiveness:** usable on tablet widths used on shop floor; tables collapse to stacked rows when narrow.
- **i18n/timezone:** display timestamps in user locale/timezone; preserve backend UTC timestamps in payloads.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide standard empty states for allocations/audit lists; safe because it does not alter domain logic. (Impacted: UX Summary, Alternate Flows)
- SD-UI-PAGINATION: Paginate allocation/audit tables (default page size 25) to prevent slow rendering; safe UI ergonomics only. (Impacted: UX Summary, Non-Functional)
- SD-ERR-MAP-HTTP: Map common HTTP errors (400/403/404/409/5xx) to consistent banners/toasts; safe because it follows standard transport semantics. (Impacted: Business Rules, Error Flows, Service Contracts)
- SD-UI-DOUBLE-SUBMIT-GUARD: Disable submit while request in-flight; safe to prevent duplicate user actions. (Impacted: Functional Behavior)
- SD-AUDIT-REFRESH-RETRY: If audit is eventually consistent, retry audit fetch up to 3 times with short backoff after successful reallocation; safe because it only affects UI freshness and is bounded. (Impacted: Alternate Flows, UX)

---

## 16. Open Questions

1. **Backend API contracts:** What are the exact Moqui screen/service endpoints (service names, parameters, response shapes) for:
   - listing allocations by stockItemId
   - getting ATP summary
   - initiating reallocation
   - listing allocation audit entries (and filtering by runId/reasonCode)?
2. **Manual trigger reasonCode:** For a user-initiated reallocation, should the frontend always send `MANUAL_OVERRIDE`, or allow selecting from the enum (e.g., `SYSTEM_REBALANCE`, `SCHEDULE_CHANGE`)? Which are valid for USER-triggered actions?
3. **Scope of reallocation request:** Should the manual reallocation run be scoped by:
   - `stockItemId` only (recommended),
   - `workOrderId` only,
   - or both (reallocate impacted items for a work order)?
4. **Permissions:** What permission string(s) gate:
   - viewing allocations
   - viewing allocation audit
   - initiating reallocation?
5. **Audit consistency model:** After a successful reallocation call, is the audit entry guaranteed to be immediately queryable, or is it eventually consistent?
6. **Display of “required quantity”:** Does backend expose each work order’s required quantity for the stock item (needed to clearly show “full allocation only” outcomes), or do we display only reserved quantities?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/88


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/88
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want reallocations so that reservations reflect updated schedule and priorities.

## Details
- Reallocation by priority and due time.
- Rules prevent starvation (optional).

## Acceptance Criteria
- Allocations updated deterministically.
- Audit includes reason.
- ATP updated.

## Integrations
- Workexec triggers priority changes; shopmgr schedule updates due times.

## Data / Entities
- Allocation, PriorityPolicy, AuditLog

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