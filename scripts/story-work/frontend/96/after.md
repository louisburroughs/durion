## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Putaway: Generate Put-away Tasks from Staging

## Primary Persona
Stock Clerk (with supporting personas: Warehouse Manager)

## Business Value
Ensure items received into staging are promptly routed into correct storage locations via generated put-away tasks, improving inventory accuracy, throughput, and auditability.

---

# 2. Story Intent

## As a / I want / So that
**As a** Stock Clerk,  
**I want** to view and claim system-generated put-away tasks created from completed receipts (with suggested destinations and fallback indicators),  
**so that** I can move items from staging to appropriate storage locations efficiently and consistently.

## In-scope
- Frontend screens to:
  - List put-away tasks generated from completed receipts
  - View a put-away task’s details (source staging, qty, suggested destination, fallback context)
  - Claim / assign tasks (based on permissions)
  - Handle tasks that require manual destination selection (route to destination selection UI entry point; selection UX may be minimal if a dedicated story exists)
- Moqui screen/actions wiring to call backend services/endpoints to load tasks and perform claim/assign operations.
- UI behavior aligned to backend story reference (task statuses, fallback behavior, assignment model).

## Out-of-scope
- Implementing the backend generation of tasks on GoodsReceipt completion (assumed provided by backend).
- Executing put-away (confirming actual movement/completing tasks) unless already supported elsewhere.
- Put-away rule configuration UI (PutawayRule CRUD).
- Designing storage topology management UI.

---

# 3. Actors & Stakeholders
- **Stock Clerk:** views/claims tasks; may select destination when required (if permitted).
- **Warehouse Manager:** assigns/reassigns tasks; monitors task pool.
- **Inventory Controller/Auditor:** expects traceability (who claimed/assigned, what was suggested vs final).
- **System (Inventory backend):** generates tasks and provides suggested/fallback metadata.

---

# 4. Preconditions & Dependencies
- Backend generates `PutawayTask` records when `GoodsReceipt` transitions to `COMPLETED` (per backend story reference).
- Backend exposes APIs/services to:
  - Query tasks (filters by status, assignee, receipt)
  - Claim a task (UNASSIGNED → ASSIGNED with assignee=current user)
  - Assign/reassign a task (manager action)
  - (If supported) resolve/select destination for `REQUIRES_LOCATION_SELECTION`
- AuthN/AuthZ integrated into frontend; permissions are enforced server-side and reflected in UI:
  - `CLAIM_PUTAWAY_TASK`
  - `ASSIGN_PUTAWAY_TASK`
  - `SELECT_PUTAWAY_LOCATION` (only if destination selection is supported in this UI)
- Storage locations exist; task payload includes human-readable refs or IDs resolvable to display names/barcodes.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Main navigation: **Inventory → Put-away Tasks**
- Contextual link from a completed Goods Receipt detail screen (if exists): “View Put-away Tasks” filtered by `sourceReceiptId`.

## Screens to create/modify (Moqui)
1. **Screen:** `Inventory/PutawayTaskList`
   - Lists tasks in shared pool with filters and actions (claim/assign).
2. **Screen:** `Inventory/PutawayTaskDetail`
   - Shows task details and provides claim/assign actions.
3. **(Optional minimal) Screen/dialog:** `Inventory/PutawayTaskSelectDestination`
   - Only used for status `REQUIRES_LOCATION_SELECTION` if backend supports destination resolution via UI.

## Navigation context
- `PutawayTaskList` → `PutawayTaskDetail` via `taskId`.
- From `PutawayTaskDetail`, optionally:
  - “Select destination” (only if status requires and user permitted)
  - Return to list preserving filters.

## User workflows
### Happy path (Stock Clerk claims)
1. Clerk opens Put-away Tasks list.
2. Filters to `UNASSIGNED`.
3. Opens a task; reviews source staging + qty + suggested destination.
4. Clicks **Claim**.
5. Task updates to `ASSIGNED` to the clerk; list and detail reflect assignment.

### Alternate path (Manager assigns)
1. Manager opens list; selects a task.
2. Clicks **Assign** and chooses a user.
3. Task updates; assignee shown.

### Alternate path (Requires destination selection)
1. Clerk opens task in `REQUIRES_LOCATION_SELECTION`.
2. If permitted, clicks **Select Destination**.
3. Chooses a location and saves; task now has destination and is eligible for downstream execution flow (completion handled elsewhere).

---

# 6. Functional Behavior

## Triggers
- User navigates to task list/detail screens.
- User initiates claim/assign/select-destination actions.

