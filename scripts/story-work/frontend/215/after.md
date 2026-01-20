## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Completion: Complete Workorder and Record Audit

### Primary Persona
Service Advisor / Shop Manager

### Business Value
Enables an explicit, auditable “Complete Workorder” action that finalizes execution, locks billable scope from uncontrolled edits, and triggers downstream processing (audit/accounting event emission) without creating AR/revenue.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor (or Shop Manager)  
**I want** to complete a work order via an explicit, permission-controlled action  
**So that** the execution is finalized with an audit trail, completion metadata is captured, further scope edits are locked, and downstream systems can react to a single, idempotent `WorkCompleted` event.

### In-scope
- UI action to complete a work order (from eligible states only)
- Capture completion notes and optional inspection outcome(s)
- Call Moqui backend transition/service to complete
- Display success/failure results and updated status
- Enforce “locked” UI behavior after completion (disable/hide execution edits)
- View completion audit metadata (who/when) and status history (read-only)

### Out-of-scope
- Defining/implementing the *precondition validation rules* themselves (explicitly referenced as “covered by validation story”)
- Reopen workflow (only respect that it exists; no UI authoring unless already present)
- Accounting/WIP ledger postings logic (frontend just triggers completion and surfaces backend result)
- Notification delivery to customer

---

## 3. Actors & Stakeholders

- **Primary Actor:** Service Advisor / Shop Manager (initiates completion, records notes)
- **Secondary Actor:** Mechanic (impacted by lockout; cannot edit scope after completion)
- **System Stakeholders:**
  - Audit subsystem (consumes/records audit events and transition history)
  - Accounting integration (consumes `WorkCompleted`; may finalize WIP if enabled)
  - Billing domain (acts later; completion must not create invoice/AR in this story)

---

## 4. Preconditions & Dependencies

### Preconditions (must be true before enabling/allowing completion)
1. Work order exists and is loadable by current user.
2. User is authenticated.
3. User has permission to complete a work order (exact permission key to be confirmed; see Open Questions).
4. Work order is in an eligible state for transition to `COMPLETED` per `WORKORDER_STATE_MACHINE.md` (see State Model).
5. “Completion preconditions are satisfied” (specific rules are external to this story; UI must handle backend failure response and present reasons).

### Dependencies
- Backend endpoint/service to complete/transition work order to `COMPLETED` (contract must be confirmed; see Open Questions).
- Backend exposes work order details including current status and completion metadata (`completedAt`, `completedBy`, etc.).
- Backend exposes transition history and/or audit events (preferred) or includes enough in work order view to show completion audit fields.
- Backend enforces idempotency and emits `WorkCompleted` event exactly once; frontend must not attempt to synthesize events.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Work Order detail screen: action button **Complete Workorder**
- Optional: from Work Order list row actions (if list exists in frontend; do not add if not already present)

### Screens to create/modify (Moqui)
1. **Modify existing** `workorder/WorkOrderDetail` screen (name illustrative; align to repo conventions)
   - Add an actions section with `transition`/`link` to completion dialog screen.
   - Add a read-only “Completion” section showing `completedAt`, `completedBy`, `completionNotes`, `inspectionOutcome` (if present).
   - Ensure edit controls for execution scope are disabled/hidden when status is `COMPLETED`.

2. **Create** a modal/dialog screen for completion:
   - `workorder/CompleteWorkOrderDialog` (modal)
   - Contains form fields for completion notes and optional inspection outcome(s)
   - Confirm action calls backend service; cancel returns to detail without change

### Navigation context
- Completion dialog launched from within work order context (workOrderId in parameters).
- After successful completion:
  - return to Work Order detail (same workOrderId)
  - refresh the work order data
  - show a toast/banner success message with new status

### User workflows
**Happy path**
1. User opens Work Order detail for a work order in an eligible state.
2. User clicks **Complete Workorder**.
3. Dialog shows completion form; user enters optional notes and optional inspection outcomes.
4. User confirms completion.
5. UI calls backend completion service.
6. On success: status updates to `COMPLETED`, completion metadata displayed, execution edits locked.

**Alternate paths**
- User cancels dialog → no changes.
- Backend rejects due to failed preconditions → UI shows blocking errors and keeps work order uncompleted.
- Backend rejects due to invalid state (already completed / cancelled / not eligible) → UI shows error and refreshes.

---

## 6. Functional Behavior

### Triggers
- User action: click **Complete Workorder** then **Confirm Complete**

### UI actions
- **Complete Workorder button visibility/enabledness**
  - Visible when the user can view the work order.
  - Enabled only when:
    - status is eligible for completion (see State Model)
    - user has completion permission
  - Disabled state must include tooltip/help text: “Cannot complete: <reason>” when reason is known (e.g., status not eligible). For validation preconditions, reason comes from backend.

