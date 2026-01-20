## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:people
- status:draft

### Recommended
- agent:people-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Integration: Attendance vs Job Time Discrepancy Report

## Primary Persona
Manager

## Business Value
Enable managers to identify attendance vs productive job-time discrepancies by technician/day/location, so they can investigate gaps, overhead, and anomalies using a consistent threshold policy.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Manager  
- **I want** a report that compares technicians’ total attendance time (clocked time) to total job time (approved productive labor) by day and location  
- **So that** I can quickly find and investigate significant discrepancies.

## In-scope
- A Moqui + Vue/Quasar screen to run and view the report.
- Filters: startDate, endDate, timezone, optional locationId, optional technicianIds, flaggedOnly.
- Tabular results at granularity: technician + location + local day.
- Exception flagging based on threshold returned by People report response (`thresholdApplied`).
- Empty/error states and proper handling of upstream failures (People domain failing due to WorkExec non-2xx).

## Out-of-scope
- Configuring threshold policies (TimekeepingPolicy CRUD).
- Implementing backend report logic or WorkExec integration.
- Row drill-down into underlying time entries, job labor lines, or reconciliation details (optional future enhancement).
- Export/CSV unless already a project standard (not specified).

---

# 3. Actors & Stakeholders
- **Manager (primary user):** runs report for permitted locations/technicians; investigates flagged rows.
- **Technician (subject):** appears in results; no direct interaction.
- **People domain service (backend SoR for attendance + threshold policy):** serves report endpoint.
- **Work Execution domain service (backend SoR for job time totals):** indirectly affects report availability; failures bubble up.

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated.
- User has authorization to view report for selected scope (location/technicians); backend enforces scope and may return 403.

## Dependencies
- People domain endpoint exists and is reachable:
  - `GET /api/people/reports/attendance-jobtime-discrepancy`
- Backend returns rows with:
  - `technicianId, technicianName, locationId, reportDate, totalAttendanceHours, totalJobHours, discrepancyHours, isFlagged, thresholdApplied`
- Backend error taxonomy may include upstream WorkExec failures mapped to non-2xx; frontend must surface clearly.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Main navigation: Reports → Timekeeping / People Reports → **Attendance vs Job Time Discrepancy** (exact menu placement may follow existing project nav patterns).

## Screens to create/modify
- **New screen**: `apps/pos/screens/reports/attendanceJobtimeDiscrepancy.xml` (name/path indicative; align to repo conventions).
  - Includes a filter form and results section.
- **Optional**: Add route/menu entry screen in reports index (only if project uses a reports landing page).

## Navigation context
- URL route should be stable and bookmarkable.
- Query parameters should reflect current filter state to support shareable links (if consistent with project patterns).

## User workflows

### Happy path
1. Manager opens the report screen.
2. Selects `startDate`, `endDate`, and `timezone`.
3. Optionally selects `locationId`, `technicianIds`, and toggles `flaggedOnly`.
4. Clicks **Run Report**.
5. Sees a table of results with attendance hours, job hours, discrepancy hours, threshold applied, and flagged indicator.

### Alternate paths
- Manager toggles **Flagged only** to focus on exceptions.
- Manager narrows to a single location or a subset of technicians.
- Manager runs with a range that returns no rows → sees empty state guidance.

---

# 6. Functional Behavior

## Triggers
- Screen load:
  - Initialize filter defaults (see Applied Safe Defaults).
- User clicks **Run Report** (primary trigger).
- User changes filters and re-runs.

## UI actions
- Validate required fields prior to requesting report.
- Call backend endpoint with query params.
- Render results in a table; allow basic client-side sorting by date/technician/location and flagged (safe UI ergonomics).
- Provide a clear visual indicator for `isFlagged=true`.

## State changes (frontend)
- `idle` → `loading` → `loaded` or `error`
- Preserve last successful results when a subsequent run errors (so the user can still view prior output), but show error banner.

## Service interactions
- Single request per run to People report endpoint.
- Do not call WorkExec from frontend (backend handles integration).

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Require:
  - `startDate` (YYYY-MM-DD)
  - `endDate` (YYYY-MM-DD)
  - `timezone` (IANA TZ string)
- Validate:
  - `startDate <= endDate`; otherwise block submit with inline error.
- `technicianIds` is optional list; if UI uses multi-select, send as repeated query param or comma-separated based on backend expectation (**Open Question**; see below).

## Enable/disable rules
- Disable **Run Report** while loading.
- Disable technician/location pickers only if required by UX constraints; otherwise keep enabled.

