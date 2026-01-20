STOP: Clarification required before finalization

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

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Reporting: Daily Dispatch Board Dashboard (Dispatch Board View + Exceptions)

**Primary Persona:** Dispatcher (also applicable: Shop Manager / Dispatch Coordinator)

**Business Value:** Provide an operational, at-a-glance view of today’s work orders, assignments, and exceptions so dispatch can proactively manage workload, reduce double-booking, and resolve blocking conflicts quickly.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Dispatcher  
- **I want** a Daily Dispatch Board dashboard that shows today’s work orders/appointments, mechanic availability signals, and conflict/exception indicators  
- **So that** I can dispatch jobs efficiently and avoid preventable scheduling conflicts

### In-scope
- A Moqui/Vue/Quasar dashboard screen that:
  - Filters by **location** and **date** (default today)
  - Loads a consolidated “dispatch board” view (work orders + mechanics + bays + exception indicators)
  - Auto-refreshes via polling every **30 seconds** plus manual refresh
  - Clearly surfaces exceptions (warning vs blocking) and stale-data/offline states
- Integration calls from the frontend to:
  - Workexec dispatch board endpoint (or equivalent)
  - People/HR availability endpoint (as defined in backend reference)
- Read-only visualization and exception highlighting (no automatic resolution)

### Out-of-scope
- WebSocket/push updates (explicitly “v2” in backend reference)
- Multi-location merged board (explicitly non-goal; one location per view)
- Configuration of default locations/bays/mechanic roles/skills
- Creating/updating work orders, estimates, approvals, receiving, picking
- Any payroll/billing effects

---

## 3. Actors & Stakeholders

- **Primary Actor:** Dispatcher (operates the board throughout the day)
- **Secondary Actors:** Shop Manager, Dispatch Coordinator
- **Data Providers (systems):**
  - `domain:workexec` (work orders, assignments, conflicts computed at dispatch time; board data endpoint)
  - People/HR service (real-time mechanic availability signal and advisory schedule/PTO)
  - Shop management/location resource source (service bays and occupancy) — consumed through the board payload
- **Stakeholders:** Shop Owner (throughput and utilization), Mechanics (impacted indirectly by dispatch decisions)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend and has access to a location.
- There exists (or may exist) work for the selected date/location.
- Backend provides an endpoint that returns dispatch board data, including exceptions, within SLA.

### Dependencies (blocking if absent)
- **Backend dispatch board endpoint** (referenced as `GET /dashboard/v1/today` in backend story) must exist and be reachable from Moqui frontend.
- People availability endpoint exists:
  - `GET /people/v1/availability?locationId=...&date=...&includeSchedule=true`
- Definition of frontend route/menu entry conventions in `durion-moqui-frontend` README (not provided in prompt).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Reporting → Dispatch Board** (or Ops/Dispatch; exact menu placement TBD)
- Direct URL route for the screen (TBD by project routing conventions)

### Screens to create/modify (Moqui)
- **Create screen:** `apps/pos/screen/reporting/DispatchBoard.xml` (name/path to be aligned with repo conventions)
  - Parameters: `locationId` (required), `date` (required; defaults to today in user/store timezone)
- Optional child screens/widgets:
  - `DispatchBoard/Board.xml` (board content)
  - `DispatchBoard/Exceptions.xml` (exceptions panel/list)
  - `DispatchBoard/Mechanics.xml` (mechanic availability list)
  - `DispatchBoard/Bays.xml` (bay occupancy list)
  - (Use Moqui screen includes to keep responsibilities clear)

### Navigation context
- Top-of-screen filters: Location selector, Date picker
- Status line: “Last updated at … (as-of …)” + stale indicator if needed
- Manual action: “Refresh Now”

### User workflows
#### Happy path
1. User navigates to Dispatch Board.
2. Defaults load: location = current/last-used, date = today.
3. Dashboard renders:
   - Work orders/appointments grouped by status
   - Assignments by mechanic and by bay/mobile
   - Exceptions highlighted (warning vs blocking)
4. Dashboard auto-refreshes every 30 seconds; user can refresh manually.

