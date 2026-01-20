## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:people
- status:draft

### Recommended
- agent:people-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

STOP: Clarification required before finalization

---

# 1. Story Header

## Title
**[FRONTEND] Timekeeping: Manager Approves/Rejects Time Entries (Period-Atomic)**

## Primary Persona
**Manager** (authorized to approve time for direct reports)

## Business Value
Finalizes employee time for a pay period with an auditable decision record, enabling payroll export and downstream reconciliation while preventing unauthorized or inconsistent edits.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Manager  
- **I want** to review an employee’s time entries for a selected pay period and approve or reject them in one atomic action  
- **So that** time is finalized (approved = locked) or returned for correction (rejected), with a complete audit trail.

## In-Scope
- Manager UI to:
  - Select an employee (direct report) and a pay period
  - View time entries and their statuses for that period
  - Approve all entries in `PENDING_APPROVAL` for the period (atomic)
  - Reject all entries in `PENDING_APPROVAL` for the period (atomic) with required reason metadata
  - View decision history (period-level approvals/rejections)
- Frontend enforcement of read-only behavior for `APPROVED` entries (no edit controls in this manager view).
- Correct handling of backend validation errors (`400`), authorization (`403`), conflicts (`409`), and not-found (`404`).

## Out-of-Scope
- Employee-side time entry editing/resubmission flow after rejection.
- “Controlled adjustments” workflow UI (TimeEntryAdjustment create/approve).
- Defining reporting relationships, role inheritance, or permission matrix (must be provided by backend/security).
- Payroll export UX.

---

# 3. Actors & Stakeholders
- **Manager (Primary Actor):** reviews and decides for employee/time period.
- **Employee (Indirect):** impacted by approval (locks) or rejection (returns to draft).
- **HR/People Ops (Stakeholder):** needs auditable records.
- **Payroll System (Stakeholder):** consumes approved time (downstream).
- **Work Execution (Optional Stakeholder):** may consume approved job time for posting.

---

# 4. Preconditions & Dependencies
- User is authenticated in the frontend session.
- User has permission `timekeeping:approve` (authorization enforced server-side).
- Backend provides:
  - List of employees manager may act on (direct reports / scope filtered)
  - List of time periods and their status (`OPEN`, `SUBMISSION_CLOSED`, `PAYROLL_CLOSED`)
  - Ability to fetch time entries for `{employeeId, timePeriodId}`
  - Action endpoints to approve/reject (period-atomic) and return appropriate status codes and error payloads.
- Time period gating enforced by backend: decisions require `TimePeriod.status >= SUBMISSION_CLOSED`.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From Manager menu: **Timekeeping → Approvals**
- Deep link supported (if routes exist): `/timekeeping/approvals` with optional query params `employeeId`, `timePeriodId`

## Screens to create/modify
1. **Screen:** `apps/pos/timekeeping/approvals.xml` (new)
   - Purpose: shell + selection controls + results panel
2. **Screen (optional child):** `apps/pos/timekeeping/approvalDetail.xml` (optional, can be same screen via subscreen)
   - Purpose: show entries table + decision buttons + decision history
3. Common components/forms:
   - Selection form (employee + pay period)
   - Entries list table (read-only)
   - Reject dialog form (reason code + notes)

## Navigation context
- Breadcrumb: `Timekeeping > Approvals`
- Back navigation returns to selection state.

## User workflows
### Happy path: Approve
1. Manager opens Approvals screen.
2. Manager selects Employee + Pay Period.
3. System loads entries; shows statuses and summary.
4. If eligible, Manager clicks **Approve Period** and confirms.
5. UI calls approve action; on success refreshes entries and history; shows success message.

### Happy path: Reject
1. Manager clicks **Reject Period**.
2. UI opens dialog requiring reason code + notes.
3. Submit triggers reject action; on success refreshes entries (now `DRAFT`) and history; shows success message.

### Alternate paths
- Period not eligible (time period still `OPEN`): decision buttons disabled with explanatory message.
- Mixed statuses (not all `PENDING_APPROVAL`): decision buttons disabled or action returns `409`; UI surfaces blocking entry IDs.
- Unauthorized: UI shows “Not authorized” and hides action buttons.

---

# 6. Functional Behavior

## Triggers
- Screen load (initial): load manager-scoped employees and available time periods (or load on demand when selection changes).
- Selection change: fetch time entries and approval history for `{employeeId,timePeriodId}`.
- Approve action: submit approve request.
- Reject action: submit reject request with required metadata.

