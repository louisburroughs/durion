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
[FRONTEND] [STORY] Dispatch: Assign Mechanic(s) and Primary Resource (Bay/Mobile) to Appointment

## Primary Persona
Dispatcher

## Business Value
Enables dispatch to allocate labor and a primary work resource to a scheduled appointment with conflict/skills validation (and controlled override), ensuring the schedule is accurate, auditable, and ready for downstream work execution.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Dispatcher  
- **I want** to create or replace an assignment for an appointment by selecting one or more mechanics (lead/helper) and a primary resource (bay/mobile)  
- **So that** the appointment is staffed and resourced without conflicts (or with an authorized override), and the assignment is tracked and auditable.

## In-scope
- View current assignment (active) and assignment history for an appointment.
- Create a new assignment for an appointment using:
  - one primary resource (`resourceId`)
  - one or more mechanics with roles (`LEAD`, `ASSIST`) per rules
  - optional override block (with required reason/notes/checks) when validation fails but override is permitted.
- UX for surfacing validation failures and offering override flow when authorized.
- Frontend handling for backend error codes/statuses: 400/403/404/409/422.
- Basic audit visibility via assignment history listing (who/when/version/status if returned by API).

## Out-of-scope
- Building roster management (mechanics/resources creation, skills administration).
- Editing assignment status transitions (e.g., marking IN_PROGRESS/COMPLETED) unless exposed by existing backend endpoint.
- Full calendar/dispatch board UI; only the assignment workflow from an appointment context.
- Emitting domain events directly (frontend does not emit events; backend does).

---

# 3. Actors & Stakeholders
- **Dispatcher**: primary user creating/reassigning assignments.
- **Mechanic(s)**: assigned personnel; may be impacted by scheduling conflicts.
- **Workshop/Operations Manager**: reviews overrides and utilization (audit).
- **System (Moqui UI)**: orchestrates calls, shows conflicts, captures override justification.

---

# 4. Preconditions & Dependencies
- Appointment exists and is in **`SCHEDULED`** state (hard requirement; not overrideable).
- Appointment has `scheduledStartAt` and `scheduledEndAt` (time window source of truth).
- Backend endpoints available (see §9):
  - `GET /appointments/{appointmentId}/assignments`
  - `POST /appointments/{appointmentId}/assignments`
- AuthN in place; user has permission:
  - Base create: `workexec.assignment.create`
  - Override create (when used): `dispatch:assignment:override_create`
- Data required to populate selection lists (mechanics/resources) must be available to frontend (endpoint(s) TBD if not already present).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From an Appointment detail screen: action/button **“Assign”** (or “Reassign” if active assignment exists).

## Screens to create/modify
1. **Modify**: `apps/pos/screen/Appointment/AppointmentDetail.xml` (name indicative)
   - Add “Assignment” panel showing current active assignment summary + “Assign/Reassign” action.
2. **Create**: `apps/pos/screen/Appointment/Assignment/AssignMechanicsResource.xml`
   - Wizard/dialog-style screen for selecting resource + mechanics and submitting.
3. **Create** (optional if not already in backend): `apps/pos/screen/Appointment/Assignment/AssignmentHistory.xml`
   - Shows list of assignments returned by GET, highlighting active assignment.

> Moqui implementation note: use a dedicated sub-screen under Appointment for assignment flow; route includes `appointmentId` in parameters.

## Navigation context
- Route pattern suggestion:
  - `/appointments/{appointmentId}` → Appointment detail
  - `/appointments/{appointmentId}/assign` → assignment creation screen (modal or full page)
- Breadcrumb: Appointments → Appointment #{appointmentId} → Assign

## User workflows

### Happy path: create assignment (no conflicts)
1. Dispatcher opens appointment detail.
2. Clicks “Assign”.
3. Selects:
   - Primary resource (one)
   - Mechanics (1+)
   - Roles auto/default (single mechanic defaults to LEAD; multi requires explicit roles)
4. Clicks “Create Assignment”.
5. UI shows success, returns to appointment detail, assignment panel updates.

### Alternate path: validation fails; dispatcher uses override
1. Dispatcher completes selections and submits.
2. Backend responds with 409/422 including error code(s).
3. UI displays blocking messages and offers “Override and Create” only if:
   - current user has override permission, and
   - failure category is overrideable, and
   - appointment is still SCHEDULED
4. Dispatcher enters:
   - override reason code
   - override notes
   - selects which checks to override (from backend error code mapping)
5. Submits override request; on success show banner indicating override was used.

