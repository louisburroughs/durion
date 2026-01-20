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

**Title:** [FRONTEND] [STORY] Scheduling: Reschedule or Cancel Appointment with Audit (Moqui Screens)

**Primary Persona:** Dispatcher

**Business Value:** Keep the shop plan accurate by allowing Dispatchers to reschedule/cancel scheduled appointments with a complete audit trail and downstream notification when the appointment is linked to an Estimate or Work Order.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Dispatcher  
- **I want** to reschedule or cancel a previously scheduled appointment  
- **So that** the schedule remains accurate, changes are auditable, and work execution workflows stay in sync when an appointment is linked to an Estimate/Work Order.

### In-scope
- View an Appointment and its current scheduling details.
- Reschedule an appointment by changing:
  - scheduled time window (start/end)
  - optionally assigned resource (technician/resource)
- Cancel an appointment by providing:
  - cancellation reason code (required)
  - cancellation notes (optional)
- Display audit history (AppointmentAudit) for reschedule/cancel actions.
- Ensure backend is notified (via backend API) when appointment is linked to Estimate/Work Order (frontend just calls API; it does not emit events itself).
- Frontend validation and error handling consistent with backend rules:
  - only `Scheduled` appointments can be rescheduled/cancelled
  - handle conflicts (resource unavailable) and invalid state

### Out-of-scope
- Creating appointments.
- Defining/configuring cancellation reason codes (source-of-truth list management).
- Notifications to customer (SMS/email).
- Editing/creating Work Orders/Estimates as a result of changes (only linkage awareness).

---

## 3. Actors & Stakeholders
- **Dispatcher (Primary):** performs reschedule/cancel actions.
- **Service Advisor (Secondary stakeholder):** may rely on accurate appointment status.
- **Technician/Resource (Secondary):** assignment changes affect workload.
- **Workexec domain services (System):** appointment update/cancel APIs, audit persistence, and domain event emission when linked.

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui frontend and has permission to modify appointments (exact permission names TBD).
- Appointment exists and can be loaded by ID.
- Backend endpoints exist and are reachable:
  - Load appointment details
  - Reschedule appointment
  - Cancel appointment
  - Load audit history
- Backend enforces state rules and resource availability checks.
- If cancellation reason codes are a controlled list, frontend must retrieve them from backend or configuration (TBD).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an appointment detail view (e.g., Schedule/Calendar view → click appointment → “View Details”).
- From an appointment list/search screen (optional if exists): open appointment → actions.

### Screens to create/modify
1. **Screen: `apps/pos/scheduling/AppointmentDetail.xml`** (new or extend existing)
   - Shows appointment summary, status, scheduled window, assigned resource, linkage indicators (Estimate/Work Order).
   - Actions:
     - `Reschedule` (enabled only when status == `Scheduled`)
     - `Cancel` (enabled only when status == `Scheduled`)
   - Tab/section: `Audit History` (read-only list)

2. **Dialog/Screen transition: `RescheduleAppointment`**
   - Form to edit:
     - New start datetime
     - New end datetime
     - Assigned resource (optional)
   - Submit triggers backend reschedule call.

3. **Dialog/Screen transition: `CancelAppointment`**
   - Form to input:
     - CancellationReasonCode (required)
     - CancellationNotes (optional)
   - Submit triggers backend cancel call.

### Navigation context
- After successful reschedule/cancel, remain on AppointmentDetail and refresh data and audit list.
- Ensure browser back does not re-submit actions (PRG pattern: Post/Redirect/Get via Moqui transition redirect).

### User workflows
- **Happy path (Reschedule):** Open appointment → Reschedule → choose new window/resource → Save → confirm success → updated details + audit entry visible.
- **Happy path (Cancel):** Open appointment → Cancel → select reason + notes → Confirm → status becomes Cancelled + audit entry visible.
- **Alternate path:** Backend reports resource conflict → show conflict message; keep user in reschedule form with inputs preserved.
- **Alternate path:** Appointment not modifiable due to state → show “cannot modify” and disable actions after refresh.