## UI actions
- **Select Employee** (picker limited to authorized scope)
- **Select Pay Period** (picker shows status)
- **Approve Period** (button + confirm)
- **Reject Period** (button opens modal)
- **Refresh** (re-fetch current selection)

## State changes (frontend)
- Local view state: `loading`, `loaded`, `error`
- `canDecide` computed based on loaded data (period status + entries all pending) but backend remains source of truth.

## Service interactions
- `GET` load employees/time periods
- `GET` load time entries for employee/period
- `GET` load period decision history (TimePeriodApproval list)
- `POST` approve period (idempotent)
- `POST` reject period (idempotent)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Reject requires:
  - `rejectionReasonCode` present
  - `rejectionNotes` non-empty after trim
  - UI must enforce required fields before submit; still handle backend `400`.
- Approve/Reject apply to **all** entries in period currently `PENDING_APPROVAL` (no partial selection UI).

## Enable/disable rules
- Disable Approve/Reject if:
  - No employee or no pay period selected
  - Entries not loaded
  - `TimePeriod.status < SUBMISSION_CLOSED` (show message: “Approval available after submission closes.”)
  - Any entry status != `PENDING_APPROVAL` (show message: “All entries must be Pending Approval to decide this period.”)
- Hide or disable decision controls if backend returns `403`.

## Visibility rules
- Decision history panel visible when a selection is made and history is available; show empty state if none.

## Error messaging expectations
- `400`: show field-level errors (reject dialog) and keep dialog open.
- `403`: show non-specific authorization message; do not reveal reporting relationship details.
- `404`: show “Employee or pay period not found.”
- `409`: show conflict banner; if response includes blocking `timeEntryIds`, list them and suggest refresh.

---

# 8. Data Requirements

## Entities involved (People domain)
- `TimeEntry`
- `TimePeriod`
- `TimePeriodApproval` (append-only history)

## Fields (type, required, defaults)

### TimeEntry (display)
- `id` (string/uuid, required, read-only)
- `employeeId` (string/uuid, required, read-only)
- `timePeriodId` (string/uuid, required, read-only)
- `status` (enum: `DRAFT|SUBMITTED|PENDING_APPROVAL|APPROVED`, required, read-only)
- `clockIn` (datetime, required, read-only)
- `clockOut` (datetime, optional, read-only)
- `jobCode` (string, optional, read-only)

### TimePeriod (selection + gating)
- `id` (string/uuid, required)
- `status` (enum: `OPEN|SUBMISSION_CLOSED|PAYROLL_CLOSED`, required)
- (Optional display) period start/end dates (types unknown → needs clarification if not provided)

### TimePeriodApproval (history list)
- `id` (string/uuid)
- `employeeId` (string/uuid)
- `timePeriodId` (string/uuid)
- `finalStatus` (enum: `APPROVED|REJECTED`)
- `approvingManagerId` (string/uuid)
- `rejectionReasonCode` (string, conditional)
- `rejectionNotes` (string, conditional)
- `processedAt` (datetime)
- `policyVersion` (string, optional)
- `requestId` (string, optional)

## Read-only vs editable by state/role
- In this manager approval UI, all TimeEntry fields are **read-only**.
- Reject dialog fields are **editable** only during reject submission.

## Derived/calculated fields (frontend)
- `entriesSummary.totalCount`
- `entriesSummary.pendingCount`
- `entriesSummary.nonPendingIds[]` (from loaded entries)
- `canApprove/canReject` computed

---

# 9. Service Contracts (Frontend Perspective)

> Moqui integration note: implement calls as Moqui services invoked by screen transitions/actions, or via REST endpoints proxied through Moqui as configured by this project. Exact mechanism requires clarification from repo conventions.

## Load/view calls
1. **Load manager employees**
   - Proposed: `GET /api/people/manager/employees` (or Moqui service `People.getDirectReports`)
   - Returns: list `{employeeId, displayName, employeeNumber?}`

2. **Load time periods**
   - Proposed: `GET /api/timekeeping/timePeriods?status>=OPEN` (or service `Timekeeping.listTimePeriods`)
   - Returns: list `{timePeriodId, status, startDate?, endDate?}`

3. **Load time entries for selection**
   - Proposed: `GET /api/timekeeping/timeEntries?employeeId=&timePeriodId=`
   - Returns: list of TimeEntry

