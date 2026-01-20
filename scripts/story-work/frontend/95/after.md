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
[FRONTEND] [STORY] Putaway: Execute Put-away Move (Staging → Storage)

### Primary Persona
Stock Clerk (warehouse/mobile user)

### Business Value
Accurately moves received inventory from staging to storage so it becomes available for picking, with an auditable ledger trail and completed putaway tasks.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Stock Clerk  
- **I want** to execute a confirmed put-away move by scanning source and destination locations (and the item/pallet)  
- **So that** the system updates on-hand by location, records an immutable PUTAWAY ledger entry, and marks the putaway task complete for downstream picking accuracy.

### In-scope
- Moqui/Vue/Quasar frontend flow to **execute** a putaway move for an existing `PutawayTask`
- Scan/enter **source location**, **item/pallet identifier**, **destination location**
- Show a confirmation summary and submit the move
- Display success/failure outcomes and reflect task completion
- Permission-aware handling of validation/authorization errors returned by backend

### Out-of-scope
- Creating putaway tasks, assigning tasks, or planning/suggesting destination locations (unless backend already provides suggestions)
- Performing reconciliation/cycle count/inventory adjustment flows (only link/navigate if such screens exist; otherwise show actionable error)
- Defining/altering cost valuation logic (inventory backend owns cost)
- Implementing backend services/entities (frontend integrates only)

---

## 3. Actors & Stakeholders
- **Stock Clerk (Primary user):** performs scanning and confirmation
- **Inventory Service (System):** validates locations/SKU, posts ledger, updates on-hand and task status
- **Work Execution (Stakeholder):** depends on accurate pick locations and availability
- **Inventory Manager (Stakeholder):** cares about auditability and exception handling (overrides, capacity)

---

## 4. Preconditions & Dependencies
- User is authenticated in the frontend and has access to inventory putaway screens
- A `PutawayTask` exists and is in an executable state (backend reference: `IN_PROGRESS` or `PENDING_EXECUTION`)
- Backend endpoints exist to:
  - load a putaway task and its required scan targets
  - validate/execute the putaway move atomically
- Barcode/location scanning capability exists in frontend (camera scanner or hardware wedge) or manual entry fallback
- Permissions are enforced by backend (frontend must handle `403` gracefully)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From a Putaway Task list screen (existing or to be added): “Execute” action on a selected task
- Direct route with task id parameter (deep link): `/inventory/putaway/task/{putawayTaskId}/execute` (route is a proposal; confirm with project routing conventions)

### Screens to create/modify (Moqui screens)
1. **Screen: `PutawayTaskList`** (modify if exists; otherwise create minimal list)
   - Shows tasks with status filter and an “Execute” action
2. **Screen: `PutawayTaskExecute`** (create)
   - Guided scan workflow (source → item/pallet → destination → confirm)
3. **Screen: `PutawayTaskDetail`** (optional modify)
   - Shows task status, ledger reference/transactionId after completion, audit metadata if available

### Navigation context
- Inventory → Putaway → Task List → Execute Task
- After success: return to Task Detail or Task List with the task marked `COMPLETED`

### User workflows
**Happy path**
1. Open Execute for a task
2. Scan/enter **source (staging) location**
3. Scan/enter **item/pallet identifier**
4. Scan/enter **destination (storage) location**
5. Review summary and **Confirm Putaway**
6. See success, task marked complete, and updated on-hand context (if displayed)

**Alternate paths**
- Destination invalid for SKU → show blocking error; allow rescan destination
- Destination full/capacity error → show blocking error; allow rescan destination or split (only if backend supports split)
- Source shows zero on-hand → show blocking error; provide next-step guidance (reconciliation required) without attempting to “fix” in UI
- Unauthorized (403) for execute/override → show permission error; disable confirm action

---

## 6. Functional Behavior

### Triggers
- User clicks “Execute” on a `PutawayTask`
- Scan events (barcode input) populate fields and trigger backend validation where supported
- User clicks “Confirm Putaway” to submit

