## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:draft

### Recommended
- agent:security-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] Users: Provision User and Link to Person

### Primary Persona
Admin (authorized to provision users)

### Business Value
Enable an employee to authenticate and be correctly attributed across downstream systems (workexec/timekeeping/shopmgr) by provisioning a platform `User` and initiating linkage to an existing `Person`.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Admin  
- **I want** to provision a new system `User` (credentials identity + initial roles) and initiate linking it to an existing `Person`  
- **So that** the employee can log in and is correctly attributed across downstream services.

### In-scope
- A Moqui/Quasar frontend flow to:
  - Search/select an existing `Person` (by email at minimum) to link
  - Capture user identity fields (username/email) and status
  - Select initial role assignments
  - Submit provisioning request to security orchestration endpoint
  - Show immediate success (async linking happens after)
  - Show “link pending/unconfirmed” messaging when appropriate
  - Display errors from provisioning endpoint with actionable messaging

### Out-of-scope
- Creating/editing `Person` records (people domain)
- Implementing/defining RBAC permission keys, role hierarchies, or authorization mechanisms
- Implementing the event consumer/linking in `pos-people` (backend responsibility)
- Downstream integrations beyond reflecting linkage status in UI (if available)

---

## 3. Actors & Stakeholders
- **Admin user**: initiates provisioning
- **Security service (pos-security-service)**: system of record for `User`, hosts provisioning endpoint
- **People service (pos-people)**: system of record for `Person` and `UserPersonLink`, consumes provisioning event
- **Downstream services**: workexec/timekeeping/shopmgr rely on link for attribution and staff visibility

---

## 4. Preconditions & Dependencies
- Admin is authenticated in the frontend and has authorization to access user provisioning UI and submit provisioning.
- A `Person` already exists in `pos-people` with a stable `personId`.
- Backend endpoints exist and are reachable from Moqui frontend:
  - Security: provision user (idempotent) and assign initial roles (as part of provisioning)
  - People: search person by email (or list/search people) to choose the target `personId`
- Correlation/request ID propagation is supported (header or field) from frontend to backend (exact mechanism TBD).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Admin navigation: **Users → Provision User** (exact menu location TBD in app nav)

### Screens to create/modify
- Create new Moqui screen (suggested path/name, adjust to repo conventions):
  - `component://frontend/screen/admin/users/ProvisionUser.xml` (or equivalent)
- Optionally modify Users admin index screen to add a link/button to provisioning flow.

### Navigation context
- Standard admin layout and breadcrumb trail:
  - Admin Home → Users → Provision User

### User workflows

#### Happy path
1. Admin opens “Provision User”.
2. Admin searches for an existing Person by email (and/or name if supported) and selects exactly one person.
3. Admin enters/confirm user identity fields (username/email) and selects status.
4. Admin selects one or more initial roles.
5. Admin submits.
6. UI shows success: user provisioned; linking will complete asynchronously.
7. UI shows returned identifiers (at least `userId`, `personId`) and a “Link status: Pending” (unless backend can confirm linked immediately).

#### Alternate paths
- Person not found: show empty state and guidance (cannot proceed until a Person exists).
- User already exists (idempotent resolve): UI still shows success and returned `userId`; messaging indicates “Existing user resolved”.
- Backend returns validation errors: UI highlights relevant fields.
- Backend returns unauthorized: UI shows “Access denied” and does not reveal sensitive details.

---

## 6. Functional Behavior

### Triggers
- Screen load: initialize form state; optionally prefill email if passed as query param (only if existing patterns support it).
- Person search action (type-to-search or explicit Search button).
- Submit action: “Provision User”.

### UI actions
- Person search input and results list; selecting a Person populates a read-only `personId` and displays Person summary.
- Provisioning form fields enabled only when a Person is selected.
- Role selection control (multi-select) populated from roles source (endpoint TBD).
- Submit button disabled until required fields are valid.

### State changes (frontend)
- Maintain view-state:
  - `idle` → `searchingPerson` → `personSelected`
  - `submitting` → `success` | `error`
- After success:
  - Store and display returned `userId`
  - Display link status as `pending` unless backend provides a definitive linked indicator

### Service interactions
- People lookup:
  - Search Persons by email (primary) to get `personId` and display fields
- Security provisioning:
  - Submit provisioning request with selected `personId`, identity fields, status, and initial roles
- Optional status check (only if endpoint exists):
  - Query for linkage status `UserPersonLink` by `userId` or `personId`

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Required:
  - `personId` (must be selected from search results; not free text)
  - `email` (format-valid; see Open Questions for whether it must match Person email)
  - `username` (required unless backend derives from email; TBD)
  - `status` (must be one of allowed statuses; enumeration TBD)
  - `initialRoles` (required? TBD—story says “initial role assignments” but not whether empty is allowed)
- Client-side validation mirrors backend constraints where known; backend remains authoritative.

