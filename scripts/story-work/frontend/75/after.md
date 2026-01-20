## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:shopmgmt
- status:draft

### Recommended
- agent:shopmgmt-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

STOP: Clarification required before finalization

---

## 1. Story Header

**Title**  
[FRONTEND] [STORY] Appointment: Reschedule Appointment with Notifications

**Primary Persona**  
Scheduler (Service Advisor / Dispatcher)

**Business Value**  
Keep shop operations coordinated by allowing authorized users to reschedule appointments while preserving estimate/work order links, preventing/handling conflicts correctly, recording an auditable reason trail, and triggering downstream notifications/updates.

---

## 2. Story Intent

**As a** Scheduler  
**I want** to reschedule an existing appointment (date/time) with a required reason and optional notes and notifications  
**So that** schedule changes are coordinated across shop operations, downstream systems, and affected parties with a complete audit trail.

### In-scope
- UI workflow to reschedule an existing appointment:
  - Select new date/time
  - Select reason (enum) and enter notes where required
  - See conflicts (hard/soft), choose alternate slot or override if permitted
  - Choose whether to notify customer/mechanic (subject to backend enforcement)
- Display resulting update status:
  - New scheduled time
  - Assignment preserved/cleared outcome
  - Notification status summary (if provided by backend)
- Facility-scoped permission gating and error feedback
- Audit visibility (who/why/when) at least in “view appointment” context if API provides it

### Out-of-scope
- Creating appointments
- Editing estimate/work order links (must remain immutable)
- Recurring/series reschedules
- Implementing conflict detection logic on the frontend (frontend only renders backend results)
- Building notification delivery UI (compose templates, channel management) beyond toggles and status display

---

## 3. Actors & Stakeholders
- **Scheduler**: initiates reschedule
- **Shop Manager / Dispatcher**: may approve overrides depending on permission
- **Customer**: may receive reschedule notifications
- **Assigned Mechanic**: may be notified and/or unassigned
- **Shop Management (shopmgmt) backend**: system of record for appointment schedule/assignment
- **Work Execution service**: consumes reschedule events for planned time updates (indirect from frontend)
- **Notification service**: sends notifications (indirect from frontend)
- **Audit service/log**: records immutable audit entries (indirect from frontend)

---

## 4. Preconditions & Dependencies

### Preconditions
- Appointment exists and is in a reschedulable state (backend-enforced; frontend must handle denials gracefully).
- User is authenticated and scoped to a facility that contains the appointment.
- User has `RESCHEDULE_APPOINTMENT` permission (backend-enforced; frontend should hide/disable entry points if permission info available).

### Dependencies
- Backend endpoints/services for:
  - loading appointment details (including current schedule, assignments, reschedule eligibility, and reschedule count if applicable)
  - conflict-check / preview (optional but strongly preferred for UX)
  - reschedule submit (with reason/notes/notify flags/override metadata)
  - (optional) audit log retrieval and notification status retrieval

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Appointment detail screen: “Reschedule” action/button (visible only when appointment is reschedulable and user has permission, if those flags are available).
- From Appointment list/board: contextual action “Reschedule” (navigates to appointment detail then reschedule modal/page, or opens directly).

### Screens to create/modify (Moqui)
- **Modify**: `apps/pos/screen/shopmgmt/AppointmentDetail.xml` (name illustrative)
  - Add “Reschedule” action and reschedule history/audit snippet area (if data available)
- **Create**: `apps/pos/screen/shopmgmt/AppointmentReschedule.xml`
  - Can be a dialog-style screen or dedicated route screen
  - Contains form for new date/time, reason, notes, notify flags, and override section
- **Optional Create/Modify**: reusable widget/form:
  - `component://.../screen/shopmgmt/widget/AppointmentRescheduleForm.xml`

### Navigation context
- Route includes `appointmentId` and preserves facility context (from session or explicit parameter).
- After success, navigate back to Appointment detail with a toast/banner indicating the updated schedule and any assignment/notification outcomes.

