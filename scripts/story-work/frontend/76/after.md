STOP: Clarification required before finalization

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

---

## 1. Story Header

**Title:** [FRONTEND] Appointment: Create Appointment from Estimate or Work Order (Shop Scheduler)

**Primary Persona:** Scheduler (Service Advisor / Dispatcher)

**Business Value:** Enables schedulers to convert an approved estimate or active work order into a scheduled appointment with resource requirements, conflict visibility, and immutable source linkage—reducing manual entry and preventing double-booking.

---

## 2. Story Intent

### As a / I want / So that
As a **Scheduler**, I want to **create an appointment from an estimate or work order** so that **the work is scheduled with the right duration, skills, and bay/mobile requirements, and conflicts are surfaced before confirming**.

### In-scope
- Entry flow to create an appointment from:
  - an **Estimate** (eligible statuses) or
  - a **Work Order** (eligible statuses)
- Pre-population of appointment request fields:
  - service description (read-only display)
  - duration minutes (WorkExec-authoritative when available)
  - required/preferred skill tags
  - bay type requirements and/or mobile eligibility
- Interactive scheduling selection:
  - pick one scheduled start date/time (from allowed date range)
  - optionally choose bay preference and mechanic preference (if exposed by backend)
- Conflict check presentation and override flows:
  - hard vs soft conflicts
  - override permission + mandatory reason when applicable
- Successful create confirmation and navigation to appointment detail

### Out-of-scope
- Rescheduling an existing appointment (separate story)
- Editing assignment post-create (mechanic/bay changes) beyond what create flow allows
- Dispatch optimization / automatic slot selection beyond backend “suggested alternatives”
- Customer notification management UI (only show result/status if returned)
- Defining facility hours, bay inventory, or assignment strategies (configuration)

---

## 3. Actors & Stakeholders
- **Primary user:** Scheduler
- **Secondary users:** Shop Manager (may override), Dispatcher
- **Systems:**
  - Shop Management (shopmgmt) backend API (system of record)
  - Work Execution service (duration/skills authority, via shopmgmt)
  - People/HR service (mechanic qualification/availability, via shopmgmt)
  - Audit service (via shopmgmt)
  - Notification service (via shopmgmt)

---

## 4. Preconditions & Dependencies
- User is authenticated in POS and has facility context selected (facility scoping required).
- User has permission `CREATE_APPOINTMENT`.
- Source exists and is eligible:
  - Estimate in APPROVED/QUOTED (exact enum values must match backend)
  - Work order not COMPLETED/CANCELLED
- Source is not already linked to an active appointment unless `allowsMultipleAppointments=true` (backend-controlled).
- Dependency: shopmgmt backend endpoints for:
  - viewing source summary & eligibility
  - conflict checking (or included as part of create)
  - creating appointment (with optional override)
- Dependency: UI routes/screen patterns as defined in `durion-moqui-frontend` README (Moqui screens, transitions, forms).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
At least one of:
- From **Estimate detail**: action “Create Appointment”
- From **Work Order detail**: action “Create Appointment”
- (Optional) Global scheduler entry with source search is out-of-scope unless already present.

### Screens to create/modify
1. **Modify**: Estimate detail screen — add transition/action to appointment create
2. **Modify**: Work order detail screen — add transition/action to appointment create
3. **Create**: `AppointmentCreate` screen (source-driven)
4. **Create/Modify**: `AppointmentDetail` screen (or existing appointment view) for post-create navigation

> Moqui expectation: implement as a screen with a form for request fields, transitions for “Check Conflicts” and “Create Appointment”, and conditional sections for conflict results and override input.

### Navigation context
- Breadcrumb includes Source → Create Appointment.
- On success: navigate to Appointment Detail (appointmentId).

### User workflows
**Happy path**
1. Scheduler clicks “Create Appointment” on eligible source.
2. Create screen loads pre-populated defaults (duration, skills, bay type requirements).
3. Scheduler chooses scheduled start date/time.
4. Scheduler clicks “Check Conflicts” (or conflicts checked automatically—TBD).
5. No conflicts → clicks “Create Appointment”.
6. Confirmation shown; redirect to appointment detail.

**Alternate paths**
- Soft conflicts returned → show warning list + suggested alternatives; scheduler may proceed (with optional reason if required by backend).
- Hard conflicts returned → block create; if user has `OVERRIDE_SCHEDULING_CONFLICT` and backend marks conflict overridable, allow override with mandatory reason.
- WorkExec duration mismatch warning shown → allow scheduler override within allowed threshold with reason (if implemented in UI; otherwise keep duration read-only and display warning).