### Enable/disable rules
- Disable provisioning form until Person selected.
- Disable Submit while submitting, or if required fields invalid/missing.

### Visibility rules
- Show selected Person summary once selected (at minimum: name + email + personId).
- Show success panel with returned IDs and audit/correlation info if provided.

### Error messaging expectations
- Map HTTP errors:
  - `400`: show field-level validation messages where possible, else a form-level message.
  - `401`: prompt re-authentication (standard app behavior).
  - `403`: show access denied and hide admin-only details.
  - `404`: if person lookup returns none, show “No matching person found”.
  - `409`: show conflict message (e.g., duplicate username/email) with next steps.
  - `5xx`: show generic error and allow retry (idempotent safe).

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- Security domain:
  - `User` (security system of record; returned identifiers/status)
  - `Role` (for selection)
- People domain:
  - `Person` (search/select)
  - `UserPersonLink` (status/confirmation display if queryable)

### Fields

#### Person (read/search)
- `personId` (string/UUID; required; read-only)
- `email` (string; required for search/display)
- `firstName`/`lastName` (string; optional display)
- Other fields: out of scope

#### Provision request (write)
- `personId` (string/UUID; required)
- `username` (string; required? TBD)
- `email` (string; required)
- `status` (enum/string; required; values TBD)
- `initialRoleIds` or `initialRoleNames` (array; required? TBD; identifier type TBD)
- `correlationId` (string; optional; if frontend provides)

#### Provision response (read)
- `userId` (string/UUID; required)
- `personId` (string/UUID; required)
- `resolvedExisting` (boolean; optional)
- `eventEnqueued` (boolean; optional)
- `correlationId` (string; optional)
- `occurredAt` (datetime; optional)

### Read-only vs editable by state/role
- All fields editable only for authorized Admins; otherwise screen should deny access (403 handling).
- After success, form becomes read-only unless admin clicks “Provision another user” to reset.

### Derived/calculated fields
- `linkStatus` (derived):
  - Default to `PENDING` after success unless backend confirms linked
  - If a link-status endpoint exists, derive from returned link record

---

## 9. Service Contracts (Frontend Perspective)

> Note: Concrete Moqui service names/endpoints are not defined in provided inputs; these are placeholders that require confirmation.

### Load/view calls
1. **Search Person**
   - Request: `GET /people?email=<query>` or `GET /people/search?email=...`
   - Response: list of persons with `personId`, `email`, names
2. **Load Roles for selection**
   - Request: `GET /roles` (security) or equivalent
   - Response: list of roles (id + displayName)

### Create/update calls
- None (people/person creation is out of scope)

### Submit/transition calls
1. **Provision user**
   - Request: `POST /users/provision`
   - Body: `{ personId, username, email, status, initialRoles[] }`
   - Response: `{ userId, personId, correlationId?, resolvedExisting? }`
   - Behavior: returns success without waiting for linking completion

### Error handling expectations
- Backend returns standard HTTP statuses per security checklist; frontend maps as in Business Rules.
- Idempotency: repeated submit due to network retry should not create duplicates; UI should allow retry on safe failure and display resolvedExisting when available.

---

## 10. State Model & Transitions

### Allowed states (frontend workflow state)
- `DRAFT` (editing; not submitted)
- `SUBMITTING`
- `PROVISIONED_PENDING_LINK` (success; async link pending)
- `PROVISIONED_LINKED` (if link confirmation available)
- `ERROR` (submission failed)

### Role-based transitions
- Only authorized Admin can transition `DRAFT → SUBMITTING`.
- Unauthorized users attempting to access screen or submit are blocked (`403`).

### UI behavior per state
- `DRAFT`: editable form, submit enabled when valid.
- `SUBMITTING`: disable inputs and submit; show progress indicator.
- `PROVISIONED_PENDING_LINK`: show success details; optional “Check link status” if supported.
- `PROVISIONED_LINKED`: show confirmed link info.
- `ERROR`: show error summary; keep form values for correction; allow retry.

---

## 11. Alternate / Error Flows

### Validation failures
- If backend returns field errors (400):
  - Highlight corresponding fields
  - Keep Person selection intact
  - Do not clear role selection

### Concurrency conflicts
- If two admins provision same identity simultaneously and backend returns `409`:
  - Show conflict message and suggest “Search existing user” (link TBD) or retry
  - Do not attempt automatic resolution client-side unless backend provides resolution semantics

### Unauthorized access
- If `401`: route to login flow (existing app behavior)
- If `403`: show access denied page/state; do not show provisioning form

### Empty states
- Person search returns no results:
  - Show “No person found for that email”
  - Provide guidance: “Create the person record first” (link to People screen if exists; otherwise plain text)

---

## 12. Acceptance Criteria