4. **Load approval history**
   - Proposed: `GET /api/timekeeping/timePeriodApprovals?employeeId=&timePeriodId=`
   - Returns: list of TimePeriodApproval (append-only, newest first)

## Create/update calls (actions)
1. **Approve**
   - Proposed: `POST /api/timekeeping/timePeriods/{timePeriodId}/employees/{employeeId}/approve`
   - Body: `{ requestId? }` (idempotency optional)
   - Success `200 OK`: returns updated entries + new approval record OR a lightweight `{finalStatus, processedAt}` (needs clarification)

2. **Reject**
   - Proposed: `POST /api/timekeeping/timePeriods/{timePeriodId}/employees/{employeeId}/reject`
   - Body: `{ rejectionReasonCode, rejectionNotes, requestId? }`
   - Success `200 OK`: returns updated entries (now `DRAFT`) + new approval record OR lightweight response (needs clarification)

## Error handling expectations
- `400`: structured field errors (at least for `rejectionReasonCode`, `rejectionNotes`)
- `403`: forbidden without relationship leakage
- `404`: missing employee/timePeriod
- `409`: conflict includes `blockingTimeEntryIds[]` when mixed statuses or concurrency detected
- Idempotent retry: repeated approve/reject returns `200` with no duplicate side effects

---

# 10. State Model & Transitions

## Allowed states
### TimeEntry.status (line-level)
- `DRAFT`
- `SUBMITTED`
- `PENDING_APPROVAL`
- `APPROVED`

### TimePeriod.status (gating)
- `OPEN`
- `SUBMISSION_CLOSED`
- `PAYROLL_CLOSED`

### TimePeriodApproval.finalStatus (history)
- `APPROVED`
- `REJECTED`

## Role-based transitions
- **Manager**:
  - `PENDING_APPROVAL → APPROVED` (period-atomic)
  - Reject action records `REJECTED` in history and sets entries back to `DRAFT` (period-atomic)
- **Employee**: not in scope here (draft/submit transitions outside)

## UI behavior per state
- If any entries `APPROVED`: show read-only and disable decisions (since not all pending).
- If period status `OPEN`: show entries (if any) but disable decisions due to gating.
- If period status `PAYROLL_CLOSED`: decisions should be disabled (approval is already past; backend should enforce).

---

# 11. Alternate / Error Flows

## Validation failures
- Reject submit missing reason/notes:
  - UI blocks submit with inline required errors
  - If backend returns `400`, show returned field messages

## Concurrency conflicts
- Approve/reject returns `409` due to entry status change:
  - Show banner “Time entries changed. Please refresh.”
  - Offer **Refresh** action; after refresh recompute eligibility

## Unauthorized access
- If load endpoints return `403`:
  - Show authorization error state
  - Do not render employee list details beyond generic message

## Empty states
- No employees returned: show “No employees available for approval.”
- No time entries for selection: show empty table state and disable decisions.
- No history: show “No approval decisions recorded for this period.”

---

# 12. Acceptance Criteria

## Scenario 1: Manager views pending entries for an employee and period
**Given** I am authenticated as a Manager with `timekeeping:approve`  
**And** I select an employee I am authorized to manage  
**And** I select a time period  
**When** the approvals view loads  
**Then** I see all time entries for that `{employeeId,timePeriodId}`  
**And** I see each entry’s status  
**And** I see the period decision history (or an empty-state message)

## Scenario 2: Approve period succeeds (atomic)
**Given** all time entries for `{employeeId,timePeriodId}` are `PENDING_APPROVAL`  
**And** the selected `TimePeriod.status` is `SUBMISSION_CLOSED` (or later but not beyond policy)  
**When** I click “Approve Period” and confirm  
**Then** the UI calls the approve endpoint once  
**And** on success the UI refreshes and shows all entries as `APPROVED`  
**And** the decision history shows a new record with `finalStatus=APPROVED` and my actor identity (as returned/displayed)

## Scenario 3: Reject period requires reason (client-side)
**Given** I have selected an eligible employee and pay period  
**When** I click “Reject Period”  
**Then** I must provide `rejectionReasonCode` and non-empty `rejectionNotes` before I can submit  
**And** if I attempt to submit without them, I see field-level required messages

