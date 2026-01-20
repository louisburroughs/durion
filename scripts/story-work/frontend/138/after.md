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
[FRONTEND] [STORY] Scheduling: View Daily Schedule by Location and Resource (with Conflict Flags)

### Primary Persona
Dispatcher

### Business Value
Enables dispatchers to view a single-day schedule organized by location and resource (bay/mobile/person where available) to manage capacity, identify overlaps, and prevent double-booking.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Dispatcher  
- **I want** to view a daily schedule for a selected location, grouped by resource (bays/mobile resources, and optionally people) with conflicts highlighted  
- **So that** I can manage workload, avoid resource collisions, and adjust assignments proactively.

### In Scope
- A daily schedule screen that:
  - Filters by **date**, **location**, **resource type**, and optional **resource**
  - Loads schedule data from a backend schedule view endpoint
  - Displays resources with their events for the day window
  - Highlights events flagged with conflicts and shows conflict details
  - Optionally requests an HR availability overlay and surfaces overlay status/warnings

### Out of Scope
- Editing schedule events (create/update appointments, blocks, etc.)
- Drag/drop rescheduling, assignment changes, or conflict resolution workflows
- Configuration of location hours, resource definitions, or approval/authorization policies
- Implementing the HR availability overlay provider (frontend only consumes status/data if present)

---

## 3. Actors & Stakeholders
- **Dispatcher (primary):** uses schedule view to manage capacity/conflicts
- **Service Advisor (secondary):** may reference schedule when coordinating customer work
- **Shop Manager (secondary):** may review schedule health and utilization
- **Scheduling backend service (system actor):** provides `ScheduleView` read model
- **Optional People/HR system (dependency):** provides availability overlay (soft dependency)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- User has authorization to view schedules for the chosen location (backend enforces; frontend must handle 403 gracefully).
- Backend endpoint is available: `GET /api/v1/schedules/view`.

### Dependencies
- Backend story reference: `durion-positivity-backend` issue #74 (schedule view API contract).
- Frontend must have an existing way to select a location (or must provide one on this screen).
- If `includeAvailabilityOverlay=true` is offered in UI, backend must support it; overlay may return `UNAVAILABLE` with warnings while still returning schedule.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Scheduling → Daily Schedule**
- Optional deep link (recommended): `/scheduling/daily?locationId=...&date=YYYY-MM-DD&resourceType=...&resourceId=...&overlay=0|1&range=...`

### Screens to create/modify
- **Create** Moqui screen (example name; final naming must match repo conventions):
  - `apps/pos/scheduling/DailySchedule.xml`
- **Optional**: add menu entry in scheduling navigation screen/menu config.

### Navigation context
- User remains in Scheduling context; changing filters updates the view without leaving the screen.
- Back navigation returns to Scheduling landing page (or prior screen).

### User workflows
**Happy path**
1. Dispatcher opens Daily Schedule.
2. Selects (or confirms) **Location** and **Date** (defaults allowed; see Applied Safe Defaults).
3. Optionally selects **Resource Type** (BAY/MOBILE_TECHNICIAN/PERSON if supported) and/or a specific resource.
4. Screen loads schedule view.
5. Dispatcher scans lanes/list; conflict events appear clearly flagged; selecting an event reveals conflict details.

**Alternate paths**
- Toggle “Include availability overlay” → reloads view; shows overlay status and any warnings.
- Change range (LOCATION_HOURS vs FULL_DAY) → reloads view.

---

## 6. Functional Behavior

### Triggers
- Screen load with required parameters present (at minimum: `locationId`, `date`).
- User changes any filter control:
  - date, location, resourceType, resourceId, includeAvailabilityOverlay, range

### UI actions
- **Load Schedule** action:
  - Disable/lock filter controls while request is in-flight (except “Cancel/Back”).
  - Show loading indicator in the schedule content region.
- **Select resource/event** action:
  - Viewing event details (read-only) including time window, title, type, and conflict list.
