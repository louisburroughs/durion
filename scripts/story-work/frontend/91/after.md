## 🏷️ Labels (Proposed)

STOP: Clarification required before finalization

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
[FRONTEND] [STORY] Counts: Execute Cycle Count and Record Variances

### Primary Persona
Auditor

### Business Value
Enable auditors to record blind cycle counts per bin, producing immutable count entries and system-calculated variances so inventory discrepancies can be reviewed, recounted within policy, and escalated when unresolved.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Auditor  
- **I want** to select a cycle count task for a bin and submit an actual counted quantity (with a bounded ability to recount)  
- **So that** the system computes variance against expected quantity, retains an immutable audit trail, and supports escalation to investigation when counts don’t reconcile.

### In-scope
- View a list of assigned/available cycle count tasks relevant to the auditor.
- Execute a **blind count** for a selected `CycleCountTask` (expected qty not shown during entry).
- Submit initial count and (if allowed) trigger/submit recounts, each creating a new immutable `CountEntry`.
- Display variance results **after submission** (and/or in a review state) in a manner consistent with blind-count policy.
- Display count entry history for a task (read-only).
- Handle validation, authorization, and cap enforcement errors from backend.
- Show state/status of the task and guide user actions accordingly.

### Out-of-scope
- Manager review/approval UI beyond triggering recount if manager persona uses same screens (see Open Questions).
- Performing inventory adjustments, posting to accounting, or emitting accounting events (future).
- Creating cycle count plans/tasks (assumed pre-existing).
- Advanced variance report aggregation across tasks (only task-level variance visibility unless backend provides report endpoints).
- Scanner/barcode-driven bin navigation (unless already standard in repo; not specified here).

---

## 3. Actors & Stakeholders
- **Auditor (primary):** Executes counts, submits quantities, may trigger one immediate recount (policy-based).
- **Inventory Manager (stakeholder):** May trigger additional recounts (policy-based) and handles investigation escalation (may be via separate story).
- **System (Inventory domain):** Computes expected quantity and variance; enforces recount caps; stores immutable entries and status history.
- **Accounting (future downstream):** May consume variance/adjustment signals later; not in current UI scope.

---

## 4. Preconditions & Dependencies
- User is authenticated in the frontend and has inventory count permissions required by backend.
- Backend provides APIs to:
  - List/view `CycleCountTask` records accessible to the user.
  - Submit initial count and recounts, creating immutable `CountEntry` records.
  - Return task status, latest entry pointer, total count entries, and whether recount is allowed.
- A `CycleCountTask` exists with:
  - product reference and location/bin reference,
  - an internal `expectedQuantity` (not shown during entry),
  - a state machine including at least `COUNTED_PENDING_REVIEW` and `REQUIRES_INVESTIGATION` (or equivalent).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Inventory → Cycle Counts → My Tasks** (exact menu location is an implementation detail; route must exist).
- Deep link: `/counts/tasks/<cycleCountTaskId>` to open a specific task.

### Screens to create/modify (Moqui screens)
1. **`Counts/TaskList`** (new or extend existing inventory counts list screen)
   - Shows tasks assigned/available to the current user.
2. **`Counts/TaskDetail`** (new)
   - Displays task context (bin/location, SKU/product description) and count entry form.
3. **`Counts/TaskHistory`** (embedded section on TaskDetail or separate screen)
   - Read-only list of `CountEntry` rows for the task (including recount sequence and timestamps).
4. **Optional**: **`Counts/VarianceView`** (if policy requires showing variance only in a separate post-submit view)

### Navigation context
- Breadcrumbs: Inventory → Cycle Counts → Task List → Task Detail.
- From Task List, selecting a row transitions to Task Detail with taskId parameter.
- From Task Detail, “Back to list” returns to Task List preserving filters (safe default).

