STOP: Clarification required before finalization

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

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Timekeeping: Record Break Start/End (Mechanic)

**Primary Persona:** Mechanic (authenticated employee user)

**Business Value:** Ensures mechanic timecards accurately reflect worked vs break time, improving payroll accuracy and compliance and reducing manager correction effort.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Mechanic  
- **I want** to start and end breaks during my workday (with a break type)  
- **So that** my timecard reflects actual working time and breaks are properly audited

### In-scope
- Frontend UI to **start a break** (select required `breakType`, optional notes if needed)
- Frontend UI to **end the current break**
- Display of current break state (in progress vs none) and recent break entries for the day/timecard
- Enforcement via UI + backend error handling for:
  - no overlapping breaks
  - cannot start/end break without active clock-in session
  - cannot start when a break already in progress
  - cannot end when no break is in progress
- Basic audit visibility (e.g., created/updated timestamps, end reason if returned)

### Out-of-scope
- Clock-in / clock-out UI (assumed existing elsewhere)
- Manager approval flows for time entries
- Editing/deleting historical breaks
- Cross-domain integrations (explicitly “none required initially” per provided inputs)
- Payroll calculations

---

## 3. Actors & Stakeholders

- **Mechanic (Primary):** starts/ends breaks.
- **Shop Manager (Stakeholder):** consumes break data indirectly via timecard review/approvals (not implemented here).
- **People domain services (System):** validates break rules and persists break TimeEntry/Break records.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- User is a Mechanic (or otherwise permitted to record their own breaks).

### Dependencies
- Backend endpoints/services must exist to:
  - Determine whether the mechanic is currently clocked in / has an active session/timecard.
  - Start a break with `breakType`.
  - End the current break.
  - Query today’s breaks (or time entries of type BREAK) to show status/history.