---

## 6. Functional Behavior

### Triggers
- User opens the create screen from an estimate/work order.
- User changes scheduled date/time or resource preferences.
- User requests conflict check (button) and/or submits create.

### UI actions
- **Load**: fetch source summary + appointment defaults.
- **Edit inputs**:
  - scheduledStartDateTime (required)
  - durationMinutes (editable only if backend allows override; otherwise read-only)
  - requiredSkills/preferredSkills (usually read-only; clarify)
  - bayTypeRequired / mobile flag (read-only if driven by service; clarify)
  - bay preference (optional)
  - mechanic preference (optional)
- **Check conflicts**: calls backend conflict check with current draft inputs.
- **Create**:
  - if conflicts present: enforce blocking rules
  - collect override flag + override reason when allowed
  - submit create request
- **Success**: show confirmation banner + navigate to appointment detail.
- **Failure**: map backend errors to form-field errors or page-level alerts.

### State changes (frontend-observable)
- Draft state in UI: `draft` → `conflicts_checked` → `ready_to_submit` (client-side only)
- Created appointment returns authoritative `appointmentId`, `appointmentStatus`, `assignmentStatus`.

### Service interactions (frontend perspective)
- `GET` source+defaults (or `GET` source then `GET` defaults)
- `POST` conflictCheck (optional but recommended)
- `POST` createAppointment

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client + server)
Client-side (non-authoritative) validations:
- scheduledStartDateTime required and must be in the future (exact rule: at least “now”; clarify if minimum notice differs for create).
- If overrideConflict=true → overrideReason required (non-empty, min length TBD).

Server-side enforcement (display results):
- Source eligibility enforced; show blocking message if not eligible.
- Conflict results:
  - **Hard conflicts**: disable “Create Appointment” unless override permitted and user has permission.
  - **Soft conflicts**: allow “Create Appointment”; show warnings and suggested alternatives.
- Duration authority / overrides:
  - If backend returns “durationMismatch” warning, display it.
  - If backend allows editing duration:
    - allow override within ±25% with reason required
    - beyond that require `APPROVE_DURATION_OVERRIDE` (UI should block or request elevated permission—TBD)

### Enable/disable rules
- Disable “Create Appointment” until:
  - scheduledStartDateTime set
  - conflicts checked successfully OR backend indicates conflict check is embedded in create response (TBD)
- If conflicts include non-overridable hard conflicts: disable submit.
- If overridable hard conflicts: enable submit only when:
  - user has permission AND overrideReason provided.

### Visibility rules
- Show “Conflict Results” section only after conflict check or after create attempt returns conflicts.
- Show “Override” inputs only when conflicts exist and backend indicates override allowed and user has permission.

### Error messaging expectations
- Permission denied → “You don’t have permission to create appointments for this facility.”
- Source already linked → “This estimate/work order is already linked to an active appointment.”
- Conflict hard block → show specific conflict codes/messages and suggested alternatives if provided.

---

## 8. Data Requirements

### Entities involved (frontend model)
- AppointmentRequest
- AppointmentRef (response)
- ConflictResult (response)
- SourceSummary (estimate/work order summary)

> Note: Actual Moqui entities and service names must match backend; treat these as view-models unless Moqui entities exist in frontend layer.

### Fields
**AppointmentRequest**
- `sourceType` (enum: `ESTIMATE` | `WORK_ORDER`) **required**
- `sourceId` (string/UUID) **required**
- `facilityId` (string/UUID) **required** (from session/context)
- `scheduledStartDateTime` (datetime, ISO-8601 w/ timezone) **required**
- `preferredDateTimeOptions[]` (datetime[]) optional (if UI supports multiple suggestions; likely omit for MVP)
- `durationMinutes` (int) required if editable; otherwise omit and let backend decide (TBD)
- `requiredSkills[]` (string[]) read-only display (TBD if editable)
- `preferredSkills[]` (string[]) read-only display (optional)
- `bayTypeRequired` (enum) read-only display (TBD)
- `isMobile` (bool) read-only display (TBD)
- `bayPreferenceId` (string) optional (if supported)
- `mechanicPreferenceId` (string) optional (if supported)
- `overrideConflict` (bool) optional, default false
- `overrideReason` (string) conditionally required
- `clientRequestId` (string) optional for idempotency (recommended; see Open Questions)

