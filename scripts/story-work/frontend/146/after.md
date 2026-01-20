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

# 1. Story Header

## Title
[FRONTEND] [STORY] Workexec: Start/Stop Job Timer for Assigned Work Order Task

## Primary Persona
Mechanic (Technician)

## Business Value
Accurate, audit-friendly labor time capture tied to a work order (and optionally a specific task/labor code), reducing manual calculations and improving downstream job costing/billing accuracy.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Mechanic  
- **I want** to start and stop a job timer for my assigned work order/task  
- **So that** the system records accurate time entries automatically and prevents overlapping timers unless allowed by policy.

## In-scope
- UI to **view active timer state** and recover after refresh/login.
- UI actions to **start** a timer (with workOrderId required; optional workOrderItemId and/or laborCode if available in the UI context).
- UI actions to **stop** timer(s) for the authenticated mechanic.
- Frontend handling of backend constraint errors (single active timer by default).
- Display of resulting time entry summary after start/stop.
- Basic audit visibility (who/when) where returned by API.

## Out-of-scope
- Timer **pause/break handling**.
- Manual editing/adjustment of completed/auto-stopped time entries.
- Creating/assigning work orders or tasks.
- Clock-in/clock-out UI and the actual auto-stop jobs (handled by backend/system).
- Defining policy values/roles (concurrent timers configuration and permissions).

---

# 3. Actors & Stakeholders
- **Mechanic (Primary)**: starts/stops timers while working.
- **Service Advisor (Stakeholder)**: reviews time captured for billing accuracy (read-only impact).
- **System/Backend**: enforces assignment and timer constraints; creates immutable time entries.
- **Audit/Reporting (Downstream)**: consumes timer events/time entry records.

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in POS frontend.
- User is a Mechanic (or otherwise authorized to use timers).
- Work order exists and is in a “workable” state per backend (default mentioned: `IN_PROGRESS`).
- Mechanic is assigned to the work order (and to the item if item-level timing is used).