### User workflows
**Happy path**
1. Scheduler opens Appointment Detail → clicks “Reschedule”.
2. Reschedule form loads current schedule + reschedule rules/limits (if available).
3. Scheduler selects new date/time, selects reason, adds notes if required, leaves notify on (default as provided by backend/UI).
4. UI performs conflict check (either on change or on “Check conflicts” action).
5. If no hard conflicts, scheduler submits.
6. UI shows confirmation state, then returns to Appointment Detail showing updated time and audit entry.

**Alternate paths**
- Conflicts detected (soft): show warning list, allow proceed.
- Conflicts detected (hard): block submit; if user has override permission, allow entering override reason/notes and proceed.
- Minimum-notice or reschedule-limit rule violated: backend returns policy error; UI explains and (if applicable) indicates “manager approval required” (without inventing the workflow).
- Mechanic/bay no longer available: backend reschedules but clears assignment; UI shows assignment status and prompts to go to assignment workflow (link to existing screen if any).

---

## 6. Functional Behavior

### Triggers
- User clicks “Reschedule” from appointment context.
- User changes proposed date/time in the reschedule form.
- User clicks “Check conflicts” (if implemented) and/or “Submit reschedule”.

### UI actions (must be deterministic)
- Load appointment snapshot for reschedule:
  - current scheduled datetime
  - current assignment (bay/mobile unit, mechanic)
  - reschedule eligibility + reason enum list (if API supports)
- Collect inputs:
  - `newScheduledDateTime` (required)
  - `rescheduleReason` (required enum)
  - `rescheduleReasonNotes` (optional unless reason=OTHER or override used)
  - notification toggles (see Open Questions)
  - override flag and override reason fields only when hard conflicts present and user has override permission
- Display conflict results:
  - categorize and render hard vs soft
  - show suggested alternatives if provided
- Submit reschedule:
  - call backend submit service
  - on success: show updated schedule + assignment outcome + notification outcome (if provided)
  - on failure: map errors to field-level or banner-level messages

### State changes (frontend perspective)
- Local UI state transitions: `idle → loading → editing → validatingConflicts → readyToSubmit → submitting → success|error`
- No frontend-owned domain state; all domain state is backend-driven.

### Service interactions (frontend perspective)
- `loadAppointmentForReschedule(appointmentId)`
- optional `checkRescheduleConflicts(appointmentId, newScheduledDateTime, preserveAssignmentPreference?)`
- `rescheduleAppointment(payload)`

---

## 7. Business Rules (Translated to UI Behavior)

> Backend is authoritative for rule enforcement. Frontend must *collect required inputs*, *request conflict evaluation*, and *render decisions*.

### Validation
- New scheduled date/time is required and must be a valid datetime in shop timezone (timezone source must be explicit; see Open Questions).
- Reschedule reason is required (enum).
- Notes required when:
  - reason = `OTHER`
  - an override is applied (hard conflict override and/or policy override), per backend rule
- If backend returns “not reschedulable state” → show blocking banner and disable submit.
- If backend returns “min notice requires approval” → show blocking banner and disable submit unless backend indicates override allowed with user permission.

### Enable/disable rules
- Disable “Submit” until required fields present and conflict check completed **if** backend requires conflict check token/version (unknown; see Open Questions). Otherwise allow submit and handle conflict response.
- Override UI elements enabled only when:
  - backend reports hard conflicts OR backend returns an error requiring override
  - and user has `OVERRIDE_SCHEDULING_CONFLICT` (if permission info available)
- Notify toggles:
  - default state should come from backend if provided; otherwise do not assume mandatory behavior beyond allowing a toggle (see Open Questions)

### Visibility rules
- Show “Assignment impact” section if backend returns assignment preservation/clearing decision.
- Show “Notification result” section if backend returns statuses (or show a generic “notification queued” message if only a boolean confirmation is returned).

