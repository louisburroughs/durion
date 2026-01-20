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

### Title
[FRONTEND] Access: Assign Roles and Scopes (Global vs Location)

### Primary Persona
Admin (a user authorized to manage other users’ access)

### Business Value
Enable least-privilege access by granting roles with correct scope (GLOBAL vs LOCATION) and effective dates, with an auditable history that downstream domains can rely on for eligibility and permissions.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Admin  
- **I want** to view, add, and revoke user role assignments with scope (GLOBAL/LOCATION) and effective dates  
- **So that** access is correctly constrained per shop location and job function, and changes are traceable.

### In-scope
- Admin UI to **list** a user’s existing RoleAssignments (active + historical within a reasonable default range).
- Admin UI to **create** a new RoleAssignment with:
  - role selection
  - scopeType selection (GLOBAL or LOCATION)
  - location selection required when LOCATION scope
  - effectiveStartDate required
  - effectiveEndDate optional
- Admin UI to **revoke/end** an existing assignment via setting effectiveEndDate (no hard delete).
- Frontend validation reflecting backend business rules (scope/location/date constraints) and rendering backend validation errors.
- Show audit-relevant metadata available from API (createdAt/updatedAt/changedBy/version/reasonCode if exposed).

### Out-of-scope
- Defining/maintaining Role metadata (e.g., creating roles, editing allowedScopes).
- Authentication implementation (login) and authorization enforcement logic beyond UI gating/handling 403.
- Cross-domain workflows (e.g., mechanic eligibility calculations, scheduling UI); only ensure role assignment data supports them.
- Event bus implementation; only ensure UI actions trigger backend actions that emit events.

---

## 3. Actors & Stakeholders

### Actors
- **Admin**: manages user access by assigning/revoking roles and scopes.
- **System (People service via Moqui)**: system of record for RoleAssignment.

### Stakeholders / Downstream consumers (informational)
- **Work Execution**: uses `MECHANIC` + LOCATION assignments for technician eligibility.
- **Shop Management / Scheduling**: uses MANAGER/DISPATCHER scope for permissions.
- **Audit/Compliance**: relies on immutable audit history of changes.

---

## 4. Preconditions & Dependencies

### Preconditions
- Admin is authenticated.
- Target user exists and is identifiable by `userId`.
- Role catalog exists with `allowedScopes` metadata per role (GLOBAL, LOCATION, or both).
- Locations exist and are queryable for LOCATION-scoped assignments.

### Frontend Dependencies (blocking if absent)
- Moqui endpoints/services to:
  - List roles including `allowedScopes`.
  - List locations (at least id + name).
  - List role assignments for a user.
  - Create role assignment.
  - End/revoke role assignment by setting `effectiveEndDate`.
- Permission model/endpoint or response codes to determine whether current user can manage role assignments (at minimum handle 403).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **User Detail** (or Admin Users list): action “Manage Access / Roles”.
- Direct route (if applicable): `/users/:userId/access` (exact route TBD by repo conventions).

### Screens to create/modify
- **Screen: UserAccessRoles** (new or extended)
  - Shows role assignments table + “Add Role Assignment” action
- **Dialog/Screen: AddRoleAssignment** (modal or sub-screen)
  - Form to create a new RoleAssignment
- **Dialog: EndRoleAssignment**
  - Confirm revoke (set effectiveEndDate) with optional reasonCode/notes if supported

*(Exact file paths and naming must follow durion-moqui-frontend README conventions; if unknown, treat as open question for implementation mapping.)*

### Navigation context
- Breadcrumb: Users → User Detail → Access / Roles
- Return to User Detail after save, preserving context.

### User workflows

#### Happy path: create LOCATION scoped assignment
1. Admin opens Manage Access for a user.
2. Admin clicks “Add Role Assignment”.
3. Admin selects role (UI displays allowed scopes).
4. Admin selects scopeType=LOCATION.
5. UI requires location selection.
6. Admin sets effectiveStartDate; optionally effectiveEndDate.
7. Admin submits; UI shows success and new assignment appears in table.

#### Happy path: create GLOBAL scoped assignment
Same as above but scopeType=GLOBAL; location field hidden/disabled and must not be sent.

#### Alternate: end/revoke an assignment
1. Admin clicks “End” on an active assignment.
2. Admin chooses end date/time (defaults to now).
3. Submits; assignment becomes inactive; remains visible historically.

#### Alternate: change scope/role
Because RoleAssignment core fields are immutable, UI guides Admin:
- End existing assignment + create new assignment.

---

## 6. Functional Behavior