#### Alternate paths
- User selects a different date (past/future): board reloads for that date.
- User switches location: board reloads for that location; no multi-location merge.
- No work orders exist: empty state explains “No work scheduled for this date/location.”

---

## 6. Functional Behavior

### Triggers
- Screen load
- Filter change: locationId/date
- Poll tick (every 30 seconds) while screen is active/visible
- Manual refresh button

### UI actions
- Select locationId
- Select date
- Click “Refresh Now”

### State changes (frontend)
- Maintain a local UI state machine:
  - `idle` → `loading` → `loaded` (or `error`)
  - `loaded` plus sub-flags: `isStale`, `isOfflineReadOnly`
- Store:
  - `lastSuccessfulFetchAt` (client timestamp)
  - `boardAsOf` (server-provided timestamp, if available)
  - `selectedLocationId`, `selectedDate`

### Service interactions
- On load and on refresh:
  1. Fetch dispatch board data (workexec)
  2. Fetch people availability data (People/HR)
  3. Merge into a single view-model for rendering (frontend-only composition)
- If backend already aggregates People/bays into a single endpoint, the frontend should prefer the single endpoint to meet SLA (needs confirmation; see Open Questions).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `locationId` must be selected before loading; if missing, prompt user to select location.
- `date` must be valid ISO date (no time component in filter UI).

### Enable/disable rules
- While `loading`, disable refresh button to prevent request storms; allow “Cancel” only if project has standard cancellation pattern (TBD).
- If backend unreachable:
  - Enter read-only/offline mode with cached last successful payload if present
  - Disable filter actions only if they would require new data; otherwise allow but show “data may be stale” (see Open Questions)

### Visibility rules
- Exceptions must be visible without drilling into each work order:
  - At minimum show a count and list of exception indicators
  - Work orders/mechanics/bays with blocking exceptions must be visually distinguishable (exact styling left to UI implementation)

### Error messaging expectations
- If any load fails:
  - Show a non-technical error banner: “Unable to load dispatch board. Try again.”
  - Provide retry action
- If People availability fails but board loads:
  - Show board with a warning: “Mechanic availability is temporarily unavailable; showing work orders only.”
  - Do not block full page (safe degradation)

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `DispatchBoardView` (read model/view DTO from backend)
- `ExceptionIndicator` (DTO)
- People availability response (DTO as defined in backend reference)

> Note: Moqui entities (DB) are not created/modified by this frontend story; this is a reporting UI consuming backend APIs.

### Fields (type, required, defaults)
#### Filter inputs
- `locationId` (string or numeric ID; **required**)
- `date` (ISO date string `YYYY-MM-DD`; **required**, default = today)

#### People availability (from `/people/v1/availability`)
- `asOf` (ISO timestamp; required)
- `location` (id; required)
- `people[]` (array; required)
  - `personId` (required)
  - `firstName`, `lastName` (required)
  - `currentStatus` (required enum: `CLOCKED_OUT`, `AVAILABLE`, `ON_JOB`, `ON_BREAK`, `PTO`)
  - `clock` (optional object)
  - `break` (optional object)
  - `pto[]` (optional array)
  - `scheduledAvailability[]` (optional array)

#### Dispatch board view (TBD schema)
- Must include at least:
  - Work orders/appointments for date/location, with:
    - `workOrderId`
    - status
    - scheduled start/end or ETA window (needed for “overdue starts” and overlap detection display)
    - assigned mechanic(s)
    - assigned bay/mobile indicator
  - Bay list with occupancy/unavailability reason
  - Exception indicators with:
    - severity (`WARNING`/`BLOCKING`)
    - message
    - target reference (workOrderId/mechanicId/bayId)
    - category/code (one of 8 enumerated conditions or equivalent identifier)

### Read-only vs editable
- Entire screen is **read-only** in this story (no dispatch assignment action is specified in the frontend story inputs).

### Derived/calculated fields (frontend)
- `isStale` = true if `now - lastSuccessfulFetchAt > 2 minutes` (from backend alternate flow guidance)
- Exception grouping counts by severity
- Overdue start highlighting: requires backend to provide “scheduled start” and current job start status (otherwise cannot compute safely)