## Dependencies
- Backend endpoints available (from backend story #82):
  - `GET /api/workexec/time-entries/timer/active`
  - `POST /api/workexec/time-entries/timer/start`
  - `POST /api/workexec/time-entries/timer/stop`
- Work order/task context available in the UI (e.g., WorkOrderAssigned event or work order detail screen) to provide:
  - `workOrderId` (required)
  - `workOrderItemId` (optional)
  - `laborCode` (optional)
- Moqui security session provides authenticated principal; frontend must not supply mechanicId.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From **Work Order Detail / Execution** screen for an assigned work order (primary).
- From a **Mechanic dashboard / My Work** screen showing assigned work orders (secondary).
- Global “Active Timer” indicator (optional UI element) that links to timer details (see Open Questions).

## Screens to create/modify (Moqui)
1. **Modify**: `WorkOrderDetail` (or equivalent existing workexec screen)
   - Add a “Timer” section with Start/Stop actions and current timer status.
2. **Create**: `TimerPanel` screenlet / embedded screen
   - Reusable component to display active timer and controls.
3. **Create** (optional but recommended for recovery): `MyActiveTimer` screen
   - Dedicated screen to show active timer(s) and allow stop.

> Note: Exact screen names/paths must follow repo conventions; implement as Moqui screens with transitions calling REST services.

## Navigation context
- Work order context provides `workOrderId`.
- If user navigates away and returns, UI must re-load active timer via `GET …/active`.

## User workflows

### Happy path: start then stop (single-timer mode)
1. Mechanic opens assigned work order.
2. UI loads active timer(s).
3. Mechanic taps **Start Timer**.
4. UI shows timer running (start time; elapsed time increments client-side).
5. Mechanic taps **Stop Timer**.
6. UI confirms timer stopped and shows recorded duration.

### Alternate: start blocked due to existing active timer
1. Mechanic taps Start Timer while another timer is active.
2. UI shows error and offers to:
   - View active timer details (via GET active)
   - Stop active timer(s)

### Recovery: refresh/relogin while timer active
1. Mechanic reloads app.
2. UI calls GET active on entry and shows running timer state.

---

# 6. Functional Behavior

## Triggers
- Screen load / route enter for timer-capable screens triggers active timer load.
- User clicks Start Timer.
- User clicks Stop Timer.

## UI actions
- **On load**:
  - Call `GET /api/workexec/time-entries/timer/active`
  - Render:
    - No active timer: show Start controls enabled (subject to having workOrderId context)
    - Active timer(s): show Stop controls enabled; Start controls disabled unless backend/policy supports multiple timers (frontend must not assume; see error handling)
- **Start Timer click**:
  - Validate required inputs present in UI context:
    - `workOrderId` required
    - If `workOrderItemId` supplied, include it
    - If `laborCode` supplied, include it
  - Call `POST …/start` with body `{ workOrderId, workOrderItemId, laborCode }`
  - On success, update UI to running state using response summary.
- **Stop Timer click**:
  - Call `POST …/stop` with empty body
  - On success, update UI to not-running state and show stopped entries summary.

## State changes (frontend)
- Maintain a local timer view state:
  - `timerState = NONE | ACTIVE | STOPPING | STARTING | ERROR`
- When ACTIVE, show:
  - startTime from server
  - elapsed time computed from (now - startTime) using client clock (display only; server remains authoritative)

## Service interactions
- All calls include auth token/session.
- Use correlation/request ID propagation if the frontend stack supports it (see Observability).

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Start Timer is blocked client-side if:
  - `workOrderId` is missing from context.
- Do **not** attempt to provide `mechanicId` in requests (backend derives it).
- Do not allow “Stop” if UI already knows there is no active timer, but still handle server 409.

## Enable/disable rules
- If GET active returns at least one ACTIVE timer:
  - Disable Start (to align with default single-timer policy)
  - Enable Stop
- If GET active returns none:
  - Enable Start (if workOrderId present)
  - Disable Stop

> If concurrent timers are enabled by backend policy, the UI may still receive 201 for start even when an active timer exists; therefore the UI must rely on backend responses and handle 409 gracefully rather than assuming policy.

## Visibility rules
- When timer ACTIVE:
  - Show workOrder reference and (if present) item/laborCode from the active timer response.
- When timer stopped:
  - Show last stopped duration and timestamps from response.

## Error messaging expectations
Map backend errors to user-facing messages (no sensitive details):
- `409 TIMER_ALREADY_ACTIVE`: “You already have an active timer. Stop it before starting a new one.”
- `409 NO_ACTIVE_TIMER`: “No active timer to stop.”
- `403 Forbidden`: “You don’t have permission to use timers.”
- `404 Not Found`: “Work order/task not available.”
- `409 Conflict` (other): “Timer action cannot be completed due to current work order status or assignment.”

---

# 8. Data Requirements

## Entities involved (frontend view models)
- **TimeEntry** (job)
- **TimeException** (read-only awareness if returned; not created by frontend)

## Fields (type, required, defaults)

### TimeEntry (as consumed by frontend)
- `timeEntryId` (string/uuid) — required
- `mechanicId` (string/uuid) — returned only; not sent
- `workOrderId` (string/uuid) — required
- `workOrderItemId` (string/uuid|null) — optional
- `laborCode` (string|null) — optional
- `startTime` (ISO-8601 UTC string) — required
- `endTime` (ISO-8601 UTC string|null) — present when stopped
- `durationInSeconds` (number|null) — present when stopped
- `status` (enum: `ACTIVE|COMPLETED|AUTO_STOPPED`) — required

### Active timer response (expected)
- Either:
  - single active timer object
  - or a list of active timers (to support concurrent mode)
Frontend must be tolerant (see Open Questions on schema).

## Read-only vs editable
- All displayed fields are read-only in this story.
- Only actions are start/stop.

## Derived/calculated fields
- `elapsedDisplaySeconds = nowClient - startTimeServer` (display only)
- `durationDisplay = format(durationInSeconds)` when stopped

---

# 9. Service Contracts (Frontend Perspective)

## Load/view calls
### Get active timer(s)
- **Method/Path:** `GET /api/workexec/time-entries/timer/active`
- **Request:** none
- **Success (200):** active timer info for authenticated mechanic
- **Empty:** returns empty list or null/empty payload meaning none active (must handle both)

## Create/update calls
### Start timer
- **Method/Path:** `POST /api/workexec/time-entries/timer/start`
- **Request body:**
  ```json
  {
    "workOrderId": "uuid",
    "workOrderItemId": "uuid|null",
    "laborCode": "string|null"
  }
  ```
- **Success (201):** created time entry summary (must include at least id, status, startTime, links)

### Stop timer(s)
- **Method/Path:** `POST /api/workexec/time-entries/timer/stop`
- **Request body:** none
- **Success (200):**
  ```json
  {
    "stopped": [ /* one or more TimeEntry summaries */ ]
  }
  ```
  In single-timer mode this will be one; in concurrent mode it may be multiple.

## Submit/transition calls
- None beyond start/stop.

## Error handling expectations
- `409 TIMER_ALREADY_ACTIVE`:
  - UI should refresh by calling `GET …/active` and render the active timer.
- `409 NO_ACTIVE_TIMER`:
  - UI should refresh by calling `GET …/active` and render none.
- Generic network/5xx:
  - Show non-technical error and allow retry; do not duplicate actions automatically.

---

# 10. State Model & Transitions

## Timer states (TimeEntry.status)
- `ACTIVE` → `COMPLETED` via Stop action
- `ACTIVE` → `AUTO_STOPPED` via system (clock-out/job); UI may discover via refresh
- `COMPLETED` / `AUTO_STOPPED` are immutable via this UI

## Role-based transitions
- Mechanic (authorized): may start/stop for self (identity derived from auth)
- Others: depending on backend permissions; frontend should rely on 403/404/409 handling and hide controls if role is clearly not mechanic (if role info exists in session; otherwise do not guess)

## UI behavior per state
- ACTIVE: show running timer and Stop button
- COMPLETED/AUTO_STOPPED (as last action result): show summary; Start enabled if no active timers remain

---

# 11. Alternate / Error Flows

## Validation failures (client)
- Missing `workOrderId` in context:
  - Disable Start, show message “Select a work order to start a timer.”

## Backend conflict: timer already active
- Start returns 409 `TIMER_ALREADY_ACTIVE`:
  - UI displays message + fetches active timers to show what is running.

## Backend conflict: stop when none active
- Stop returns 409 `NO_ACTIVE_TIMER`:
  - UI displays message + refreshes active timers (should show none).

## Concurrency conflicts
- Rapid double-click start/stop:
  - UI must disable the action button while request is in-flight.
- Two devices:
  - If a timer is started/stopped elsewhere, GET active refresh reflects server truth.

## Unauthorized access
- 403:
  - Hide Start/Stop controls for the session after first 403 and show a permission message.

## Empty states
- No active timer:
  - Show “No active timer” and Start available (with context).

---

# 12. Acceptance Criteria

### Scenario 1: Load screen shows no active timer
**Given** I am authenticated as a mechanic  
**And** I open an assigned work order screen with a valid `workOrderId`  
**When** the screen loads  
**Then** the app calls `GET /api/workexec/time-entries/timer/active`  
**And** if no active timer exists, the UI shows “No active timer”  
**And** the “Start Timer” action is enabled  
**And** the “Stop Timer” action is disabled.

### Scenario 2: Start timer creates an active time entry
**Given** I have no active timer  
**And** I am viewing a work order with `workOrderId=W1`  
**When** I click “Start Timer”  
**Then** the app calls `POST /api/workexec/time-entries/timer/start` with `workOrderId=W1`  
**And** the API responds `201` with a `TimeEntry` having `status=ACTIVE` and `startTime`  
**And** the UI displays the timer as running.

### Scenario 3: Stop timer completes the time entry
**Given** I have an active timer running  
**When** I click “Stop Timer”  
**Then** the app calls `POST /api/workexec/time-entries/timer/stop`  
**And** the API responds `200` with `stopped[]` containing at least one entry  
**And** each returned entry has `endTime`, `durationInSeconds`, and `status=COMPLETED` or `AUTO_STOPPED`  
**And** the UI displays the stopped summary and shows no active timer.

### Scenario 4: Prevent starting a second timer in single-timer mode
**Given** I already have an active timer  
**When** I attempt to start another timer  
**Then** the API responds `409` with error code `TIMER_ALREADY_ACTIVE`  
**And** the UI shows an error stating an active timer already exists  
**And** the UI refreshes by calling `GET /api/workexec/time-entries/timer/active` and displays the active timer.

### Scenario 5: Stop fails when no timer active
**Given** I have no active timer  
**When** I click “Stop Timer”  
**Then** the API responds `409` with error code `NO_ACTIVE_TIMER`  
**And** the UI shows an error stating there is no active timer to stop  
**And** the UI refreshes active timer state via `GET /api/workexec/time-entries/timer/active`.

### Scenario 6: Recover after refresh
**Given** I started a timer and it is still active  
**When** I refresh the page or reopen the app  
**Then** the app calls `GET /api/workexec/time-entries/timer/active`  
**And** the UI shows the active timer as running using the returned `startTime`.

---

# 13. Audit & Observability

## User-visible audit data
- Display (if present in responses):
  - `startTime`, `endTime`, `status`
  - Work order/task identifiers
- Do not display mechanicId if considered sensitive; treat as internal unless required.

## Status history
- Not required to display full history; only current active timer and last stop result.
- If backend provides multiple stopped entries (concurrent stop), show all in a list.

## Traceability expectations
- Frontend should include a client-generated correlation ID header if the existing frontend stack supports it (align with repo conventions).
- Log (frontend) timer actions with:
  - route/screen
  - workOrderId/workOrderItemId (non-PII)
  - outcome (success/failure + error code)

---

# 14. Non-Functional UI Requirements

- **Performance:** Active timer load should complete quickly on screen entry; avoid polling by default (manual refresh acceptable).
- **Accessibility:** Timer controls must be keyboard accessible; provide text labels for screen readers; ensure sufficient contrast.
- **Responsiveness:** Controls usable on tablet-sized devices typical for shop floor.
- **i18n/timezone:** Display times in local UI timezone, but preserve UTC in data; duration is timezone-independent.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show explicit “No active timer” empty state and disable Stop when none active; qualifies as safe because it’s purely presentational and does not change domain policy. (Impacted sections: UX Summary, Alternate/Empty states, Acceptance Criteria)
- SD-UX-INFLIGHT-GUARD: Disable Start/Stop buttons while request is in-flight to prevent double submission; qualifies as safe because it prevents duplicate calls without altering business rules. (Impacted sections: Error Flows, Functional Behavior)

---

# 16. Open Questions
1. What is the **exact response schema** for `GET /api/workexec/time-entries/timer/active` (single object vs `{ active: [] }` vs raw array)? Frontend will implement tolerant parsing, but needs a canonical contract for tests.
2. Does the frontend have an existing **Work Order Task / Item** concept and identifier (`workOrderItemId`) available in the UI context today, or is v1 limited to work-order-level timing only?
3. Should the UI expose a **global active timer indicator** (visible outside work order screens) as part of this story, or is it limited to within the work order detail?
4. Are there specific **role names/permissions** exposed to the frontend to pre-hide controls, or must we rely entirely on backend 403 responses?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/146


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/146
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to start and stop a job timer for a workorder task** so that **I can accurately capture job time without manual calculations**.

## Details
- Timer references workOrderId and optional workOrderItemId/laborCode.
- Enforce one active timer per mechanic (default).

## Acceptance Criteria
- Start/stop timer produces a job time entry.
- Prevent multiple active timers unless configured.
- Audited.

## Integration Points (workexec)
- Inbound: WorkOrderAssigned for context.

## Data / Entities
- TimeEntry (job)
- JobLink

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

====================================================================================================