---

## 6. Functional Behavior

### Triggers
- Dispatcher clicks **Reschedule** or **Cancel** on AppointmentDetail.

### UI actions
- Reschedule:
  - Open reschedule form prefilled with current scheduled window and assigned resource (if present).
  - Client-side validation:
    - start < end
    - both start and end present
- Cancel:
  - Open cancel form.
  - Client-side validation:
    - reason code required

### State changes (frontend-observed)
- On success:
  - Reschedule updates `ScheduledTimeWindow` and possibly `AssignedResourceId` (displayed values change).
  - Cancel updates `Status` to `Cancelled`, and displays cancellation reason/notes (if returned by API).
- On failure:
  - No local optimistic update; show error and re-load appointment if needed (especially for 409 conflicts).

### Service interactions
- Load appointment on entering AppointmentDetail.
- On submit:
  - call reschedule or cancel endpoint
  - refresh appointment detail + audit list after success

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Only status `Scheduled` is modifiable**
  - If appointment status != `Scheduled`:
    - Hide/disable Reschedule and Cancel buttons.
    - If user somehow submits (stale UI), handle `409 Conflict` by showing message and refreshing appointment.

- **Cancellation reason must be from a predefined list**
  - UI must present a controlled selection (dropdown) backed by backend list/config.
  - If backend rejects reason as invalid (`400`), show inline validation.

- **Reschedule must respect availability**
  - Backend is authoritative for resource availability; UI handles `409 Conflict` with actionable message.

### Enable/disable rules
- Reschedule button enabled only when:
  - appointment.status == `Scheduled`
- Cancel button enabled only when:
  - appointment.status == `Scheduled`

### Visibility rules
- Link indicator section visible when appointment has a link to Estimate or Work Order (fields TBD).
- Audit section always visible; show empty state if none.

### Error messaging expectations
- 404: “Appointment not found.”
- 409 invalid state: “Appointment cannot be modified because it is <status>.”
- 409 resource conflict: “Selected resource is unavailable for that time window.”
- 400 validation: field-specific messages (reason code, time window).

---

## 8. Data Requirements

### Entities involved (frontend view models)
- `Appointment`
- `AppointmentAudit`
- `WorkexecLinkRef` (or link fields on Appointment)

### Fields (type, required, defaults)
**Appointment**
- `appointmentId` (string/number, required, read-only)
- `status` (enum: `Scheduled`, `InProgress`, `Completed`, `Cancelled`, required, read-only)
- `scheduledStart` (datetime, required when Scheduled)
- `scheduledEnd` (datetime, required when Scheduled)
- `assignedResourceId` (string/number, optional)
- `workOrderLinkRef` (nullable; exact structure TBD)
- `estimateLinkRef` (nullable; exact structure TBD)
- `cancellationReasonCode` (string, nullable; required when Cancelled)
- `cancellationNotes` (string/text, nullable)

**AppointmentAudit**
- `auditId` (required, read-only)
- `appointmentId` (required, read-only)
- `timestamp` (datetime, required, read-only)
- `actorId` (required, read-only)
- `action` (enum: `CREATED`, `RESCHEDULED`, `CANCELLED`, required, read-only)
- `changes` (json/text, read-only)

### Read-only vs editable (by state/role)
- Editable only through actions and only when status == `Scheduled`:
  - scheduledStart/scheduledEnd/assignedResourceId via Reschedule flow
  - cancellationReasonCode/cancellationNotes via Cancel flow
- All other fields read-only.

### Derived/calculated fields
- `scheduledTimeWindowDisplay` derived from start/end in user timezone.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend story shows example endpoints but does not specify Appointment endpoints concretely. These are **blocking** until confirmed.

### Load/view calls
- `GET /api/appointments/{appointmentId}`
  - Returns Appointment with link refs and cancellation fields.