- **Conflict interaction**:
  - For `hasConflict=true`, show severity and list conflicting event IDs; allow user to expand/collapse conflict list.

### State changes (frontend state only)
- Persist current filters in:
  - URL query params (preferred for shareable deep links)
  - Local component state for immediate reactivity
- Schedule view data state:
  - `idle` → `loading` → `loaded` OR `error`

### Service interactions
- Call backend `GET /api/v1/schedules/view` with query params.
- No writes/updates in this story.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **locationId** required before fetching schedule.
- **date** required, format `YYYY-MM-DD` before fetching schedule.
- If resourceType is set, it must be one of backend-supported enums:
  - `BAY | MOBILE_TECHNICIAN | PERSON` (PERSON may not be supported; see Open Questions)
- range must be `LOCATION_HOURS | FULL_DAY`.

### Enable/disable rules
- “Load/Refresh” auto-triggers on filter change; while loading:
  - Prevent multiple concurrent loads (debounce or cancel prior request).
- Resource selector:
  - If `resourceType` not selected: resourceId filter control disabled.
  - If schedule view returns `resources[]`, resourceId selector options should be derived from those resources (stable).

### Visibility rules
- Show **warnings** banner if `warnings[]` present (e.g., `HR_SYSTEM_UNAVAILABLE`).
- Show overlay status indicator if field returned:
  - `availabilityOverlayStatus = AVAILABLE | UNAVAILABLE`
- If conflicts exist:
  - Visual marker for conflict and severity on events
  - Conflict details shown on event detail expansion

### Error messaging expectations
- `400`: “Invalid schedule filters. Please verify location/date.”
- `403`: “You don’t have access to this location’s schedule.”
- `404`: “Location or resource not found.”
- Network/timeouts: “Schedule service unavailable. Try again.”
- Overlay unavailable but schedule OK: non-blocking warning banner; schedule still renders.

---

## 8. Data Requirements

### Entities involved (read-only from frontend)
- `ScheduleView` (read model from backend)
- `ScheduleView.resources[]`
- `ScheduleEvent`
- `conflictDetails.conflictingEventIds`
- Optional overlay-related fields: `availabilityOverlayStatus`, `warnings`

### Fields (type, required, defaults)

**Request parameters (UI model)**
- `locationId` (string|number depending on backend; **required**)
- `date` (string `YYYY-MM-DD`, **required**)
- `resourceType` (enum string, optional)
- `resourceId` (string, optional)
- `includeAvailabilityOverlay` (boolean, optional; default false)
- `range` (enum string, optional; default `LOCATION_HOURS`)

**Response: ScheduleView**
- `locationId` (string) required for rendering header/context
- `date` (string) required
- `viewGeneratedAt` (UTC instant string) optional for display/debug
- `dayStartAt` (UTC instant string) required for day window rendering
- `dayEndAt` (UTC instant string) required
- `warnings` (string[]) optional
- `availabilityOverlayStatus` (string enum) optional
- `resources` (array) required (may be empty)

**Response: Resource**
- `resourceId` (string) required
- `resourceType` (string enum) required
- `resourceName` (string) required
- `events` (array) required (may be empty)
- (optional future) overlay fields per resource/person if returned

**Response: ScheduleEvent**
- `eventId` (string) required (stable id)
- `eventType` (string enum) required
- `subType` (string optional)
- `startTime` (UTC instant) required
- `endTime` (UTC instant) required
- `title` (string) required
- `hasConflict` (boolean) required
- `severity` (enum string; expected `BLOCKING|WARNING` when conflict) optional if `hasConflict=false`
- `conflictDetails.conflictingEventIds` (string[]) optional

### Read-only vs editable by state/role
- All fields in this story are **read-only**.
- Role impacts access only via backend authorization (403).

