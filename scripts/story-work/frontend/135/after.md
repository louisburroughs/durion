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
- risk:external-dependency

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

**Title**  
[FRONTEND] [STORY] Dispatch: Determine Mechanic Availability for a Time Window

**Primary Persona**  
Dispatcher

**Business Value**  
Enables dispatchers to assign work without double-booking by showing mechanic availability for a requested time window with explainable conflict reason codes.

---

## 2. Story Intent

**As a** Dispatcher  
**I want** to query and view mechanic availability for a specific time window (optionally filtered to selected mechanics)  
**So that** I can schedule/assign work confidently and understand why a mechanic is unavailable.

### In-scope
- UI to input a time window and location, optionally select mechanic(s)
- Calling an availability API and presenting results per mechanic
- Displaying overall availability status and conflict blocks with standardized reason codes
- Handling API failures (including upstream HR dependency failures) and invalid input validation

### Out-of-scope
- Creating/editing shifts, PTO, assignments, or travel blocks
- Persisting new travel blocks from the frontend
- Implementing the backend aggregation logic (assumed provided by backend)
- Notification/event subscription UI (e.g., consuming AvailabilityChanged events)

---

## 3. Actors & Stakeholders

- **Dispatcher (primary user):** performs availability checks to plan assignments
- **Service Advisor / Shop Manager (stakeholders):** rely on accurate scheduling outcomes
- **Mechanic (subject):** schedule is queried; no direct interaction in this story
- **External HR system (dependency):** provides shift/PTO data (surfaced via backend)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- User has permission to access dispatch/scheduling functions and view mechanic availability for a location.

### Dependencies
- Backend availability endpoint exists and is reachable from Moqui frontend.
- Backend returns reason codes and timestamps in UTC per business rules.
- Mechanic directory/listing capability for selection (either from existing UI state or an API).

**Blocking dependency (needs clarification):** definitive backend endpoint path(s) and payload schema for availability query/result.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Dispatch/Scheduling area: “Check Mechanic Availability”
- From a work assignment flow: “Check availability” action before assigning

### Screens to create/modify (Moqui)
- **New screen**: `apps/pos/screen/dispatch/AvailabilityCheck.xml` (name indicative)
  - Purpose: capture query inputs + show results list
- **Optional embedded section**: an includeable screenlet for reuse inside assignment flows (if project conventions support)

### Navigation context
- Route under a dispatch namespace, e.g. `/dispatch/availability` (exact path must match repo conventions; confirm during implementation)
- Breadcrumb: Dispatch → Availability

### User workflows
**Happy path**
1. Dispatcher opens Availability Check screen.
2. Enters:
   - Location
   - Start time (UTC or local w/ conversion—see Open Questions)
   - End time
   - Optional mechanic filter (multi-select)
3. Clicks “Search/Check”.
4. UI shows list of mechanics with:
   - OverallStatus: AVAILABLE / UNAVAILABLE / PARTIALLY_AVAILABLE
   - Conflicts timeline list with reasonCode and start/end

**Alternate paths**
- No mechanics returned: show empty state “No mechanics found for this location/filter.”
- Mechanic has no conflicts: show AVAILABLE and “No conflicts in window”.
- Partial availability: show conflict blocks and the remaining free time implied (display-only; do not compute scheduling suggestions beyond showing blocks).

---

## 6. Functional Behavior

### Triggers
- User action: “Check Availability” button submits query.
- Optional: auto-run when arriving with prefilled window/location from another flow (only if navigation provides params; otherwise out of scope).

### UI actions
- Validate inputs client-side before calling backend:
  - startTime required
  - endTime required
  - locationId required
  - startTime < endTime
- Submit query to backend service (see Service Contracts).
- Render loading state; disable submit while in-flight.
- Render results:
  - Sort mechanics deterministically (default: by name if available; else by mechanicId)
  - For each mechanic show overallStatus and conflict blocks sorted by startTime ascending
- Provide “Retry” on error.

### State changes (frontend)
- Local UI state: query form model, loading flag, results model, error banner model.
- No persistent state changes required.

### Service interactions
- Call availability query endpoint.
- Optional call to load mechanics list for selection if not already available.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Time window validity:** if startTime ≥ endTime, block submit and show inline error: “Start time must be before end time.”
- **Required location:** block submit and show inline error: “Location is required.”

### Enable/disable rules
- Disable submit while loading.
- Disable mechanic selector until location is chosen (to avoid cross-location confusion), unless mechanics are global.

### Visibility rules
- Show conflicts section only when overallStatus ≠ AVAILABLE OR conflicts array non-empty.
- Show external dependency error messaging distinctly when backend indicates HR dependency failure (503).