### UI actions
- Stepper-style progression (or equivalent): Source → Item/Pallet → Destination → Confirm
- Each scan field supports:
  - scan input (keyboard wedge) and manual typing
  - “Clear” action
- Confirmation step shows:
  - task identifier
  - SKU/product reference
  - quantity to move (task quantity)
  - fromLocation (source)
  - toLocation (destination)

### State changes (frontend view state)
- Local view state: `currentStep`, `scannedSourceId/code`, `scannedItemId/code`, `scannedDestinationId/code`, `isSubmitting`, `lastError`
- Server state changes on submit (backend-owned):
  - on-hand decrement at source + increment at destination
  - `InventoryLedgerEntry` created with `transactionType=PUTAWAY`
  - `PutawayTask.status` → `COMPLETED`
  - audit log written

### Service interactions (frontend → Moqui backend)
- Load task details on screen entry
- Optionally validate scanned codes as user proceeds (if backend provides validate endpoints)
- Execute putaway on confirm (single atomic call)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (UI-level, before calling backend)
- Require all scan inputs before enabling “Confirm Putaway”:
  - source location
  - item/pallet identifier
  - destination location
- Prevent submission while `isSubmitting=true`
- Display backend validation errors verbatim where safe (mapped to user-friendly messages)

### Enable/disable rules
- “Confirm Putaway” disabled unless:
  - all required fields present
  - task status is executable (not `COMPLETED`, not canceled)
  - user has permission (if permission info is available from backend; otherwise rely on handling 403)

### Visibility rules
- Show task immutable info (SKU, qty) as read-only
- If task already `COMPLETED`, show read-only completion summary and hide confirm controls

### Error messaging expectations (must map backend codes)
Backend reference enumerates these codes; UI must handle at minimum:
- `LOCATION_NOT_VALID_FOR_SKU` → “Destination location is not valid for this item. Scan a different storage location.”
- `NO_ON_HAND_AT_SOURCE_LOCATION` → “No on-hand quantity at the source location. Reconciliation is required before putaway.”
- Capacity full error code **TBD** (see Open Questions) → message instructing user to select another location (and split if supported)
- Generic validation errors → show inline near relevant field when possible, otherwise as banner/toast
- 403 → “You don’t have permission to perform this action.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `PutawayTask` (read/update via service)
- `InventoryLedgerEntry` (created by backend; frontend may display reference)
- `StorageLocation` (resolved by barcode/code)
- `AuditLog` or audit metadata (read-only display if available)

### Fields (type, required, defaults)
**PutawayTask (read)**
- `putawayTaskId` (string/ID, required)
- `status` (enum, required)
- `productId` or `sku` (string/ID, required)
- `quantity` (decimal, required; precision rules owned by backend)
- `sourceLocationId` and/or `sourceLocationCode` (required by task)
- (optional) `itemOrPalletId` expected by task (if task binds to a pallet/container)

**Execute Putaway request (submit)**
- `putawayTaskId` (required)
- `fromLocationCode` or `fromLocationId` (required)
- `toLocationCode` or `toLocationId` (required)
- `itemOrPalletCode` (required if backend requires; otherwise optional) **TBD**
- `quantity` (optional if implied by task; **must not assume**—see Open Questions)

**Response (read)**
- `transactionId` (string, required for success display)
- `putawayTaskStatus` (enum, required)
- (optional) updated on-hand snapshots for from/to locations (nice to show, not required) **TBD**

### Read-only vs editable by state/role
- Read-only always: task SKU/product, planned quantity, task status
- Editable: scan inputs until submission; after success become read-only
- Override inputs (reason/justification/approver) only if backend supports overrides and user has permissions **TBD** (see Open Questions)

### Derived/calculated fields
- Display-only: “Move summary” assembled from task + scanned values
- Do not calculate availability/ATP in frontend; show backend-provided values only

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names/paths must align with backend implementation. Below are frontend-required contracts; if different in backend, adapt mapping but keep behaviors.

### Load/view calls
1. **Get Putaway Task**
   - Request: `GET /inventory/putaway/task/{putawayTaskId}`
   - Response includes: status, SKU/product, qty, expected source location, any constraints
   - Errors: 404 (not found), 403 (no access)