### Scenario 1: Successful provisioning with pending link
**Given** I am authenticated as an Admin authorized to provision users  
**And** a Person exists with a known email and `personId`  
**When** I search for the person by email and select the result  
**And** I enter required user fields and select initial roles  
**And** I submit the Provision User form  
**Then** the frontend sends a provisioning request containing `personId` and the entered user fields  
**And** I see a success confirmation including the returned `userId` and `personId`  
**And** I see messaging that linking completes asynchronously (pending)

### Scenario 2: Idempotent retry resolves existing user
**Given** I am authenticated as an authorized Admin  
**And** a previous provisioning attempt for the same identity already created the user  
**When** I submit the provisioning form again with the same identity inputs  
**Then** the frontend displays success (not duplicate-created)  
**And** the response indicates the user was resolved as existing (if backend provides such a flag)  
**And** the displayed `userId` matches the existing user

### Scenario 3: Person not found blocks provisioning
**Given** I am authenticated as an authorized Admin  
**When** I search for a Person by an email that does not exist  
**Then** I see an empty-state message indicating no matching person was found  
**And** the Provision User submit action remains disabled until a Person is selected

### Scenario 4: Backend validation error is shown
**Given** I am authenticated as an authorized Admin  
**And** I have selected a Person  
**When** I submit the form with an invalid email format (or other invalid field)  
**Then** the backend returns a 400 validation error  
**And** the frontend displays the validation message and indicates which field must be corrected  
**And** my entered values remain so I can fix and resubmit

### Scenario 5: Unauthorized access is denied
**Given** I am authenticated as a user without provisioning authorization  
**When** I navigate to Users → Provision User  
**Then** I receive an access denied experience (403 handling)  
**And** I cannot submit provisioning requests

---

## 13. Audit & Observability

### User-visible audit data
- After successful provisioning, display (if returned):
  - `correlationId`
  - `occurredAt`
  - “Provisioned by: <current admin>” (only if available from session; do not fabricate backend audit)

### Status history
- UI should not attempt to build a full audit trail unless an audit endpoint exists.
- If link status endpoint exists, show last-checked time and current status.

### Traceability expectations
- Frontend should pass/propagate a correlation/request ID (mechanism TBD) and log client-side (console/dev logging per project conventions) without exposing sensitive tokens/PII.

---

## 14. Non-Functional UI Requirements
- Performance: Person search should debounce input (e.g., 300ms) to avoid excessive calls (safe UI default).
- Accessibility: All form controls have labels; errors are announced and associated with fields.
- Responsiveness: Screen usable on tablet/desktop; form layout adapts without losing required information.
- i18n/timezone/currency: Not applicable beyond standard localization of dates if `occurredAt` displayed.

---

## 15. Applied Safe Defaults
- SD-UX-01 Debounced search input: Assume a 300ms debounce on person email search to reduce API load; qualifies as safe UI ergonomics. Impacted sections: UX Summary, Functional Behavior, Non-Functional.
- SD-ERR-01 Standard HTTP-to-UI mapping: Use conventional handling for 400/401/403/404/409/5xx without inventing domain policy; safe because it mirrors backend status semantics. Impacted sections: Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Permissions/Access Control (blocking):** What permission(s) (keys) gate:
   - Viewing the Provision User screen
   - Submitting `POST /users/provision`
   (Security contract forbids inventing permission names.)

2. **Endpoint contracts (blocking):** What are the exact Moqui-accessible endpoints/services for:
   - Person search/select (people service) including response fields
   - Role list for selection (security service) including identifier type (roleId vs roleName)
   - Provision user request/response schema and error shape

3. **Identity rules (blocking):**
   - Is `username` required, or is it derived from email by backend?
   - Must provisioned user email **match** the selected Person’s email exactly, or can it differ?

4. **User status enumeration (blocking):** What are allowed `status` values and default?

5. **Initial role assignment rule (blocking):** Are zero roles allowed at provision time, or is at least one role required?

6. **Link status visibility (non-blocking if not required):** Is there an API to query whether the `UserPersonLink` has been created (linked vs pending), and should the frontend poll or provide a manual “Check status” action?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Users: Provision User and Link to Person  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/155  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Users: Provision User and Link to Person

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to create a user in pos-security-service and link them to a person in pos-people** so that **the employee can log in and be correctly attributed across workexec and timekeeping**.

## Details
- Create user with username/email, status, and initial role assignments.
- Link userId ↔ personId using email as primary match.
- Idempotent behavior on retries.

## Acceptance Criteria
- User created (or resolved) in pos-security-service.
- Link created between user and person.
- Initial role assignment applied.
- Audit record captured with actor and timestamp.

## Integration Points (workexec/shopmgr)
- workexec resolves technician attribution using personId/userId link.
- shopmgr receives new staff visibility if assigned to a location.

## Data / Entities
- User (pos-security-service)
- Person (pos-people)
- UserPersonLink (pos-people)

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