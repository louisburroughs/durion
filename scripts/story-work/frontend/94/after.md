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
[FRONTEND] [STORY] Putaway: Replenish Pick Faces from Backstock (Optional) — Replenishment Task List + Execute Move

### Primary Persona
Warehouse Associate (secondary: Inventory Manager)

### Business Value
Keeps pick faces stocked by surfacing system-generated replenishment tasks and enabling associates to complete inventory moves with auditability, reducing mechanic stockouts and pick delays.

---

## 2. Story Intent

### As a / I want / So that
**As a** Warehouse Associate,  
**I want** to view replenishment tasks and execute the backstock → pick face move,  
**so that** pick locations stay stocked and the system records an auditable inventory transfer.

### In-scope
- View a list of open replenishment tasks (PENDING / IN_PROGRESS).
- View task details (item, qty, source, destination, trigger metadata).
- Start work on a task (mark IN_PROGRESS).
- Complete a task by confirming quantities moved and submitting a transfer.
- See success confirmation and updated task status.
- View audit/status history for a task (read-only).

### Out-of-scope
- Defining/editing `ReplenishmentPolicy` (min/max) UI.
- Automatically generating tasks (event/batch logic) — backend responsibility.
- Complex picking UX (batch picking routes, cart optimization).
- Inventory valuation/costing display or edits.
- Substitution, backorders, or multi-SKU fulfillment (not defined).

---

## 3. Actors & Stakeholders
- **Warehouse Associate (Primary user):** executes replenishment tasks.
- **Inventory Manager:** monitors replenishment workload/compliance.
- **Mechanic/Technician (Indirect beneficiary):** faster picks; fewer stockouts.
- **System/Inventory Services (Backend):** provides tasks, enforces idempotency, records ledger transfers and audits.

---

## 4. Preconditions & Dependencies
1. Backend exposes APIs to:
   - Query replenishment tasks and task details.
   - Transition task statuses (PENDING → IN_PROGRESS → COMPLETED; optional CANCELLED).
   - Execute/record inventory transfer for a task (creates InventoryLedgerEntry of type Transfer).
2. Backend enforces business rules from inventory domain (duplicate prevention, task idempotency, sourcing decisions).
3. User authentication exists; authorization checks are enforceable per endpoint.
4. Storage locations have identifiable labels/barcodes in the system (at least displayable).
5. Inventory quantities are stored in a consistent unit of measure for the SKU (frontend treats quantity as integer unless contract says otherwise).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Inventory → Putaway/Replenishment → Replenishment Tasks**
- Optional deep link: `/inventory/replenishment/tasks` and `/inventory/replenishment/tasks/<taskId>`

### Screens to create/modify
1. **Screen:** `apps/pos/screen/inventory/ReplenishmentTaskList.xml`
2. **Screen:** `apps/pos/screen/inventory/ReplenishmentTaskDetail.xml`
3. (Optional) Reusable widget/screenlet for task status chip + trigger metadata.

### Navigation context
- From Inventory module.
- Task list → task detail.
- Task detail → complete flow → returns to task detail (now COMPLETED) or back to list.

### User workflows
**Happy path**
1. Associate opens Task List; filters to their site/location (if supported) and status=PENDING.
2. Selects a task to open details.
3. Clicks **Start Task** (sets IN_PROGRESS).
4. Confirms **quantity moved** (default to task quantity) and submits **Complete Task**.
5. System records transfer and marks task COMPLETED; UI shows confirmation and ledger reference (if provided).

**Alternate paths**
- Task already claimed/in-progress by someone else → show read-only with message.
- Partial move allowed? (unclear) If allowed, complete with less quantity and leave task open or split tasks (unclear).
- Insufficient stock at source at execution time → backend rejects; UI shows error and keeps task IN_PROGRESS or reverts (backend-defined).

---

## 6. Functional Behavior

### Triggers
- User navigates to list/detail screens.
- User actions: Start, Complete (and possibly Cancel).

### UI actions
**Task List**
- Load tasks with filters:
  - Status (default: PENDING, IN_PROGRESS)
  - Search by SKU / taskId
  - Filter by source/destination location (if supported)
  - Sort by createdAt (default: oldest first)
- Row click opens detail.
- If backend supports, quick action: “Start” from list.

**Task Detail**
- Shows task header: status, SKU, qty, source, destination, triggerType, decisionReason, sourcingReason, createdAt, assignedTo.
- Actions by state:
  - PENDING: Start Task
  - IN_PROGRESS: Complete Task (and possibly Cancel)
  - COMPLETED/CANCELLED: no write actions
- Complete Task form:
  - quantityMoved (prefill = task.quantity; editable per rules below)
  - optional notes (if supported by backend)
  - submit

### State changes
- `PENDING` → `IN_PROGRESS` via explicit user action.
- `IN_PROGRESS` → `COMPLETED` on successful transfer submission.
- Optional: `PENDING/IN_PROGRESS` → `CANCELLED` (unclear if allowed).

