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
[FRONTEND] [STORY] Execution: Start Work Order and Track Status (WIP Reasons + History)

### Primary Persona
Technician (primary), Shop Manager (secondary)

### Business Value
Provide explicit, auditable work order start and in-progress status reason tracking so the shop can see real-time execution state, prevent starting work when blocked by pending approvals, and retain immutable history for operational analytics.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Technician or Shop Manager  
- **I want** to explicitly start a work order and update its in-progress reason (waiting parts/approval/etc.)  
- **So that** the system accurately reflects execution progress, enforces start eligibility rules, and preserves an immutable status/reason history.

### In Scope
- Work order detail UI shows current status and in-progress reason (when applicable).
- “Start Work” action that calls backend start endpoint and updates UI state.
- “Update In-Progress Reason” action available only while work order is in `WORK_IN_PROGRESS`.
- Read-only, append-only history view of status/reason changes (transition audit list).
- Frontend handling for blocked start (non-startable state, pending blocking change request).
- Explicit mapping of API/service calls, error handling, and Moqui screen transitions.

### Out of Scope
- Auto-start on first labor entry (explicitly out of scope; follow-on story).
- Creating/approving/declining change requests (only used as a blocking condition display).
- Any new workflow states beyond those enumerated in the authoritative work order FSM.
- Permission model design (frontend respects API 401/403; does not invent RBAC rules).

---

## 3. Actors & Stakeholders
- **Technician:** starts work, updates in-progress reason during execution.
- **Shop Manager:** may start work and update reason; monitors progress.
- **Service Advisor:** indirect stakeholder (change requests may block start).
- **Audit/Operations:** relies on immutable history for compliance and analytics.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- Work order exists and is viewable by the user.
- Backend endpoints for:
  - loading work order details,
  - starting a work order,
  - updating in-progress reason,
  - retrieving transition/history events,
  are available and accessible.

### Dependencies
- Backend workexec state machine implementation and contracts (status + reason rules, blocking change request rule).
- A backend endpoint (or embedded field in work order response) that indicates whether the work order has blocking change requests, or a deterministic way to query them.

> **Note:** Provided references conflict on start-eligible statuses (`SCHEDULED` appears in one backend story excerpt but the authoritative FSM doc says start-eligible is `APPROVED`, `ASSIGNED`). This story cannot be finalized without confirming the canonical contract for frontend gating and error copy.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From “My Work” / “Assigned Work Orders” list: open a work order detail.
- From search results / work order lookup: open work order detail.

### Screens to create/modify
1. **Modify** `WorkOrderDetail` screen (existing or new under a workexec menu tree):
   - Add action controls: `Start Work` button (conditional)
   - Add reason selector (conditional)
   - Add status/reason history section or link
2. **Create/Modify** `WorkOrderStatusHistory` embedded section or separate screen:
   - List of immutable status/reason change events (read-only)

*(Exact screen paths/components depend on repo conventions; implement within Moqui screen XML and Vue/Quasar components as used in durion-moqui-frontend.)*

### Navigation context
- Work order detail remains the canonical place for execution actions.
- After successful start or reason change, remain on same screen and refresh data.

### User workflows
#### Happy path: Start work
1. User opens work order detail.
2. UI shows `Start Work` enabled if startable and not blocked.
3. User clicks `Start Work`.
4. UI prompts for optional “reason” text (if backend accepts) or proceeds directly.
5. Backend returns updated state; UI refreshes:
   - status becomes `WORK_IN_PROGRESS`
   - reason defaults to `ACTIVE_WORK`
   - started-at timestamp displays (read-only)
6. Status history shows a new entry for the start transition.

#### Happy path: Update in-progress reason
1. While in `WORK_IN_PROGRESS`, UI shows reason selector.
2. User selects reason (e.g., `WAITING_PARTS`) and confirms.
3. Backend persists reason change; UI refreshes and history shows a reason-change entry.

#### Alternate path: Start blocked
- If backend rejects start due to:
  - non-startable status, or
  - blocking change request,
  UI shows the backend message and keeps state unchanged.

---

## 6. Functional Behavior

### Triggers
- User clicks `Start Work`.
- User changes `In-Progress Reason` while in `WORK_IN_PROGRESS`.

### UI actions
- **Start Work**
  - Visible when work order status is start-eligible (see State Model section).
  - Disabled with inline explanation if blocked by change request (if known from data).
  - On click: call start service, then reload work order + history.
- **Update In-Progress Reason**
  - Visible only when status is `WORK_IN_PROGRESS`.
  - Allowed values constrained to the backend enum list.
  - On submit: call reason update service, then reload work order + history.
- **View history**
  - Always available (read-only list).
  - Supports empty state (“No status history yet”).