### Derived/calculated fields (frontend-only)
- `durationMinutes` derived from `startTime/endTime` for display.
- `isWithinDayWindow` derived by comparing event times to `dayStartAt/dayEndAt` (optional display hint only; no business decisions).
- Conflict badge severity label derived from `severity`.

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
**GET** `/api/v1/schedules/view`

**Query params**
- `locationId` (required)
- `date` (required, `YYYY-MM-DD`, interpreted by backend in location timezone)
- `resourceType` (optional)
- `resourceId` (optional)
- `includeAvailabilityOverlay` (optional boolean)
- `range` (optional: `LOCATION_HOURS|FULL_DAY`)

**Success (200)**
- Body matches `ScheduleView` JSON shape (see backend reference).

### Create/update calls
- none

### Submit/transition calls
- none

### Error handling expectations
- Map HTTP statuses:
  - 400 → show validation error banner; keep prior successful data visible if available
  - 403/404 → show blocking empty state
  - 5xx/timeouts → show retry option; do not clear prior successful data automatically
- If response is 200 with `warnings` including HR overlay issues:
  - treat as **non-blocking**; display warning banner but render schedule

---

## 10. State Model & Transitions

### Allowed states (screen-level)
- `idle` (no request yet; missing required filters)
- `loading` (in-flight request)
- `loaded` (rendering schedule)
- `error` (request failed; may still show last known good schedule)

### Role-based transitions
- Dispatcher can access screen; backend ultimately enforces.
- If user lacks permission for a location: transition to `error` with 403 messaging and disable schedule rendering.

### UI behavior per state
- `idle`: prompt user to select location and date.
- `loading`: show skeleton/loader; prevent duplicate fetches.
- `loaded`: show resources and events; allow filter adjustments.
- `error`: show error banner + retry; if last good data exists, keep it visible with “stale” indicator.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing location/date:
  - Do not call API; show inline prompt.
- Invalid enum selection (resourceType/range):
  - Prevent selection in UI by using fixed option lists; if backend returns 400 anyway, show error.

### Concurrency conflicts
- Not applicable (read-only). If user changes filters quickly:
  - Cancel prior request or ignore out-of-order responses (latest-wins).

### Unauthorized access
- On 403:
  - Render empty state with permission message
  - Keep filters visible so user can switch locations (if allowed)

### Empty states
- `resources[]` empty:
  - “No schedulable resources found for this location/date.”
- Resources exist but all `events[]` empty:
  - “No scheduled events in the selected window.”
- Conflicts none:
  - No special banner; conflict markers absent.

---

## 12. Acceptance Criteria

### Scenario 1: View daily schedule for a location
**Given** I am an authenticated Dispatcher  
**And** I have access to location `<locationId>`  
**When** I open the Daily Schedule screen for date `<date>` and location `<locationId>`  
**Then** the system requests `GET /api/v1/schedules/view?locationId=<locationId>&date=<date>`  
**And** I see a list of resources returned by the API  
**And** each resource shows its scheduled events within the returned `dayStartAt/dayEndAt` window.

### Scenario 2: Filter by resource type
**Given** I am viewing the daily schedule for a location and date  
**When** I select resourceType `BAY`  
**Then** the system reloads the schedule using `resourceType=BAY`  
**And** I only see resources of type `BAY` returned by the API.

### Scenario 3: Filter to a single resource
**Given** I am viewing the daily schedule for a location and date  
**And** the UI has a resource selector populated from the loaded resources  
**When** I select a specific resource `<resourceId>`  
**Then** the system reloads the schedule using `resourceId=<resourceId>`  
**And** I only see that one resource in the results.

### Scenario 4: Conflicts are clearly flagged with details
**Given** the API returns at least one event with `hasConflict=true` and `severity=BLOCKING`  
**When** I view the resource’s event list  
**Then** the conflicting event is visually flagged as a conflict  
**And** I can view the list of `conflictDetails.conflictingEventIds` for that event.