### Error messaging expectations
- Field-level: missing reason, invalid datetime, missing notes when required.
- Banner-level:
  - permission denied
  - reschedule limit exceeded without approval
  - hard conflicts without override permission
  - backend/service unavailable (retry guidance)

---

## 8. Data Requirements

### Entities involved (frontend read/write)
- **Appointment** (read)
- **AppointmentUpdate** (write model for reschedule)
- **Conflict** (read model)
- **AuditLog** (read, optional)
- **NotificationOutbox / NotificationStatus** (read, optional)

### Fields
**Appointment (read)**
- `appointmentId` (string/ID, required)
- `facilityId` (string/ID, required)
- `scheduledDateTime` (datetime, required)
- `state/status` (enum/string, required)
- `estimateId` (ID, nullable)
- `workOrderId` (ID, nullable)
- assignment:
  - `bayId` (ID, nullable)
  - `mobileUnitId` (ID, nullable)
  - `mechanicId` (ID, nullable)

**AppointmentUpdate (submit)**
- `appointmentId` (required)
- `newScheduledDateTime` (required datetime)
- `rescheduleReason` (required enum: CUSTOMER_REQUEST, SHOP_CAPACITY, EQUIPMENT_ISSUE, MECHANIC_UNAVAILABLE, PARTS_DELAY, WEATHER, EMERGENCY, MANAGER_DISCRETION, OTHER)
- `rescheduleReasonNotes` (string, required when reason=OTHER; also required when overriding)
- `notifyCustomer` (boolean, optional/required? see Open Questions)
- `notifyMechanic` (boolean, optional/derived? see Open Questions)
- `overrideHardConflicts` (boolean, only when needed)
- `overrideReason` (string, required when overrideHardConflicts=true)
- `overrideApprovedBy` (ID/string, **do not implement collection unless backend requires**; see Open Questions)
- `clientRequestId` (string idempotency key; if supported)

**Conflict result (read)**
- `conflicts[]`: each with
  - `code` (string)
  - `severity` (HARD|SOFT)
  - `message` (string)
  - `resourceType` (BAY|MECHANIC|CAPACITY|HOURS|OTHER)
- `suggestedAlternatives[]` (optional): datetime list and/or resource suggestions

### Read-only vs editable
- estimate/workOrder link fields are read-only and must not be editable.
- previous scheduled time is read-only display.

### Derived/calculated fields (display-only)
- “Notice window” indicator (e.g., “<24h”) should only be shown if backend provides computed policy evaluation; do not compute policy locally unless explicitly defined.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui services/screen actions must call backend services; exact service names/endpoints are not provided in inputs, so these are contract placeholders.

### Load/view calls
1. `shopmgmt.Appointment.get#ForReschedule`
   - Request: `appointmentId`
   - Response: appointment snapshot + eligibility + rescheduleCount + allowedReasons + permissions (optional) + timezone (preferred)

2. Optional: `shopmgmt.Appointment.check#RescheduleConflicts`
   - Request: `appointmentId`, `newScheduledDateTime`
   - Response: `conflicts[]`, `suggestedAlternatives[]`, `canOverride` (optional)

### Create/update calls
- none (reschedule is an update/transition)

### Submit/transition calls
1. `shopmgmt.Appointment.reschedule#Submit`
   - Request: `AppointmentUpdate` (see Data Requirements)
   - Response (preferred): updated appointment + assignment outcome + notification outcome + audit reference

### Error handling expectations
- 400/validation: map to field errors (reason missing, notes missing, datetime invalid)
- 403/permission: show access denied, disable action
- 409/conflict: show conflicts; if overridable, prompt for override reason and re-submit with override flag
- 404: appointment not found → navigate back to list with error banner
- 5xx/network: show retry banner; keep form inputs intact

---

## 10. State Model & Transitions

### Allowed states (relevant to reschedule)
- Reschedulable: `SCHEDULED`, `CONFIRMED`, `AWAITING_PARTS` (per backend reference)
- Not reschedulable: `COMPLETED`, `CANCELLED` and others (backend authoritative)