### State changes (frontend-observable)
- On successful start: status transitions to `WORK_IN_PROGRESS`, reason becomes `ACTIVE_WORK`, started-at populated.
- On reason update: status remains `WORK_IN_PROGRESS`, reason changes to selected value.
- History list grows append-only (frontend treats it as immutable).

### Service interactions
- Load work order details (GET).
- Start work order (POST).
- Update in-progress reason (POST/PUT; TBD exact endpoint).
- Load transitions/history (GET).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Start Work** action:
  - Must not be offered/enabled when status is not start-eligible (frontend gating), but backend remains the source of truth.
  - Must be blocked when a pending blocking change request exists (frontend displays block if backend provides signal; otherwise rely on backend rejection message).
- **Update Reason** action:
  - Only permitted when status is `WORK_IN_PROGRESS`.
  - Only allowed reason values:
    - `ACTIVE_WORK`
    - `WAITING_PARTS`
    - `WAITING_CUSTOMER_APPROVAL`
    - `WAITING_DIAGNOSIS`
    - `WAITING_EXTERNAL_SERVICE`
    - `PAUSED_BY_SHOP`
  - Invalid value should not be selectable; if backend rejects anyway, show validation error.

### Enable/disable rules
- Start button:
  - Enabled if `status ∈ StartEligibleStatuses` AND `hasBlockingChangeRequest != true`
  - Disabled otherwise, with inline reason:
    - “Not startable in current status: <status>”
    - or “Blocked: pending approval required”
- Reason selector:
  - Enabled only when `status == WORK_IN_PROGRESS`.

### Visibility rules
- Show `inProgressReason` field only when `status == WORK_IN_PROGRESS` (or show read-only “—” otherwise).
- Show `workStartedAt` when present.

### Error messaging expectations
- Prefer backend-provided message strings for business-rule rejections.
- Standard mappings:
  - 400: validation error → show field-level or banner message
  - 401: not authenticated → redirect/login flow (project standard)
  - 403: forbidden → “You do not have permission to perform this action.”
  - 404: not found → “Work order not found.”
  - 409: conflict/concurrency → “Work order was updated by another user. Refresh and try again.”

---

## 8. Data Requirements

### Entities involved (frontend consumption)
- `WorkOrder`
- `WorkOrderStateTransition` and/or `WorkOrderStatusHistory` (naming differs between provided backend references; must match actual API)
- `ChangeRequest` (read-only signal for blocking condition; details optional)

### Fields
#### WorkOrder (read)
- `id` (string/uuid; required)
- `status` (string enum; required)
- `inProgressReason` (string enum; nullable; required when status is `WORK_IN_PROGRESS`)
- `workStartedAt` (ISO-8601 UTC timestamp; nullable)
- (optional but useful) `assignedTo`, `customer`, `vehicle` summary fields if already present on the screen

#### History record (read)
- `id` (string/uuid; required)
- `workOrderId` (string/uuid; required)
- `fromStatus` (string; nullable)
- `toStatus` (string; required)
- `changeType` (enum: `STATUS_CHANGE` | `REASON_CHANGE`; required)
- `reason` (string; optional free text)
- `changedByUserId` (string/uuid; required)
- `changedAt` (ISO-8601 UTC timestamp; required)
- (optional) `metadata` (JSON; optional)

#### Blocking change request signal (read)
One of:
- `hasBlockingChangeRequest` (boolean), OR
- list endpoint that returns change requests with statuses/types, OR
- backend start endpoint returns a specific error code when blocked.

### Read-only vs editable
- Editable:
  - Start action (no direct field edit)
  - `inProgressReason` (only while WIP)
- Read-only:
  - `status`
  - `workStartedAt`
  - All history records

### Derived/calculated fields (frontend)
- `isStartEnabled` = computed based on status + blocking flag (if available)
- Display-friendly labels for statuses and reason enums (i18n-ready keys)

---

## 9. Service Contracts (Frontend Perspective)

> **Important:** Endpoint paths differ between provided references:
> - FSM doc uses: `POST /api/workorders/{id}/start`, `GET /api/workorders/{id}/transitions`
> - backend story excerpt uses: `POST /workexec/v1/workorders/{id}:start`
>
> Frontend cannot be buildable until canonical routes are confirmed.

### Load/view calls
- `GET <TBD> /api/workorders/{id}`  
  **Response:** WorkOrder DTO including status/reason/startedAt (and optionally blocking flag)
- `GET <TBD> /api/workorders/{id}/transitions` (or `/history`)  
  **Response:** list of transition/history records sorted by `changedAt` ascending or descending (must be specified)

### Create/update calls
- **Start work order**
  - `POST <TBD> /api/workorders/{id}/start` or `POST /workexec/v1/workorders/{id}:start`
  - Request body (FSM doc): `{ "userId": <id>, "reason": <string?> }`
  - **Frontend requirement:** obtain `userId` from session context if backend allows; do not prompt user to type userId.
