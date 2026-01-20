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

### Title
[FRONTEND] [STORY] People: Assign Person to Location with Primary Flag and Effective Dates

### Primary Persona
Manager (people/location administrator)

### Business Value
Ensures accurate, effective-dated employee-to-location eligibility so downstream systems (workexec/shopmgr) can build rosters and enforce staffing eligibility based on a single source of truth.

---

## 2. Story Intent

### As a / I want / So that
**As a** Manager,  
**I want** to assign an employee (Person) to one or more Locations with effective dates and a primary designation,  
**so that** operations can reliably determine where the employee is eligible to work and which location is primary at any point in time.

### In-scope
- Moqui frontend screens to:
  - View a Person’s location assignments (active + historical).
  - Create a new `PersonLocationAssignment` (with effective dates and primary flag).
  - Update an existing assignment (effective dates and primary flag, plus optional reason code if supported).
  - End an assignment via setting `effectiveEndAt` (no hard delete).
- UI validation and clear error handling for:
  - Effective date validity.
  - Overlap conflicts.
  - Primary assignment rule feedback (including automatic demotion behavior as reported by backend).
- Audit visibility (read-only) of assignment change metadata where exposed (created/updated by/at; version).

### Out-of-scope
- Defining/altering permission matrices or authentication behavior.
- Implementing backend event emission, saga retries, or downstream integration logic.
- Location creation/management.
- Person/employee profile creation/updates beyond selecting an existing Person.

---

## 3. Actors & Stakeholders
- **Manager (Actor):** Creates/updates/ends assignments for employees.
- **Employee/Technician (Subject):** The Person being assigned.
- **Workexec / Shopmgr (Stakeholders):** Consume assignment data; not directly interacted with via UI in this story.
- **Auditor/HR Admin (Stakeholder):** Needs traceability of changes (audit metadata).

---

## 4. Preconditions & Dependencies
- User is authenticated.
- User has permission to manage person location assignments (exact permission string TBD; see Open Questions).
- Person exists and is selectable/viewable in frontend.
- Location list is available for selection (source endpoint/screen TBD).
- Backend provides APIs/services to:
  - Query assignments by person.
  - Create assignment.
  - Update assignment / end assignment.
  - Enforce overlap + primary constraints and return actionable errors.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From People/Employees list: select a Person → “Location Assignments” section/tab.
- Direct route (deep link): `/people/person/{personId}/locations` (route naming TBD to match repo conventions).

### Screens to create/modify
1. **Modify existing Person detail screen** (or create if missing):
   - Add a “Location Assignments” subsection with an embedded list and actions.
2. **Add/edit assignment dialog/screen**:
   - Used for create and update flows (same form, mode-driven).
3. **End assignment confirmation dialog**:
   - Sets `effectiveEndAt` rather than deleting.

### Navigation context
- Breadcrumb: People → Person → Location Assignments
- Back navigation returns to Person detail.

### User workflows
**Happy path (Create primary assignment)**
1. Manager opens Person → Location Assignments.
2. Clicks “Add Assignment”.
3. Selects Location, enters Role (if required by backend), sets `isPrimary=true`, sets `effectiveStartAt`, optional `effectiveEndAt`.
4. Saves; UI refreshes list, showing new assignment as active/primary.
5. If backend demoted an existing primary, UI reflects updated previous assignment end date/primary status after refresh.

**Alternate path (Create non-primary secondary assignment)**
- Same as above but `isPrimary=false`; primary remains unchanged.

**Alternate path (End assignment)**
- Manager selects an active assignment → “End Assignment”.
- Provides end timestamp (default now) and confirms.
- Assignment shows ended/inactive.

**Alternate path (Update dates / toggle primary)**
- Manager opens existing assignment → edit → adjusts effective dates or primary flag → save.

---

## 6. Functional Behavior

### Triggers
- Entering Location Assignments view triggers load of person + assignments.
- Clicking add/edit/end triggers corresponding form actions and service calls.

### UI actions
- List actions:
  - “Add Assignment”
  - “Edit” (for assignments that are not immutable per backend policy; see Open Questions)
  - “End” (sets end date)
  - Filter toggles: Active only / Include historical