### Alternate path: reassignment
- If an active assignment exists, the flow is the same, but UI text indicates it will replace the current assignment; after success, history shows previous assignment marked CANCELLED/SUPERSEDED (as returned by backend).

---

# 6. Functional Behavior

## Triggers
- User clicks “Assign/Reassign” from appointment detail.

## UI actions
- Load assignment history for appointment.
- Collect assignment inputs (resource + mechanics + roles).
- Submit create request (with or without override).
- Display result and navigate back/update context.

## State changes (frontend-visible)
- Appointment screen shows “Assigned” state in UI (display-only).
- Active assignment shown as:
  - resource
  - mechanics list with roles
  - status (expected `CONFIRMED` on create)
  - version (monotonic; create=1)
- If reassignment: prior active assignment shown as `CANCELLED`/`SUPERSEDED` depending on backend.

## Service interactions
- `GET /appointments/{appointmentId}/assignments` to show active/history.
- `POST /appointments/{appointmentId}/assignments` to create assignment.
- No frontend-side availability/skills computation; backend is source of truth.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation (client-side: basic; server-side: authoritative)
Client-side must validate before submit:
- Required:
  - `resourceId` selected
  - at least one `mechanic` selected
- Roles:
  - if exactly 1 mechanic: role field optional; UI may default to LEAD but should allow submission without explicit role.
  - if >1 mechanics:
    - each mechanic must have a role selected
    - exactly one LEAD
- Override block:
  - if override toggle enabled:
    - `reasonCode` required
    - `notes` required (non-empty)
    - `overriddenChecks[]` must be non-empty

Server-side errors must be surfaced clearly (see §11).

## Enable/disable rules
- Disable “Create Assignment” until required fields valid.
- Show/enable “Override” section only after a failed submission OR if user explicitly chooses “Create with override” and has permission (see Open Questions for whether proactive override is allowed).
- Disable override option if backend indicates hard-block (appointment not SCHEDULED) or if user lacks override permission.

## Visibility rules
- Show assignment history section only if GET returns items; otherwise show empty state “No assignments yet”.
- If active assignment exists, show “Reassign” label and warning text: “Creating a new assignment will replace the active assignment.”

## Error messaging expectations
- Map backend error codes to user-facing messages:
  - `MECHANIC_UNAVAILABLE` → “One or more selected mechanics are unavailable in the appointment time window.”
  - `RESOURCE_UNAVAILABLE` → “Selected resource is unavailable in the appointment time window.”
  - `SKILL_MISMATCH` → “Selected team does not meet required skills for this appointment.”
  - `INVALID_ROLE_SET` → “For multiple mechanics, select exactly one Lead and assign roles for all mechanics.”
  - `OVERRIDE_NOT_PERMITTED` (403) → “You don’t have permission to override scheduling checks.”
- Always display the appointment time window in conflict messages (start/end) to reduce ambiguity.

---

# 8. Data Requirements

## Entities involved (frontend concern; backend SoR)
- Appointment (read): `appointmentId`, `status`, `scheduledStartAt`, `scheduledEndAt`
- Assignment (read/write via API): `assignmentId`, `appointmentId`, `resourceId`, `status`, `version`, override fields
- AssignmentMechanic (read/write via API): `mechanicId`, `role`
- Mechanic roster + skills (read for selection UI) — **endpoint/entity TBD**
- Resource roster (bays/mobile units) (read for selection UI) — **endpoint/entity TBD**

## Fields (type, required, defaults)

### Create Assignment request (frontend payload)
- `resourceId` (UUID/string) — required
- `mechanics` (array) — required, min 1  
  - `mechanicId` (UUID/string) — required
  - `role` (enum `LEAD|ASSIST`) — optional if mechanics length == 1, required otherwise
- `override` (object) — optional
  - `used` (boolean) — required if override object present
  - `overriddenChecks` (array enum) — required if `used=true`, min 1
  - `reasonCode` (enum) — required if `used=true`
  - `notes` (string) — required if `used=true`

### Display fields (from GET assignments)
Minimum required for UI:
- `assignmentId`
- `status` (`CONFIRMED|IN_PROGRESS|COMPLETED|CANCELLED`)
- `version`
- `resourceId` (+ optionally resource display name)
- `mechanics[]` with role
- audit-ish fields if provided: `createdAt`, `createdBy`, `isOverridden`, `overrideReasonCode`

## Read-only vs editable
- Existing assignments are read-only in this story (no status edits).
- Only create/reassign via POST.