- `GET /api/appointments/{appointmentId}/audit`
  - Returns list of AppointmentAudit entries sorted descending by timestamp.

### Create/update calls
- **Reschedule**
  - `POST /api/appointments/{appointmentId}/reschedule`
  - Request body:
    - `newScheduledStart` (ISO-8601)
    - `newScheduledEnd` (ISO-8601)
    - `newAssignedResourceId` (optional)
  - Success: `200 OK` with updated Appointment OR minimal success + requires reload.

- **Cancel**
  - `POST /api/appointments/{appointmentId}/cancel`
  - Request body:
    - `cancellationReasonCode` (string)
    - `cancellationNotes` (optional string)
  - Success: `200 OK` with updated Appointment OR minimal success + requires reload.

### Submit/transition calls
- Not applicable beyond the above action endpoints.

### Error handling expectations
- `400 Bad Request`: invalid time window, invalid reason code.
- `403 Forbidden`: user not authorized.
- `404 Not Found`: appointmentId unknown.
- `409 Conflict`: invalid state or resource conflict.
- `5xx`: show generic error; allow retry.

---

## 10. State Model & Transitions

### Appointment allowed states (as per backend reference)
- `Scheduled`
- `InProgress`
- `Completed`
- `Cancelled`

### Allowed transitions (frontend-relevant)
- `Scheduled` → `Scheduled` (reschedule: time/resource change)
- `Scheduled` → `Cancelled` (cancel with reason)
- No other transitions in scope.

### Role-based transitions
- Dispatcher can trigger reschedule/cancel (exact RBAC permissions TBD).
- UI must hide/disable actions if backend indicates unauthorized (403) and/or if a permission flag is available in response (TBD).

### UI behavior per state
- `Scheduled`: show enabled Reschedule/Cancel.
- `InProgress`, `Completed`, `Cancelled`: show actions disabled; show reason fields when Cancelled.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Reschedule with end <= start:
  - prevent submit; show inline message “End time must be after start time.”
- Cancel without reason:
  - prevent submit; “Reason is required.”

### Backend conflicts
- `409 Conflict` invalid state:
  - Show message and refresh appointment detail; keep user on AppointmentDetail.
- `409 Conflict` resource unavailable:
  - Show message and keep reschedule dialog open with prior values preserved.

### Concurrency conflicts
- If appointment changed since loaded (backend may still return 409 or updated data):
  - On 409, reload appointment and display updated current window/status to user.

### Unauthorized access
- `403 Forbidden`:
  - Show “You do not have permission to modify appointments.”
  - Disable actions after response.

### Empty states
- Audit history empty:
  - Show “No changes recorded yet.”

---

## 12. Acceptance Criteria

### Scenario 1: Reschedule a Scheduled appointment successfully
**Given** I am logged in as a Dispatcher  
**And** an appointment exists with status `Scheduled`  
**When** I submit a reschedule with a new start/end window (end after start) and an available resource (or no resource)  
**Then** the appointment detail must display the updated scheduled window (and resource if changed)  
**And** an audit entry with action `RESCHEDULED` must be visible in the audit history  
**And** the UI must show a success confirmation without leaving the AppointmentDetail screen.

### Scenario 2: Cancel a Scheduled appointment successfully
**Given** I am logged in as a Dispatcher  
**And** an appointment exists with status `Scheduled`  
**When** I cancel the appointment providing a valid cancellation reason code  
**Then** the appointment status must display as `Cancelled`  
**And** the cancellation reason code (and notes if entered) must be displayed  
**And** an audit entry with action `CANCELLED` must be visible in the audit history.

### Scenario 3: Attempt to reschedule a Completed appointment is rejected
**Given** I am logged in as a Dispatcher  
**And** an appointment exists with status `Completed`  
**When** I attempt to reschedule the appointment  
**Then** the UI must prevent the action (button disabled) **or** if submitted from stale UI the API response must be handled  
**And** I must see an error message indicating the appointment cannot be modified in its current status  
**And** the displayed appointment data must remain unchanged after refresh.