- Form fields (minimum):
  - Location (required)
  - Effective start (required)
  - Effective end (optional)
  - Primary checkbox (required boolean)
  - Role (string) **only if required by backend contract** (currently ambiguous; see Open Questions)
  - Change reason code (optional) **only if backend supports** (ambiguous)

### State changes (frontend)
- Local form state transitions: pristine → editing → submitting → success/error.
- On success: invalidate/reload assignment list; show toast/banner “Assignment saved”.
- On conflict/validation error: show inline field errors and/or a banner.

### Service interactions
- Load:
  - Get person summary (name, status) for header/context.
  - Get assignments for personId with optional filters (active/historical).
- Mutations:
  - Create assignment.
  - Update assignment OR end assignment (update with `effectiveEndAt`) depending on backend.
- Concurrency:
  - If optimistic locking/version is required, include `version` on update; handle 409 conflict.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `locationId` required.
- `effectiveStartAt` required, must be valid ISO-8601 timestamp input.
- If `effectiveEndAt` provided: must be ≥ `effectiveStartAt`.
- If backend enforces no overlap for `(personId, locationId, role)`:
  - On save, handle 409/400 with message “Overlapping assignment exists for this person/location/role during the selected dates.”
- If backend enforces single primary at a time:
  - UI allows selecting primary, but must warn that setting primary may demote another primary (do not compute demotion in UI; backend is SoR).
  - On save success, UI reloads and shows the resulting primary assignment(s).

### Enable/disable rules
- Disable Save while submitting or when required fields missing.
- Disable “End” action for already-ended assignments.
- If backend disallows editing past assignments, UI must hide/disable “Edit” for ended assignments after learning rule (see Open Questions).

### Visibility rules
- Active assignments shown by default; historical expandable/toggle.
- Primary indicator shown in list (e.g., “Primary” badge/column).

### Error messaging expectations
- 400 validation errors: show field-level errors where possible; otherwise show banner with backend message.
- 403: show “You do not have permission to manage location assignments.”
- 404 (person not found): show not-found empty state and navigation back.
- 409 overlap or version conflict: show conflict banner with recommended next step (“Refresh and try again”).

---

## 8. Data Requirements

### Entities involved
- `Person` (read-only context)
- `Location` (for selection/display)
- `PersonLocationAssignment` (create/update/query)

### Fields (type, required, defaults)
**PersonLocationAssignment (frontend view model)**
- `assignmentId` (UUID, read-only)
- `personId` (UUID, required; from route/context)
- `locationId` (UUID, required, editable)
- `role` (string, required?) **TBD**
- `isPrimary` (boolean, required, editable; default false)
- `effectiveStartAt` (timestamp/ISO-8601 UTC, required, editable; default now in UI only)
- `effectiveEndAt` (timestamp/ISO-8601 UTC, optional, editable)
- `changeReasonCode` (string, optional, editable if supported)
- `version` (integer, read-only on create; required on update if backend uses optimistic locking)
- `createdAt/createdBy/updatedAt/updatedBy` (read-only display if provided)

### Read-only vs editable by state/role
- By default (until clarified):
  - Active assignments: editable dates and primary flag; ending allowed.
  - Ended assignments: read-only (no edit), but viewable.
- Role-based editability is permission-gated; exact permissions TBD.

### Derived/calculated fields
- `isActive` derived in UI for display: based on `effectiveStartAt`, `effectiveEndAt`, and current time (display only; backend remains authoritative).
- “Primary at time range” is not computed beyond display of `isPrimary` on returned records.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend contract endpoints/services are not provided in the frontend issue; Moqui may expose these as REST, JSON-RPC, or service calls. This story requires confirmation of exact service names/paths.

### Load/view calls
- `GET /people/person/{personId}` (or Moqui screen/service) → person summary.
- `GET /people/person/{personId}/location-assignments?activeOnly=true|false` → list assignments.
- `GET /locations?status=ACTIVE` → location picker dataset.