---

## 9. Service Contracts (Frontend Perspective)

> Endpoint names below reflect backend reference; exact base URL and auth headers follow project conventions (TBD).

### Load/view calls
1) **Dispatch Board**
- `GET /dashboard/v1/today?locationId={locationId}&date={YYYY-MM-DD}`  
  - **Expected:** 200 with board payload including work orders, bay status, exception indicators, and a server as-of timestamp if available.
  - **SLA:** P50 < 1.0s, P95 < 2.0s, P99 < 3.5s (gateway receipt → JSON response), as per backend reference.

2) **People Availability**
- `GET /people/v1/availability?locationId={locationId}&date={YYYY-MM-DD}&includeSchedule=true`
  - **Expected:** 200 with schema provided in backend reference.

### Create/update calls
- None (read-only dashboard in this story).

### Submit/transition calls
- None.

### Error handling expectations
- 401/403: show “You don’t have access to Dispatch Board for this location.” and stop polling.
- 404: show “Dispatch Board not available.” (misconfiguration) and stop polling.
- 409: if returned for concurrency/state (unlikely for GET), show retry suggestion.
- 5xx/network: show offline warning; keep cached data visible if available; continue polling with backoff (see safe defaults).

---

## 10. State Model & Transitions

### Allowed states (UI state)
- `loading-initial`
- `loaded-fresh`
- `loaded-stale`
- `error-initial` (no cached data)
- `offline-readonly` (cached data shown)

### Role-based transitions
- This story does not define roles/permissions beyond “Dispatcher/Shop Manager can view.” Any finer RBAC requires clarification and may be enforced by backend (401/403 handling above).

### UI behavior per state
- `loading-initial`: show skeleton/loading indicator; no stale banner.
- `loaded-fresh`: show board + last-updated timestamps.
- `loaded-stale`: show board + “data may be stale” banner.
- `offline-readonly`: show board + offline banner; disable actions that require fresh data.
- `error-initial`: show error state with retry; no board content.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing locationId: show inline validation and prevent requests.
- Invalid date format: reset to today and show inline message (or block until corrected; TBD by UI standard).

### Concurrency conflicts
- If poll response includes an `asOf` older than previously received (clock skew/out-of-order):
  - Keep the newer dataset; log a warning in console (no user disruption).

### Unauthorized access
- On 401: redirect to login (if app pattern), preserve intended route.
- On 403: show access denied, stop polling, allow location change if user might have access elsewhere.

### Empty states
- No work orders: show “No appointments/work orders for {date} at {location}.”
- No mechanics returned: show “No mechanics scheduled/available data for this date.” (do not block board display)

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Initial load defaults (today + location) and SLA
**Given** I am an authenticated Dispatcher with access to a location  
**When** I open the Dispatch Board screen  
**Then** the screen loads the board for today and my selected location  
**And** the dashboard renders work orders/appointments, mechanics availability, bay status, and exceptions  
**And** the initial load completes within the defined SLA targets (P50 < 1s, P95 < 2s, P99 < 3.5s) as measured in frontend instrumentation

### Scenario 2: Filter by location and date
**Given** I am viewing the Dispatch Board  
**When** I change the location filter to another location I have access to  
**Then** the dashboard reloads and displays only data for the newly selected location  
**When** I change the date filter to a different date  
**Then** the dashboard reloads and displays only data for that date

### Scenario 3: Auto-refresh polling
**Given** I am viewing the Dispatch Board  
**When** 30 seconds have elapsed since the last successful refresh  
**Then** the frontend requests updated board data  
**And** the displayed data updates to reflect changes

### Scenario 4: Manual refresh
**Given** I am viewing the Dispatch Board  
**When** I click “Refresh Now”  
**Then** the frontend immediately requests updated data  
**And** the displayed data updates upon success

### Scenario 5: Exceptions visible with severity
**Given** the backend returns exception indicators including both WARNING and BLOCKING severities  
**When** the Dispatch Board renders  
**Then** I can see exceptions without drilling into each work order  
**And** exceptions are clearly distinguishable by severity  
**And** items with BLOCKING exceptions are visually indicated as blocked