## Visibility rules
- Results section visible only after first run (or show an initial “Run report to see results” state).
- Show `thresholdApplied` per row (minutes) alongside discrepancy (hours) to aid interpretation.

## Error messaging expectations
- 400: show “Invalid request” + field-level hints if provided.
- 403: show “Not authorized for selected scope.”
- 503/500: show “Report unavailable right now. Try again.”
- If backend surfaces WorkExec error codes (e.g., `WORKEXEC_UNAVAILABLE`), show a specific message: “Job time system unavailable; report cannot be generated.”

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- No direct entity CRUD. Read-only report view.
- Data originates from People domain report aggregation.

## Fields (type, required, defaults)

### Request (query params)
- `startDate` (string `YYYY-MM-DD`, required)
- `endDate` (string `YYYY-MM-DD`, required, inclusive)
- `timezone` (string IANA TZ, required)
- `locationId` (string/int depending on system, optional)
- `technicianIds` (list<UUID>, optional)
- `flaggedOnly` (boolean, optional, default false)

### Response row (read-only)
- `technicianId` (UUID/string, required)
- `technicianName` (string, required)
- `locationId` (string/int, required)
- `reportDate` (string `YYYY-MM-DD`, required)
- `totalAttendanceHours` (number decimal, required)
- `totalJobHours` (number decimal, required)
- `discrepancyHours` (number decimal, required; can be negative)
- `isFlagged` (boolean, required)
- `thresholdApplied` (int minutes, required)

## Derived/calculated fields (frontend)
- None required; display values as provided.
- Optional UI-only derived formatting:
  - Display discrepancy with sign (e.g., `-1.50`).
  - Optional “Flag reason” tooltip: `abs(discrepancyHours)*60 > thresholdApplied` (do not recompute flag; rely on backend `isFlagged`).

---

# 9. Service Contracts (Frontend Perspective)

## Load/view calls
- `GET /api/people/reports/attendance-jobtime-discrepancy`

### Query serialization
- Must send:
  - `startDate`, `endDate`, `timezone`
- May send:
  - `locationId`
  - `technicianIds`
  - `flaggedOnly`

**Note:** precise encoding for `technicianIds` must match backend implementation (see Open Questions).

## Create/update calls
- None.

## Submit/transition calls
- None.

## Error handling expectations
- Treat any non-2xx as failure for the run:
  - Show error banner/message.
  - Keep prior results (if any).
- If backend returns structured error payload:
  - Display human-friendly message and include correlationId/requestId when present (for support).

---

# 10. State Model & Transitions

## Allowed states (frontend view state)
- `idle`: screen loaded, no results yet.
- `loading`: request in flight.
- `loaded`: results rendered (may be empty).
- `error`: last request failed.

## Role-based transitions
- If user lacks permission, backend returns 403; frontend transitions to `error` with authorization messaging.
- No frontend-side role inference or permission matrix (do not hide entry purely client-side unless project already provides an auth capability map).

## UI behavior per state
- `idle`: show filter form + helper text; results hidden or empty placeholder.
- `loading`: show spinner/progress; disable Run button.
- `loaded`: show table; show “No results” empty state if zero rows.
- `error`: show error banner; allow user to adjust filters and retry.

---

# 11. Alternate / Error Flows

## Validation failures (client-side)
- Missing required fields: show inline validation, do not call API.
- startDate > endDate: inline error on date range.

## Backend validation failures (400)
- Show error banner; if response indicates which param invalid, attach hint near field.

## Concurrency conflicts
- Not applicable (read-only).

## Unauthorized access (403)
- Show explicit message that scope is forbidden; do not silently drop selected scope values.

## Empty states
- No rows returned:
  - Show “No discrepancies found for the selected period/scope.”
  - If `flaggedOnly=true`, message should mention that only flagged rows are included.

## Upstream dependency failure (WorkExec issues surfaced by People endpoint)
- If response contains recognizable code (e.g., `WORKEXEC_UNAVAILABLE`):
  - Show “Job time system unavailable; report cannot be generated. Try again later.”
- Otherwise generic 5xx/503 handling.

---

# 12. Acceptance Criteria

## Scenario 1: Run report successfully with required filters
**Given** I am an authenticated Manager  
**And** I can access the Attendance vs Job Time Discrepancy report screen  
**When** I enter a valid `startDate`, `endDate`, and `timezone`  
**And** I click Run Report  
**Then** the UI sends a GET request to `/api/people/reports/attendance-jobtime-discrepancy` with those parameters  
**And** I see a results table grouped by technician, location, and day (one row per combination) showing attendance hours, job hours, discrepancy hours, and flagged status.