### Create/update calls
- Create:
  - `POST /people/person/{personId}/location-assignments`
  - Body: `locationId, role?, isPrimary, effectiveStartAt, effectiveEndAt?, changeReasonCode?`
- Update:
  - `PUT /people/location-assignments/{assignmentId}` (or POST action)
  - Body includes fields being updated + `version` if required.

### Submit/transition calls
- End assignment:
  - Either a dedicated action `POST /people/location-assignments/{assignmentId}/end` with `effectiveEndAt`
  - Or standard update with `effectiveEndAt` set.

### Error handling expectations
- `400` invalid timestamps/required fields → display inline errors.
- `403` permission denied → show blocking banner, disable mutation actions.
- `404` not found → show not found state.
- `409` overlap or optimistic locking conflict → show conflict banner; prompt refresh.
- Network/timeouts → show retry UI state.

---

## 10. State Model & Transitions

### Allowed states (derived from dates)
Assignments are treated as:
- **Scheduled**: `effectiveStartAt` in the future
- **Active**: `effectiveStartAt <= now` and (`effectiveEndAt` is null or `now < effectiveEndAt` per backend rule; backend story contains an inconsistency about inclusive end; see Open Questions)
- **Ended/Historical**: `effectiveEndAt` in the past

### Role-based transitions
- Manager (with proper permission) can:
  - Create scheduled/active assignments.
  - End active assignments (set end timestamp).
  - Potentially edit assignments (TBD based on immutability rules).
- Unauthorized users:
  - View may be allowed or denied (TBD); mutations denied.

### UI behavior per state
- Scheduled: show as upcoming; allow edit/end (end means set end before start? should be prevented; validate end >= start).
- Active: show as active; allow end.
- Ended: read-only, no end/edit.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields → inline errors, no submit.
- End before start → inline error on end date.
- Invalid timestamp format → inline error.

### Concurrency conflicts
- If backend returns 409 with version mismatch:
  - UI shows “This assignment was updated by another user. Refresh to see latest.”
  - Provide “Refresh” button to reload list and reopen edit if needed.

### Unauthorized access
- If load call returns 403: show “Not authorized” page/state; hide assignments data.
- If mutation returns 403: show banner; keep view mode.

### Empty states
- No assignments:
  - Show “No location assignments yet” with CTA “Add Assignment” (if authorized).
- No locations available for picker:
  - Show error/empty “No active locations available” and block save.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View a person’s assignments (empty)
**Given** I am a Manager with permission to view location assignments  
**And** a Person exists with no `PersonLocationAssignment` records  
**When** I open the Person’s “Location Assignments” view  
**Then** I see an empty state indicating no assignments exist  
**And** I see an “Add Assignment” action available

### Scenario 2: Create first primary assignment
**Given** I am a Manager with permission to manage location assignments  
**And** a Person exists with no current assignments  
**When** I create a new assignment with a valid `locationId`, valid `effectiveStartAt`, and `isPrimary=true`  
**Then** the system saves successfully  
**And** the assignment list refreshes showing the new assignment marked as Primary

### Scenario 3: Create secondary (non-primary) assignment
**Given** I am a Manager with permission to manage location assignments  
**And** a Person has an existing active primary assignment  
**When** I create a second assignment to a different location with `isPrimary=false` and valid dates  
**Then** the system saves successfully  
**And** the list shows both assignments  
**And** the original assignment remains marked as Primary

### Scenario 4: Create new primary triggers backend demotion and UI reflects result
**Given** I am a Manager with permission to manage location assignments  
**And** a Person has an existing active primary assignment at Location A  
**When** I create a new assignment at Location B with `isPrimary=true` and a later `effectiveStartAt`  
**Then** the system saves successfully  
**And** after refresh, the list shows Location B assignment as Primary  
**And** the previously primary assignment is no longer primary and/or has an adjusted end date as returned by backend

### Scenario 5: Overlapping assignment rejected
**Given** I am a Manager with permission to manage location assignments  
**And** a Person has an assignment to Location A for role X during a given date range  
**When** I attempt to create another assignment for the same Location A and role X whose effective dates overlap  
**Then** the system rejects the save with a conflict/validation error  
**And** I see an error message indicating overlapping assignments are not allowed  
**And** no new assignment appears in the list