### Scenario 6: Stale data indicator
**Given** I am viewing the Dispatch Board with previously loaded data  
**And** the backend becomes unreachable  
**When** more than 2 minutes pass since the last successful fetch  
**Then** the UI indicates the displayed data is stale  
**And** if cached data exists, it remains visible in read-only mode

### Scenario 7: People availability fails but board loads
**Given** the Dispatch Board endpoint responds successfully  
**And** the People availability endpoint fails with a 5xx or network error  
**When** the dashboard loads  
**Then** the dashboard still shows work orders and bays  
**And** it shows a warning that mechanic availability is unavailable

---

## 13. Audit & Observability

### User-visible audit data
- Show “Last updated” (client time) and “As of” (server time if provided) for traceability.

### Status history
- Not required in this dashboard (reporting snapshot only). No work order transition history UI in scope.

### Traceability expectations
- Frontend telemetry should record:
  - `locationId`, `date` (non-PII identifiers)
  - request correlation id if returned by backend
  - fetch duration and success/failure
- Do **not** log personal data (full names) in client logs beyond what is needed for UI rendering.

---

## 14. Non-Functional UI Requirements

- **Performance:** Meet SLA as perceived in UI; avoid blocking rendering on secondary calls (People) if primary board call succeeds.
- **Accessibility:** Keyboard-operable filters; readable contrast for warning/blocking indicators; ARIA labels for refresh controls.
- **Responsiveness:** Must function on tablet and desktop dispatch stations.
- **i18n/timezone:** Dates/times must render in store/user timezone; backend timestamps are UTC (`asOf`); convert for display without altering underlying filter date. (Currency not applicable for this story.)

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit empty states for “no work orders” and “no mechanics data”; safe because it does not alter domain logic. (Impacted sections: UX Summary, Alternate/Empty states)
- SD-OBS-FE-PERF-METRICS: Add standard frontend timing instrumentation around API calls to verify SLA; safe because it is observability-only. (Impacted: Acceptance Criteria, Audit & Observability)
- SD-ERR-NETWORK-DEGRADE: If a secondary dependency (People availability) fails, degrade gracefully while still rendering primary board data; safe because it does not change source-of-truth data or decisions. (Impacted: Error Flows, Service Contracts)

---

## 16. Open Questions

1. **Backend API contract for Dispatch Board:** What is the exact request/response schema for `DispatchBoardView` and `ExceptionIndicator` (fields, IDs, and enumerations) and the exact endpoint path (is it truly `GET /dashboard/v1/today` and does it accept `date` and `locationId`)?  
2. **Aggregation responsibility:** Does the dispatch board endpoint already include mechanic availability and bay occupancy, or must the frontend always call `/people/v1/availability` separately and merge? (Impacts SLA and composition logic.)  
3. **“Appointments” vs “Work Orders”:** In the frontend story, “appointments” are mentioned. Are these represented as work orders with scheduled times, or a separate entity? If separate, what endpoint supplies them?  
4. **Routing/menu conventions:** What is the required Moqui screen path, menu name, and URL routing pattern for “Reporting” screens in `durion-moqui-frontend`?  
5. **RBAC expectations:** Which roles may view the Dispatch Board? Is it restricted by location membership only, or specific permission(s) (e.g., `DISPATCH_VIEW`)?  
6. **Exception definitions source:** Are exceptions computed entirely by backend (recommended), and does the frontend ever compute/derive exceptions like “overdue starts”? If yes, define the rules precisely; if no, confirm backend provides “overdue start” indicators.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Reporting: Daily Dispatch Board Dashboard  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/124  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Reporting: Daily Dispatch Board Dashboard

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want a dashboard showing today’s appointments, assignments, and exceptions so that I can manage the day efficiently.

## Details
- Show appointments by status, assigned mechanic, bay/mobile.
- Highlight overdue starts, conflicts, missing assignments.

## Acceptance Criteria
- Loads within SLA.
- Filters by location/date.
- Exceptions visible.

## Integrations
- Pulls status from workexec and availability/time signals from HR.

## Data / Entities
- DispatchBoardView, ExceptionIndicator

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