- **Completion dialog form**
  - Fields:
    - Completion Notes (optional, multiline)
    - Inspection Outcome (optional; exact structure TBD)
  - Confirm button:
    - disabled while submitting
    - shows spinner/progress
  - Cancel button closes dialog

### State changes (frontend-observed)
- Before completion: work order in in-progress-related state (or other eligible state per backend)
- After successful completion:
  - work order status becomes `COMPLETED`
  - completion fields populated (completedAt, completedBy)
  - UI locks scope edits (see Business Rules)

### Service interactions
- Load work order details on entry and after completion.
- Submit completion request via Moqui service call (REST or Moqui transition service).
- On success: refresh detail and optionally fetch transition/audit history for display.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Completion must be explicit:
  - require user confirmation (dialog confirm) before calling backend
- Completion preconditions:
  - UI does not attempt to evaluate domain rules beyond status/permission gating
  - UI must render backend validation failures clearly and non-destructively
- Idempotency:
  - UI must prevent double-submit:
    - disable confirm button on submit
    - if user retries due to network failure, UI must handle “already completed” response gracefully (treat as success after refresh, if backend indicates completed)

### Enable/disable rules
- If status is `COMPLETED`:
  - Hide or disable “Complete Workorder” action
  - Disable/hide all execution-scope editing actions (add/edit/remove parts/labor/fees) in the Work Order UI surface controlled by this frontend
- If status is `CANCELLED`:
  - Completion must not be possible (disabled/hidden)
- If status is not eligible:
  - Completion action disabled with explanation (status-based)

### Visibility rules
- Completion section visible when status is `COMPLETED` OR when completion fields exist.
- Completion notes visible only if present; otherwise show “None”.

### Error messaging expectations
- Invalid state: “Work order cannot be completed from status <status>.”
- Preconditions fail: show list of failed checks from backend (if provided), else generic “Completion requirements not met.”
- Unauthorized: “You don’t have permission to complete work orders.”
- Concurrency/409: “Work order changed since you opened it. Refresh and try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `WorkOrder` (authoritative for status and completion metadata)
- `WorkOrderStateTransition` and/or `AuditEvent` (for who/when/why; depends on available endpoint)
- `InspectionRecord` (optional outcomes; structure unclear)

### Fields
**WorkOrder**
- `workOrderId` (string/number; must match backend)
- `status` (enum/string; includes `COMPLETED`)
- `completedAt` (datetime, UTC)
- `completedBy` (userId)
- `completionNotes` (string/text, optional)
- `inspectionOutcome` (TBD; optional)
- Optional but useful:
  - `version` / `lastUpdatedStamp` for optimistic concurrency (if provided)

**Audit / Transition display (read-only)**
- `fromStatus`, `toStatus`, `transitionedAt`, `transitionedBy`, `reason`

### Read-only vs editable
- Before completion:
  - CompletionNotes/InspectionOutcome editable only within completion dialog at time of completion
- After completion:
  - completion fields are read-only
  - execution edits are locked (read-only)

### Derived/calculated fields
- Display “Completed by <userDisplayName> at <localized time>” derived from `completedBy` + user lookup (if available) and `completedAt`.
- If user display names are not available, display userId.

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking note:** backend endpoints for completion in workexec state machine docs show `/api/workorders/{id}/start` and transition history endpoints, but do not explicitly define a `/complete` endpoint. This must be confirmed.

### Load/view calls
- `GET /api/workorders/{id}` (or `/api/work-orders/{id}`)  
  Used to load current `status` and completion fields.

- Optional (for audit UI):
  - `GET /api/workorders/{id}/transitions`
  - `GET /api/workorders/{id}/snapshots`

### Create/update calls
- None directly for completion (completion is a transition/command)

### Submit/transition calls
One of the following must exist (confirm which):
- `POST /api/workorders/{id}/complete` with body: `{ userId, reason?, completionNotes?, inspectionOutcome? }`
- OR generic transition endpoint: `POST /api/workorders/{id}/transition` with `{ toStatus: "COMPLETED", userId, reason, ... }`
- OR Moqui service such as `workexec.WorkOrderStateMachine.completeWorkOrder`

**Request fields expected (frontend needs)**
- `userId` (can come from session; avoid passing if backend derives; confirm)
- `reason` (optional; can default “Work order completed”)
- `completionNotes` (optional)
- `inspectionOutcome` (optional; TBD)