**Note:** Backend story reference (#84) defines behavior, but exact API/service names for Moqui are not provided in the frontend inputs; see Open Questions.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From the primary “Timekeeping” area available to mechanics (e.g., “My Day”, “Timecard”, or “Clock” screen).
- Optional: a persistent action area in the mechanic home/dashboard showing break state.

### Screens to create/modify
- **Modify**: Mechanic timekeeping screen (existing) to add break controls and break list panel/section.
- **Create (if missing)**: A focused sub-screen/dialog for “Start Break” (type selection + confirm).

### Navigation context
- Screen path should live under the mechanic’s timekeeping flow; implement as a Moqui screen with transitions to start/end actions and re-render on success.

### User workflows
**Happy path — Start break**
1. Mechanic opens timekeeping screen.
2. Sees “Start Break” action enabled when eligible.
3. Chooses `breakType` and confirms.
4. UI shows break now “In progress” with start time and “End Break” enabled.

**Happy path — End break**
1. Mechanic clicks “End Break”.
2. UI confirms (optional) then submits.
3. UI shows break completed and “Start Break” enabled again.

**Alternate paths**
- If not clocked in: break actions disabled and guidance shown.
- If break already in progress: start action disabled; show current break details.
- If backend rejects due to overlap or state mismatch: show error and refresh break status.

---

## 6. Functional Behavior

### Triggers
- User clicks **Start Break**
- User clicks **End Break**

### UI actions
- **Start Break**
  - Open a modal/form to select `breakType` (MEAL/REST/OTHER) and submit.
  - On submit, call start-break service/endpoint.
- **End Break**
  - Call end-break service/endpoint for the current user’s active break.

### State changes (frontend view model)
- `breakStatus`: `NONE` | `IN_PROGRESS`
- `activeBreak`: object (id, breakType, startTime, etc.) when in progress
- `breaksToday[]`: list of break entries for display

### Service interactions
- On screen load: fetch “current timekeeping context” (clocked-in status and active break, plus today’s break list).
- After start/end success: re-fetch current context (or apply response to state if response includes updated break).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `breakType` is required to start a break.
- For `breakType = OTHER`, backend reference suggests optional notes; **UI must not enforce notes** unless contract is confirmed (Open Question).

### Enable/disable rules
- **Start Break** enabled only when:
  - user is clocked in / has active session
  - no active break in progress
- **End Break** enabled only when:
  - user is clocked in / has active session (if backend requires)
  - an active break is in progress

### Visibility rules
- When a break is in progress:
  - Show active break summary (type, started at, elapsed time if available client-side).
  - Hide/disable “Start Break”; show “End Break”.
- When no break is in progress:
  - Show “Start Break”.
  - Show today’s completed breaks list (if any) with start/end times and type.

### Error messaging expectations
Frontend must map backend errors to user-friendly messages:
- 400 validation: show specific field error (e.g., “Break type is required.”)
- 409 conflict: show “A break is already in progress” or “Breaks cannot overlap” depending on error code/message
- 403 forbidden: “You don’t have permission to record breaks.”
- 404/422 state mismatch: “No active break to end.” (if backend uses these; Open Question)
- Network/500: generic “Unable to record break right now. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **TimeEntry (break)** (from provided inputs)
- Backend reference indicates a **Break** entity; frontend should treat it as a break-time entry record regardless of underlying entity.

### Fields
Minimum fields needed for UI:
- `breakId` (string/UUID) — read-only
- `breakType` (enum: `MEAL` | `REST` | `OTHER`) — required on start; read-only after create
- `status` (`IN_PROGRESS` | `COMPLETED`) — read-only
- `startTime` (timestamp) — read-only
- `endTime` (timestamp|null) — read-only
- `endReason` (`MANUAL_ENDED` | `AUTO_ENDED_AT_CLOCKOUT` | null) — read-only (display if present)
- `notes` (string|null) — editable only at start time *if supported*
- `createdAt`, `updatedAt`, `createdBy`, `updatedBy` — read-only (audit display optional)

### Read-only vs editable by state/role
- Mechanic can only:
  - create a break (start) with `breakType` (+ optional notes)
  - end the active break
- Mechanic cannot edit completed breaks or timestamps via UI.

### Derived/calculated fields
- `elapsedSeconds` for active break: computed client-side from `startTime` and current time for display only (not persisted).

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation may use screen transitions calling services. Exact service names/endpoints are **TBD** pending backend contract alignment.

### Load/view calls
- **Get current timekeeping state**
  - Returns: `isClockedIn`, `activeBreak` (nullable), `breaksToday[]`
  - Used on initial screen render and after mutations.

### Create/update calls
- **Start break**
  - Request: `{ breakType, notes? }`
  - Response: created `break` (recommended) or success flag
- **End break**
  - Request: `{ activeBreakId? }` (prefer server-derived by current user; Open Question)
  - Response: updated break or success flag

### Submit/transition calls (Moqui screens)
- `transition name="startBreak"` → calls service `…StartBreak` and then redirects back with message
- `transition name="endBreak"` → calls service `…EndBreak` and then redirects back with message

### Error handling expectations
- Support structured errors that include:
  - `errorCode` (e.g., `BREAK_ALREADY_IN_PROGRESS`, `NOT_CLOCKED_IN`, `NO_ACTIVE_BREAK`, `OVERLAPPING_BREAK`)
  - `message`
  - `fieldErrors` (for `breakType`)
If backend does not provide codes, UI falls back to parsing message + HTTP status.

---

## 10. State Model & Transitions

### Allowed states (break)
- `IN_PROGRESS`
- `COMPLETED`

### Role-based transitions
- Mechanic:
  - `NONE` → `IN_PROGRESS` via Start Break
  - `IN_PROGRESS` → `COMPLETED` via End Break
- System:
  - `IN_PROGRESS` → `COMPLETED` via auto-end at clock-out (display-only impact; no UI action here)

### UI behavior per state
- `IN_PROGRESS`: show “End Break”, show active break details; prevent new start.
- `COMPLETED`/none active: show “Start Break”; show list/history.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing `breakType` → inline error on selection field; prevent submit.
- Invalid enum returned/unsupported → show generic error and log.

### Concurrency conflicts
- Mechanic attempts Start Break but another terminal/session already started one:
  - Backend returns 409; UI shows conflict message and refreshes context.
- Mechanic attempts End Break but it was already auto-ended or ended elsewhere:
  - Backend returns state mismatch; UI shows “No active break” and refreshes context.

### Unauthorized access
- Backend returns 403:
  - UI shows permission error and disables break controls.

### Empty states
- No breaks today:
  - Show “No breaks recorded today.”

---

## 12. Acceptance Criteria

### Scenario 1: Start break successfully
**Given** I am authenticated as a Mechanic  
**And** I am clocked in with an active session/timecard  
**And** I have no break currently in progress  
**When** I choose `breakType = MEAL` and submit “Start Break”  
**Then** the UI shows a success confirmation  
**And** the UI shows an active break in progress with start time  
**And** the “Start Break” action is disabled and “End Break” is enabled

### Scenario 2: End break successfully
**Given** I am authenticated as a Mechanic  
**And** I am clocked in  
**And** I have a break in progress  
**When** I click “End Break”  
**Then** the UI shows a success confirmation  
**And** the active break is no longer in progress  
**And** the completed break appears in today’s break list with an end time

### Scenario 3: Prevent overlapping / duplicate start
**Given** I am authenticated as a Mechanic  
**And** I have a break in progress  
**When** I attempt to start another break  
**Then** the UI prevents the action (button disabled) **or** the backend rejects it  
**And** if rejected, the UI displays “A break is already in progress.”  
**And** no additional break is shown as created

### Scenario 4: Require break type on start
**Given** I am authenticated as a Mechanic  
**And** I am clocked in  
**When** I open “Start Break” and submit without selecting a break type  
**Then** the UI shows an inline validation error indicating break type is required  
**And** no start-break request is sent

### Scenario 5: Cannot start break when not clocked in
**Given** I am authenticated as a Mechanic  
**And** I am not clocked in (no active session)  
**When** I view the timekeeping screen  
**Then** the “Start Break” control is disabled  
**And** the UI explains I must be clocked in to start a break  
**And** if I still attempt via direct route/action, the backend error is displayed and no break is created

### Scenario 6: Cannot end break when none is active
**Given** I am authenticated as a Mechanic  
**And** I have no active break  
**When** I attempt to end a break  
**Then** the UI disables “End Break”  
**And** if the backend responds with “No active break to end”, the UI displays that message and refreshes

### Scenario 7: Audit visibility (frontend)
**Given** I have recorded breaks today  
**When** I view the break list  
**Then** each entry shows at minimum break type, start time, and end time (if completed)  
**And** if the backend returns `endReason`, it is displayed for completed breaks

---

## 13. Audit & Observability

### User-visible audit data
- Display timestamps for break start/end.
- Display `endReason` when provided (manual vs auto-ended).

### Status history
- The break list for the day functions as the status history view.
- No editing; list is append-only from the mechanic’s perspective.

### Traceability expectations
- All start/end actions must include correlation/request IDs in network logs (frontend console/logger) and pass through to Moqui via headers if supported by project conventions (Open Question if standardized).

---

## 14. Non-Functional UI Requirements

- **Performance:** Initial load of timekeeping screen (including break state) should complete within 2s on typical store network; subsequent start/end actions should update UI within 500ms after response.
- **Accessibility:** All controls keyboard accessible; modal has focus trap; labels for break type selection; error text announced via ARIA live region if supported by Quasar patterns.
- **Responsiveness:** Works on POS terminal resolutions and tablet sizes; actions remain reachable without horizontal scrolling.
- **i18n/timezone:** Display times in the location/user timezone consistent with the rest of the POS UI (Open Question if app standard is set); store values as server timestamps.

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE**
  - **Assumed:** Show “No breaks recorded today” when list is empty.
  - **Why safe:** Pure UI ergonomics; does not change domain behavior.
  - **Impacted sections:** UX Summary, Alternate / Error Flows, Acceptance Criteria.
- **SD-ERR-HTTP-STATUS-MAP**
  - **Assumed:** Standard mapping of 400/403/409/500 to inline vs toast errors with refresh-on-conflict.
  - **Why safe:** Error presentation only; respects backend as source of truth.
  - **Impacted sections:** Business Rules, Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions

1. **Backend contract (blocking):** What are the exact Moqui service names / REST endpoints and payloads for:
   - loading current clock-in/break context
   - starting a break
   - ending a break
   Include status codes and error codes.
2. **Scope (blocking):** Is a break tied to a **Timecard**, **ClockInSession**, or generic **TimeEntry** in the frontend API? Which identifier(s) must the UI pass (if any), or is it fully derived from the authenticated user?
3. **Notes requirement (blocking):** For `breakType = OTHER`, is `notes` required, optional, or unsupported?
4. **Timezone standard (blocking):** What timezone should the UI use for displaying break times (user profile timezone vs location timezone vs device)?
5. **Last-used break type default (non-blocking but important):** Should the UI default `breakType` to last-used (as in backend reference) and if so, does the backend provide last-used, or should the frontend infer from the most recent break?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Timekeeping: Record Break Start/End  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/148  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Record Break Start/End

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to record breaks** so that **my timecard reflects actual working time**.

## Details
- Break segments attached to day/timecard.
- Prevent overlapping breaks.

## Acceptance Criteria
- Break start/end supported.
- No overlapping breaks.
- Audited.

## Integration Points (workexec/shopmgr)
- None required initially.

## Data / Entities
- TimeEntry (break)

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