- **Update in-progress reason**
  - `POST/PUT <TBD>` (not specified in provided references)
  - Request body should minimally include: `{ "inProgressReason": "WAITING_PARTS", "reason": "<optional note>" }` (TBD)

### Submit/transition calls
- Start and reason change are both transitions from frontend’s perspective.

### Error handling expectations
- Must display backend business-rule messages for:
  - non-startable state
  - blocked by pending change request
- Must treat 409 as refresh-needed concurrency.

---

## 10. State Model & Transitions

### Allowed states (authoritative FSM doc)
- `DRAFT`
- `APPROVED`
- `ASSIGNED`
- `WORK_IN_PROGRESS`
- `AWAITING_PARTS`
- `AWAITING_APPROVAL`
- `READY_FOR_PICKUP`
- `COMPLETED`
- `CANCELLED`

### Start transition
- Allowed “start eligible” statuses per FSM doc: **`APPROVED`, `ASSIGNED`**
- Start transitions to: `WORK_IN_PROGRESS`

> **Conflict:** backend story excerpt also mentions `SCHEDULED` as startable, but FSM doc does not include `SCHEDULED`. Must clarify which is correct for frontend gating and tests.

### In-progress reason (sub-status)
- Only valid while `status == WORK_IN_PROGRESS`
- Allowed enum values listed in section 7.

### Role-based transitions
- Not specified in provided inputs. Frontend must rely on backend authorization (403) and avoid inventing role gating.

### UI behavior per state
- `APPROVED` / `ASSIGNED`: show Start button (enabled unless blocked).
- `WORK_IN_PROGRESS`: hide Start; show reason selector; show startedAt; show history.
- Other states: no Start; reason selector hidden/disabled; history still viewable.

---

## 11. Alternate / Error Flows

### Validation failures
- Setting reason when not in `WORK_IN_PROGRESS` → backend rejects (400/409). UI shows message and refreshes work order.
- Invalid reason enum → backend rejects (400). UI should not allow selection.

### Concurrency conflicts
- Two users attempt start or reason change:
  - If backend returns 409, UI shows conflict message and reloads work order + history.

### Unauthorized access
- 401 → route to login / session renewal (per app convention).
- 403 → show permission error; keep screen read-only.

### Empty states
- No history records returned:
  - Show “No status history recorded yet.”

---

## 12. Acceptance Criteria

### Scenario 1: Start work order from APPROVED
**Given** a work order exists with `status = APPROVED`  
**And** there is no blocking pending change request for the work order  
**And** I am an authenticated Technician  
**When** I open the work order detail screen  
**Then** I see a `Start Work` action enabled  

**When** I click `Start Work`  
**Then** the frontend calls the start endpoint for that work order  
**And** on success the UI refreshes and shows `status = WORK_IN_PROGRESS`  
**And** `inProgressReason = ACTIVE_WORK` is displayed  
**And** `workStartedAt` is displayed as a UTC timestamp  
**And** the history list includes a new `STATUS_CHANGE` entry from `APPROVED` to `WORK_IN_PROGRESS`.

### Scenario 2: Start work order from ASSIGNED
**Given** a work order exists with `status = ASSIGNED`  
**And** there is no blocking pending change request  
**When** I click `Start Work`  
**Then** the work order transitions to `WORK_IN_PROGRESS` and the UI reflects the same outcomes as Scenario 1.

### Scenario 3: Reject start from non-startable state
**Given** a work order exists with `status = COMPLETED`  
**When** I open the work order detail screen  
**Then** I do not see `Start Work`, or it is disabled with an explanation  

**When** I attempt to start via any available UI affordance  
**Then** the backend rejection is shown (message includes current status)  
**And** the UI remains showing `status = COMPLETED`  
**And** no new history entry appears after refresh.

### Scenario 4: Block start due to pending change request
**Given** a work order exists with `status` in a start-eligible state  
**And** a blocking change request exists (pending approval / awaiting advisor review per backend contract)  
**When** I click `Start Work`  
**Then** the backend rejects the request  
**And** the UI shows: “Cannot start work. A pending change request requires approval before proceeding.” (or backend-provided equivalent)  
**And** the work order status remains unchanged after refresh.

### Scenario 5: Change in-progress reason
**Given** a work order exists with `status = WORK_IN_PROGRESS` and `inProgressReason = ACTIVE_WORK`  
**When** I set the in-progress reason to `WAITING_PARTS` and confirm  
**Then** the frontend calls the reason update endpoint  
**And** the UI refreshes and shows `inProgressReason = WAITING_PARTS`  
**And** the canonical `status` remains `WORK_IN_PROGRESS`  
**And** the history list includes a new `REASON_CHANGE` entry.