2. **(Optional) Resolve/validate scanned codes**
   - `GET /inventory/location/resolve?code=...`
   - `GET /inventory/putaway/task/{id}/validateDestination?locationCode=...`
   - If not available, validation occurs during execute call only.

### Create/update calls
- None directly; execute call performs atomic updates

### Submit/transition calls
1. **Execute Putaway**
   - `POST /inventory/putaway/execute`
   - Body: identifiers described in Data Requirements
   - Success: returns `transactionId`, updated task status (COMPLETED), timestamps
   - Validation failures: `400` with machine-readable code(s) and field-level details
   - Unauthorized: `403`
   - Concurrency: `409` if task already completed/changed or inventory changed such that move can’t proceed

### Error handling expectations
- Map HTTP status to UI:
  - 400: show validation error; keep user on same step and highlight field if possible
  - 403: show permission error; disable confirm
  - 404: show “Task not found” with link back to list
  - 409: show “Task was updated elsewhere. Reload required.” with Reload action
  - 500: show generic error and allow retry (idempotency considerations rely on backend; see Open Questions)

---

## 10. State Model & Transitions

### Allowed states (PutawayTask)
- `PENDING_EXECUTION` (executable)
- `IN_PROGRESS` (executable)
- `COMPLETED` (non-executable; read-only)

(Other states like `CANCELLED` may exist but are not defined here.)

### Role-based transitions
- Stock Clerk can transition executable → `COMPLETED` only by successful execute API
- Overrides (capacity/compatibility) require specific permissions if supported:
  - `OVERRIDE_LOCATION_COMPATIBILITY`
  - `OVERRIDE_LOCATION_CAPACITY`
  (Permissions must be enforced by backend; UI only reacts)

### UI behavior per state
- Executable: show scan workflow and confirm
- Completed: show completion summary, ledger transactionId, disable scans/confirm
- Unknown/unsupported state: show read-only task info and a message “This task cannot be executed in its current status.”

---

## 11. Alternate / Error Flows

### Validation failures
- Missing scan inputs → client-side block; show inline required markers
- Backend `LOCATION_NOT_VALID_FOR_SKU` → remain on Destination step; clear destination field; prompt to rescan
- Backend `NO_ON_HAND_AT_SOURCE_LOCATION` → remain on Confirm step (or Source step), disable confirm; show guidance text and link back

### Concurrency conflicts
- If backend returns 409 (task completed elsewhere or inventory changed):
  - show banner “Task changed. Reload to continue.”
  - provide Reload action calling Get Putaway Task again
  - if status is now COMPLETED, switch to completed view

### Unauthorized access
- 403 on load: show access denied and link back
- 403 on execute: show access denied; keep scans but disable confirm to prevent repeated attempts

### Empty states
- Task list empty: show “No putaway tasks available” and allow refresh
- Scanner not available: allow manual entry for codes

---

## 12. Acceptance Criteria

### Scenario 1: Successful Put-away move completes task
**Given** a PutawayTask is in status `IN_PROGRESS` for quantity `10` of SKU `ABC-123` from source location `STAGING-01`  
**And** the user opens the Execute Putaway screen for that task  
**When** the user scans/enters source `STAGING-01`  
**And** scans/enters the required item/pallet identifier  
**And** scans/enters destination `A-01-B-03`  
**And** clicks “Confirm Putaway”  
**Then** the frontend calls the Execute Putaway service once  
**And** on success shows a confirmation including a `transactionId`  
**And** the task is shown as `COMPLETED` in the UI.

### Scenario 2: Destination invalid for SKU blocks move
**Given** an executable PutawayTask is loaded  
**When** the user submits a destination that the backend rejects as incompatible  
**Then** the frontend displays error code/message for `LOCATION_NOT_VALID_FOR_SKU`  
**And** no success state is shown  
**And** the user can rescan/enter a different destination and retry.