## UI actions
### Putaway Task List
- Search/filter:
  - Status (multi-select)
  - Assignee (Me / Unassigned / specific user if permitted)
  - Source Receipt ID (optional)
  - Product (by name/SKU if backend supports)
- Row actions (enabled by permission + state):
  - **Claim** (if `status=UNASSIGNED` and user has `CLAIM_PUTAWAY_TASK`)
  - **Assign** (if user has `ASSIGN_PUTAWAY_TASK`; opens assignee picker)

### Putaway Task Detail
- Displays:
  - Product
  - Quantity
  - Source location (staging)
  - Suggested destination (final suggested)
  - Original suggested destination + fallback reason when present
  - Status
  - Assignee
  - Links: source receipt (if route exists), locations (if route exists)
- Actions:
  - Claim (UNASSIGNED)
  - Assign/Reassign (manager)
  - Select destination (only when `REQUIRES_LOCATION_SELECTION` and permitted)

## State changes (frontend-observed)
- `UNASSIGNED` → `ASSIGNED` upon claim/assign
- Task remains `REQUIRES_LOCATION_SELECTION` until destination selected (if supported)
- No frontend-driven transitions to `IN_PROGRESS/COMPLETED` in this story unless existing backend supports and is explicitly wired (not assumed).

## Service interactions
- On list load: call backend list/query service with filters + paging.
- On detail load: call backend get-by-id service.
- On claim: call claim service; refresh detail + list row.
- On assign: call assign service; refresh.
- On select destination: call destination resolution service; refresh.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Claim button only enabled when:
  - task `status == UNASSIGNED`
  - user has permission `CLAIM_PUTAWAY_TASK`
- Assign button only enabled when:
  - user has permission `ASSIGN_PUTAWAY_TASK`
  - task `status` is not terminal (`CANCELLED`, `COMPLETED`) if those exist in UI payload
- Select destination only enabled when:
  - task `status == REQUIRES_LOCATION_SELECTION`
  - user has permission `SELECT_PUTAWAY_LOCATION`

## Enable/disable rules
- If backend returns `403`, show “Not authorized” and do not retry automatically.
- If backend returns validation error (400) on claim/assign due to stale status, show message and refresh task.

## Visibility rules
- Fallback indicators:
  - If `fallbackReason` present, show “Fallback applied” and display `originalSuggestedLocation` and `finalSuggestedLocation`.
- Suggested destination display:
  - If `suggestedDestinationLocationId` null, must display “Destination selection required”.

## Error messaging expectations
- Map backend error payload into:
  - Banner error on screen for load failures
  - Inline dialog error for action failures (claim/assign)
- Messages must include task identifier and backend-provided message when available.

---

# 8. Data Requirements

## Entities involved (frontend consumption)
- `PutawayTask`
- `StorageLocationRef` (or storage location summary)
- `GoodsReceipt` reference (read-only link)
- `User` (assignee display/picker)

## Fields (type, required, defaults)
### PutawayTask (minimum UI fields)
- `taskId` (string/UUID, required)
- `sourceReceiptId` (string/UUID, required)
- `productId` (string/UUID, required)
- `productDisplayName` (string, required for UI unless resolvable)
- `productSku` (string, optional)
- `quantity` (decimal/string, required)
- `sourceLocationId` (string/UUID, required)
- `sourceLocationDisplay` (string, required for UI unless resolvable)
- `suggestedDestinationLocationId` (string/UUID, nullable when requires selection)
- `suggestedDestinationDisplay` (string, nullable)
- `originalSuggestedLocationId` (string/UUID, nullable)
- `originalSuggestedLocationDisplay` (string, nullable)
- `finalSuggestedLocationId` (string/UUID, nullable)
- `finalSuggestedLocationDisplay` (string, nullable)
- `fallbackReason` (enum string, nullable: `DESTINATION_FULL|UNAVAILABLE`)
- `status` (enum string: `UNASSIGNED|ASSIGNED|IN_PROGRESS|COMPLETED|CANCELLED|REQUIRES_LOCATION_SELECTION`)
- `assigneeId` (string/UUID, nullable)
- `assigneeDisplayName` (string, nullable)
- `createdAt` (datetime string, required)
- `updatedAt` (datetime string, required)

### Assign action input
- `taskId` (required)
- `assigneeId` (required)

### Select destination input (if supported)
- `taskId` (required)
- `destinationLocationId` (required)
- `reason`/note (optional; only if backend supports—do not assume)