### Triggers
- Screen load: fetch user role assignments + role catalog (+ locations as needed).
- Role selection changes: update scope options based on role.allowedScopes.
- ScopeType changes: toggle location requirement/visibility.
- Submit create: call create service.
- Submit end: call update/end service.

### UI actions (deterministic)
- **Role dropdown**: shows roleName; stores roleId.
- **Allowed scope hint**: read-only display from role.allowedScopes.
- **ScopeType radio/select**:
  - Only values allowed by selected role are selectable.
  - If role allows only one scope, preselect it and disable toggling.
- **Location picker**:
  - Enabled/required only when scopeType=LOCATION.
  - Disabled/cleared when scopeType=GLOBAL.
- **EffectiveStartDate picker**: required; date-time in tenant/user timezone.
- **EffectiveEndDate picker**: optional; must be >= start if present.
- **Create button**: disabled until required fields valid.

### State changes (frontend)
- After create/end success: refresh assignments list (source of truth = backend response).
- Optimistic UI is optional; default is pessimistic (wait for response).

### Service interactions (Moqui)
- Use Moqui screen transitions or REST calls (depending on project convention) to invoke services:
  - load lists
  - create RoleAssignment
  - end RoleAssignment

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- RoleAssignment must have:
  - `roleId` required
  - `scopeType` required
  - `effectiveStartDate` required
  - `effectiveEndDate` optional; if provided must be >= start
- If `scopeType=LOCATION`:
  - `locationId` required
- If `scopeType=GLOBAL`:
  - `locationId` must be null/omitted (UI must clear and not submit)

### Enable/disable rules
- ScopeType choices constrained by `role.allowedScopes`.
- Submit disabled if:
  - missing required fields
  - invalid date range
  - LOCATION scope with missing location

### Visibility rules
- Location picker visible only for LOCATION scope (or visible but disabled; choose one—see Open Question).

