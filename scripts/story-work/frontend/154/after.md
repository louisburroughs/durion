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

STOP: Clarification required before finalization

---

## 1. Story Header

**Title:** Users: Disable User (Offboarding) Without Losing History (Frontend / Moqui)

**Primary Persona:** Admin

**Business Value:** Immediately revoke a user’s system access while preserving historical labor/timekeeping data for audit, reporting, and payroll continuity.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Admin  
- **I want** to disable (soft-offboard) a user account from the POS UI  
- **So that** the user cannot authenticate and cannot be selected for future work/schedules, while all historical records remain intact and all forced changes are auditable.

### In-scope
- Moqui/Vue/Quasar UI flow to initiate **Disable User**
- Confirmation UI and policy-driven options (as exposed by backend) for handling:
  - ending active location assignments (optional)
  - force-stopping active job timers/time entries (initiated by backend orchestration)
- Display of resulting user/person status and effective timestamp
- Display of audit/status history (at minimum: show last status change metadata if available)
- Frontend error handling for: not found, already disabled, forbidden, validation errors, conflict
- Ensure disabled users are excluded from UI pickers/lists **where this frontend controls those lists** (user search/select screens in this repo)

### Out-of-scope
- Implementing actual authentication blocking in Security Service (backend/integration responsibility)
- Implementing saga retry/DLQ operational workflows (not requested in this frontend issue)
- Defining/altering permission matrix beyond checking a single capability gate for disable action (requires clarification)
- Termination (`TERMINATED`) workflow (this story sets `DISABLED` only)

---

## 3. Actors & Stakeholders
- **Admin (actor):** Performs disable action.
- **People Service (system):** System of record for User/Person status.
- **Security Service (downstream):** Blocks authentication after disable (event-driven).
- **Work Execution / Shop Management (downstream):** Stops timers and excludes disabled users from future assignment/scheduling.
- **Auditors/Operations (stakeholders):** Need traceability of who disabled whom and why.

---

## 4. Preconditions & Dependencies
- Admin is authenticated in the frontend.
- Admin has authorization to disable users (permission name/claim **TBD**, see Open Questions).
- Target user exists and is in `ACTIVE` state at time of action.
- Backend endpoint(s) exist to:
  - fetch user detail including `User.status`, `Person.status`, `statusEffectiveAt`, `statusReasonCode` (if used)
  - execute disable/offboard action with optional assignment handling inputs