## Read-only vs editable by state/role
- Read-only always: product, qty, source location, receipt ref, created/updated.
- Editable only via actions:
  - Assignee via claim/assign permissions
  - Destination only when status requires selection and permission granted

## Derived/calculated fields
- “Fallback applied” derived from `fallbackReason != null`.
- “Unassigned” derived from `assigneeId == null` and `status==UNASSIGNED`.

---

# 9. Service Contracts (Frontend Perspective)

> Note: Exact service names/paths must match backend; below are required capabilities. If Moqui uses entity-find/services rather than REST, implement equivalent Moqui service calls.

## Load/view calls
1. **List tasks**
   - Capability: query by filters + paging/sort
   - Request (conceptual):
     - `status[]`, `assigneeId` (optional), `sourceReceiptId` (optional), `productQuery` (optional)
     - `pageIndex`, `pageSize`, `sortBy`, `sortOrder`
   - Response:
     - `items: PutawayTaskSummary[]`, `totalCount`

2. **Get task detail**
   - Input: `taskId`
   - Output: full `PutawayTask` for display

## Create/update calls
- None (task creation is system-triggered by receipt completion)

## Submit/transition calls
1. **Claim task**
   - Input: `taskId`
   - Server determines assignee = current user
   - Expected result: updated task (or success + refetch)

2. **Assign/Reassign task**
   - Input: `taskId`, `assigneeId`
   - Permission required: `ASSIGN_PUTAWAY_TASK`

3. **Select destination** (only if backend supports)
   - Input: `taskId`, `destinationLocationId`
   - Permission required: `SELECT_PUTAWAY_LOCATION`
   - Expected result: task updated with destination and status no longer `REQUIRES_LOCATION_SELECTION` (exact next status is backend-defined; UI must refetch and display whatever returned)

## Error handling expectations
- `400` validation: show backend message; refresh task.
- `403` forbidden: show authorization error; hide action on subsequent render if permissions endpoint indicates missing perm.
- `404`: show “Task not found” with link back to list.
- Concurrency: if claim conflicts, backend should return validation/conflict; UI must refetch.

---

# 10. State Model & Transitions

## Allowed states (display + filtering)
- `UNASSIGNED`
- `ASSIGNED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`
- `REQUIRES_LOCATION_SELECTION`

## Role-based transitions (frontend-initiated)
- Stock Clerk:
  - `UNASSIGNED` → `ASSIGNED` via Claim (requires `CLAIM_PUTAWAY_TASK`)
- Warehouse Manager:
  - `UNASSIGNED/ASSIGNED` → `ASSIGNED` via Assign/Reassign (requires `ASSIGN_PUTAWAY_TASK`)
- Destination selector (could be Stock Clerk if permitted):
  - `REQUIRES_LOCATION_SELECTION` → (backend-defined next state) via Select Destination (requires `SELECT_PUTAWAY_LOCATION`)

## UI behavior per state
- `UNASSIGNED`: show “Claim” (if permitted); highlight as available.
- `ASSIGNED`: show assignee; show “Reassign” for managers.
- `REQUIRES_LOCATION_SELECTION`: show warning; show “Select destination” if permitted; suggested destination fields may be empty.
- Terminal (`COMPLETED`, `CANCELLED`): actions disabled/hidden; read-only.

---

# 11. Alternate / Error Flows

## Validation failures
- Claim fails because task is no longer UNASSIGNED:
  - UI shows “Task already claimed/updated. Refreshing…” then refetch detail/list.
- Assign fails due to invalid assignee:
  - UI shows field-level error from backend; keeps dialog open.

## Concurrency conflicts
- Two users attempt to claim:
  - One succeeds; the other receives error and task refresh shows assignee.

## Unauthorized access
- User without permission:
  - Action buttons not shown if permission is known.
  - If attempted (deep link/action), backend `403` shown and UI remains read-only.

## Empty states
- No tasks match filters:
  - Show empty state with suggestion to clear filters and/or verify receipts are completed.

---

# 12. Acceptance Criteria

## Scenario 1: View generated put-away tasks after receipt completion
**Given** a Goods Receipt has transitioned to `COMPLETED` and backend generated put-away tasks in `UNASSIGNED`  
**When** a Stock Clerk navigates to Inventory → Put-away Tasks  
**Then** the list displays the generated tasks including product, quantity, source staging location, and suggested destination (if present)  
**And** the Stock Clerk can open a task detail screen by selecting a row.