### Role-based transitions
- Scheduler with `RESCHEDULE_APPOINTMENT`: can request reschedule when eligible and no blocking hard conflicts/policy blocks.
- Users with `OVERRIDE_SCHEDULING_CONFLICT`: can override hard conflicts with reason.
- Users with `APPROVE_RESCHEDULE`: can approve policy exceptions (minimum notice / reschedule count) if backend supports such override.

### UI behavior per state
- If appointment state not reschedulable: hide/disable “Reschedule” and show tooltip/message (if state is known).
- If reschedulable: allow entering reschedule.
- If override required and user lacks permission: block submission with clear message.

---

## 11. Alternate / Error Flows

1. **Validation failure (client-side)**
   - Missing reason or datetime → inline errors, prevent submit.
   - Notes missing for OTHER or override → inline error.

2. **Hard conflicts detected**
   - Display conflicts as blocking.
   - If user has override permission: show override reason input, allow submit.
   - If not: disable submit and provide instruction to contact manager/dispatcher.

3. **Soft conflicts detected**
   - Display warnings; allow proceed (submit enabled).

4. **Concurrency conflict**
   - Backend returns 409 “appointment changed/version mismatch” (if supported):
     - UI prompts user to reload appointment snapshot, preserving their proposed inputs where possible.

5. **Unauthorized**
   - Backend returns 403:
     - UI navigates to appointment detail with “Not authorized to reschedule” banner.

6. **External service degradation (notifications/workexec)**
   - If backend returns partial success (reschedule succeeded but notification failed/queued):
     - UI must show reschedule success and surface notification status and manual follow-up prompt (if status provided).

7. **Empty states**
   - No suggested alternatives returned → show “No alternatives provided” and allow user to pick a different time.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Reschedule succeeds with no conflicts
Given a Scheduler has `RESCHEDULE_APPOINTMENT` permission for the facility  
And an appointment exists in state SCHEDULED with an estimate/work order link  
When the Scheduler selects a new scheduled date/time and a reschedule reason (not OTHER)  
And submits the reschedule request  
Then the UI shows the updated scheduled date/time on the Appointment Detail screen  
And the estimate/work order links remain unchanged and read-only  
And an audit record summary (who/why/when) is visible or linked if provided by the backend.

### Scenario 2: Reschedule blocked due to hard conflict without override permission
Given a Scheduler has `RESCHEDULE_APPOINTMENT` permission but not `OVERRIDE_SCHEDULING_CONFLICT`  
And the backend reports a HARD conflict for the selected new date/time  
When the Scheduler attempts to submit the reschedule  
Then the UI blocks submission  
And displays the hard conflict(s) as blocking errors  
And does not change the appointment scheduled time.

### Scenario 3: Reschedule proceeds with soft conflict warning
Given a Scheduler has `RESCHEDULE_APPOINTMENT` permission  
And the backend reports only SOFT conflicts for the selected new date/time  
When the Scheduler submits the reschedule request  
Then the UI allows submission after showing the warning(s)  
And on success shows the updated scheduled date/time.

### Scenario 4: Hard conflict override requires reason and permission
Given a user has `RESCHEDULE_APPOINTMENT` and `OVERRIDE_SCHEDULING_CONFLICT` permissions  
And the backend reports a HARD conflict for the selected new date/time  
When the user enters an override reason (and notes if required)  
And submits the reschedule request with override enabled  
Then the reschedule succeeds  
And the UI indicates that an override was applied  
And the audit trail includes the override reason (as provided by backend).

### Scenario 5: Reason OTHER requires notes
Given a Scheduler is rescheduling an eligible appointment  
When the Scheduler selects reschedule reason OTHER  
And leaves notes empty  
Then the UI prevents submission and shows a field-level error for notes  
When the Scheduler enters notes and submits  
Then the reschedule request is sent successfully (subject to backend validation).