### Scenario 6: End an assignment
**Given** I am a Manager with permission to manage location assignments  
**And** a Person has an active assignment  
**When** I end the assignment by setting `effectiveEndAt` to now (or a selected timestamp)  
**Then** the system saves successfully  
**And** the assignment is shown as ended/historical and is no longer shown in “Active only” view

### Scenario 7: Permission denied on mutation
**Given** I am authenticated but do not have permission to manage location assignments  
**When** I attempt to create, edit, or end an assignment  
**Then** the system denies the action with a 403 response  
**And** the UI displays a permission error message  
**And** no changes are applied

---

## 13. Audit & Observability

### User-visible audit data
- For each assignment (if provided by backend): display `createdAt`, `createdBy`, `updatedAt`, `updatedBy`, and `version` in a details panel or row expansion.

### Status history
- Show effective date range history per assignment; allow toggle to include historical.

### Traceability expectations
- Frontend must pass correlation/request ID headers if supported by existing frontend HTTP client conventions (TBD by repo).
- On save/end, log client-side info (non-PII): personId, assignmentId, outcome, HTTP status.

---

## 14. Non-Functional UI Requirements
- **Performance:** Assignment list load should render within 1s for up to 200 assignments (paging if more).
- **Accessibility:** All form inputs labeled; keyboard navigable dialogs; error messages announced to screen readers.
- **Responsiveness:** Usable on tablet widths; list wraps appropriately.
- **i18n/timezone:** Display timestamps in the user’s local timezone but submit in ISO-8601 UTC as required by backend; clarify inclusive/exclusive end behavior (see Open Questions).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-state message + primary CTA when assignment list is empty; qualifies as safe UI ergonomics. Impacted sections: UX Summary, Acceptance Criteria.
- SD-UX-PAGING-FILTER: Default “Active only” filter with option to include historical; paging for large lists; safe because it doesn’t change domain logic. Impacted sections: UX Summary, Alternate/Empty states.
- SD-ERR-MAP-HTTP: Standard mapping of HTTP 400/403/404/409 to inline/banners and retry prompts; safe because it’s generic error handling. Impacted sections: Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend contract (blocking):** What are the exact Moqui service names or REST endpoints for:
   - listing assignments by person,
   - creating an assignment,
   - updating an assignment,
   - ending an assignment,
   - listing locations for selection?
2. **Role field (blocking):** Is `role` required for `PersonLocationAssignment` in the frontend? If yes, what are the allowed values (enum) and how is it selected (free text vs picker)?
3. **Primary uniqueness scope (blocking):** Is “exactly one primary” enforced per **person overall**, or per **person+role** (backend reference mentions `(personId, role)` in demotion logic)? UI needs to know how to message and how to filter/display primaries.
4. **Effective end inclusivity (blocking):** Backend text states `effectiveEndAt` is “inclusive” but also defines active as `now < effectiveEndAt`. Which is authoritative for display calculations and messaging?
5. **Editability/immutability (blocking):** Are assignments editable after creation, or must changes be made by ending and creating a new assignment (as with RoleAssignments)? If editable, which fields are editable?
6. **Permissions (blocking):** What permission(s) gate view vs manage actions for location assignments in this frontend?
7. **Change reason code (non-blocking unless required):** Is `changeReasonCode` required/available in UI for create/update/end? If yes, what are valid codes and where do we fetch them?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/150

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/150  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative  
As a **Manager**, I want **to assign employees to one or more locations with a primary designation** so that **workexec and shopmgr can enforce correct staffing eligibility**.

## Details  
- Multiple active assignments allowed; exactly one primary.  
- Effective-dated assignments.

## Acceptance Criteria  
- Can assign/unassign person to locations.  
- Primary location enforced.  
- Assignment changes are audited and emitted.

## Integration Points (workexec/shopmgr)  
- workexec eligible technician list constrained by assignment.  
- shopmgr roster uses the same assignment source.

## Data / Entities  
- PersonLocationAssignment

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

END FRONTEND STORY (FULL CONTEXT)