### Scenario 6: Prevent reason change when not in WORK_IN_PROGRESS
**Given** a work order exists with `status = APPROVED`  
**When** I view the work order detail screen  
**Then** I cannot edit the in-progress reason  

**When** a reason update is attempted (e.g., stale UI)  
**Then** the UI displays the backend validation error  
**And** after refresh the work order remains unchanged.

### Scenario 7: Concurrency conflict handling
**Given** I have the work order detail open  
**And** another user starts the work order or changes its reason before I submit my action  
**When** I click `Start Work` or update the reason  
**And** the backend returns `409 Conflict`  
**Then** the UI displays a conflict message prompting refresh  
**And** the UI reloads the latest work order state and history.

---

## 13. Audit & Observability

### User-visible audit data
- Show a history list including:
  - from/to status (when present)
  - change type (status vs reason change)
  - changed at (UTC displayed in user locale formatting but sourced from UTC)
  - changed by (display userId; optionally resolve to name if already supported)
  - optional note/reason text

### Status history
- Must be immutable (read-only UI; no edit/delete controls).
- Default sort: most recent first (unless repo conventions dictate otherwise).

### Traceability expectations
- Every start and reason change performed through UI must result in a corresponding history entry visible after refresh.

---

## 14. Non-Functional UI Requirements

- **Performance:** Work order detail load (including history) should render within 2 seconds on typical shop network; history can be paginated if large.
- **Accessibility:** Actions must be keyboard accessible; buttons have discernible labels; validation errors announced to screen readers (Quasar standard patterns).
- **Responsiveness:** Usable on tablet form factor (common in shop floor).
- **i18n/timezone:** Display timestamps in local time with explicit indication they originate from UTC; enum labels use i18n keys where available.

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE**: Provide explicit empty state text for history list when no records exist; safe because it’s purely UI ergonomics. (Impacted: UX Summary, Error Flows, Acceptance Criteria)
- **SD-UX-PAGINATION**: Paginate history list when record count is large (page size TBD by existing conventions); safe as it doesn’t change domain behavior. (Impacted: UX Summary, Non-Functional)
- **SD-ERR-HTTP-MAP**: Standard HTTP error mapping (401/403/404/409/5xx) to user messages; safe because it follows implied backend contract patterns without inventing business rules. (Impacted: Business Rules, Error Flows, Service Contracts)

---

## 16. Open Questions

1. **Canonical API routes:** What are the exact Moqui endpoints for:
   - start work order,
   - update in-progress reason,
   - load transitions/history?
   (Provided references conflict: `/api/workorders/{id}/start` vs `/workexec/v1/workorders/{id}:start`.)

2. **Start-eligible statuses:** Is `SCHEDULED` a valid startable status for work orders in this system?
   - FSM doc: start-eligible = `APPROVED`, `ASSIGNED`
   - backend story excerpt: includes `SCHEDULED`
   Frontend gating + test cases depend on the canonical list.

3. **Blocking change request status naming:** For “pending approval change request,” should frontend consider:
   - `AWAITING_ADVISOR_REVIEW` (from Change Request workflow doc), or
   - `PENDING_APPROVAL` (from backend story excerpt),
   or both? What does the backend actually return?

4. **Reason update contract:** What is the endpoint + request schema to change `inProgressReason`, and does it accept an optional user-entered note/reason that is stored in history?

5. **History entity naming/shape:** Should frontend call `/transitions` (FSM doc) or a `WorkOrderStatusHistory` endpoint (backend story excerpt)? What fields are guaranteed in response?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Execution: Start Workorder and Track Status  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/224  
Labels: frontend, story-implementation, user

### Story Description

/kiro  
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Technician / Shop Manager

## Trigger
Technician begins work on an assigned workorder.

## Main Flow
1. Technician opens assigned workorder.
2. Technician selects 'Start Work' (or shop auto-starts on first labor entry).
3. System transitions workorder to InProgress with validation.
4. Technician updates status codes as needed (waiting parts, waiting approval).
5. System records status change events.

## Alternate / Error Flows
- Workorder not in executable state → block start and show reason.
- Pending approval change request → block progression into billable new work.

## Business Rules
- State transitions must be explicit and validated.
- Status history must be retained.

## Data Requirements
- Entities: Workorder, WorkorderStatusEvent, ChangeRequest
- Fields: status, statusReason, changedBy, changedAt, changeRequestId

## Acceptance Criteria
- [ ] Workorder can be started only when in proper state.
- [ ] Status changes are recorded and visible.
- [ ] Approval-gated statuses prevent unauthorized scope expansion.

## Notes for Agents
Use status events for throughput/cycle-time analytics later.

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