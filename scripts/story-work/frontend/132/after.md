## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Timekeeping: Start/Stop Work Session (with Breaks + Overlap Guard) for Assigned Work Order Task

### Primary Persona
Mechanic

### Business Value
Capture accurate, auditable labor time against a specific work order task (net of breaks) to support operational execution and downstream payroll/job-costing consumption, while preventing invalid/overlapping time entries.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Mechanic  
- **I want** to start and stop a work session (and optionally record breaks) tied to my assigned work order task  
- **So that** my labor time is captured accurately for costing and downstream processing, without accidental overlaps or edits after locking/approval.

### In-scope
- UI to **start** a work session for a selected assigned work order task.
- UI to **stop** the currently active session (or active session for that task if backend supports).
- UI to **start/stop breaks** within an active work session (manual break segments).
- UI enforcement of **overlap prevention** and explicit **override path** when backend indicates it is allowed (config + permission + audit reason).
- UI enforcement that **locked/approved** sessions cannot be modified.
- Viewing basic session details and computed totals as returned by backend.

### Out-of-scope
- Manager approval/locking workflow UI (approve/reject) beyond enforcing immutability when locked.
- Configuration UI for overlap policies / approval methods.
- Payroll export screens and downstream integrations UI.
- Creating/assigning work order tasks; this story assumes tasks exist and are assigned.

---

## 3. Actors & Stakeholders
- **Mechanic (Primary)**: starts/stops sessions and records breaks.
- **Service Manager / Admin (Approver)**: approves/locks sessions (enforced as read-only here).
- **Workexec backend service (SoR)**: owns `WorkSession` lifecycle, validations, and computed duration.
- **Payroll / HR / Job Costing (Downstream)**: consume events; no direct UI scope here.

---

## 4. Preconditions & Dependencies
- User is authenticated in POS frontend.
- Mechanic has at least one assigned work order task that is eligible to start work (backend-enforced).
- Backend endpoints for work sessions exist and are reachable (see Service Contracts).
- Frontend has a way to identify:
  - current userId/mechanicId (from session/auth context)
  - selected workOrderId + workOrderTaskId (from navigation context)
  - current locationId (from POS context; if not available, backend must infer)