### Error messaging expectations
- Inline validation messages for required fields and invalid date ranges.
- Backend errors displayed in a non-technical banner + field-level mapping where possible:
  - 400 validation → show message and highlight offending field(s) when identifiable
  - 403 → “You do not have permission to manage roles.”
  - 404 (user/role/location missing) → show not found and suggest refresh
  - 409 conflict (if backend uses for duplicates/overlaps) → show conflict message and do not retry automatically

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Role` (metadata): roleId, roleName, allowedScopes
- `RoleAssignment`: assignmentId, userId, roleId, scopeType, locationId, effectiveStartDate, effectiveEndDate, createdAt, updatedAt, changedBy, version, reasonCode
- `Location`: locationId, locationName (minimum)

### Fields (type, required, defaults)

#### RoleAssignment create form fields
- `userId` (UUID/string): required (from route/context; not editable)
- `roleId` (UUID/string): required
- `scopeType` (enum: GLOBAL|LOCATION): required; default:
  - if role.allowedScopes has single value → that value
  - else no default (force explicit selection) *(safe default not allowed if policy? This is UX-only; acceptable.)*
- `locationId` (UUID/string): required iff scopeType=LOCATION; default null
- `effectiveStartDate` (timestamp): required; default = now (tenant timezone) *(safe UX default)*
- `effectiveEndDate` (timestamp): optional; default null
- `reasonCode` (string): optional (only if backend supports and provides list; otherwise omit)

### Read-only vs editable by state/role
- RoleAssignment core fields (roleId, scopeType, locationId, start date) treated as immutable after creation in UI:
  - UI does not offer “edit role/scope”; only “End assignment” and “Create new”.
- `effectiveEndDate` editable for ending/revoking.

### Derived/calculated fields
- “Status” derived in UI:
  - Active if start <= now and (end is null or end > now)
  - Future if start > now
  - Expired if end <= now
*(Display-only; backend remains source of truth.)*

---

## 9. Service Contracts (Frontend Perspective)

> Endpoints/service names are not provided in inputs. Below defines **required contracts**; implementation must map to actual Moqui services/transitions.

### Load/view calls
1. **Get Role catalog**
   - Request: `GET /api/roles` (placeholder)
   - Response: list of `{ roleId, roleName, allowedScopes[] }`
2. **Get Locations**
   - Request: `GET /api/locations` (placeholder)
   - Response: list of `{ locationId, locationName }`
3. **Get RoleAssignments for user**
   - Request: `GET /api/users/{userId}/role-assignments?includeInactive=true` (placeholder)
   - Response: list of RoleAssignment DTOs including `version` for optimistic locking if used

### Create/update calls
4. **Create RoleAssignment**
   - Request: `POST /api/users/{userId}/role-assignments`
   - Body:
     - roleId
     - scopeType
     - locationId (only when LOCATION)
     - effectiveStartDate
     - effectiveEndDate (optional)
     - reasonCode (optional)
   - Response: `201` with created assignment

5. **End/Revoke RoleAssignment**
   - Request: `POST /api/role-assignments/{assignmentId}/end` or `PUT /api/role-assignments/{assignmentId}`
   - Body:
     - effectiveEndDate = selected end time
     - version (if required)
     - reasonCode (optional)
   - Response: `200` with updated assignment

### Submit/transition calls (Moqui screens)
- If project uses Moqui screen transitions instead of REST:
  - Define transitions `createRoleAssignment`, `endRoleAssignment` that call services and return to the list screen with message.

### Error handling expectations
- 400: show backend message; map to fields if `fieldErrors` structure exists (unknown).
- 403: show permission error and disable create/end actions.
- 409: show conflict (e.g., invalid overlap or duplicate) and require user correction.
- 5xx/timeouts: show retry option; do not duplicate-submit (idempotency key if available—unknown).

---

## 10. State Model & Transitions

### Allowed states (derived for UI)
- **FUTURE**: now < effectiveStartDate
- **ACTIVE**: effectiveStartDate <= now < effectiveEndDate (or end null)
- **EXPIRED**: effectiveEndDate <= now

### Role-based transitions
- Admin (authorized) can:
  - Create new assignment (any state created may be FUTURE or ACTIVE depending on start)
  - End ACTIVE or FUTURE assignment by setting effectiveEndDate (end must be >= start; may be now or earlier than now only if backend allows backdating—unknown)

### UI behavior per state
- ACTIVE:
  - Show “End” action enabled
- FUTURE:
  - Show “End” action enabled (ends before it becomes active) *(requires backend support; if not supported, block—see Open Questions)*
- EXPIRED:
  - No end action (already ended); view-only

---

## 11. Alternate / Error Flows

### Validation failures
- Attempt submit with LOCATION scope and no location:
  - UI blocks with inline error; if backend returns 400, show message too.
- End date before start:
  - UI blocks; backend 400 handled similarly.

### Concurrency conflicts
- If backend uses `version` optimistic locking:
  - On 409/version mismatch: show “This assignment changed since you loaded it. Refresh and try again.”
  - Refresh list automatically after conflict acknowledgment.

### Unauthorized access
- If user lacks permission:
  - On load or action call returning 403:
    - show not-authorized banner
    - hide/disable add/end buttons
    - keep read-only list if allowed by backend; otherwise show access denied screen

### Empty states
- No role assignments:
  - Show empty message and CTA “Add Role Assignment” (if authorized).
- No locations returned (when needed):
  - Disable submit for LOCATION scope and show “No locations available” with suggestion to contact admin.

---

## 12. Acceptance Criteria

### Scenario 1: View user role assignments
**Given** I am an authenticated Admin  
**And** a user exists with `userId`  
**When** I navigate to the user’s “Access / Roles” screen  
**Then** I see a list of the user’s role assignments including role, scopeType, location (if applicable), effectiveStartDate, effectiveEndDate, and derived status (Active/Future/Expired)

### Scenario 2: Create a GLOBAL role assignment
**Given** I am an authenticated Admin  
**And** a role exists with `allowedScopes` including `GLOBAL`  
**When** I create a role assignment with `scopeType=GLOBAL` and a valid effectiveStartDate  
**Then** the UI submits the create request without `locationId`  
**And** I see a success confirmation  
**And** the new assignment appears in the list with scopeType GLOBAL and no location

### Scenario 3: Create a LOCATION role assignment
**Given** I am an authenticated Admin  
**And** a role exists with `allowedScopes` including `LOCATION`  
**And** at least one location exists  
**When** I create a role assignment with `scopeType=LOCATION`, a selected `locationId`, and a valid effectiveStartDate  
**Then** the UI submits the create request including `locationId`  
**And** the assignment appears in the list with scopeType LOCATION and the selected location

### Scenario 4: Prevent LOCATION scope without location
**Given** I am an authenticated Admin  
**And** I am creating a role assignment  
**When** I select `scopeType=LOCATION` and do not select a location  
**Then** the Create action is disabled or shows an inline validation error  
**And** no request is submitted until a location is selected

### Scenario 5: Enforce role allowedScopes in UI
**Given** I am an authenticated Admin  
**And** I select a role whose `allowedScopes` is `[LOCATION]`  
**When** I view the scopeType control  
**Then** `GLOBAL` is not selectable  
**And** if I attempt submission via any means and backend returns `400` “Role X does not allow GLOBAL scope…”  
**Then** the UI displays that error and keeps me on the form

### Scenario 6: Reject invalid date range
**Given** I am an authenticated Admin  
**When** I set effectiveEndDate earlier than effectiveStartDate  
**Then** the UI shows an inline error  
**And** the Create action is blocked  
**And** if submitted and backend returns 400, the error is shown and no assignment is created

### Scenario 7: End an active assignment (soft revoke)
**Given** I am an authenticated Admin  
**And** a role assignment is currently ACTIVE  
**When** I end the assignment with effectiveEndDate = now  
**Then** the UI submits an end/revoke request  
**And** the assignment remains visible in the list with an end date set  
**And** its derived status becomes EXPIRED (or non-active) after refresh

### Scenario 8: Permission denied
**Given** I am authenticated but not authorized to manage role assignments  
**When** I open the Access / Roles screen or try to add/end an assignment  
**Then** the UI shows an authorization error message  
**And** create/end actions are disabled/hidden  
**And** no unauthorized changes occur

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided by API):
  - `createdAt`, `updatedAt`
  - `changedBy` (as identifier; avoid exposing PII beyond what API returns)
  - `reasonCode` (if used)

### Status history
- RoleAssignment history is represented by records with effective dating; UI must not delete entries.
- For “changes” to immutable fields, UI guides end+create, resulting in a clear historical trail.

### Traceability expectations
- Frontend should propagate correlation/request ID headers if project convention exists (unknown).
- Log UI action events (non-PII) per workspace convention: view screen, submit create, submit end, error encountered.

---

## 14. Non-Functional UI Requirements

- **Performance:** initial load should complete with ≤3 network calls (roles, assignments, locations) and render within 2 seconds on typical broadband; locations call may be deferred until LOCATION scope is selected.
- **Accessibility:** keyboard navigable form controls; proper labels for inputs; error messages associated to fields.
- **Responsiveness:** usable on tablet widths; dialogs should fit small screens.
- **i18n/timezone:** dates displayed in tenant/user timezone; store/send timestamps in ISO-8601; currency not applicable.

---

## 15. Applied Safe Defaults

- SD-UX-01 (Lazy load reference data): Locations list may be loaded only when `scopeType=LOCATION` is selected to reduce initial load time; qualifies as safe UX optimization. Impacted sections: UX Summary, Service Contracts, Performance.
- SD-UX-02 (Default effectiveStartDate=now): Pre-fill effectiveStartDate with current time for convenience; safe because user can change and backend still validates. Impacted sections: Data Requirements, Acceptance Criteria.
- SD-ERR-01 (Standard HTTP error mapping): Map 400/403/404/409/5xx to banner + field errors when available; safe because it does not alter business logic. Impacted sections: Business Rules, Error Flows, Service Contracts.

---

## 16. Open Questions

1. What are the **actual Moqui service names / REST endpoints** for:
   - listing Roles (with allowedScopes),
   - listing Locations,
   - listing a user’s RoleAssignments,
   - creating RoleAssignment,
   - ending RoleAssignment (set effectiveEndDate)?
2. What is the **required permission** (or set) for viewing vs mutating role assignments in the frontend? (Backend story implies Admin-only, but frontend must know how to gate UI beyond handling 403.)
3. Does the backend support **ending FUTURE assignments** (effectiveEndDate before start) and/or **backdating** end dates (end < now)? If not, UI must restrict the end-date picker accordingly.
4. Is there a **reasonCode** requirement for ending/revoking assignments (required/optional), and if required, what are the allowed values?
5. Does RoleAssignment update/end require an **optimistic locking `version`** field in the request? If yes, confirm the error shape for version conflicts.
6. Should the role assignments list default to **active only** or **include inactive/history**? (Backend story describes querying active; admin UI often needs history for audit.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Access: Assign Roles and Scopes (Global vs Location) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/153

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Access: Assign Roles and Scopes (Global vs Location)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/153
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Access: Assign Roles and Scopes (Global vs Location)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to assign roles with optional location scope** so that **users have appropriate access per shop and job function**.

## Details
- Roles include: OWNER, ADMIN, MANAGER, HR, ACCOUNTING, DISPATCHER, SERVICE_WRITER, MECHANIC, AUDITOR, READ_ONLY.
- Scope types: GLOBAL or LOCATION.
- Effective dates supported.

## Acceptance Criteria
- Role assignment supports scope and effective dates.
- Permission checks enforced consistently.
- Changes are audited and versioned.

## Integration Points (workexec/shopmgr)
- workexec technician eligibility uses MECHANIC + location assignment.
- shopmgr scheduling permissions depend on MANAGER/DISPATCHER scope.

## Data / Entities
- RoleAssignment
- RoleGrant

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