## Scenario 2: Flagged rows are clearly indicated
**Given** the report response includes a row with `isFlagged=true`  
**When** the results are displayed  
**Then** the UI clearly marks that row as flagged  
**And** the row displays `thresholdApplied` so I can understand the applied threshold.

## Scenario 3: Filter to flaggedOnly
**Given** I have selected valid dates and timezone  
**When** I set `flaggedOnly=true` and run the report  
**Then** the UI includes `flaggedOnly=true` in the request  
**And** the results show only rows returned by the server  
**And** if zero rows are returned, I see an empty-state message indicating no flagged discrepancies for the selected scope.

## Scenario 4: Client-side validation prevents invalid date ranges
**Given** I am on the report screen  
**When** I set `startDate` later than `endDate`  
**And** I click Run Report  
**Then** the UI prevents the request from being sent  
**And** I see an inline error indicating the start date must be on or before the end date.

## Scenario 5: Unauthorized scope shows explicit error
**Given** I am authenticated but not authorized for a selected location or technician scope  
**When** I run the report for that forbidden scope  
**And** the backend responds with 403  
**Then** the UI shows an authorization error message  
**And** the UI does not silently remove or alter my selected filters.

## Scenario 6: Backend unavailable or WorkExec-related failure bubbles to UI
**Given** I am on the report screen with valid filters  
**When** I run the report  
**And** the backend responds with 503 or an error indicating job-time dependency is unavailable  
**Then** the UI shows a non-technical error message that the report is unavailable  
**And** I can retry after the error without reloading the page.

---

# 13. Audit & Observability

## User-visible audit data
- Not required to display audit logs in UI for v1.
- If backend returns a `requestId`/`correlationId`, show it in an expandable “Details” area of the error banner to assist support.

## Status history
- Not applicable for read-only report.

## Traceability expectations
- Frontend should log (client-side) report runs at INFO level (if project has client logging):
  - timestamp, user id (if available client-side), startDate/endDate/timezone, locationId presence, technicianIds count, flaggedOnly
  - Do **not** log technician names or any PII beyond IDs if avoidable.

---

# 14. Non-Functional UI Requirements

## Performance
- Report should render up to typical manager-scale volumes (assume hundreds of rows) without UI freezing.
- Use virtual scroll for table if project standard; otherwise paginate (safe default).

## Accessibility
- All form controls labeled.
- Table supports keyboard navigation and screen-reader friendly headers.
- Error messages announced (ARIA live region) if using Quasar notifications/banners.

## Responsiveness
- Filters usable on tablet-sized screens.
- Table should allow horizontal scrolling on small screens.

## i18n/timezone/currency
- Timezone is explicitly selectable/required; must accept IANA TZ.
- Hours displayed as decimal with 2 decimals as provided by backend; no currency.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a clear “no results” state after a successful run with zero rows; safe because it’s purely presentational and does not change domain behavior. Impacted sections: UX Summary, Alternate / Error Flows, Acceptance Criteria.
- SD-UX-PAGINATION: If result set is large, default to paginated table (or virtual scroll if standard) to prevent rendering slowness; safe because it is UI ergonomics only. Impacted sections: Non-Functional UI Requirements, UX Summary.

---

# 16. Open Questions
1. What is the expected query parameter encoding for `technicianIds` on `/api/people/reports/attendance-jobtime-discrepancy` (repeated params vs comma-separated vs JSON array string)?
2. Should the report screen enforce a maximum date range (e.g., 31/90 days) to prevent heavy queries, or is this strictly server-controlled?
3. Do we have an existing standardized timezone picker/list in this frontend (and default timezone behavior), or must this screen introduce one?
4. Is there an established reports navigation location/route convention in `durion-moqui-frontend` (exact screen path and menu link), or should we add it under a generic Reports section?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Integration: Attendance vs Job Time Discrepancy Report — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/144


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Integration: Attendance vs Job Time Discrepancy Report
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/144
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Attendance vs Job Time Discrepancy Report

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Manager**, I want **a report comparing attendance time to job time** so that **I can identify gaps, overhead, and anomalies**.

## Details
- Summarize by technician/day/location.
- Flag differences above a configurable threshold.

## Acceptance Criteria
- Report shows clocked hours vs job timer total.
- Flags exceptions.

## Integration Points (workexec)
- Optional: correlate with labor lines for reconciliation.

## Data / Entities
- TimeEntry
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