- Dependency: backend story referenced as implemented/available API contract (Durion backend issue #68). If endpoints differ from the high-level contract, this story remains at risk until confirmed.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From a Work Order / Task execution screen: action button(s)
  - “Start Work”
  - “Stop Work”
  - “Start Break”
  - “End Break”
- Optional global entry: “My Active Session” (header/toolbar quick access) if project conventions allow.

### Screens to create/modify
- **Modify** an existing Work Order Task screen (or create a sub-screen) to include timekeeping actions.
  - Example screen path (needs alignment to repo conventions):  
    `apps/pos/screen/workorder/TaskDetail.xml` (or equivalent in Moqui screens tree)
- **Create** a lightweight Work Session detail modal/panel screen for viewing session timing and break state:
  - `apps/pos/screen/timekeeping/WorkSessionPanel.xml` (name indicative)

### Navigation context
- Work order context params: `workOrderId`, `workOrderTaskId`
- User context: derived from Moqui user session; do not require manual entry.
- After start/stop/break actions, remain on the same task screen and refresh session state panel.

### User workflows
**Happy path: start → break → resume → stop**
1. Mechanic opens assigned task.
2. Clicks **Start Work**.
3. UI shows session “IN_PROGRESS”, start timestamp, and enables break/stop actions.
4. Clicks **Start Break**, then **End Break**.
5. Clicks **Stop Work**; UI displays completed duration (net of breaks).

**Alternate: overlap prevented**
- Mechanic clicks Start Work but already has an active session; backend returns conflict → UI explains and offers navigation to active session.

**Alternate: overlap override allowed**
- Backend indicates override is permitted only with explicit override and reason → UI prompts for reason and retries via override-capable request (if supported by API).

**Alternate: locked**
- If session status is `APPROVED` or `locked=true`, all mutation actions are disabled; attempting deep-link action results in error banner.

---

## 6. Functional Behavior

### Triggers
- Button clicks: Start Work, Stop Work, Start Break, End Break
- Screen load: fetch current task + current active session state (if available) to set button enabled/disabled states.

### UI actions
- **Start Work**
  - Collect required identifiers: `workOrderId`, `workOrderTaskId`
  - Include `userId/mechanicId` only if backend requires (prefer backend deriving from auth if supported).
  - Call start service; on success, refresh session data.
- **Stop Work**
  - Call stop service (by sessionId if known; otherwise task-scoped stop if backend supports).
  - On success, refresh and show final totals.
- **Start Break / End Break**
  - Only available when session is `IN_PROGRESS`.
  - Call break start/stop endpoints with sessionId.
  - Refresh break list/state after each operation.

### State changes (frontend)
- No client-side authoritative state transitions; frontend reflects backend state.
- UI state derived from loaded `WorkSession.status`, `locked`, and “active break” indicator from backend (or inferred from last break segment missing endAt if returned).

### Service interactions
- Load task context (existing work order/task services already used by app).
- Work session actions via workexec endpoints (see Service Contracts).
- Handle 409 conflicts for overlaps and concurrent actions.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Before calling Start Work:
  - Ensure `workOrderId` and `workOrderTaskId` present in route/context; if missing, block action and show error “Task context missing.”
- Before calling Stop Work / Break actions:
  - Ensure an active `workSessionId` is loaded; if not, disable actions and show “No active session.”

### Enable/disable rules
- **Start Work** enabled only when:
  - No active session for this mechanic **OR** backend allows overlap override (requires server confirmation; see Open Questions about how frontend learns this)
  - Current session for this task is not already `IN_PROGRESS`
  - Not locked
- **Stop Work** enabled only when:
  - There is an active session in `IN_PROGRESS` (for the mechanic or for this task, depending on backend model)
  - Session is not locked
- **Start Break** enabled only when:
  - Session is `IN_PROGRESS`
  - No break currently active (if backend provides; otherwise infer)
  - Not locked
- **End Break** enabled only when:
  - Session is `IN_PROGRESS`
  - A break is currently active
  - Not locked

### Visibility rules
- Show “Locked/Approved” badge when `locked=true` or `status=APPROVED`.
- Show overlap warning banner when backend returns conflict on start due to existing active session.

### Error messaging expectations
- 400 validation: show field-level or banner message from backend (sanitize if needed).
- 403 unauthorized: “You don’t have permission to perform this action.”
- 404 not found: “Work order/task/session not found or no longer available.”
- 409 conflict:
  - Overlap: “You already have an active session. Stop it before starting another.”
  - Concurrent stop/start: “This session changed since you loaded the page. Refresh and try again.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `WorkSession`
- `BreakSegment`
- (Indirect) `WorkOrder`, `WorkOrderTask`

### Fields (type, required, defaults)
**WorkSession (read model as returned)**
- `workSessionId` (UUID/string) — required
- `mechanicId` (UUID/string) — required
- `workOrderId` (UUID/string) — required
- `workOrderTaskId` (UUID/string) — required
- `locationId` (UUID/string) — required (or backend-derived)
- `resourceId` (UUID/string|null) — optional
- `startAt` (ISO-8601 UTC string) — required
- `endAt` (ISO-8601 UTC string|null) — optional
- `status` (enum) — required: `IN_PROGRESS`, `COMPLETED`, `APPROVED`, `REJECTED` (as per backend reference)
- `locked` (boolean) — required
- `totalDurationSeconds` (int) — required on completed/approved (may be 0 while in progress depending on backend)
- Override audit (read-only):
  - `overlapOverrideUsed` (boolean)
  - `overrideReason` (string|null)
  - `overriddenByUserId` (UUID/string|null)
  - `overrideAt` (ISO-8601|null)

**BreakSegment (read model as returned)**
- `breakSegmentId` (UUID/string) — required
- `workSessionId` (UUID/string) — required
- `breakStartAt` (ISO-8601 UTC string) — required
- `breakEndAt` (ISO-8601 UTC string|null) — optional while active
- `breakType` (enum|null) — optional (`MEAL`, `REST`, `OTHER`)
- `notes` (string|null) — optional

### Read-only vs editable by state/role
- Mechanic can mutate only when session not locked and status is appropriate:
  - Create/start session
  - Stop session
  - Manage breaks
- When `locked=true` or `status=APPROVED`: all fields read-only; mutation calls should be blocked in UI and rejected by backend.

### Derived/calculated fields
- Display “Net duration” using `totalDurationSeconds` from backend as source of truth.
- Optionally display human-friendly duration formatting (HH:MM:SS) derived in UI.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend reference lists high-level endpoints; exact paths and payloads must be confirmed against the Moqui integration layer used in this repo. Until confirmed, treat as “contract-at-risk”.

### Load/view calls
- **Get active session for mechanic** (recommended for UX; if exists)
  - `GET /api/work-sessions/active?mechanicId={id}` (or `/api/work-sessions/active` derived from auth)
  - Returns 200 with WorkSession or 204/404 when none.
- **Get work session by id**
  - `GET /api/work-sessions/{workSessionId}`
- **List breaks for session**
  - `GET /api/work-sessions/{workSessionId}/breaks`

### Create/update calls
- **Start work session**
  - `POST /api/work-sessions/start`
  - Request (minimum):
    - `workOrderId`
    - `workOrderTaskId`
    - `locationId` (if required)
    - optional: `resourceId`
    - optional: `overrideReason` (only when override path used)
  - Response: 201 with created WorkSession
- **Stop work session**
  - `POST /api/work-sessions/stop`
  - Request: either `{ workSessionId }` OR `{ workOrderTaskId }` (must be confirmed)
  - Response: 200 with updated WorkSession

### Submit/transition calls (breaks)
- `POST /api/work-sessions/{id}/breaks/start`
  - optional body: `breakType`, `notes`
- `POST /api/work-sessions/{id}/breaks/stop`
  - Response: updated BreakSegment or WorkSession

### Error handling expectations
- 400: invalid task state, missing required fields, break rules violated (overlap/containment)
- 403: lacking permission (including overlap override permission)
- 409: overlap conflict; concurrent mutation
- UI must surface backend-provided message keys/text; do not attempt to reproduce policy client-side beyond basic disabling.

---

## 10. State Model & Transitions

### Allowed states (WorkSession)
- `IN_PROGRESS`
- `COMPLETED`
- `APPROVED`
- `REJECTED` (present in backend data model; UI read-only display if returned)

### Role-based transitions (as reflected in UI)
- Mechanic:
  - `IN_PROGRESS` → `COMPLETED` via Stop Work
  - create `IN_PROGRESS` via Start Work
  - manage breaks while `IN_PROGRESS`
- Manager/Admin (out-of-scope):
  - `COMPLETED` → `APPROVED` (locks session)

### UI behavior per state
- `IN_PROGRESS`: show live state; enable Stop/Break actions (unless locked).
- `COMPLETED`: disable Start Break/End Break/Stop; allow Start Work on other task (subject to overlap policy).
- `APPROVED`: show locked badge; disable all mutations.
- `REJECTED`: show status; disable all mutations; show reason if backend provides (not defined in inputs).

---

## 11. Alternate / Error Flows

### Validation failures
- Missing context IDs: show blocking banner; do not call backend.
- Break start when already on break: backend 409/400; UI shows “Break already active.”

### Concurrency conflicts
- Two devices attempt stop/start: backend 409; UI instructs refresh and reload active session state.

### Unauthorized access
- Mechanic attempts override without permission: backend 403; UI: “Override not permitted.”

### Empty states
- No active session:
  - Task screen shows “No active session” and enables Start Work (if task eligible).
- No breaks:
  - Break list shows empty state “No breaks recorded.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Start work session successfully
**Given** I am logged in as a Mechanic  
**And** I am viewing an assigned Work Order Task eligible to start  
**And** I have no other active `IN_PROGRESS` work session (or overlap is permitted and I use the correct path)  
**When** I click “Start Work”  
**Then** the system creates a WorkSession with `status = IN_PROGRESS` and `startAt` in UTC  
**And** the UI displays the active session details and enables “Stop Work” and “Start Break”.

### Scenario 2: Stop work session successfully
**Given** I have an active WorkSession in `IN_PROGRESS` for the current task  
**When** I click “Stop Work”  
**Then** the system updates the session to `status = COMPLETED` with `endAt` in UTC  
**And** the UI shows `totalDurationSeconds` (net of breaks) returned by the backend  
**And** the UI disables break controls for that session.

### Scenario 3: Start break and end break within a session
**Given** I have an active WorkSession in `IN_PROGRESS`  
**When** I click “Start Break”  
**Then** a BreakSegment is created with `breakStartAt` in UTC  
**And** the UI disables “Start Break” and enables “End Break”  
**When** I click “End Break”  
**Then** the BreakSegment is updated with `breakEndAt` in UTC  
**And** the UI re-enables “Start Break”.

### Scenario 4: Overlap prevented by default
**Given** I am logged in as a Mechanic  
**And** I already have an `IN_PROGRESS` WorkSession  
**When** I attempt to start another WorkSession  
**Then** the backend responds with a conflict (HTTP 409)  
**And** the UI displays an overlap error message  
**And** no new session is shown as started.

### Scenario 5: Overlap override requires permission + reason
**Given** I already have an `IN_PROGRESS` WorkSession  
**And** the backend policy allows overlap only with config + permission + explicit override reason  
**When** I attempt to start a new WorkSession and provide an override reason  
**Then** the new WorkSession is created and includes override audit fields as returned  
**And** the UI indicates an override was used.  

### Scenario 6: Locked/approved sessions are immutable
**Given** a WorkSession is `APPROVED` or has `locked=true`  
**When** I view the session  
**Then** the UI disables Start/Stop/Break actions for that session  
**And** if I attempt a mutation via direct call/navigation, the backend rejects it and the UI displays an error.

---

## 13. Audit & Observability

### User-visible audit data
- Display (read-only) on the session panel when available:
  - `status`, `startAt`, `endAt`, `totalDurationSeconds`
  - `locked` indicator
  - If override used: `overrideReason`, `overrideAt`, `overriddenByUserId` (or “Manager/User” if mapped)

### Status history
- Out-of-scope to render full transition history; but UI should not prevent backend from later adding it.
- If backend returns status changes/timestamps, display them read-only.

### Traceability expectations
- Each start/stop/break call should include standard correlation headers if the frontend stack supports it (per repo convention).
- UI should log (frontend console/logger) minimal action telemetry without PII beyond IDs already in route.

---

## 14. Non-Functional UI Requirements
- **Performance:** initial session panel load should complete within 2s on typical shop network; avoid excessive polling.
- **Accessibility:** all controls keyboard reachable; button labels descriptive; status changes announced via aria-live region where feasible in Quasar.
- **Responsiveness:** usable on tablet-sized devices used in bays; actions should be touch-friendly.
- **i18n/timezone:** display timestamps in shop/user local time while preserving UTC in payloads; show timezone indicator. (Do not change backend storage.)

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for “no active session” and “no breaks”. Why safe: purely UI ergonomics, no policy impact. Impacted sections: UX Summary, Alternate/Empty states.
- SD-ERR-HTTP-MAP: Map HTTP 400/403/404/409/500 to consistent Quasar notifications/banners. Why safe: standard error-handling, no domain policy inference. Impacted sections: Business Rules, Error Flows, Service Contracts.

---

## 16. Open Questions
1. What are the **exact backend endpoint paths** and request/response schemas for:
   - start session, stop session
   - start/stop break
   - fetching active session and breaks  
   (Backend story lists high-level endpoints but Moqui frontend needs exact contracts.)
2. How should the frontend determine whether **overlap override is available** before attempting start?
   - Does the start endpoint return a structured 409 payload with “overrideAllowed” metadata, or is there a dedicated policy endpoint?
3. Is `mechanicId` derived from the authenticated user in the backend, or must the frontend pass `mechanicId/userId` explicitly?
4. How is `locationId` sourced in the frontend (POS context) and is it required by the backend for starting sessions?
5. Does the backend support stopping by `{workSessionId}` only, or can it stop by `{workOrderTaskId}` / “current active session”?
6. Are there any **additional WorkSession statuses** beyond those listed that must be displayed (e.g., `PAUSED`), or is the enum fixed?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Timekeeping: Start/Stop Work Session for Assigned Work — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/132


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Timekeeping: Start/Stop Work Session for Assigned Work
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/132
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Start/Stop Work Session for Assigned Work

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mechanic**, I want to start/stop a work session tied to a workorder/task so that time is captured for payroll and costing.

## Details
- Work session includes mechanicId, workorderId, location/resource, start/end, breaks.
- Prevent overlap unless permitted.

## Acceptance Criteria
- Start/stop supported.
- Overlaps prevented.
- Lock after approval.

## Integrations
- Shopmgr→HR WorkSession events/API.
- Optional: Workexec consumes labor actuals.

## Data / Entities
- WorkSession, BreakSegment, ApprovalStatus

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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