## Scenario 4: Reject period succeeds (atomic, returns entries to draft)
**Given** all time entries for `{employeeId,timePeriodId}` are `PENDING_APPROVAL`  
**And** the `TimePeriod.status` is `SUBMISSION_CLOSED` (or later but allowed)  
**When** I submit rejection with a reason code and notes  
**Then** the UI calls the reject endpoint  
**And** on success the UI refreshes and shows entries in `DRAFT`  
**And** the decision history shows a new record with `finalStatus=REJECTED` including the reason metadata

## Scenario 5: Mixed statuses prevent decision (conflict)
**Given** at least one time entry in the period is not `PENDING_APPROVAL`  
**When** I attempt to approve or reject  
**Then** the UI prevents the action OR the API returns `409 Conflict`  
**And** the UI displays a conflict message  
**And** if blocking entry IDs are provided, they are shown to the user

## Scenario 6: Unauthorized manager cannot decide
**Given** I am authenticated but not authorized to approve time for the selected employee  
**When** I attempt to load or decide on the period  
**Then** the API returns `403 Forbidden`  
**And** the UI shows a generic “Not authorized” message  
**And** does not expose reporting relationship details

---

# 13. Audit & Observability

## User-visible audit data
- Display in history list (from `TimePeriodApproval`):
  - `processedAt`
  - `finalStatus`
  - `approvingManagerId` rendered as name if provided by API (needs clarification)
  - For rejection: reason code + notes

## Status history
- Period-level append-only history shown newest-first.
- Entries table reflects current `TimeEntry.status` after actions.

## Traceability expectations
- Frontend includes a generated `requestId` for approve/reject (if supported) to aid idempotency and tracing.
- Correlation ID propagated via standard headers if project conventions exist (needs clarification from repo).

---

# 14. Non-Functional UI Requirements
- **Performance:** Initial load and selection change should feel responsive; show loading states for each fetch.
- **Accessibility:** Reject dialog and tables must be keyboard navigable; validation errors announced to screen readers.
- **Responsiveness:** Works on tablet; table supports horizontal scroll if needed.
- **i18n/timezone:** Display datetimes in the user/location timezone as configured by the app (needs clarification on timezone source and formatting conventions).

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging for no employees, no entries, no history; qualifies as UI ergonomics and does not affect domain policy. Impacted sections: UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-LOADING-STATES: Use standard loading indicators and disable action buttons during requests; qualifies as UI ergonomics. Impacted sections: Functional Behavior, Error Flows.
- SD-ERR-MAP-HTTP: Map HTTP 400/403/404/409 to standardized banners and field errors; qualifies as standard error-handling mapping. Impacted sections: Business Rules, Service Contracts, Error Flows.

---

# 16. Open Questions
1. **Backend contract specifics:** What are the exact endpoints (or Moqui services) for:
   - listing manager-authorized employees,
   - listing time periods,
   - fetching time entries,
   - fetching approval history,
   - approve/reject actions?
2. **Response payloads:** Do approve/reject endpoints return updated `TimeEntry` rows and `TimePeriodApproval` record(s), or should the frontend re-fetch after a `200`?
3. **Rejection reason codes:** Is `rejectionReasonCode` a fixed enum/list endpoint-provided, or free text? If fixed, what is the source endpoint and display labels?
4. **Identity display:** Should the UI display `approvingManagerId` as a human name? If so, does the history endpoint include manager display info, or is there a people lookup service?
5. **Moqui frontend integration pattern:** In this repo, should Vue/Quasar call backend REST directly, or via Moqui screen actions/transitions that invoke services? Provide the preferred convention for this module.
6. **Time period display fields:** Are pay period start/end dates available and required in the UI? If yes, confirm field names/types and timezone rules for rendering.

---

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Timekeeping: Manager Approves/Rejects Time Entries — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/147

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Timekeeping: Manager Approves/Rejects Time Entries  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/147  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Manager Approves/Rejects Time Entries

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative  
As a **Manager**, I want **to approve or reject time entries** so that **time is locked and ready for export and reconciliation**.

## Details  
- Reject requires reason.  
- Approved becomes read-only except controlled adjustment.

## Acceptance Criteria  
- Approve/reject per person per period.  
- Reason required on rejection.  
- Audit trail includes actor and changes.

## Integration Points (workexec/shopmgr)  
- Optional: workexec uses approved job time for labor posting.

## Data / Entities  
- TimeEntryApproval

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: People Management

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