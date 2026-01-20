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

### Title
[FRONTEND] [STORY] Timekeeping: Mechanic Clock In/Out (Moqui Screen + Service Wiring)

### Primary Persona
Mechanic (authenticated POS user)

### Business Value
Accurate, auditable attendance time tracking by enabling a mechanic to clock in/out with server-authoritative timestamps and clear prevention of duplicate/open entries.

---

## 2. Story Intent

### As a / I want / So that
**As a** Mechanic,  
**I want to** clock in and clock out with a single action,  
**so that** my attendance time is recorded accurately and can be audited.

### In-scope
- A dedicated Moqui screen flow for a mechanic to:
  - View current clock status (clocked in vs clocked out)
  - Perform **Clock In** or **Clock Out** (one tap)
- Frontend enforcement + backend error handling for:
  - Preventing double clock-in without clock-out
  - Preventing clock-out when not clocked in
- Display of last/open TimeEntry context after load and after actions (timestamps + timezone)
- Audit-oriented UI exposure (read-only): show created/updated timestamps and who performed action when returned by backend
- Optional “shift context display” (if endpoint exists) as non-blocking enhancement (no new business logic)

### Out-of-scope
- Payroll calculations, wage rules, overtime policies
- Manager corrections/edits to TimeEntry records
- Defining/implementing authorization policy and role model (frontend will respect backend 401/403)
- Creating or changing domain state machines beyond the timekeeping open/closed entry behavior
- Notification workflows

---

## 3. Actors & Stakeholders
- **Mechanic (Primary Actor):** clocks in/out.
- **Shop Manager (Stakeholder):** relies on accurate attendance records; may review exceptions later.
- **Payroll/HR (Downstream):** consumes time entries (not implemented here).
- **System (Moqui UI):** orchestrates calls, displays status and errors.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS frontend.
- User has an identity mapping usable by backend as “mechanic/employee” (e.g., `userId`/`partyId` equivalent).
- Backend provides timekeeping endpoints (see Service Contracts) with server-generated UTC timestamps.