**Response expectations**
- HTTP 200 with updated work order status and completion fields, OR
- HTTP 201 with transition record, OR
- HTTP 409 if already completed / concurrency conflict (exact mapping TBD)

### Error handling expectations
- 400: validation failures (missing required fields, preconditions fail) → show inline + banner
- 403: permission denied → banner, disable action
- 404: work order not found → navigate back with error
- 409: conflict/invalid transition due to state change → prompt refresh
- 5xx: system error → retry guidance

---

## 10. State Model & Transitions

### Allowed states (authoritative from `WORKORDER_STATE_MACHINE.md`)
- `DRAFT`
- `APPROVED`
- `ASSIGNED`
- `WORK_IN_PROGRESS`
- `AWAITING_PARTS`
- `AWAITING_APPROVAL`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

### Allowed transitions (relevant subset)
- Completion is transition to `COMPLETED` from one of:
  - `WORK_IN_PROGRESS`
  - `AWAITING_PARTS`
  - `AWAITING_APPROVAL`
  - `READY_FOR_PICKUP`
  - (Possibly other states per backend FSM implementation; must not guess beyond documented transitions—see Open Questions)

### Role-based transitions
- Service Advisor / Shop Manager: allowed to complete (subject to permission)
- Others: typically not allowed

### UI behavior per state
- `WORK_IN_PROGRESS` / `AWAITING_PARTS` / `AWAITING_APPROVAL` / `READY_FOR_PICKUP`:
  - show “Complete Workorder” action if permission allows
- `COMPLETED`:
  - show completion metadata
  - lock execution edits
- `CANCELLED`:
  - no completion action; read-only
- `DRAFT` / `APPROVED` / `ASSIGNED`:
  - no completion action (must be started first); read-only or limited edits depending on existing screens (do not expand scope here)

---

## 11. Alternate / Error Flows

### Validation failures (preconditions not satisfied)
- Backend returns 400 with structured list of failed conditions (if available)
- UI displays:
  - banner “Cannot complete work order”
  - list of reasons
  - keeps dialog open for user to review notes/outcome and cancel

### Concurrency conflicts
- If backend returns 409 due to stale status/version:
  - UI shows “Work order updated. Refreshing…”
  - refresh work order data
  - re-evaluate action availability

### Unauthorized access
- If backend returns 403:
  - show permission error
  - disable completion action until next reload (or indefinitely)

### Empty states
- If audit/transition history endpoint returns empty:
  - show “No transitions recorded” (but completion fields still shown if completed)

---

## 12. Acceptance Criteria

### Scenario 1: Complete work order successfully from eligible state
**Given** a work order exists in status `READY_FOR_PICKUP` (or another completion-eligible status)  
**And** the logged-in user has permission to complete work orders  
**And** completion preconditions are satisfied by the backend  
**When** the user clicks “Complete Workorder”, enters optional completion notes, and confirms  
**Then** the frontend sends a completion transition request for that work order  
**And** the work order status is displayed as `COMPLETED` after refresh  
**And** `completedAt` and `completedBy` are shown in the UI  
**And** execution edit actions (add/edit/remove parts/labor/fees) are disabled/hidden.

### Scenario 2: Attempt completion when backend preconditions fail
**Given** a work order is in a completion-eligible status  
**And** the logged-in user has permission to complete work orders  
**But** backend completion preconditions are not satisfied  
**When** the user confirms completion  
**Then** the backend responds with a validation failure  
**And** the UI shows a blocking error message (including specific failed checks if provided)  
**And** the work order remains not completed (status unchanged after refresh)  
**And** no success toast is shown.

### Scenario 3: Attempt completion for already completed work order (idempotent UX)
**Given** a work order is already in status `COMPLETED`  
**When** the user navigates to the work order detail  
**Then** the “Complete Workorder” action is not available  
**And** completion metadata is displayed read-only.

### Scenario 4: Double-submit protection
**Given** a completion dialog is open for a completion-eligible work order  
**When** the user clicks confirm completion  
**Then** the confirm button is disabled while the request is in flight  
**And** repeated clicks do not send duplicate completion requests.

### Scenario 5: Unauthorized completion attempt
**Given** a work order is in a completion-eligible status  
**And** the logged-in user lacks completion permission  
**When** the user attempts to complete the work order (via direct URL or UI if visible)  
**Then** the backend responds with 403 (or equivalent)  
**And** the UI shows an authorization error  
**And** the work order remains unchanged.

### Scenario 6: Conflict due to concurrent update
**Given** a work order is open in the UI  
**And** another user changes its status such that completion is no longer valid  
**When** the first user attempts to complete it  
**Then** the backend responds with 409 (or invalid transition)  
**And** the UI refreshes the work order and shows an actionable conflict message.