**AppointmentRef (response)**
- `appointmentId` **required**
- `appointmentStatus` (enum) required
- `assignmentStatus` (enum) required
- `scheduledStartDateTime`
- `estimatedDurationMinutes`
- `assignedBay` (nullable)
- `assignedMechanic` (nullable)
- `estimateId` (nullable)
- `workOrderId` (nullable)

### Read-only vs editable by state/role
- Scheduler:
  - editable: scheduledStartDateTime
  - editable only if permitted: overrideConflict, overrideReason, durationMinutes (if backend supports)
  - read-only: source linkage fields, required skills, bay type requirements
- Manager:
  - may see/perform overrides if permission present

### Derived/calculated fields
- “Duration mismatch” warning (derived from backend response)
- Conflict summary counts by severity (derived from ConflictResult)

---

## 9. Service Contracts (Frontend Perspective)

> Backend API details are marked TBD in domain guide; frontend must not guess exact endpoints. Implement via Moqui service calls consistent with existing project patterns (Open Questions).

### Load/view calls
- `loadSourceForAppointmentCreate(sourceType, sourceId, facilityId)` → returns:
  - source summary (customer/vehicle/service description)
  - eligibility flags / reasons
  - default duration + mismatch warnings
  - required/preferred skills
  - bay/mobile requirements
  - whether multiple appointments allowed

### Conflict check calls
- `checkAppointmentConflicts(appointmentDraft)` → returns ConflictResult:
  - `hasConflicts`
  - list of conflicts with:
    - `severity` (HARD|SOFT)
    - `code`
    - `message`
    - `overridable` (bool)
  - `suggestedAlternatives[]` (datetime options and/or bay/mechanic suggestions)

### Create/update calls
- `createAppointmentFromSource(appointmentRequest)` → returns AppointmentRef or error payload including conflicts.

### Error handling expectations
- Validation errors: map field errors to inputs (scheduledStartDateTime, overrideReason, durationMinutes).
- Permission errors: show blocking banner; do not retry.
- Concurrency/duplicate link: show error with action to open existing appointment if appointmentId returned.
- Service unavailable (WorkExec/People): show degraded warning if backend returns partial data; still allow create if backend allows.

---

## 10. State Model & Transitions

### Allowed states (display-only)
- `appointmentStatus`: (TBD by backend; display as returned)
- `assignmentStatus`: e.g., `ASSIGNED`, `DEFERRED`, `AWAITING_SKILL_FULFILLMENT` (display as returned)

### Role-based transitions (frontend)
- Scheduler with `CREATE_APPOINTMENT` can submit create.
- Override path requires `OVERRIDE_SCHEDULING_CONFLICT`.
- Duration override beyond threshold requires `APPROVE_DURATION_OVERRIDE` (if UI supports duration edit).

### UI behavior per state
- If returned `assignmentStatus=AWAITING_SKILL_FULFILLMENT`, show prominent banner: “Appointment created but requires skill resolution.”
- If `assignedBay` null, indicate “Bay assignment deferred.”

---

## 11. Alternate / Error Flows

- **Ineligible source**
  - Show reasons and disable submit
  - Provide link back to source
- **Conflicts present**
  - Soft: allow proceed; show warnings
  - Hard non-overridable: block submit; show alternatives if available
  - Hard overridable: require permission + reason
- **Concurrent link created by another user**
  - Create returns duplicate/link error; show existing appointment link if provided
- **Unauthorized**
  - On load or submit, show access denied; do not render sensitive data
- **Empty states**
  - No suggested alternatives: show “No alternatives available; adjust date/time.”

---

## 12. Acceptance Criteria

### Scenario 1: Create appointment from eligible estimate with no conflicts
Given I am a Scheduler with `CREATE_APPOINTMENT` in facility F  
And I open “Create Appointment” from an estimate in an eligible status  
When I select a scheduled start date/time  
And I run conflict check  
And no conflicts are returned  
And I submit create  
Then an appointment is created  
And the response includes an appointmentId and immutable link to the estimate  
And I see a success confirmation  
And I am navigated to the appointment detail screen.

### Scenario 2: Soft conflicts are shown but allow creation
Given I am a Scheduler with `CREATE_APPOINTMENT`  
And conflict check returns one or more SOFT conflicts  
When I review the conflict list  
And I submit create without override permission  
Then the appointment is created successfully  
And the conflicts are recorded by backend (implied)  
And the UI displays that conflicts existed (warning banner) and shows the appointment detail.