### Service interactions
- Read: list + detail + history/audit (if endpoint exists).
- Write: start transition; complete (transfer + status update) ideally as one backend transaction.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `quantityMoved`:
  - Must be a positive integer.
  - Must be ≤ task.quantity **unless backend supports partial replenishment completion semantics** (OPEN QUESTION).
- Locations and SKU are read-only on the UI (sourcing is deterministic and backend-owned).
- Prevent editing of system-managed metadata: triggerType, decisionReason, sourcingReason.

### Enable/disable rules
- **Start Task** enabled only when:
  - task.status == PENDING
  - user has permission to work replenishment tasks (permission name is unclear → OPEN QUESTION)
- **Complete Task** enabled only when:
  - task.status == IN_PROGRESS
  - (if assignedTo exists) assignedTo == currentUser (else show explanation) — assignment model unclear (OPEN QUESTION)

### Visibility rules
- Assigned-to block shown only if field exists.
- Audit/history section shown if backend provides history; otherwise omit.

### Error messaging expectations
- 400 validation: show field-level error (quantityMoved) or banner (general).
- 403: show “You don’t have permission to perform this action.”
- 409 concurrency: show “Task was updated by another user; refresh to continue.” Provide Refresh button.
- 404: “Task not found.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `ReplenishmentTask`
- `ReplenishmentPolicy` (reference only; not edited here)
- `InventoryLedgerEntry` (reference link/ID after completion, if provided)

### Fields (type, required, defaults)
**ReplenishmentTask**
- `taskId` (UUID, required, read-only)
- `itemSKU` (string, required, read-only)
- `quantity` (integer, required, read-only)
- `sourceLocationId` (UUID/string, required, read-only)
- `destinationLocationId` (UUID/string, required, read-only)
- `status` (enum: PENDING/IN_PROGRESS/COMPLETED/CANCELLED, required, read-only except via transitions)
- `triggerType` (enum EVENT/BATCH, read-only)
- `decisionReason` (enum BELOW_MIN/SAFETY_SCAN, read-only)
- `sourcingReason` (string/enum, read-only)
- `createdAt` (timestamp, read-only)
- `assignedTo` (UUID, optional, read-only)

**Complete form (UI model)**
- `quantityMoved` (integer, required; default = `ReplenishmentTask.quantity`)
- `completionNote` (string, optional) **only if backend supports**

### Read-only vs editable by state/role
- All task fields read-only always.
- Only transition actions are available depending on state and permissions.

### Derived/calculated fields
- Display labels for locations (e.g., barcode/human name) derived from `sourceLocationId` and `destinationLocationId` by calling a location lookup service (if exists; otherwise show IDs) (OPEN QUESTION).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contracts are not provided in this frontend issue; endpoints below are **placeholders** that must be mapped to actual Moqui services once confirmed.

### Load/view calls
- `GET /replenishmentTasks?status=...&filters...`  
  Returns list with pagination metadata.
- `GET /replenishmentTasks/{taskId}`  
  Returns full task detail.
- Optional: `GET /replenishmentTasks/{taskId}/history` for status/audit entries.

### Create/update calls
- None (task creation is backend/system-generated).

### Submit/transition calls
- Start task:
  - `POST /replenishmentTasks/{taskId}/start`
  - Expected response: updated task (status=IN_PROGRESS; assignedTo set or unchanged)
- Complete task (preferred atomic):
  - `POST /replenishmentTasks/{taskId}/complete` with body `{ quantityMoved, note? }`
  - Expected response: updated task (COMPLETED) + ledger transfer reference `{ ledgerEntryId? }`
- If backend splits transfer vs task completion (not preferred):
  - `POST /inventory/transfer` then `POST /replenishmentTasks/{taskId}/complete` (would require extra error handling and compensation) (OPEN QUESTION)

### Error handling expectations
- 400: validation details in response; map to inline form errors and banner.
- 403: show permission error; disable further write actions.
- 409: indicate stale state; offer refresh and retry.
- 500/timeout: show transient error; keep user input; allow retry.

---

## 10. State Model & Transitions

### Allowed states (as per backend reference)
- `PENDING`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`

### Role-based transitions (UI enforcement; backend authoritative)
- Warehouse Associate:
  - PENDING → IN_PROGRESS (Start)
  - IN_PROGRESS → COMPLETED (Complete)
  - (Optional) PENDING/IN_PROGRESS → CANCELLED (Cancel) **unclear**
- Inventory Manager:
  - May view all tasks; may override/cancel **unclear**

### UI behavior per state
- PENDING: show Start button; hide completion form.
- IN_PROGRESS: show completion form + Complete button.
- COMPLETED/CANCELLED: show summary + audit info; no edit actions.

---

## 11. Alternate / Error Flows

1. **Empty list**
   - No open tasks: show empty state “No replenishment tasks found” and suggest adjusting filters.
2. **Concurrency conflict**
   - Task transitions return 409: UI refreshes task detail; if now COMPLETED/CANCELLED, show updated state and disable actions.
3. **Unauthorized**
   - Any write returns 403: show error and disable action buttons.
4. **Execution-time inventory constraint**
   - Completing task fails due to insufficient source stock or invalid location status: show backend message; keep task in IN_PROGRESS and allow retry or back out (backend-defined).
5. **Network failure**
   - Show retry; do not clear form input.

---

## 12. Acceptance Criteria

```gherkin
Scenario: View open replenishment tasks
  Given I am an authenticated Warehouse Associate
  When I navigate to Inventory > Putaway/Replenishment > Replenishment Tasks
  Then I see a list of replenishment tasks filtered to status PENDING and IN_PROGRESS by default
  And each row displays taskId, itemSKU, quantity, source location, destination location, status, and createdAt