- Frontend has or will add a user detail screen route that can host this action (route **TBD** based on repo conventions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **User Management list/search**: select a user → open User Detail → click **Disable User**
- Optional: direct deep link to user detail screen by `userId`

### Screens to create/modify
1. **User List/Search Screen** (existing or to be created)
   - Must visually indicate disabled status and exclude disabled users by default in pickers (configurable filter).
2. **User Detail Screen**
   - Add **Disable User** action when user is `ACTIVE` and admin is authorized.
   - Show status, effective timestamp, and reason (if present).
3. **Disable User Confirmation Dialog / Subscreen**
   - Confirmation step
   - Optional policy-driven assignment handling choices (if backend supports/exposes)

### Navigation context
- `Users` module → `User List` → `User Detail`
- Post-success: remain on User Detail with updated status OR navigate back to list with toast (choose one; see Open Questions)

### User workflows
**Happy path**
1. Admin opens User Detail for an ACTIVE user.
2. Admin clicks **Disable User**.
3. UI shows confirmation with summary of effects (access revoked; timers stopped; assignments may end; history retained).
4. Admin selects optional assignment handling (if enabled).
5. Admin confirms.
6. UI shows success message and refreshes user detail showing `DISABLED` and effective timestamp.

**Alternate paths**
- User already disabled: action hidden or disabled; if attempted, show informational message.
- Backend returns “downstream steps queued” (if backend exposes): UI shows non-blocking warning/status (requires contract).

---

## 6. Functional Behavior

### Triggers
- Admin clicks **Disable User** button on User Detail screen.

### UI actions
- Open modal dialog:
  - Requires explicit confirmation (typed confirmation or checkbox) **TBD** (safe default not allowed for destructive confirmation style; see Open Questions).
  - Collect optional `statusReasonCode` if backend requires/accepts it (currently described as optional).
  - Collect assignment termination option **only if** backend indicates policy allows alternatives.

### State changes (frontend)
- On success:
  - Update local view-model: `user.status=DISABLED`, `person.status=DISABLED`, `statusEffectiveAt=now from backend response`
  - Disable further editing/actions that require active status (at least hide “Disable User” and show status badge)
- On failure:
  - Do not change UI state; show error banner/toast with actionable message.

### Service interactions
- Load user detail on entry and after successful disable.
- Call disable action endpoint/service with payload including:
  - `targetUserId`
  - optional `statusReasonCode`
  - optional assignment handling option (enum)
  - idempotency key (if supported) **TBD**

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Must not allow disable if user is already `DISABLED` or `TERMINATED` (UI should prevent; backend remains source of truth).
- If `statusReasonCode` is required by policy/config, UI must require selection before enabling confirm (TBD).
- If assignment option `END_ASSIGNMENTS_AT_DATE` is supported, UI must validate date/time is in the future and timezone-aware (TBD).
- Confirmation must be explicit to avoid accidental disable (exact mechanism TBD).

### Enable/disable rules
- **Disable User** button visible/enabled only when:
  - current user has required permission
  - target user status == `ACTIVE`
- Assignment handling controls visible only when backend/policy indicates they are applicable (TBD how exposed).

### Visibility rules
- Disabled users:
  - should not appear in assignment/schedule pickers in this frontend (where the frontend controls filtering)
  - should be filterable/searchable in Admin user list with an “Include disabled” toggle/filter (safe default allowed for UI ergonomics)

### Error messaging expectations
- 403: “You don’t have permission to disable users.”
- 404: “User not found or no longer available.”
- 409: If conflict (already disabled / duplicate / policy conflict): show backend message; suggest refresh.
- 400: Show field-level validation messages (reason code, date).

---

## 8. Data Requirements

### Entities involved (frontend view)
- `User`
- `Person`
- `RoleAssignment` / `PersonLocationAssignment` (only for display/option summary if backend returns)
- `TimeEntry` (only for display/summary if backend returns)
- `AuditLog` (read-only display if available)

### Fields
**User (read-only on this screen except action)**
- `userId` (string/UUID, required)
- `username` (string, required)
- `status` (enum: `ACTIVE` | `DISABLED` | `TERMINATED`, required)
- `statusEffectiveAt` (datetime, required when status != ACTIVE)
- `statusReasonCode` (string/enum, optional)
- `personId` (string/UUID, required)

**Person (read-only)**
- `personId` (string/UUID)
- `legalName` (string)
- `status` (enum: `ACTIVE` | `DISABLED` | `TERMINATED`)
- `statusEffectiveAt` (datetime)
- `statusReasonCode` (string/enum, optional)

**Disable action inputs (editable in dialog)**
- `statusReasonCode` (optional unless policy requires)
- `assignmentTerminationOption` (enum, optional; see below)
- `assignmentEndAt` (datetime, required only if option is scheduled end)

**AssignmentTerminationOption** (policy-driven; from backend reference)
- `END_ASSIGNMENTS_NOW`
- `END_ASSIGNMENTS_AT_DATE`
- `LEAVE_ASSIGNMENTS_ACTIVE` (requires elevated permission + policy)

### Derived/calculated fields
- Display-only “Effective status since” formatted in tenant/location timezone (timezone source TBD).

---

## 9. Service Contracts (Frontend Perspective)

> NOTE: Exact endpoint names are not provided in inputs. Moqui developers must map these to actual services/screens once clarified.

### Load/view calls
- `GET /people/users/{userId}` or Moqui service equivalent `PeopleServices.get#UserDetail`
  - Response includes User + linked Person + status fields

### Create/update calls (action)
- `POST /people/users/{userId}/disable` or Moqui service `PeopleServices.disable#User`
  - Request (proposed):
    - `userId`
    - `statusReasonCode` (optional)
    - `assignmentTerminationOption` (optional)
    - `assignmentEndAt` (optional)
    - `idempotencyKey` (optional, if supported)
  - Response (proposed):
    - updated `User` + `Person` status fields
    - optional `downstreamActions`: `{ timersStop: SUCCESS|QUEUED|FAILED, assignmentsEnd: SUCCESS|QUEUED|FAILED }` (TBD)

### Error handling expectations
- 400 validation errors: return structured field errors; UI maps to form field messages.
- 403 forbidden: UI shows access denied; action remains unavailable.
- 404 not found: UI shows not found and offers navigation back.
- 409 conflict: UI shows conflict message and refresh prompt.
- 5xx: UI shows generic error and suggests retry; no local state change.

---

## 10. State Model & Transitions

### Allowed states (authoritative from People domain)
- `ACTIVE`
- `DISABLED` (target of this story)
- `TERMINATED` (not set by this story)

### Role-based transitions
- `ACTIVE -> DISABLED`: allowed for Admin with `user.disable` (exact permission token TBD)
- `DISABLED -> ACTIVE`: not part of story
- Any transition involving `TERMINATED`: not part of story

### UI behavior per state
- `ACTIVE`:
  - Show Disable action (if authorized)
- `DISABLED`:
  - Hide/disable Disable action
  - Show status metadata and reason (if present)
  - Show informational note: “Access revoked; history retained.”
- `TERMINATED`:
  - Hide disable action; show “Terminated” state (read-only)

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required reason/date (if required): prevent confirm; show field error.
- Invalid scheduled end date: block submit with message.

### Concurrency conflicts
- If user was disabled by another admin between load and submit:
  - backend returns 409 or “already disabled”
  - UI refreshes data and shows “User is already disabled.”

### Unauthorized access
- User lacks permission:
  - Disable action not rendered
  - If deep-linked and action attempted, show 403 error.

### Empty states
- If user detail fails to load (404):
  - show empty/not-found screen with link back to Users list.

---

## 12. Acceptance Criteria

### Scenario 1: Admin disables an active user successfully
**Given** an Admin with disable permission is viewing an ACTIVE user detail page  
**When** the Admin clicks “Disable User”, confirms the action, and submits  
**Then** the frontend calls the disable-user service with the target `userId` and any selected options  
**And** the UI refreshes and displays `User.status = DISABLED` and `Person.status = DISABLED`  
**And** the UI displays `statusEffectiveAt` from the backend response  
**And** the “Disable User” action is no longer available for that user.

### Scenario 2: User is already disabled
**Given** an Admin is viewing a user whose status is `DISABLED`  
**When** the page renders  
**Then** the UI does not offer the “Disable User” action  
**And** the UI shows the disabled status and effective timestamp.

### Scenario 3: Permission denied
**Given** a signed-in user without the disable permission attempts to disable an ACTIVE user (via UI or direct action trigger)  
**When** the disable request is made  
**Then** the frontend displays an “Access Denied” error  
**And** no status fields in the UI are changed.

### Scenario 4: User not found
**Given** an Admin navigates to a user detail route for a `userId` that does not exist  
**When** the frontend loads the user detail  
**Then** the UI shows a “User not found” empty state  
**And** provides navigation back to the Users list.

### Scenario 5: Backend validation error for assignment options
**Given** the backend requires an assignment end date when `END_ASSIGNMENTS_AT_DATE` is selected  
**When** the Admin selects `END_ASSIGNMENTS_AT_DATE` but does not provide an end date and submits  
**Then** the backend returns a 400 validation error with field details  
**And** the frontend shows the error message on the end date field and does not complete the disable flow.

---

## 13. Audit & Observability

### User-visible audit data
- On User Detail, show (if provided by backend):
  - “Disabled by” (actor)
  - “Disabled at” (`statusEffectiveAt`)
  - “Reason” (`statusReasonCode`)
- If audit log list endpoint exists, include a read-only “Recent status changes” section filtered to user/person.

### Status history
- At minimum, display current status + effective timestamp.
- If history is available, show chronological list (read-only).

### Traceability expectations
- Frontend must include a correlation/request ID header if the platform standard supports it (TBD per repo conventions).
- UI should log (client-side) a structured event “user_disable_initiated” and “user_disable_result” without PII beyond userId (safe logging).

---

## 14. Non-Functional UI Requirements
- **Performance:** User detail load < 2s on typical broadband; disable action returns with loading indicator and prevents double submit.
- **Accessibility:** Confirmation dialog keyboard navigable; focus trap; buttons labeled; errors announced (aria-live) per Quasar best practices.
- **Responsiveness:** Works on tablet widths used in shop environment.
- **i18n/timezone:** Display timestamps in tenant/location timezone and localized format (timezone source TBD). No currency.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide a standard “Not found / failed to load” empty state with retry/back navigation; safe as UI ergonomics only. (Impacted: UX Summary, Alternate/Error Flows)
- SD-UI-PREVENT-DOUBLE-SUBMIT: Disable confirm button and show spinner while request in-flight; safe to prevent duplicate actions. (Impacted: Functional Behavior, NFR)
- SD-UI-LIST-FILTER: In user lists/pickers, default filter excludes DISABLED users with an “Include disabled” toggle; safe ergonomic default and consistent with domain rule to exclude disabled from assignment pickers. (Impacted: UX Summary, Business Rules)

---

## 16. Open Questions
1. **Backend contract (blocking):** What are the exact Moqui service names and/or REST endpoints for:
   - loading user detail (User + Person status fields)
   - performing disable action (payload + response)?
2. **Permissions (blocking):** What exact permission/role check should the frontend use to show/enable “Disable User”? Is it strictly `user.disable` or something else in this repo?
3. **Assignment options exposure (blocking):** How does the backend communicate whether `LEAVE_ASSIGNMENTS_ACTIVE` or `END_ASSIGNMENTS_AT_DATE` are allowed (policy + elevated permission)? Is there a “capabilities” field returned to the UI?
4. **Reason code policy (blocking):** Is `statusReasonCode` required, optional, or conditionally required? If required, what are the allowed values and display labels?
5. **Confirmation UX (blocking):** What confirmation mechanism is required by product/security policy (simple confirm button vs typed username vs checkbox + reason)?
6. **Post-success navigation (blocking):** After disabling, should the UI remain on the User Detail page (refreshed) or navigate back to the Users list?
7. **Timezone source (blocking):** Should `statusEffectiveAt` be displayed in tenant timezone, location timezone, or browser timezone? Where is that configured in the frontend/Moqui context?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Users: Disable User (Offboarding) Without Losing History — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/154


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Users: Disable User (Offboarding) Without Losing History
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/154
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Users: Disable User (Offboarding) Without Losing History

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to disable a user account** so that **access is removed while historical labor and timekeeping records remain intact**.

## Details
- Disable login in pos-security-service.
- Optionally end active location assignments.
- Force-stop any active job timers.

## Acceptance Criteria
- Disabled users cannot authenticate.
- Person record retained and marked inactive (policy-driven).
- All forced stops and changes are audited.

## Integration Points (workexec/shopmgr)
- workexec excludes disabled users from assignment.
- shopmgr excludes disabled users from future schedules.

## Data / Entities
- User
- Person
- Assignment
- TimeEntry

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