### Error messaging expectations
- 400: show validation message from backend if provided; otherwise “Invalid request. Please review the time window and try again.”
- 401/403: “You don’t have access to view mechanic availability.”
- 404: “Availability service not found” (should not happen in prod; still handle).
- 409: “Availability changed; please retry.” (only if backend uses concurrency semantics; otherwise generic)
- 503: “Availability is temporarily unavailable because a required scheduling system is down. Please try again later.”

---

## 8. Data Requirements

### Entities involved (frontend models)
- `AvailabilityQuery`
- `AvailabilityResult`

### Fields

**AvailabilityQuery (request)**
- `startTime` (datetime, required, UTC ISO-8601 string)
- `endTime` (datetime, required, UTC ISO-8601 string)
- `locationId` (id/uuid, required)
- `mechanicIds` (array<id/uuid>, optional)

**AvailabilityResult (response)**
- `queryWindow.startTime` (datetime UTC)
- `queryWindow.endTime` (datetime UTC)
- `mechanicAvailabilities[]`
  - `mechanicId` (id/uuid)
  - `overallStatus` enum: `AVAILABLE` | `UNAVAILABLE` | `PARTIALLY_AVAILABLE`
  - `conflicts[]`
    - `startTime` (datetime UTC)
    - `endTime` (datetime UTC)
    - `reasonCode` enum: `ON_PTO` | `OFF_SHIFT` | `ASSIGNED_WORK_ORDER` | `TRAVEL_BLOCK`

### Read-only vs editable
- All returned availability data is read-only.

### Derived/calculated fields (frontend)
- None required beyond formatting timestamps for display.
- Do not derive availability beyond what backend returns (avoid policy guessing).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is currently not fully specified for the Moqui frontend; below is the required contract shape for implementation. Exact paths must be confirmed (Open Questions).

### Load/view calls
1. **(Optional) Load mechanics list for location**
   - `GET /api/mechanics?locationId=...` (placeholder; must be confirmed)
   - Response: list with `{ mechanicId, displayName }`

### Create/update calls
- None.

### Submit/transition calls
1. **Availability query**
   - Preferred: `POST /api/v1/availability/query` (placeholder) or `POST /api/workexec/availability` (placeholder)
   - Request body: `AvailabilityQuery`
   - Response 200: `AvailabilityResult`
   - Error codes:
     - 400 invalid window/inputs
     - 401/403 unauthorized
     - 503 upstream HR unavailable (distinct)

### Error handling expectations (Moqui/Vue)
- Map non-2xx to a user-visible banner + retain form values for retry.
- Preserve correlation/request id if backend returns it in headers (log it; display only if project convention allows).

---

## 10. State Model & Transitions

### Applicable state model
- This is a **query-only** UI; no workexec entity state transitions occur in this frontend story.

### UI behavior per availability status (per mechanic)
- `AVAILABLE`: show “Available” and no conflicts (or “No conflicts”)
- `UNAVAILABLE`: show “Unavailable” and list all conflicts in window
- `PARTIALLY_AVAILABLE`: show “Partially available” and list conflicts in window

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing location/start/end: prevent submit; highlight fields.
- startTime ≥ endTime: prevent submit.

### Backend validation failure (400)
- Show inline banner; if backend provides field errors, map them to fields (if schema provided; otherwise generic banner).

### Concurrency conflicts
- If backend returns 409 (e.g., recalculated schedule), show “Availability changed; please retry.”

### Unauthorized access
- 401: redirect to login (per app convention) or show session expired.
- 403: show access denied page/banner.

### Empty states
- No mechanics returned: show empty results state with suggestion to adjust filters.
- Mechanics returned but conflicts empty: show as available.

### External dependency failure
- 503: show dependency-down message; keep last successful results visible but visually marked as “stale” **only if** project already uses stale patterns (otherwise do not assume; show error and clear results).

---

## 12. Acceptance Criteria

### Scenario 1: Available mechanic, no conflicts
**Given** I am an authenticated Dispatcher  
**And** I select a location and enter a valid start and end time window  
**When** I submit the availability check  
**And** the API returns a mechanic with `overallStatus` = `AVAILABLE` and `conflicts` = `[]`  
**Then** the UI shows that mechanic as Available  
**And** the UI shows no conflict blocks for that mechanic

### Scenario 2: Unavailable due to PTO
**Given** I submit a valid availability check  
**When** the API returns a mechanic with `overallStatus` = `UNAVAILABLE`  
**And** at least one conflict with `reasonCode` = `ON_PTO` and a start/end time  
**Then** the UI shows that mechanic as Unavailable  
**And** the UI lists the PTO conflict with its time range and reason code