### Scenario 6: Notification failure is surfaced without losing reschedule success
Given a Scheduler reschedules an appointment successfully  
And the backend response indicates notification delivery failed or requires manual follow-up  
When the UI displays the result  
Then the UI shows reschedule success  
And displays notification failure status and a prompt to manually contact affected parties.

---

## 13. Audit & Observability

### User-visible audit data
- Show at minimum:
  - rescheduledBy (actor)
  - rescheduledAt (timestamp)
  - reason (enum) and notes (if allowed to display)
  - previous vs new scheduled datetime
  - whether override applied

### Status history
- If backend provides reschedule history (previous schedule times): render in a simple list/table.

### Traceability expectations
- Include `appointmentId` in all frontend logs.
- Propagate correlation/request ID if backend supports it (e.g., `X-Request-Id` or Moqui context).

---

## 14. Non-Functional UI Requirements

- **Performance**: conflict check and submit interactions should keep UI responsive; show loading state; avoid repeated calls on every keystroke (debounce datetime changes if auto-checking).
- **Accessibility**: all form controls have labels; errors are announced; keyboard operable.
- **Responsiveness**: works on tablet widths used in shop environments.
- **i18n/timezone**:
  - Datetime display and input must be consistent with facility timezone (source must be clarified if not provided).
  - No currency handling in this story.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Show explicit empty state when no conflicts or no alternatives are returned; qualifies as UI ergonomics; impacts UX Summary / Alternate Flows.
- SD-UX-LOADING: Standard loading and disabled-submit states during service calls; qualifies as UI ergonomics; impacts Functional Behavior / Error Flows.
- SD-ERR-MAP: Map 400→field errors, 403→unauthorized banner, 409→conflict/concurrency banner, 5xx→retry; qualifies as standard error-handling mapping; impacts Service Contracts / Alternate Flows.

---

## 16. Open Questions

1. **Rewrite Variant conflict**: This story’s domain is `shopmgmt`, but the required variant mapping table does not include `domain:shopmgmt`. Which variant should be used for shopmgmt stories? (Currently set to `workexec-structured` as the closest operational-flow variant, but this needs confirmation.)
2. **Backend service contract names**: What are the exact Moqui services/endpoints for:
   - loading appointment for reschedule
   - conflict checking
   - submitting reschedule
   Provide request/response schemas or entity fields actually exposed.
3. **Timezone source of truth**: Should scheduling input/display use facility timezone from backend, user profile timezone, or browser timezone? Is it returned in appointment payload?
4. **Conflict-check UX contract**: Is conflict checking a separate call returning suggested alternatives, or is it only returned on submit (409 with conflict payload)? Does backend require a “conflictCheckToken/version” to submit?
5. **Notification toggles**: Does the frontend collect `notifyCustomer` and `notifyMechanic`, or is notification behavior determined entirely by backend policy? If toggles exist, what are defaults and when are they forced on (e.g., within 24h)?
6. **Manager approval flow**: For minimum notice (<24h / <4h) and “max 2 reschedules” rules, how is approval represented in the UI?
   - Is it just permission-based self-approval (`APPROVE_RESCHEDULE`)?
   - Or is there an explicit approver identity capture / second factor / workflow?
7. **Audit visibility**: Is there an API to retrieve audit entries for an appointment, or does the reschedule response include audit metadata? What fields are allowed to display (PII constraints)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Appointment: Reschedule Appointment with Notifications — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/75

Labels: frontend, story-implementation, user

### Story Description

/kiro  
# User Story

## Narrative  
As a **Scheduler**, I want to reschedule appointments so that changes are coordinated.

## Details  
- Reschedule maintains link to estimate/workorder.  
- Capture reason and notify downstream systems (optional).

## Acceptance Criteria  
- Reschedule updates shopmgr.  
- Conflicts prevented.  
- Audit includes who/why.

## Integrations  
- Shopmgr update API; workexec sees updated planned time.

## Data / Entities  
- AppointmentUpdate, NotificationOutbox, AuditLog

## Classification (confirm labels)  
- Type: Story  
- Layer: Experience  
- domain :  Point of Sale

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