### Dependencies
- Backend story reference indicates open clarification around **location validation policy**. This frontend story is blocked until policy is decided because it affects UI behavior, error messaging, and possibly required inputs.
- A “current location/shop context” must be available in frontend session/context OR user must select a location (unclear; see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Primary: POS navigation item **Timekeeping → Clock In/Out**
- Secondary (optional): from Mechanic home/dashboard card “Clock status”

### Screens to create/modify
- **New Screen:** `apps/pos/screen/timekeeping/ClockInOut.xml` (name illustrative; follow repo conventions)
  - Uses a single view with:
    - Status panel: “Clocked In” / “Clocked Out”
    - Last clock-in time (if open entry)
    - Last clock-out time (if last entry closed)
    - Location display (current or selected)
    - Primary action button toggles between Clock In / Clock Out
- **Optional embedded component screenlet** for dashboard usage, if project uses screenlets.

### Navigation context
- Screen requires access to:
  - Authenticated user identity
  - Current shop/location context (or selection control if not implicit)

### User workflows
**Happy path – Clock In**
1. Mechanic opens Timekeeping screen.
2. Screen loads current status and shows “Clock In”.
3. Mechanic taps “Clock In”.
4. UI disables button, shows in-progress indicator.
5. On success, UI shows “Clocked In” and displays clock-in timestamp + timezone.

**Happy path – Clock Out**
1. Mechanic opens Timekeeping screen while clocked in.
2. Screen shows “Clock Out”.
3. Mechanic taps “Clock Out”.
4. On success, UI shows “Clocked Out” and displays clock-out timestamp + timezone.

**Alternate path – Location selection (only if required)**
- Mechanic selects a location before clocking in/out; selection persists for the session (frontend-only persistence unless backend requires otherwise).

---

## 6. Functional Behavior

### Triggers
- Screen load → fetch current timekeeping status for the authenticated mechanic.
- Button press:
  - If status is “clocked out” → call Clock In
  - If status is “clocked in” → call Clock Out

### UI actions
- Show a single primary action button whose label and action is determined by loaded status.
- Disable action button during network request; prevent double-submission.
- On success:
  - Refresh status by using response payload OR reloading status endpoint (preferred for consistency if backend response is minimal).
- On failure:
  - Show inline error banner/toast with actionable message.
  - Maintain prior known status (do not optimistically flip state).

### State changes (frontend-local)
- `viewState.status`: `UNKNOWN | CLOCKED_OUT | CLOCKED_IN`
- `viewState.pendingAction`: `NONE | CLOCK_IN | CLOCK_OUT`
- `viewState.lastEntry`: last known TimeEntry (open or last closed)

### Service interactions
- `GET current status` on screen load (and after successful actions)
- `POST clock-in` and `POST clock-out` on action

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- UI MUST NOT allow Clock In when backend-reported status indicates an open entry (clocked in).
- UI MUST NOT allow Clock Out when backend-reported status indicates no open entry (clocked out).
- If backend rejects due to race/concurrency, UI must display backend message and reload status.

### Enable/disable rules
- Primary action button enabled only when:
  - Status is known (`CLOCKED_IN` or `CLOCKED_OUT`)
  - No request is pending
  - Required location input (if any) is satisfied

### Visibility rules
- If status unknown due to load failure:
  - Show retry action and disable clock action button.
- Show location context always (read-only if implicit; editable if selection is required).

### Error messaging expectations
Map these cases to user-facing messages (verbatim unless backend provides a better message):
- Double clock-in attempt: “You are already clocked in. You must clock out before clocking in again.”
- Clock-out without clock-in: “You are not currently clocked in.”
- Not authorized for location (if enforced): “Clock-in failed: You are not authorized for this location.”
- Generic system error: “Unable to record time right now. Please try again or contact a manager.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **TimeEntry** (attendance record)

### Fields (type, required, defaults)
Because backend contract is not fully specified for frontend payloads, frontend requires at minimum the following data shape for display and validation:

**TimeEntry (response)**
- `timeEntryId` (string/uuid, required)
- `mechanicId` (string/uuid, required)
- `locationId` (string/uuid, required)
- `clockInTimestampUtc` (ISO-8601 string, required when clocked in/out)
- `clockInTimezone` (IANA string, required when clocked in/out)
- `clockOutTimestampUtc` (ISO-8601 string, nullable)
- `clockOutTimezone` (IANA string, nullable)
- `createdAtUtc` (ISO-8601 string, optional but preferred)
- `updatedAtUtc` (ISO-8601 string, optional but preferred)

**TimekeepingStatus (response)**
- `status` enum: `CLOCKED_IN | CLOCKED_OUT`
- `openTimeEntry` (TimeEntry | null)
- `lastClosedTimeEntry` (TimeEntry | null) (optional but useful)

**Clock action request (frontend → backend)**
- `locationId` (required **if** backend requires explicit location; unclear)
- No timestamps provided by frontend (server-authoritative UTC is required)

### Read-only vs editable
- Timestamps are **read-only**.
- Location:
  - Read-only if derived from terminal/session.
  - Editable only if required due to missing config/context (see Open Questions).

### Derived/calculated fields (frontend only)
- Local display time = convert `*TimestampUtc` into user’s locale/timezone for display, but also display stored timezone value (or location timezone) for context.
- Duration (optional): if open entry, show elapsed time since clock-in (requires a safe default decision; not required).

---

## 9. Service Contracts (Frontend Perspective)

> Note: Exact Moqui service names and REST routes are not in provided inputs. Frontend will call backend REST endpoints as per backend reference; Moqui screens will use `rest`/`service-call` patterns consistent with the project.

### Load/view calls
1. **Get current timekeeping status**
   - **Request:** `GET /api/timekeeping/me/status` (placeholder; needs confirmation)
   - **Response (200):** TimekeepingStatus (see Data Requirements)
   - **Errors:**
     - 401 → redirect to login/session expired handling
     - 403 → show unauthorized message
     - 5xx/timeout → show retry state, keep actions disabled

### Create/update calls
2. **Clock in**
   - **Request:** `POST /api/timekeeping/me/clock-in` with body `{ locationId? }`
   - **Response (201/200):** TimeEntry or TimekeepingStatus
   - **Errors:**
     - 409 conflict if already clocked in
     - 403 if not authorized for location (if enforced)
     - 400 validation errors (missing location, etc.)

3. **Clock out**
   - **Request:** `POST /api/timekeeping/me/clock-out` with body `{ locationId? }` (if needed)
   - **Response (200):** TimeEntry or TimekeepingStatus
   - **Errors:**
     - 409 conflict if not clocked in / no open entry
     - 403 unauthorized for location (if enforced)
     - 400 validation errors

### Submit/transition calls
- Not applicable beyond clock-in/out actions.

### Error handling expectations
- For 400/403/409: show a specific message (from backend `message` if provided; otherwise use mapped defaults above), then refresh status.
- For 5xx/network: show generic error and allow retry; do not change UI status.

---

## 10. State Model & Transitions

### Allowed states (timekeeping UI state)
- `CLOCKED_OUT` → user can perform **Clock In**
- `CLOCKED_IN` → user can perform **Clock Out**

### Role-based transitions
- Mechanic role (or equivalent) must be allowed to clock in/out.
- If other roles can do this (e.g., manager clocking on behalf), that is out-of-scope and must not be implied.

### UI behavior per state
- **CLOCKED_OUT**
  - Primary button: “Clock In”
  - Show last closed entry if available (clock-out time)
- **CLOCKED_IN**
  - Primary button: “Clock Out”
  - Show open entry details (clock-in time, location)
- **UNKNOWN/ERROR**
  - Primary button disabled
  - Show retry

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required location input (if required by backend):
  - Show “Select a location to continue” and block action.
- Backend returns 400 with field errors:
  - Show first error message in banner; optionally highlight relevant input (location).

### Concurrency conflicts
- Mechanic clocks in on another device while this screen is open:
  - Clock-in request returns 409; UI shows “You are already clocked in…” and reloads status.
- Mechanic clocks out on another device:
  - Clock-out request returns 409; UI shows “You are not currently clocked in.” and reloads.

### Unauthorized access
- 401: route to login flow.
- 403: show “You do not have permission to use timekeeping” and hide/disable actions.

### Empty states
- No previous entries:
  - Show status and action button; omit “last entry” panel.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View clock status on load (clocked out)
**Given** the mechanic is authenticated  
**And** the backend reports the mechanic status is `CLOCKED_OUT`  
**When** the mechanic opens the Timekeeping Clock screen  
**Then** the UI shows status `Clocked Out`  
**And** the primary action is `Clock In`  
**And** no clock-out action is available.

### Scenario 2: Successful clock in
**Given** the mechanic is authenticated  
**And** the backend reports the mechanic status is `CLOCKED_OUT`  
**When** the mechanic taps `Clock In`  
**Then** the frontend sends a clock-in request to the backend  
**And** the UI disables the action while the request is pending  
**And** on success the UI shows status `Clocked In`  
**And** displays the server-recorded clock-in UTC timestamp and stored timezone.

### Scenario 3: Prevent double clock-in (UI + backend)
**Given** the mechanic is authenticated  
**And** the backend reports the mechanic status is `CLOCKED_IN`  
**When** the mechanic views the Timekeeping Clock screen  
**Then** the `Clock In` action is not available  
**And** the primary action is `Clock Out`.

**And Given** the mechanic is `CLOCKED_IN`  
**When** the mechanic attempts to invoke clock-in (e.g., by rapid double-tap or stale UI)  
**Then** the backend responds with a conflict error  
**And** the UI displays “You are already clocked in. You must clock out before clocking in again.”  
**And** the UI reloads and displays the current status.

### Scenario 4: Successful clock out
**Given** the mechanic is authenticated  
**And** the backend reports the mechanic status is `CLOCKED_IN` with an open TimeEntry  
**When** the mechanic taps `Clock Out`  
**Then** the frontend sends a clock-out request to the backend  
**And** on success the UI shows status `Clocked Out`  
**And** displays the server-recorded clock-out UTC timestamp and stored timezone.

### Scenario 5: Prevent clock-out when not clocked in
**Given** the mechanic is authenticated  
**And** the backend reports the mechanic status is `CLOCKED_OUT`  
**When** the mechanic attempts to clock out (via stale state or direct action)  
**Then** the backend responds with a conflict error  
**And** the UI displays “You are not currently clocked in.”  
**And** the UI reloads status.

### Scenario 6: Location authorization failure (if enforced)
**Given** location validation is enabled by policy  
**And** the mechanic is not authorized for the current/selected location  
**When** the mechanic taps `Clock In`  
**Then** the backend responds with 403 (or configured error)  
**And** the UI displays “Clock-in failed: You are not authorized for this location.”  
**And** no TimeEntry is created.

### Scenario 7: System/network failure
**Given** the mechanic is authenticated  
**And** the backend is unavailable or returns 5xx  
**When** the mechanic taps `Clock In` or `Clock Out`  
**Then** the UI shows “Unable to record time right now. Please try again or contact a manager.”  
**And** the UI does not change the displayed clock status  
**And** the mechanic can retry.

---

## 13. Audit & Observability

### User-visible audit data
- Display (read-only) from backend when available:
  - last action timestamps (clock-in/out UTC)
  - associated location
  - stored timezone (IANA)
- Do not display sensitive internal IDs unless already standard in POS UI.

### Status history
- Out-of-scope for this story to build full history view.
- If backend provides last entry, show last closed entry summary.

### Traceability expectations
- Frontend should include correlation/request ID header if standard in the project; log client-side errors with route + action + timestamp (no PII beyond user ID if already available in auth context).

---

## 14. Non-Functional UI Requirements

- **Performance:** status load should render actionable UI within 1s on typical store network; show skeleton/loading state while fetching.
- **Accessibility:** action button reachable by keyboard; status changes announced (aria-live) if framework supports; sufficient color contrast.
- **Responsiveness:** usable on tablet and desktop.
- **i18n/timezone:** timestamps displayed in local device format; store timezone string displayed as provided (IANA). Currency not applicable.

---

## 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE:** Show a clear empty state when no prior TimeEntry exists; qualifies as safe because it does not change business logic, only presentation. (Impacted: UX Summary, Alternate/Empty states)
- **SD-UX-REQUEST-IN-FLIGHT-GUARD:** Disable primary action and prevent double-submit while request is pending; qualifies as safe because it prevents accidental duplicate requests without altering domain policy. (Impacted: Functional Behavior, Error Flows)
- **SD-ERR-HTTP-MAPPING:** Standard handling for 401/403/409/5xx with retry and message mapping; qualifies as safe because it follows common REST semantics and does not invent business rules. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Location validation policy (blocking):** Is “mechanic assigned to selected location” enforcement **Not enforced**, **Strict**, or **Soft/flagged**? This directly affects UI requirements (location selection, warnings vs blocking) and error handling.
2. **Location source (blocking):** How does the frontend determine `locationId` for timekeeping?
   - Derived implicitly from terminal/session context, or
   - Mechanic must select a location, or
   - Backend infers location and frontend sends none.
3. **Backend endpoints (blocking):** What are the exact REST paths and response schemas for:
   - current status
   - clock-in
   - clock-out  
   (Placeholders above must be replaced to be Moqui-buildable without guesswork.)
4. **Identity mapping (blocking):** Does backend identify mechanic via auth token (“me”) or does frontend need to send `userId/requestedBy`? If sending IDs is required, which identifier (Moqui `userId`, `partyId`, employeeId) is canonical?
5. **Shift context integration (non-blocking):** If “shopmgr shift can be displayed” is desired, what endpoint and minimal fields should be displayed?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Timekeeping: Mechanic Clock In/Out  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/149  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Mechanic Clock In/Out

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to clock in and out** so that **my attendance time is recorded accurately**.

## Details
- Capture UTC timestamp and local timezone.
- Optional validation that mechanic is assigned to the selected location.

## Acceptance Criteria
- One-tap clock in/out.
- Prevent double clock-in without clock-out.
- Entries are auditable.

## Integration Points (workexec/shopmgr)
- shopmgr shift can be displayed for context (optional).

## Data / Entities
- TimeEntry (attendance)

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