---

## 13. Audit & Observability

### User-visible audit data
- On completed work order detail, show:
  - Completed timestamp (`completedAt`, localized for display; stored UTC)
  - Completing user (`completedBy`)
  - Completion notes
  - Optional inspection outcome summary (if stored)

### Status history
- Provide a read-only list of transitions if endpoint exists:
  - includes the transition to `COMPLETED` with who/when/reason
- If only completion fields exist, minimum audit is the completion section.

### Traceability expectations
- All completion actions must be correlated:
  - frontend should include correlation/request ID header if standard in project (safe default only if existing)
  - log client-side errors with workOrderId context (non-PII)

---

## 14. Non-Functional UI Requirements

- **Performance:** completion action should respond within 2 seconds under normal conditions; show loading state if longer.
- **Accessibility:** dialog is keyboard navigable; focus is trapped within modal; buttons have accessible labels.
- **Responsiveness:** works on tablet widths (service desk usage).
- **i18n/timezone:** display `completedAt` in user’s locale/timezone; store/send timestamps as provided by backend (UTC).
- **Security:** do not expose internal accounting payloads; do not log completion notes in client console logs.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Show “None” for missing optional completion notes/outcomes; safe as it is purely presentational and does not change domain behavior. (Impacted: UX Summary, Data Requirements)
- SD-UX-SUBMIT-LOCK: Disable confirm button during submit to prevent duplicate requests; safe UI ergonomics. (Impacted: Functional Behavior, Acceptance Criteria, Error Flows)
- SD-ERR-HTTP-MAP: Standard mapping of 400/403/404/409/5xx to user messages without guessing business policy; safe because it’s generic transport-level handling. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Completion endpoint contract:** What is the exact backend API/service for completing a work order in this system (path, method, request/response)? Is it `POST /api/workorders/{id}/complete`, a generic transition endpoint, or a Moqui service call?
2. **Eligible “from” statuses for completion:** Which work order statuses are allowed to transition to `COMPLETED` (authoritative list)? The FSM doc lists overall states but not explicit completion-from states.
3. **Permission key(s):** What permission/role gate should the frontend use (e.g., `WORKORDER_COMPLETE`)? Is it derived from Moqui security groups or via backend authorization only?
4. **Inspection outcome structure:** What is `inspectionOutcome` exactly (enum, free text, structured checklist)? Is it stored on `InspectionRecord` and linked to WorkOrder, or embedded on WorkOrder?
5. **Lock semantics in UI:** Which specific “execution edits” must be locked in the frontend after completion (parts, labor, fees, notes, assignments)? Are any fields still editable post-completion (e.g., internal notes)?
6. **Audit data source:** Should the frontend display audit via `WorkOrderStateTransition` endpoints (`/transitions`) or via a generic `AuditEvent` endpoint? Which exists in the Moqui backend?
7. **Idempotency UX:** If the completion request is retried and backend returns “already completed”, should UI treat that as success (refresh + show completed) or as an error message?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Completion: Complete Workorder and Record Audit  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/215  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Complete Workorder and Record Audit

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300007/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor / Shop Manager

## Trigger
Completion preconditions are satisfied.

## Main Flow
1. User selects 'Complete Workorder'.
2. System transitions workorder to Completed state.
3. System records completion timestamp and completing user.
4. System stores completion notes and optional inspection outcomes.
5. System locks execution edits except via controlled reopen workflow.

## Alternate / Error Flows
- Completion attempted with failing preconditions → block (covered by validation story).

## Business Rules
- Completion transition must be explicit and auditable.
- Completion locks billable scope unless reopened with permissions.

## Data Requirements
- Entities: Workorder, AuditEvent, InspectionRecord
- Fields: status, completedAt, completedBy, completionNotes, inspectionOutcome

## Acceptance Criteria
- [ ] Workorder transitions to Completed only when preconditions pass.
- [ ] Completion is auditable (who/when).
- [ ] Workorder is locked against uncontrolled edits.
- [ ] WorkCompleted event is emitted once per completion
- [ ] Event includes final billable scope and totals snapshot
- [ ] WIP accounting (if enabled) is finalized correctly
- [ ] Completion does not create AR or revenue
- [ ] Repeated completion attempts do not duplicate events

## Integrations

### Accounting
- Emits Event: WorkCompleted
- Event Type: Non-posting or Posting (WIP → Finished, if enabled)
- Source Domain: workexec
- Source Entity: Workorder
- Trigger: Workorder transitioned to Completed state
- Idempotency Key: workorderId + completionVersion


## Notes for Agents
Treat completion as a state transition with strong validations and audit.


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