### Scenario 4: Resource conflict during reschedule
**Given** I am logged in as a Dispatcher  
**And** an appointment exists with status `Scheduled`  
**When** I reschedule to a time window where the selected resource is unavailable  
**Then** the UI must display a conflict error message  
**And** the reschedule form must remain open with my entered values preserved  
**And** the appointment must not be updated.

### Scenario 5: Cancel fails without a valid reason code
**Given** I am logged in as a Dispatcher  
**And** an appointment exists with status `Scheduled`  
**When** I submit cancellation without a reason code or with an invalid reason code  
**Then** the UI must show an inline validation error (client-side for missing; server-side for invalid)  
**And** the appointment must not be updated.

---

## 13. Audit & Observability

### User-visible audit data
- AppointmentDetail must show an audit list with:
  - timestamp (localized display, stored UTC)
  - actor (ID or display name if available)
  - action type (RESCHEDULED/CANCELLED)
  - changed fields summary (derived from `changes` payload if available)

### Status history
- Audit list acts as status/change history; no separate timeline required.

### Traceability expectations
- After successful reschedule/cancel, UI should display the latest audit entry without requiring a full page reload (but a data refresh call is acceptable).
- Correlation ID handling:
  - If backend returns/request supports correlation IDs, frontend should pass through existing request ID header per project conventions (TBD by repo README).

---

## 14. Non-Functional UI Requirements

- **Performance:** Appointment detail load (appointment + audit) should render within 2s on typical network; audit list paginated if large.
- **Accessibility:** All actions must be keyboard accessible; dialogs have focus trap; form errors announced via aria-live (Quasar patterns).
- **Responsiveness:** Works on tablet and desktop; dialogs usable on narrow screens.
- **i18n/timezone:** Display datetimes in store/user timezone; send ISO-8601 to backend in UTC or with offset (must match backend expectation—TBD).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show explicit empty-state message for no audit entries; safe because it does not change domain behavior; impacts UX Summary, Alternate/Empty states.
- SD-UX-POST-REDIRECT-GET: Use PRG-style redirect after POST transitions to avoid double-submit; safe as it’s a UI ergonomics pattern; impacts UX Summary, Error Flows.
- SD-UX-PAGINATION: Paginate audit list when > 50 entries; safe as it’s presentation-only; impacts UX Summary, Data Requirements.

---

## 16. Open Questions

1. **Backend endpoints/paths:** What are the exact REST endpoints for loading an Appointment, rescheduling, cancelling, and fetching audit history (paths + request/response schemas)? (Blocking)
2. **CancellationReasonCode list source:** Is there an endpoint to retrieve valid cancellation reason codes (and display labels), or is it a static enum/config shipped to frontend? Provide the authoritative list and identifiers. (Blocking)
3. **RBAC/permissions:** What permission(s)/role(s) gate reschedule and cancel actions? Should frontend rely solely on 403 handling, or will the appointment payload include allowed-actions flags? (Blocking)
4. **Resource selection:** What is the “resource” entity for assignment (technician user? calendar resource?) and how is availability checked/exposed (do we need a “search available slots/resources” endpoint)? (Blocking)
5. **Time handling:** Should frontend send timestamps in UTC (`Z`) or local with offset? What timezone should be used for display (user vs location/shop)? (Blocking)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Scheduling: Reschedule or Cancel Appointment with Audit  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/137  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Scheduling: Reschedule or Cancel Appointment with Audit

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to reschedule or cancel an appointment so that the plan stays accurate.

## Details
- Reschedule: change window/resource; Cancel: reason.
- Notify workexec if linked to estimate/WO.

## Acceptance Criteria
- Reschedule updates schedule.
- Cancel sets status+reason.
- Changes audited.

## Integrations
- Emit AppointmentUpdated/Cancelled to workexec when linked.

## Data / Entities
- Appointment, AppointmentAudit, WorkexecLinkRef

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