### Scenario 3: Source has zero on-hand blocks move
**Given** an executable PutawayTask is loaded  
**When** the user submits and the backend returns `NO_ON_HAND_AT_SOURCE_LOCATION`  
**Then** the frontend displays a blocking error explaining reconciliation is required  
**And** the Confirm action remains disabled until the user changes inputs or reloads  
**And** the UI does not attempt to create inventory or proceed.

### Scenario 4: Task already completed (concurrency)
**Given** the user has the Execute screen open for a PutawayTask  
**When** the backend responds `409 Conflict` indicating the task is already completed  
**Then** the frontend prompts the user to reload  
**And** after reload the task is displayed as `COMPLETED` and the execute controls are hidden/disabled.

### Scenario 5: Unauthorized user cannot execute
**Given** a user without required permission attempts to confirm putaway  
**When** the backend responds `403 Forbidden`  
**Then** the frontend shows an access denied message  
**And** does not show success  
**And** prevents repeated submissions by disabling Confirm until reload/navigation.

---

## 13. Audit & Observability

### User-visible audit data
- After success, show:
  - `transactionId`
  - completion timestamp (if returned)
  - actor (current user display name if available, otherwise omit)
- If backend provides audit history, show a “View audit details” link (optional; do not invent if absent)

### Status history
- Display task status and last updated timestamp (if provided)

### Traceability expectations
- All execute requests include a correlation id header (if project standard exists) and propagate to logs
- Frontend logs (console/dev) should not expose sensitive data; record minimal error diagnostics

---

## 14. Non-Functional UI Requirements
- **Performance:** initial task load < 2s on typical network; avoid N+1 calls during scanning
- **Accessibility:** all inputs labeled; step changes announced; error messages associated with fields
- **Responsiveness:** optimized for mobile scanner devices; large tap targets; works in portrait
- **i18n/timezone:** timestamps displayed in user locale/timezone if present; no currency handling required

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty-state messaging and refresh action on task list; safe because it does not change domain logic; impacts UX Summary, Error Flows.
- SD-UX-MANUAL-ENTRY-FALLBACK: Allow manual entry when scanner is unavailable; safe as it’s purely input modality; impacts UX Summary, Functional Behavior.
- SD-ERR-HTTP-MAP: Standard mapping of 400/403/404/409/500 to banner/inline errors; safe because it’s presentation-only and relies on backend codes; impacts Service Contracts, Error Flows.

---

## 16. Open Questions
1. **Backend contract alignment:** What are the exact Moqui endpoints/services and payload schemas for:
   - loading a PutawayTask
   - executing the putaway (required fields; especially whether `quantity` is required or always implied by task)?
2. **Item/pallet identifier requirement:** Is scanning an item/pallet/container **mandatory** for all putaways, or only for palletized/serialized flows? What field name and format should the frontend use?
3. **Capacity full error:** What error code and response shape represents “destination full capacity,” and does the backend support **split quantity across multiple locations** in one task execution?
4. **Overrides UX:** Are compatibility/capacity overrides enabled in v1? If yes:
   - how does frontend discover permissions (`OVERRIDE_LOCATION_COMPATIBILITY`, `OVERRIDE_LOCATION_CAPACITY`)?
   - what reason codes list should be used, and is manager approval captured in the same request?
5. **Task state model:** Are there additional `PutawayTask` states (e.g., `CANCELLED`, `ON_HOLD`) that must be handled explicitly in UI?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Putaway: Execute Put-away Move (Staging → Storage) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/95

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Putaway: Execute Put-away Move (Staging → Storage)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/95  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Putaway: Execute Put-away Move (Staging → Storage)

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **Stock Clerk**, I want to execute put-away moves so that inventory becomes available for picking.

## Details  
- Scan from/to locations.  
- Update ledger with movement PutAway.

## Acceptance Criteria  
- Ledger entry created.  
- On-hand updated per destination.  
- Task marked complete.  
- Audited.

## Integrations  
- Workexec sees accurate pick locations.

## Data / Entities  
- InventoryLedgerEntry(PutAway), PutawayTaskState, AuditLog

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