## Derived/calculated fields (frontend)
- “Active assignment” derived as first assignment with status in (`CONFIRMED`,`IN_PROGRESS`) returned by GET (or explicit `active` flag if backend provides).

---

# 9. Service Contracts (Frontend Perspective)

## Load/view calls

### Get assignment history (and active)
- **Method/Path**: `GET /appointments/{appointmentId}/assignments`
- **Success**: `200 OK` returns list (may be empty)
- **Frontend expectations**:
  - includes enough fields to render active summary + history (see §8)
  - order: newest-first preferred (if not specified, frontend sorts by `createdAt` if available)

## Create/update calls

### Create assignment (also used for reassignment)
- **Method/Path**: `POST /appointments/{appointmentId}/assignments`
- **Request body**: as defined in §8
- **Success**: `201 Created`
  - returns created assignment (preferred) OR an id reference; frontend then refreshes via GET.
- **Errors**:
  - `400 Bad Request`: malformed/missing required fields
  - `403 Forbidden`: permission denied; include code `OVERRIDE_NOT_PERMITTED` when applicable
  - `404 Not Found`: appointment not found
  - `409 Conflict`: availability conflict; include code `MECHANIC_UNAVAILABLE` or `RESOURCE_UNAVAILABLE`
  - `422 Unprocessable Entity`: skills/role validation; include code `SKILL_MISMATCH` or `INVALID_ROLE_SET`
  - `409 Conflict` may also be used for “one active assignment per appointment” race conditions

## Submit/transition calls
- None beyond POST create.

## Error handling expectations (UI)
- For 409/422: show inline, actionable errors; keep user selections.
- For 403: show blocking toast/banner and disable override UI.
- For 404: navigate back to appointments list with message “Appointment no longer exists.”
- For network/5xx: show retry option; do not clear entered data.

---

# 10. State Model & Transitions

## Appointment state constraints (hard block)
- Assignment creation allowed only when appointment is `SCHEDULED`. If not scheduled:
  - hide/disable “Assign/Reassign”
  - if user reaches screen via deep link, screen must show blocking message and disable submit.

## Assignment statuses (display semantics)
- `CONFIRMED`, `IN_PROGRESS` → considered “active” and block availability
- `COMPLETED`, `CANCELLED` → historical; do not block availability

## Role-based transitions (frontend gating)
- Dispatcher with `workexec.assignment.create` can attempt creation.
- Override submission requires `dispatch:assignment:override_create` and completion of override fields.

> Note: Work order/workexec events and state transitions are backend-managed.

---

# 11. Alternate / Error Flows

## Validation failures (client-side)
- Missing resource → show “Select a resource.”
- No mechanics → show “Select at least one mechanic.”
- Multi-mechanic without roles / multiple leads → show role rule message; block submit.

## Backend conflicts (409)
- Mechanic unavailable:
  - Show list of selected mechanics (by display name if available) and message referencing window.
  - Offer override path if permitted.
- Resource unavailable:
  - Highlight selected resource; offer override if permitted.

## Backend semantic issues (422)
- Skill mismatch:
  - Show message; do not auto-change selection.
  - Offer override if permitted and backend categorizes as overrideable.
- Invalid role set:
  - Force user to correct roles; do not show override (overrideability for INVALID_ROLE_SET is **not** listed; treat as non-overrideable).

## Concurrency conflicts
- If POST fails with conflict due to “active assignment already exists” (race):
  - UI refreshes assignment history and informs user “Assignment was updated by someone else; review current assignment before retrying.”

## Unauthorized access
- If user lacks create permission:
  - hide assign action from appointment detail (preferred)
  - if deep-linked, show 403 state with no submit.

## Empty states
- No assignments returned: show empty history state.
- No mechanics/resources available for selection: show empty picker state and guidance (contact admin).

---

# 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create assignment with single mechanic (defaults to LEAD)
**Given** an appointment is in `SCHEDULED` state with a defined start and end time  
**And** I have permission `workexec.assignment.create`  
**When** I select a primary resource and exactly one mechanic  
**And** I submit create assignment without specifying a role  
**Then** the system creates the assignment successfully  
**And** the appointment shows an active assignment with status `CONFIRMED`  
**And** the mechanic is shown as `LEAD` in the assignment display (either returned by API or inferred from response)

### Scenario 2: Create assignment with multiple mechanics requires exactly one Lead
**Given** an appointment is `SCHEDULED`  
**When** I select two mechanics  
**And** I do not assign roles for both mechanics  
**Then** the UI prevents submission and shows an error indicating roles are required  
**When** I set exactly one mechanic to `LEAD` and the other to `ASSIST`  
**Then** submission is enabled