### Scenario 3: Hard conflict blocks creation without override
Given I am a Scheduler with `CREATE_APPOINTMENT` but without `OVERRIDE_SCHEDULING_CONFLICT`  
And conflict check returns a HARD conflict marked non-overridable  
When I attempt to submit create  
Then the UI blocks submission  
And I see the hard conflict details and any suggested alternatives.

### Scenario 4: Hard conflict can be overridden with permission and reason
Given I am a Scheduler with `CREATE_APPOINTMENT` and `OVERRIDE_SCHEDULING_CONFLICT`  
And conflict check returns a HARD conflict marked overridable  
When I choose to override  
And I provide an override reason  
And I submit create  
Then the appointment is created  
And I see confirmation that an override was applied (based on backend response).

### Scenario 5: Source already linked to active appointment
Given I open “Create Appointment” from a source already linked to an active appointment  
When the create screen loads (or when I attempt to create)  
Then I see a blocking message indicating the source is already linked  
And I am provided a link to view the existing appointment if appointmentId is available.

---

## 13. Audit & Observability
- UI must display (when returned by backend):
  - createdBy/createdAt (if present in response)
  - override flag and override reason summary (if present)
- Frontend observability:
  - Log screen entry with sourceType/sourceId (no PII)
  - Log conflict check outcome counts (hard/soft) (no PII)
  - Log create success/failure with appointmentId when available
  - Propagate correlation/request IDs if the frontend framework supports it (TBD by repo conventions)

---

## 14. Non-Functional UI Requirements
- **Performance:** initial load and conflict check should feel responsive; avoid repeated polling.
- **Accessibility:** all form fields labeled; conflict list readable by screen readers; focus moves to first error on submit failure.
- **Responsiveness:** usable on tablet widths common in shops.
- **i18n/timezone:** datetimes displayed in facility/local timezone; ensure ISO submission to backend (timezone handling must match backend expectations—TBD).
- **Security:** do not expose mechanic/customer PII in logs; enforce permission-gated rendering.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-01: Provide empty-state copy when no conflicts/suggestions are returned; qualifies as UI ergonomics; impacts UX Summary, Error Flows.
- SD-UX-ERRORMAP-01: Standard mapping of backend validation errors to field-level messages and top banner for non-field errors; qualifies as standard error-handling; impacts Service Contracts, Error Flows.
- SD-OBS-BOILER-01: Emit basic frontend logs/events for screen load, conflict check, and create submit with correlation ID (no PII); qualifies as observability boilerplate; impacts Audit & Observability.

---

## 16. Open Questions
1. What are the **exact Moqui screen route conventions** in this repo for Estimate Detail, Work Order Detail, Appointment Create, and Appointment Detail (screen paths and parameter names)?
2. What are the **actual backend endpoints/service names** for:
   - load defaults/eligibility
   - conflict check
   - create appointment  
   (Need request/response schemas and error code shapes for implementation.)
3. Is **conflict checking required as a separate call** before create, or is it embedded in create (returning conflicts and requiring a second “confirm override” submit)?
4. Is `durationMinutes` **editable in the UI**? If yes:
   - what is the allowed override range for Scheduler without approval?
   - what permission gates the wider override?
   - is override reason mandatory for any duration change?
5. Are `requiredSkills`, `bayTypeRequired`, and `isMobile` **editable** at create time, or strictly derived from source/service catalog?
6. What are the **exact eligible statuses** for estimates and work orders (enum values) that frontend should display and interpret?
7. Should frontend generate and send a **clientRequestId/idempotency key** for create to prevent duplicate appointments on retries? If yes, what header/field name?
8. How should facility **operating hours** be surfaced in UI when backend blocks scheduling outside hours (message only vs show hours + next available)?
9. What fields are returned in AppointmentRef needed for the confirmation screen (appointment number, scheduled time display, assignment summary)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Appointment: Create Appointment from Estimate or Order  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/76  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Appointment: Create Appointment from Estimate or Order

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Scheduler**, I want to create an appointment from an estimate/order so that the work is scheduled and resources assigned.

## Details
- Appointment includes preferred times, service duration, skill tags, bay/mobile requirements.
- Linked to estimate/workorder if applicable.

## Acceptance Criteria
- Appointment created with confirmation.
- Linked refs stored.
- Conflicts surfaced.

## Integrations
- Shopmgr appointment create API; workexec duration hints (optional).

## Data / Entities
- AppointmentRequest, AppointmentRef, ConflictResult

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