### Scenario 5: HR overlay failure is non-blocking
**Given** I enable “include availability overlay”  
**And** the API returns `200 OK` with `availabilityOverlayStatus=UNAVAILABLE` and `warnings` containing `HR_SYSTEM_UNAVAILABLE`  
**When** the schedule renders  
**Then** I see a non-blocking warning banner about HR availability  
**And** the schedule resources and events still render normally.

### Scenario 6: Unauthorized location access
**Given** I attempt to load a schedule for a location I am not authorized to view  
**When** the API responds with `403 Forbidden`  
**Then** the UI shows an access denied message  
**And** does not display schedule data for that location  
**And** I can change filters and retry with another location.

### Scenario 7: Performance expectation (frontend observable)
**Given** I load the schedule for a single location/day without overlay  
**When** the API responds successfully  
**Then** the UI renders the schedule view without unnecessary additional API calls  
**And** the UI records/prints the measured load time in client logs/telemetry (if enabled by project conventions).

---

## 13. Audit & Observability

### User-visible audit data
- Display (optional) `viewGeneratedAt` as “Last refreshed at …” for dispatcher confidence.

### Status history
- Not applicable (read-only schedule view). No state transitions persisted by frontend.

### Traceability expectations
- Frontend should include correlation/request ID headers if already standardized in the project.
- Log client-side errors with:
  - locationId, date, resourceType/resourceId, overlay flag, range
  - HTTP status / error type

---

## 14. Non-Functional UI Requirements

- **Performance:** Avoid N+1 frontend calls; only call schedule endpoint per filter change (debounced). Use request cancellation or latest-wins to prevent UI thrash.
- **Accessibility:** All filter controls keyboard-navigable; conflict indicators conveyed via text (not color-only). Screen reader labels for severity and warnings.
- **Responsiveness:** Must work on tablet widths used in shop environments; resource lanes/list collapses appropriately (implementation-defined).
- **i18n/timezone:** Display times in the **location’s timezone** if available in frontend context; otherwise display in user’s local timezone but clearly indicate timezone used. Do not change backend UTC handling.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATES: Provide explicit empty states for “no resources” and “no events”; qualifies as safe because it changes only presentation, not domain logic. Impacted sections: UX Summary, Alternate/Empty States, Acceptance Criteria.
- SD-UX-DEBOUNCE-LATEST-WINS: Debounce filter-triggered loads and ignore out-of-order responses; qualifies as safe because it prevents duplicate reads without altering business rules. Impacted sections: Functional Behavior, Error Flows, Non-Functional.
- SD-ERR-HTTP-STATUS-MAP: Standard mapping of 400/403/404/5xx to user messages; qualifies as safe because it follows backend contract and affects only UI messaging. Impacted sections: Business Rules, Service Contracts, Error Flows.

---

## 16. Open Questions
1. Are **PERSON** resources supported in the frontend for v1, or should the UI only offer `BAY` and `MOBILE_TECHNICIAN` until confirmed by backend capabilities?
2. What is the canonical **location selector** source in this frontend (existing screen/component/service), and what identifier type is used for `locationId` (string vs numeric)?
3. Does the backend return (or does frontend already know) the **location timezone** needed to display times correctly? If not, should UI display UTC or user-local time with a timezone label?
4. Are there established Moqui screen naming/routing conventions for Scheduling modules in this repo (e.g., base path `/scheduling/*`), or should this story define the route?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Scheduling: View Schedule by Location and Resource — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/138

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Scheduling: View Schedule by Location and Resource  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/138  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Scheduling: View Schedule by Location and Resource

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **Dispatcher**, I want a daily schedule view by location/bay/mobile so that I can manage capacity and avoid conflicts.

## Details  
- Views: location calendar, bay lanes, mobile list.  
- Conflict highlighting and filters.

## Acceptance Criteria  
- Filter by date/location/resource.  
- Conflicts flagged.  
- Loads within SLA.

## Integrations  
- Optional HR availability overlay.

## Data / Entities  
- ScheduleView(read model), ConflictFlag

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

END BACKEND REFERENCES