### Scenario 3: Partially available due to assignment
**Given** I submit a valid availability check  
**When** the API returns a mechanic with `overallStatus` = `PARTIALLY_AVAILABLE`  
**And** a conflict block with `reasonCode` = `ASSIGNED_WORK_ORDER`  
**Then** the UI shows that mechanic as Partially available  
**And** the UI lists the assignment conflict block in chronological order

### Scenario 4: Invalid time window blocked client-side
**Given** I am on the Availability Check screen  
**When** I enter a start time that is after or equal to the end time  
**Then** the Check Availability action is blocked  
**And** I see an inline validation error stating start time must be before end time  
**And** no API request is sent

### Scenario 5: Upstream HR dependency down
**Given** I submit a valid availability check  
**When** the API responds with HTTP 503 indicating an upstream scheduling dependency is unavailable  
**Then** the UI shows an error banner explaining availability cannot be determined right now  
**And** the UI provides a Retry action  
**And** the form inputs remain populated

### Scenario 6: Permission denied
**Given** I submit a valid availability check  
**When** the API responds with HTTP 403  
**Then** the UI shows an access denied message  
**And** results are not displayed

---

## 13. Audit & Observability

### User-visible audit data
- Not applicable (query UI).

### Status history
- Not applicable.

### Traceability expectations
- Frontend logs (console/log pipeline per project conventions) should include:
  - query window (start/end) and locationId
  - count of mechanics returned
  - request/correlation id if provided by backend
- Do not log PII beyond mechanicId/locationId.

---

## 14. Non-Functional UI Requirements

- **Performance:** Results view should render smoothly for typical location mechanic counts (define threshold if known; otherwise keep implementation efficient—virtual list optional but not required without data).
- **Accessibility:** All form controls labeled; errors announced to screen readers; keyboard navigable results list.
- **Responsiveness:** Works on tablet widths used in shop environments.
- **i18n/timezone:** Display times in shop local time if app standard; keep request/response in UTC. (Blocking until clarified if app standard is unknown.)

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE**
  - **Assumed:** Provide a standard empty state when no mechanics/results are returned.
  - **Why safe:** Pure UI ergonomics; does not alter domain policy or calculations.
  - **Impacted sections:** UX Summary, Alternate / Error Flows, Acceptance Criteria
- **SD-UX-LOADING-DISABLE-SUBMIT**
  - **Assumed:** Disable submit button while request is in-flight and show loading indicator.
  - **Why safe:** Prevents duplicate submissions; no business semantics changed.
  - **Impacted sections:** Functional Behavior, Business Rules
- **SD-ERR-STD-HTTP-MAPPING**
  - **Assumed:** Map common HTTP errors (400/401/403/503) to user-friendly banners while preserving inputs.
  - **Why safe:** Standard error handling; does not invent authorization or workflow rules.
  - **Impacted sections:** Business Rules, Service Contracts, Alternate / Error Flows

---

## 16. Open Questions

1. What is the **exact backend endpoint** for mechanic availability query for the Moqui frontend (path, method, auth), and does it match the backend story’s `/api/v1/...` concept or something else?
2. What is the **authoritative request/response JSON schema** (including field names, ID types uuid vs long, and error payload shape) for `AvailabilityQuery` and `AvailabilityResult`?
3. How should the frontend handle **timezone presentation**:
   - Input times entered in shop-local timezone then converted to UTC for API?
   - Or require UTC input (unlikely for users)?
4. Is there an existing **mechanic directory/list API** or store in the frontend to populate the optional mechanic filter? If yes, what is the contract?
5. For `reasonCode` values, is the enumerated list strictly the four codes shown (`ON_PTO`, `OFF_SHIFT`, `ASSIGNED_WORK_ORDER`, `TRAVEL_BLOCK`), or can additional codes appear? If additional codes can appear, what should the UI do (e.g., show “Other” + raw code)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Dispatch: Determine Mechanic Availability for a Time Window  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/135  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Determine Mechanic Availability for a Time Window

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to see which mechanics are available for a time window so that I can assign work without double-booking.

## Details
- Availability includes shifts/PTO (HR), existing assignments, mobile travel blocks.
- Explainability via reason codes.

## Acceptance Criteria
- API returns availability + reasons.
- Conflicts detected.

## Integrations
- Shopmgr queries HR availability endpoint or consumes AvailabilityChanged events.

## Data / Entities
- AvailabilityQuery, AvailabilityResult

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