Scenario: View replenishment task details
  Given a replenishment task exists with status PENDING
  When I open the task detail page
  Then I see the task’s itemSKU, quantity, source location, destination location, triggerType, decisionReason, sourcingReason, and status

Scenario: Start a replenishment task
  Given a replenishment task exists with status PENDING
  And I have permission to work replenishment tasks
  When I click "Start Task"
  Then the task status becomes IN_PROGRESS
  And the UI shows the completion form with quantityMoved defaulted to the task quantity

Scenario: Complete a replenishment task successfully
  Given a replenishment task exists with status IN_PROGRESS
  When I enter quantityMoved equal to the task quantity
  And I click "Complete Task"
  Then the system records an inventory transfer from the source location to the destination location
  And the task status becomes COMPLETED
  And I see a success message

Scenario: Prevent completion with invalid quantity
  Given a replenishment task exists with status IN_PROGRESS
  When I enter quantityMoved as 0 or a negative number
  And I click "Complete Task"
  Then I see a validation error on quantityMoved
  And no request to complete the task is submitted

Scenario: Handle permission error on start/complete
  Given a replenishment task exists with status PENDING or IN_PROGRESS
  When I attempt to Start or Complete the task without sufficient permission
  Then I receive a 403 response
  And the UI displays an authorization error message
  And write actions are disabled for that task

Scenario: Handle concurrency conflict during completion
  Given a replenishment task exists with status IN_PROGRESS
  And another user completes the task before I submit
  When I click "Complete Task"
  Then I receive a 409 conflict response
  And the UI prompts me to refresh
  And after refresh the task status is shown as COMPLETED and actions are disabled
```

---

## 13. Audit & Observability

### User-visible audit data
- On task detail, show (if provided):
  - createdAt, createdBy/system
  - status history entries (fromStatus, toStatus, changedAt, changedBy)
  - triggerType, decisionReason, sourcingReason (read-only metadata)

### Status history
- Display a chronological list of transitions.
- If unavailable from backend, show at least current status + createdAt.

### Traceability expectations
- Completion success view should display any returned reference IDs:
  - `ledgerEntryId` (or equivalent transfer reference)
  - `taskId`

---

## 14. Non-Functional UI Requirements
- **Performance:** task list loads first page within 2s on typical connection; show loading skeleton/spinner.
- **Accessibility:** keyboard navigable actions; form fields have labels; status conveyed with text (not color-only).
- **Responsiveness:** usable on tablet widths; list supports horizontal truncation with details in row expansion if needed.
- **i18n/timezone:** timestamps rendered in user’s locale/timezone; IDs/SKUs not localized.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide deterministic empty-state messaging for no tasks; safe UI ergonomics; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION: Default server-driven pagination for task list; safe because it does not change domain logic; impacts UX Summary, Service Contracts.
- SD-ERR-HTTP-MAP: Standard mapping of 400/403/404/409/5xx to inline/banner messages; safe because it’s presentation-layer only; impacts Business Rules, Error Flows.

---

## 16. Open Questions
1. **Backend API contract:** What are the actual Moqui service names / REST paths for listing tasks, viewing detail, starting, and completing a task? Is completion atomic (transfer + status) in one call?
2. **Permissions:** What permission strings/roles gate: view tasks, start task, complete task, cancel task?
3. **Assignment semantics:** When a task is started, is `assignedTo` set/required, and must only the assignee complete it?
4. **Partial completion policy:** Can `quantityMoved` be less than task.quantity? If yes, what happens to the remaining quantity (task remains open, new task created, or task adjusted)?
5. **Cancel behavior:** Is CANCELLED a supported UI action? Who can cancel and under what conditions?
6. **Location display:** Is there a service to resolve `locationId` to a human label/barcode per site? If not, should we display raw IDs only?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Putaway: Replenish Pick Faces from Backstock (Optional) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/94

Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Putaway: Replenish Pick Faces from Backstock (Optional)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want replenishment moves so that pick locations stay stocked.

## Details
- Define min/max for pick bins.
- Create replenishment tasks when below min.

## Acceptance Criteria
- Replenishment tasks created.
- Moves recorded.
- Audited.

## Integrations
- Improves mechanic pick speed; reduces stockouts.

## Data / Entities
- ReplenishmentPolicy, ReplenishmentTask, InventoryLedgerEntry(Transfer)

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