### Scenario 3: Backend rejects due to mechanic availability conflict
**Given** an appointment is `SCHEDULED`  
**And** I have permission `workexec.assignment.create`  
**When** I submit an assignment that conflicts with an existing mechanic booking  
**Then** I see an error message indicating mechanic unavailability for the appointment time window  
**And** my selected inputs remain intact for editing

### Scenario 4: Override flow allowed and succeeds
**Given** an appointment is `SCHEDULED`  
**And** I have permissions `workexec.assignment.create` and `dispatch:assignment:override_create`  
**When** I submit an assignment that fails with error code `MECHANIC_UNAVAILABLE`  
**Then** the UI offers an override option  
**When** I enable override and provide a reason code, notes, and select `MECHANIC_AVAILABILITY` in overridden checks  
**And** I resubmit  
**Then** the assignment is created successfully  
**And** the UI indicates the assignment was created with an override (e.g., “Override used” banner)

### Scenario 5: Override not permitted
**Given** an appointment is `SCHEDULED`  
**And** I have permission `workexec.assignment.create` but not `dispatch:assignment:override_create`  
**When** I submit an assignment and backend responds `403 Forbidden` with code `OVERRIDE_NOT_PERMITTED`  
**Then** the UI shows a permission error  
**And** the override UI is not available  
**And** no assignment is created

### Scenario 6: Hard block when appointment is not SCHEDULED
**Given** an appointment is not in `SCHEDULED` state  
**When** I navigate to the assign screen  
**Then** the UI displays a blocking message stating assignments can only be created for scheduled appointments  
**And** the submit action is disabled

### Scenario 7: Reassignment replaces active assignment
**Given** an appointment is `SCHEDULED` and currently has an active assignment  
**When** I create a new assignment for the same appointment  
**Then** the appointment shows only one active assignment  
**And** the previous assignment appears in history with a non-active status (e.g., `CANCELLED`/`SUPERSEDED`) as returned by the backend

---

# 13. Audit & Observability

## User-visible audit data
- Assignment history list must show (when provided by API):
  - created timestamp
  - created by (user display name/id)
  - status + version
  - override indicator + reason code (if overridden)

## Status history
- Use `GET /appointments/{appointmentId}/assignments` as the primary source for history.
- If the API includes versioning, display version to support traceability.

## Traceability expectations
- Frontend must include correlation/request ID if project convention exists (Moqui often uses request attributes; otherwise log locally).
- On create success/failure, log structured console/app log entries with appointmentId and error code (no PII).

---

# 14. Non-Functional UI Requirements

- **Performance**: assignment history load should complete within 2s on typical LAN; show loading state.
- **Accessibility**: all form controls labeled; errors associated with fields; keyboard navigable role selection.
- **Responsiveness**: usable on tablet width; mechanics list supports scrolling.
- **i18n/timezone**: display appointment times in shop/location timezone if available; otherwise user timezone (no guessing—use existing app convention).

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for “no assignments”, “no mechanics”, “no resources” to avoid blank panels; qualifies as safe UI ergonomics. (Impacted: UX Summary, Alternate/waswo Flows)
- SD-ERR-STD-MAPPING: Map HTTP 400/403/404/409/422/5xx into consistent banner + inline field errors while preserving form state; qualifies as safe error-handling boilerplate. (Impacted: Service Contracts, Alternate / Error Flows, Acceptance Criteria)

---

# 16. Open Questions
1. What existing backend endpoints (or Moqui services) should the frontend use to **list/select mechanics and resources** (including display names, skills, and availability hints)? If none exist, should this story include building minimal selection endpoints or is it dependent on another story?
2. Does the backend `GET /appointments/{appointmentId}/assignments` return **createdAt/createdBy/isOverridden/overrideReasonCode** fields needed for the audit/history UI, or should the frontend display a reduced history?
3. Should the UI allow a **proactive override** toggle before first submission, or only after receiving an overrideable failure response?
4. What is the canonical **Appointment detail route/screen name** in this Moqui frontend repo (so implementation wires into the correct navigation and menu patterns)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/134  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to assign mechanics and a resource to an appointment so that work can be executed efficiently.

## Details
- Validate mechanic/resource availability + required skills.
- Support team assignment (lead/helper).

## Acceptance Criteria
- Assignment created only if checks pass (or override).
- Changes audited.
- Schedule updated.

## Integrations
- Emit AssignmentCreated/Updated to workexec when linked.

## Data / Entities
- Assignment, AssignmentRole, AuditLog

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