## Scenario 2: Display fallback metadata when destination was adjusted
**Given** a put-away task has `fallbackReason = DESTINATION_FULL`  
**And** the task includes `originalSuggestedLocation` and `finalSuggestedLocation`  
**When** the user views the task detail  
**Then** the UI displays the fallback indicator and shows both original and final suggested destinations.

## Scenario 3: Claim an unassigned task
**Given** a put-away task is in `UNASSIGNED` status  
**And** the current user has permission `CLAIM_PUTAWAY_TASK`  
**When** the user clicks “Claim” from the list or detail  
**Then** the UI calls the claim service for that `taskId`  
**And** on success the task is shown as `ASSIGNED` to the current user in both list and detail.

## Scenario 4: Prevent claim when unauthorized
**Given** a put-away task is `UNASSIGNED`  
**And** the current user does not have permission `CLAIM_PUTAWAY_TASK`  
**When** the user views the task list or detail  
**Then** the “Claim” action is not displayed  
**And** if the user attempts a direct claim action (e.g., via URL/action) and receives `403`  
**Then** the UI shows a “Not authorized” error and does not change the task state.

## Scenario 5: Manual destination selection required
**Given** a put-away task is in `REQUIRES_LOCATION_SELECTION`  
**When** a user opens the task detail  
**Then** the UI indicates that destination selection is required  
**And** if the user has permission `SELECT_PUTAWAY_LOCATION`, a “Select destination” action is available  
**And** if the user selects a destination and saves successfully, the UI refreshes and displays the updated destination and status returned by backend.

## Scenario 6: Concurrency on claim
**Given** a put-away task is `UNASSIGNED` and visible to two users  
**When** User A claims the task successfully  
**And** User B attempts to claim the same task afterward  
**Then** User B sees an error indicating the task is no longer available  
**And** the UI refresh shows the task as assigned to User A.

---

# 13. Audit & Observability

## User-visible audit data
- Display created/updated timestamps on detail.
- Display assignee changes implicitly via current assignee and updated time (full history display only if backend provides it; do not assume).

## Status history
- If backend exposes status history/audit entries for tasks, add a read-only “History” section (optional; only if endpoint exists).
- Otherwise, show current status and timestamps.

## Traceability expectations
- Task detail must show `sourceReceiptId` and allow navigation to receipt (if receipt screen exists).
- If backend returns applied rule reference or rule name, display it read-only (optional; do not assume presence).

---

# 14. Non-Functional UI Requirements

- **Performance:** Task list initial load should render within 2s for first page under normal conditions; use paging.
- **Accessibility:** All actions keyboard-navigable; dialogs have proper focus management; labels for form controls.
- **Responsiveness:** Works on tablet-sized screens used in warehouse; table can switch to card list at small widths.
- **i18n/timezone:** Display timestamps in user locale/timezone per app standard; do not change stored values.
- **Security:** Never rely on frontend-only permission checks; always handle 403 from backend.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-state message and “Clear filters” action when no tasks are returned; safe because it does not alter domain logic. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-PAGINATION: Default page size 25 with server-side paging; safe UI ergonomics only. (Impacted: UX Summary, Service Contracts)
- SD-ERR-MAP-HTTP: Standard mapping of 400/403/404/5xx to banner/dialog errors; safe because it is generic error presentation. (Impacted: Business Rules, Error Flows)

---

# 16. Open Questions
1. What are the exact backend API endpoints (or Moqui services) for: list tasks, get task, claim, assign, and (if supported) select destination? Provide request/response schemas and error payload format.
2. Is there an existing Goods Receipt detail screen/route in this frontend to deep-link from `sourceReceiptId`? If yes, what is the screen path and parameter name?
3. For `REQUIRES_LOCATION_SELECTION`, does backend provide a “search locations” endpoint with compatibility constraints (e.g., allowed locations for this product), or should the UI present a generic location picker?
4. Does backend expose permission/feature flags to allow frontend to hide actions reliably (e.g., `hasPermission()` service), or should UI render actions and rely solely on 403 handling?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Putaway: Generate Put-away Tasks from Staging  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/96  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Putaway: Generate Put-away Tasks from Staging

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **Stock Clerk**, I want put-away tasks generated so that received items are placed into proper storage locations.

## Details  
- Rules: default bin by product category, manual destination.  
- Tasks list product, qty, from staging, suggested destination.

## Acceptance Criteria  
- Put-away tasks created after receipt.  
- Suggested destinations provided.  
- Tasks assignable.

## Integrations  
- Uses storage topology and optional replenishment rules.

## Data / Entities  
- PutawayTask, PutawayRule, StorageLocationRef

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