### User workflows
#### Happy path: initial blind count
1. Auditor opens Task List and selects an `ACTIVE` (or equivalent) task.
2. Task Detail loads task context **without expectedQuantity**.
3. Auditor enters `actualQuantity`.
4. Auditor submits count.
5. UI shows submission success, updated task status, and the recorded entry in history.
6. UI shows variance information only after submission (subject to Open Question #1).

#### Alternate path: immediate recount (auditor)
1. After initial submission, if “Recount available” and auditor has not used their self-recount allowance:
2. Auditor triggers recount mode and submits a new `actualQuantity`.
3. UI shows new entry appended with incremented sequence and proper linkage.

#### Alternate path: cap reached
1. Task already has 3 entries.
2. Any attempt to trigger recount results in backend error and/or status changes to `REQUIRES_INVESTIGATION`.
3. UI shows status and blocks recount UI.

---

## 6. Functional Behavior

### Triggers
- Route entry to Task List or Task Detail.
- User submits count form.
- User clicks “Trigger recount” (if allowed) then submits recount form.

### UI actions
- **Task List**
  - Filter by status (safe default).
  - Select task row → navigate to Task Detail.
- **Task Detail**
  - Display: task identifiers, location/bin barcode (if available), product/SKU, UOM (if available).
  - Input: `actualQuantity` with numeric validation per UOM rules (see Open Question #2).
  - Actions:
    - `Submit Count` (initial) when task state allows.
    - `Trigger Recount` when allowed by backend/policy and cap not exceeded.
    - `Submit Recount` when recount mode active.
  - History: list immutable `CountEntry` entries.
  - Read-only status indicator for task status.

### State changes (frontend-visible)
- After successful submit:
  - Task status updates to `COUNTED_PENDING_REVIEW` (or backend equivalent).
  - `latestCountEntryId` updates.
  - `totalCountEntries` increments.
- When recount cap exceeded:
  - Backend sets task status to `REQUIRES_INVESTIGATION`; UI reflects this and disables count inputs.

### Service interactions (Moqui)
- Use Moqui `service-call` from screens/forms to backend services (REST via Moqui service facade or standard service definitions in this project).
- Forms submit to transitions that invoke services and then redirect back to the Task Detail with refreshed data.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `actualQuantity`:
  - Must be numeric.
  - Must be **>= 0**.
  - Decimal precision must comply with the item’s UOM rules (Open Question #2 if not provided).
- Do not allow empty submission; show inline error: “Quantity is required.”
- If backend rejects (400) with field errors, map to the form field and show a summary message.

### Enable/disable rules
- Disable `Submit Count` if:
  - task not in a state that accepts a count (e.g., already `REQUIRES_INVESTIGATION`, already finalized/closed),
  - user lacks permission,
  - cap reached.
- Show `Trigger Recount` only if backend indicates recount is allowed for this user and state (prefer backend-driven boolean flags to avoid duplicating policy).

### Visibility rules (blind count)
- `expectedQuantity` must **never be displayed** during count entry.
- Variance display timing:
  - Variance may be displayed only after submission or in history view (Open Question #1 to confirm).

### Error messaging expectations
- Unauthorized (403): “You do not have permission to perform this action.”
- Recount not allowed: “Recount is not allowed for this task.”
- Cap reached / investigation: “Recount limit reached. Task requires investigation.”
- Concurrency (409): “This task was updated by another user. Refresh and try again.”

---

## 8. Data Requirements

### Entities involved (frontend view)
- `CycleCountTask`
- `CountEntry`
- (Optional/future) `VarianceReport`
- `AuditLog` (exposed via backend as audit metadata or history list)

### Fields (type, required, defaults)

#### `CycleCountTask` (read + state indicators)
- `cycleCountTaskId` (ID, required)
- `status` (enum/string, required)
- `productId` (ID, required)
- `productSku`/`internalName` (string, optional but desirable for UX)
- `storageLocationId` / `binId` (ID, required)
- `binLabel` / `barcode` (string, optional)
- `uomId` / `uomLabel` (string, optional; required to validate decimals if used)
- `latestCountEntryId` (ID, optional)
- `totalCountEntries` (integer, required if cap enforcement shown in UI)
- **Derived (frontend-only):**
  - `canSubmitCount` (boolean; preferably provided by backend)
  - `canTriggerRecount` (boolean; preferably provided by backend)
  - `maxCountsAllowed` (int; if provided)

#### `CountEntry` (read-only list + post-submit display)
- `countEntryId` (ID)
- `cycleCountTaskId` (ID)
- `auditorId` (ID)
- `actualQuantity` (decimal/integer)
- `expectedQuantity` (decimal/integer) — **must not display during entry; may display in history based on policy (Open Question #1)**
- `variance` (decimal/integer)
- `countedAt` (timestamp)
- `recountSequenceNumber` (int)
- `recountOfCountEntryId` (ID, nullable)

### Read-only vs editable
- Editable: only `actualQuantity` input in the submission form.
- Read-only: all other fields including variance, expected qty, and audit fields.

### Derived/calculated fields
- Variance is computed by backend: `variance = actualQuantity - expectedQuantity`.
- UI should not calculate variance except for display of backend-provided value (avoid drift).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is referenced from the backend story but concrete endpoint names are not provided here. Moqui implementation must map to actual services once confirmed.

### Load/view calls
- `Counts.TaskList.get`  
  - Inputs: filters (status), pagination
  - Output: list of `CycleCountTask` summary records
- `Counts.TaskDetail.get`
  - Inputs: `cycleCountTaskId`
  - Output: `CycleCountTask` detail + `CountEntry` history (or separate call)

### Create/update calls
- None (no editing tasks here)

### Submit/transition calls
- `Counts.CountEntry.create` (initial count)
  - Inputs: `cycleCountTaskId`, `actualQuantity`
  - Output: created `CountEntry` + updated `CycleCountTask` status/pointers
- `Counts.Recount.trigger` (optional if backend models a trigger step)  
  - Inputs: `cycleCountTaskId`
  - Output: updated task / recount allowed state
- `Counts.Recount.submit` (if separate from create)  
  - Inputs: `cycleCountTaskId`, `actualQuantity`, maybe `recountOfCountEntryId`
  - Output: created `CountEntry` + updated task

### Error handling expectations
- `400` validation errors: return field-level details; UI maps to form.
- `403` forbidden: show permission message; keep task view in read-only mode.
- `404` task not found or not accessible: show “Task not found or access denied” and link back to list.
- `409` concurrency/state change: prompt refresh; optionally auto-refresh the task detail.

---

## 10. State Model & Transitions

### Allowed states (minimum required for UI)
- `ACTIVE` (or equivalent “ready to count”)
- `COUNTED_PENDING_REVIEW`
- `REQUIRES_INVESTIGATION`
- (Optional) `FINALIZED` / `CLOSED` if backend uses it (Open Question #3)

### Role-based transitions (as surfaced to UI)
- Auditor:
  - `ACTIVE` → submit initial count → `COUNTED_PENDING_REVIEW`
  - May trigger **one** immediate recount prior to manager finalization (backend-enforced)
- Inventory Manager:
  - May trigger recounts beyond auditor allowance until cap (backend-enforced)
- Any role:
  - Attempt beyond cap → transition to `REQUIRES_INVESTIGATION` (backend enforced), UI becomes non-editable

### UI behavior per state
- `ACTIVE`: show count entry form, hide expected qty.
- `COUNTED_PENDING_REVIEW`: show history; allow recount only if backend says yes.
- `REQUIRES_INVESTIGATION`: disable all count inputs; show status and guidance to contact manager.
- `FINALIZED/CLOSED` (if exists): read-only.

---

## 11. Alternate / Error Flows

### Validation failures
- Negative quantity submitted → inline error; prevent submit.
- Non-numeric input → inline error.
- Excess decimal precision → inline error if UOM rules known; otherwise rely on backend error mapping.

### Concurrency conflicts
- Task status changed since load (e.g., another auditor counted) → backend returns 409; UI shows refresh prompt and reloads task.

### Unauthorized access
- User without permission tries to submit → 403; disable form and show message.

### Empty states
- Task list empty → show “No cycle count tasks available” with suggestion to adjust filters.
- Task history empty:
  - If task is `ACTIVE`: expected; show “No counts submitted yet.”
  - Otherwise: show error banner “Counts missing; refresh or contact support” (possible data issue).

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Auditor submits initial blind count and system records variance
**Given** an Auditor has access to a cycle count task in a countable state  
**And** the task has an internal expected quantity (not shown during entry)  
**When** the Auditor enters an actual quantity of `102` and submits  
**Then** the UI sends a count submission for that task to the backend  
**And** the backend returns a newly created immutable CountEntry (sequence 1)  
**And** the UI refreshes and shows the task status as `COUNTED_PENDING_REVIEW`  
**And** the UI shows the recorded count entry in history  
**And** the UI does not display expected quantity prior to submission.

### Scenario 2: Auditor attempts to submit a negative quantity
**Given** an Auditor is on a cycle count task detail screen  
**When** they enter `-1` and attempt to submit  
**Then** the UI blocks submission  
**And** displays “Quantity must be zero or a positive number.”

### Scenario 3: Auditor triggers one immediate recount and submits a second entry
**Given** a task has an initial CountEntry  
**And** the backend indicates the Auditor can trigger a self recount for the task  
**When** the Auditor triggers recount and submits a new actual quantity  
**Then** the backend creates a new immutable CountEntry with incremented recount sequence  
**And** the UI shows both entries in history in sequence order.

### Scenario 4: Auditor attempts a second self recount and is rejected
**Given** a task already has an initial count and one auditor recount  
**When** the Auditor attempts to trigger another recount  
**Then** the backend returns `403` or a domain error indicating recount not allowed  
**And** the UI displays an access/policy message  
**And** the UI does not allow submission of another recount.

### Scenario 5: Recount cap reached escalates to investigation
**Given** a task has 3 total CountEntry records  
**When** the user attempts to trigger or submit another recount  
**Then** the backend blocks the request and sets status to `REQUIRES_INVESTIGATION` (or returns that status)  
**And** the UI refreshes to show `REQUIRES_INVESTIGATION`  
**And** all count submission controls are disabled.

### Scenario 6: Unauthorized user cannot submit counts
**Given** a user without cycle count execution permission opens a task  
**When** they attempt to submit a count  
**Then** the backend returns `403 Forbidden`  
**And** the UI shows “You do not have permission to perform this action”  
**And** the UI keeps the task in read-only mode.

---

## 13. Audit & Observability

### User-visible audit data
- Task history shows:
  - `countedAt` timestamp
  - `recountSequenceNumber`
  - `actualQuantity`
  - `variance` (if allowed to display; see Open Question #1)
  - performer (auditor name/ID if available)

### Status history
- If backend exposes status transitions, show a simple status history section (timestamp + from/to + actor). If not exposed, omit.

### Traceability expectations
- Every submission should surface a confirmation containing `countEntryId` (or reference) for support/debugging (e.g., “Recorded as Entry #<id>” in a non-sensitive way).

---

## 14. Non-Functional UI Requirements
- **Performance:** Task detail load should complete with a single round trip where possible; avoid N+1 calls when rendering history.
- **Accessibility:** All form controls labeled; validation errors announced; keyboard navigable submit actions.
- **Responsiveness:** Task list and detail usable on tablet-sized screens typical for warehouse/auditor usage.
- **i18n/timezone:** Display timestamps in user locale/timezone while storing/using UTC from backend.
- **Numeric input:** Respect locale decimal separator if project supports it (otherwise enforce dot and document).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide clear empty states for task list/history; safe because it does not change domain logic. Impacted sections: UX Summary, Alternate/Empty states.
- SD-UX-PAGINATION: Paginate task list with standard page size; safe because it only affects presentation and load behavior. Impacted sections: UX Summary, Service Contracts.
- SD-ERR-MAP-STD: Map backend 400/403/404/409 to standard UI messages and field errors; safe because it follows HTTP semantics and does not invent policy. Impacted sections: Business Rules, Error Flows, Service Contracts.

---

## 16. Open Questions
1. **Blind count reveal policy:** After submission, may the Auditor see `expectedQuantity` and `variance` immediately, or only Inventory Manager/review screens? (Backend story implies variance is computed; UI display rules must be confirmed.)
2. **UOM / decimal precision rules:** Are counted quantities always integers, or can they be decimals by item/UOM? If decimals allowed, what precision per UOM and rounding rules should the UI enforce vs defer to backend?
3. **Full task state machine:** Besides `COUNTED_PENDING_REVIEW` and `REQUIRES_INVESTIGATION`, what other statuses exist (e.g., `ACTIVE`, `FINALIZED`, `CLOSED`), and which are countable/recountable?
4. **Service/API contracts:** What are the exact Moqui service names or REST endpoints and response schemas for:
   - task list,
   - task detail + history,
   - submit initial count,
   - trigger/submit recount?
5. **Authorization mapping:** What permissions/roles are enforced by backend and should be reflected in UI gating (e.g., `TRIGGER_RECOUNT_SELF`, `TRIGGER_RECOUNT_ANY`), and how does the frontend discover them (claims vs endpoint flags)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Counts: Execute Cycle Count and Record Variances — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/91

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Counts: Execute Cycle Count and Record Variances  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/91  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Counts: Execute Cycle Count and Record Variances

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As an **Auditor**, I want to record counted quantities so that variances can be reviewed and corrected.

## Details  
- Count tasks per bin.  
- Record counts; optional recount.  
- Variance report generated.

## Acceptance Criteria  
- Counts recorded.  
- Variance computed.  
- Recount supported (basic).  
- Audited.

## Integrations  
- May later emit accounting adjustment events.

## Data / Entities  
- CycleCountTask, CountEntry, VarianceReport, AuditLog

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