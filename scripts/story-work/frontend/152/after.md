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
[FRONTEND] [STORY] People: Create/Update Employee Profile (pos-people)

### Primary Persona
HR Administrator (HR user)

### Business Value
Maintain accurate employee profiles (identity, status, contact info) for operational directory use and downstream consistency in work execution and shop management, with auditability and duplicate detection feedback.

---

## 2. Story Intent

### As a / I want / So that
**As an** HR Administrator,  
**I want** to create and update employee profiles from the POS frontend,  
**so that** employee identity and employment status are accurate, auditable, and available to downstream systems.

### In-scope
- Moqui/Vue/Quasar frontend screens to:
  - Create a new Employee Profile
  - Edit an existing Employee Profile
  - View duplicate warnings and conflict errors
  - Display audit metadata (created/updated timestamps; actor if available)
- Field-level validation aligned to People domain rules (required fields, date rules, enum constraints)
- Handling of backend responses: 201/200 success, 400 validation, 403 forbidden, 404 not found, 409 conflict, warnings payload

### Out-of-scope
- Role assignment, user disable/termination workflows, location assignments
- Time entry approval, breaks, scheduling/dispatch
- Implementing downstream consumers (workexec/shopmgr); only ensure data is captured consistently
- Defining backend entities/services beyond calling existing endpoints/contracts

---

## 3. Actors & Stakeholders
- **HR Administrator (primary actor):** creates/updates employee profiles.
- **Store/Operations Managers (stakeholder):** consume accurate employee status/name in rosters (indirect).
- **Downstream systems (stakeholders):** workexec, shopmgr consume employee identity/status.
- **Audit/Compliance (stakeholder):** expects traceability of changes.

---

## 4. Preconditions & Dependencies
- User is authenticated in the POS frontend.
- User is authorized for employee profile operations:
  - Create requires `people.employee.create`
  - Update requires `people.employee.update`
- Backend provides API endpoints (or Moqui services) consistent with backend story reference:
  - `POST /employees` create
  - `PUT /employees/{employeeId}` update
  - A “get employee” endpoint for edit screen prefill (not specified in inputs; see Open Questions)
- Backend returns:
  - `409 Conflict` on high-confidence duplicates
  - `warnings[]` payload on ambiguous duplicates (soft warning)
  - Audit fields (createdAt, updatedAt) in response

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: People / Employees (exact menu placement TBD)
- “Create Employee” button from Employees list (list screen itself is not required by this story but may exist; see Open Questions)
- “Edit” action from an employee view/edit context (e.g., list row action or direct URL)

### Screens to create/modify
- **New screen**: `apps/pos-people/screen/EmployeeProfileEdit.xml` (create + edit unified)
  - Mode determined by presence of `employeeId` parameter
- **(Optional, if missing)**: minimal `EmployeeProfileView.xml` read-only view (only if needed to satisfy navigation; otherwise edit screen can display read-only fields as needed)

### Navigation context
- URL patterns (Moqui screen paths; exact root depends on repo conventions):
  - Create: `/pos-people/employee/create`
  - Edit: `/pos-people/employee/:employeeId/edit`
- Breadcrumb: People > Employee > (Create | Edit)

### User workflows
**Happy path (Create)**
1. HR opens Create Employee.
2. Enters required fields + optional contact info.
3. Clicks Save.
4. UI shows success message and stays on edit screen with new `employeeId` (or routes to view/edit).

**Happy path (Update)**
1. HR opens Edit for existing employee.
2. Updates allowed fields.
3. Clicks Save.
4. UI shows success message; updated timestamps refresh.

**Alternate path (Soft duplicate warning)**
- Save succeeds but backend returns `warnings[]`; UI displays non-blocking warning panel and retains saved state.

**Alternate path (Hard duplicate conflict)**
- Save fails with 409; UI shows blocking banner with conflict details and highlights relevant fields.

---

## 6. Functional Behavior

### Triggers
- Screen load:
  - If `employeeId` present: load employee profile for edit.
  - If absent: initialize empty form with defaults.
- User action: Save (create/update).

### UI actions
- Form fields editable per rules (see Data Requirements).
- Save button:
  - Disabled while request in-flight.
  - On click: validate client-side; if valid, call backend.
- Cancel button:
  - Navigates back to previous screen or employee list (TBD) without saving; warns on unsaved changes (safe UI default).

### State changes (frontend)
- `formState`: pristine/dirty tracking
- `saveState`: idle/loading/success/error
- `warningsState`: none | present (from backend)
- On create success: route updates to include new `employeeId`.

### Service interactions (Moqui)
- Load:
  - Call a service or REST endpoint to fetch employee profile by `employeeId`.
- Save:
  - If create: call create endpoint/service with payload
  - If update: call update endpoint/service with payload

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
Client-side validations (must mirror backend; backend remains authoritative):
- Required on create (and required to remain present on update):
  - `legalName` (non-empty)
  - `employeeNumber` (non-empty)
  - `status` (must be one of `ACTIVE | ON_LEAVE | SUSPENDED | TERMINATED`)
  - `hireDate` (valid date)
- Date rule:
  - If `terminationDate` provided, must be **>=** `hireDate`
- Email normalization expectation:
  - UI should trim whitespace; convert to lowercase before submit (safe, reversible normalization)
- Phone normalization expectation:
  - UI should trim whitespace; do not guess E.164 formatting rules beyond stripping spaces/dashes unless backend provides helper/formatter (see Open Questions)

### Enable/disable rules
- Save disabled when:
  - Required fields missing/invalid
  - Request in-flight
- Termination date field:
  - Enabled always, but validated against hire date
- Status change:
  - Always editable by authorized HR user (no additional lifecycle restrictions were provided)

### Visibility rules
- Show warning panel if backend returns `warnings[]` (soft duplicate).
- Show field-level errors when backend returns 400 with validation details.
- Show blocking banner on 409 duplicate conflict.
- Show read-only audit fields when available: createdAt, updatedAt (and createdBy/updatedBy if returned).

### Error messaging expectations
- 400: “Please correct highlighted fields.” + per-field messages.
- 409: “Possible duplicate detected. Cannot save with duplicate employee number/email/phone.” (Use backend-provided message if present.)
- 403: “You don’t have permission to manage employee profiles.”
- 404 (edit load): “Employee not found.”

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- **EmployeeProfile** (authoritative in People domain; may map to `Person` + profile internally, but UI treats it as Employee Profile)

### Fields (type, required, defaults)
**EmployeeProfile**
- `employeeId`: UUID/string (read-only; required for edit mode)
- `legalName`: string (required)
- `preferredName`: string (optional)
- `employeeNumber`: string (required; unique)
- `status`: enum (required; default on create TBD—see Open Questions)
- `hireDate`: date (required)
- `terminationDate`: date (optional; must be >= hireDate)
- `contactInfo.primaryEmail`: string/email (optional; unique if provided; UI lowercases)
- `contactInfo.primaryPhone`: string (optional; unique if provided)
- `contactInfo.secondaryEmail`: string/email (optional; UI lowercases)
- `contactInfo.secondaryPhone`: string (optional)
- `contactInfo.address.*`: strings (optional)
- `contactInfo.emergencyContact.*`: strings (optional)
- `createdAt`: timestamp (read-only; display if provided)
- `updatedAt`: timestamp (read-only; display if provided)

### Read-only vs editable by state/role
- By role:
  - Only users with `people.employee.create` can access create route and submit create.
  - Only users with `people.employee.update` can access edit route and submit update.
- By status:
  - No additional UI restrictions provided for editing TERMINATED employees; must not assume policy. (Open Question)

### Derived/calculated fields
- None required on frontend.
- Optional display-only “Display Name” = preferredName if present else legalName (safe UI convenience; does not persist).

---

## 9. Service Contracts (Frontend Perspective)

> Backend reference mentions REST-style endpoints. Moqui frontend may call via XHR to backend REST or via Moqui services; exact integration mechanism depends on repo conventions (Open Question). Below is the required contract behavior.

### Load/view calls
- **Get Employee Profile**
  - `GET /employees/{employeeId}` (or Moqui service `people.EmployeeProfile.get`)
  - Success: 200 with EmployeeProfile payload (fields above)
  - Errors: 403/404

### Create/update calls
- **Create**
  - `POST /employees`
  - Request body: EmployeeProfile fields excluding `employeeId`, `createdAt`, `updatedAt`
  - Response: 201 with created profile
  - May include `warnings: [{ code, matches? }]`
  - Errors: 400, 403, 409
- **Update**
  - `PUT /employees/{employeeId}`
  - Request body: full modifiable profile fields
  - Response: 200 with updated profile
  - May include `warnings[]`
  - Errors: 400, 403, 404, 409

### Submit/transition calls
- None (no lifecycle transition endpoints beyond update were provided)

### Error handling expectations
- 400 returns structured field errors (format TBD; UI must handle both:
  - Moqui `errorInfo`/`errors` patterns, and/or
  - REST validation error arrays)
- 409 includes:
  - message
  - optional `possibleDuplicatePersonId` only if authorized (UI should not require it)
- Warnings are non-blocking and must be displayed after save.

---

## 10. State Model & Transitions

### Allowed states (EmployeeProfile.status)
- `ACTIVE`
- `ON_LEAVE`
- `SUSPENDED`
- `TERMINATED`

### Role-based transitions
- Not defined beyond “HR can create/update”.
- UI must allow selecting any enum value unless backend rejects.

### UI behavior per state
- Display current status prominently (as data, not layout).
- If status is `TERMINATED` and terminationDate missing, UI should allow entry and validate against hireDate (do not auto-set).

---

## 11. Alternate / Error Flows

### Validation failures (400)
- Backend returns validation errors.
- UI maps errors to fields and shows summary banner.
- Form remains editable; save re-enabled after response.

### Duplicate conflicts (409)
- UI shows blocking banner with backend message.
- If backend indicates which field collided (preferred), highlight those fields; otherwise highlight employeeNumber and primaryEmail/primaryPhone.

### Concurrency conflicts
- Not specified (ETag/versioning not provided). If backend returns 409 for stale update, UI shows generic conflict and prompts reload. (Open Question: do we have row version?)

### Unauthorized access (403)
- On load or save: show permission error and disable editing; provide navigation away.

### Empty states
- Create screen: empty form with required-field indicators.
- Edit screen: while loading, show loading state; if 404, show not-found empty state.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create employee profile successfully
**Given** I am authenticated as a user with permission `people.employee.create`  
**When** I navigate to the Create Employee Profile screen  
**And** I enter `legalName`, `employeeNumber`, `status`, and `hireDate` with valid values  
**And** I click Save  
**Then** the frontend sends a create request to the backend  
**And** I see a success confirmation  
**And** the form displays the returned `employeeId` and audit timestamps if provided

### Scenario 2: Update employee profile successfully
**Given** I am authenticated as a user with permission `people.employee.update`  
**And** an employee exists with `employeeId = E123`  
**When** I open the Edit Employee Profile screen for `E123`  
**And** I change `preferredName`  
**And** I click Save  
**Then** the frontend sends an update request for `E123`  
**And** I see a success confirmation  
**And** the displayed `updatedAt` value refreshes if provided

### Scenario 3: Block create on high-confidence duplicate (409)
**Given** I am authenticated as a user with permission `people.employee.create`  
**And** the backend would detect an existing employee with the same `primaryEmail`  
**When** I attempt to create an employee profile with that same `primaryEmail`  
**Then** the save fails  
**And** I see a blocking duplicate error message  
**And** no navigation occurs away from the create form

### Scenario 4: Show soft duplicate warning when backend returns warnings
**Given** I am authenticated as a user with permission `people.employee.create`  
**And** the backend is configured to return `warnings` for ambiguous duplicates  
**When** I create an employee profile that triggers an ambiguous duplicate warning  
**Then** the employee profile is created successfully  
**And** I see a non-blocking warning panel displaying the warning code(s) returned by the backend

### Scenario 5: Client-side validation prevents submit for missing required fields
**Given** I am authenticated as a user with permission `people.employee.create`  
**When** I leave `legalName` empty  
**Then** the Save action is disabled or shows a validation error on Save  
**And** no create request is sent until the field is corrected

### Scenario 6: Date validation for termination date
**Given** I am editing an employee profile  
**When** I set `terminationDate` to a date earlier than `hireDate`  
**Then** I see a validation error indicating termination date must be on or after hire date  
**And** I cannot successfully save until corrected

### Scenario 7: Forbidden access
**Given** I am authenticated without `people.employee.create`  
**When** I navigate to the Create Employee Profile screen  
**Then** I see an access denied message  
**And** I cannot submit a create request

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided by backend):
  - `createdAt`, `updatedAt`
  - (Optional) `createdByUserId`, `updatedByUserId` or names if backend supplies

### Status history
- Not in scope unless backend provides an endpoint; do not invent.

### Traceability expectations
- Frontend must propagate correlation/request ID headers if the app convention supports it (e.g., `X-Correlation-Id`) (Open Question).
- Log (frontend console/logger) non-PII operational events:
  - screen load success/failure
  - save attempt/result
  - duplicate warning received (code only; avoid printing full profile details)

---

## 14. Non-Functional UI Requirements
- **Performance:** edit screen loads within 2s on typical network for a single profile payload; show skeleton/loading indicator.
- **Accessibility:** all form inputs labeled; validation errors associated to inputs; keyboard navigable; color not sole indicator.
- **Responsiveness:** usable on tablet-sized screens typical for POS back office.
- **i18n/timezone:** dates shown in tenant/user locale/timezone if the app supports it; do not assume multi-currency requirements.

---

## 15. Applied Safe Defaults
- SD-UX-UNSAVED-CHANGES: Warn on navigation away with unsaved form changes; safe because it’s reversible UI behavior and does not affect domain policy; impacts UX Summary, Alternate/Error Flows.
- SD-UX-LOADING-STATES: Provide loading indicator and disabled Save during requests; safe because it prevents double-submit without changing business rules; impacts UX Summary, Functional Behavior.
- SD-UX-WARNING-DISPLAY: Display backend `warnings[]` as non-blocking callout after save; safe because it reflects backend-provided data without altering decisions; impacts Business Rules, Alternate/Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend read endpoint:** What is the authoritative endpoint/service to load an employee profile for edit (`GET /employees/{id}` vs Moqui service name)? What is the exact response schema?
2. **Error payload shape:** What is the standard error response format in this frontend/backoffice stack for 400/409 (field error mapping keys)? Provide an example payload.
3. **Routing/menu conventions:** What is the correct Moqui screen path/module for `pos-people` (e.g., `/apps/pos-people/...`) and where should the navigation entry live?
4. **Default status on create:** Should new employees default to `ACTIVE`, `ON_LEAVE`, or require explicit selection (no default)?
5. **Edit rules for TERMINATED:** Are TERMINATED employees editable (e.g., contact info corrections) or locked except for specific fields? If locked, which fields remain editable and under what permission?
6. **Phone normalization:** Should the frontend enforce/format E.164, and if so, what library/pattern is standard in this repo? Or is normalization strictly backend-only?
7. **Optimistic concurrency:** Does the backend require/return a version/etag (e.g., `lastUpdatedStamp`) that must be sent on update to prevent lost updates?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Users: Create/Update Employee Profile (pos-people) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/152


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Users: Create/Update Employee Profile (pos-people)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/152
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Users: Create/Update Employee Profile (pos-people)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **HR user**, I want **to maintain employee profile details** so that **the organization has a reliable directory for operations and compliance**.

## Details
- Fields: legal name, preferred name, employee number, status, hire/term dates, contact info.
- Basic duplicate warning based on employee number and email.

## Acceptance Criteria
- Create/update person with required fields.
- Duplicate warning presented for likely collisions.
- All changes audited.

## Integration Points (workexec/shopmgr)
- workexec displays technician identity consistently.
- shopmgr shows staff name and status for rosters.

## Data / Entities
- Person

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


BACKEND STORY REFERENCES (FOR REFERENCE ONLY)

----------------------------------------------------------------------------------------------------

Backend matches (extracted from story-work):


[1] backend/88/backend.md

    Labels: type:story, domain:people, status:ready-for-dev


----------------------------------------------------------------------------------------------------

Backend Story Full Content:



### BACKEND STORY #1: backend/88/backend.md

------------------------------------------------------------

Title: [BACKEND] [STORY] Users: Create/Update Employee Profile (pos-people)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/88
Labels: type:story, domain:people, status:ready-for-dev

STOP: Clarification required before finalization

## 🏷️ Labels (Final)

### Required
- type:story
- domain:people
- status:ready-for-dev

### Recommended
- agent:people
- agent:story-authoring

---
**Rewrite Variant:** crm-pragmatic

---

## Story Intent
**As an** HR Administrator,
**I want to** create and maintain comprehensive employee profiles through a system API,
**so that** employee data is accurate, centralized, and available for operational, compliance, and integrated system needs.

## Actors & Stakeholders
- **HR Administrator:** The primary user responsible for creating and managing employee data.
- **System (API Consumer):** Any authorized internal system that needs to create or update employee records.
- **`workexec` System (Downstream Consumer):** Relies on accurate employee data (especially for technicians) to display user identity.
- **`shopmgr` System (Downstream Consumer):** Uses employee name and status for workforce management and rosters.
- **Audit System (Stakeholder):** Subscribes to all data modification events for compliance and traceability.

## Preconditions
- The calling user or system is authenticated.
- The calling user or system possesses the necessary permissions (`people.employee.create`, `people.employee.update`) to manage employee profiles.

## Functional Behavior

### 1. Create Employee Profile
- The system shall provide an API endpoint (e.g., `POST /employees`) to create a new employee profile.
- The endpoint accepts a payload containing the employee's details as specified in the Data Requirements.
- Before creation, the system MUST validate that the proposed `employeeNumber` and `primaryEmail` do not already exist for another employee.
- The system performs duplicate detection checking high-confidence keys (email, phone, externalEmployeeNumber):
  - If exact match found: return `409 Conflict` with optional `possibleDuplicatePersonId` (only if caller authorized)
  - If ambiguous match found: create profile and return `200/201` with `warnings: [{ code: "POSSIBLE_DUPLICATE", matches: [...] }]`
- Upon successful creation, the system returns the full profile of the newly created employee, including a system-generated unique identifier (`employeeId`), and returns an HTTP `201 Created` status.
- A `EmployeeProfileCreated` event is emitted for auditing purposes.

### 2. Update Employee Profile
- The system shall provide an API endpoint (e.g., `PUT /employees/{employeeId}`) to update an existing employee profile.
- The endpoint accepts a payload containing the complete set of modifiable fields.
- The system MUST validate that the target `employeeId` exists before attempting an update.
- If the `employeeNumber` or `primaryEmail` are being changed, the system MUST validate their uniqueness against other records (excluding the current employee).
- The system performs duplicate detection on the new values using the same high-confidence/soft-warning logic as create.
- Upon successful update, the system returns the full, updated profile and an HTTP `200 OK` status.
- A `EmployeeProfileUpdated` event is emitted for auditing purposes, including before/after snapshot of changed fields.

### 3. Contact Info Validation (Workflow Gate)
- **Before activation or assignment to schedules/timekeeping:** The system SHALL require at least one reliable contact method (e.g., `primaryEmail` or `primaryPhone`).
- This is a **workflow enforcement**, not a persistence constraint (to support progressive onboarding).
- Downstream services (workexec, shopmgr) should check contact info completeness before assignment operations.

### Alternate / Error Flows
- **Invalid Data:** If the request payload is missing required fields or contains malformed data (e.g., invalid date format), the API shall return an HTTP `400 Bad Request` response with a clear error message detailing the validation failures.
- **Duplicate Data on Create (High-Confidence):** If an attempt is made to create a profile with an exact match on email, phone, or employee number that already exists, the API shall return an HTTP `409 Conflict` response.
- **Duplicate Data on Create (Ambiguous):** If name similarity, partial phone match, or same address is detected, the API shall create the profile and return `200/201 Created` with a warning payload.
- **Resource Not Found:** If an update is attempted for an `employeeId` that does not exist, the API shall return an HTTP `404 Not Found` response.
- **Authorization Failure:** If the authenticated actor lacks the required permissions to perform the action, the API shall return an HTTP `403 Forbidden` response.

## Business Rules
- `employeeNumber` must be unique across all employee profiles.
- The primary email address in `contactInfo` must be unique across all employee profiles (if provided).
- `legalName`, `employeeNumber`, `status`, and `hireDate` are mandatory fields for profile creation.
- A `terminationDate`, if provided, cannot be earlier than the `hireDate`.
- **Status Lifecycle (Authoritative - NEW):**
  - `ACTIVE` — employed and eligible for assignment/work
  - `ON_LEAVE` — employed but temporarily inactive for scheduling/timekeeping
  - `SUSPENDED` — access/work paused pending action (administrative)
  - `TERMINATED` — employment ended; historical record retained
- **Status Rules:**
  - `ON_LEAVE` and `SUSPENDED` are treated as **not eligible for new assignments** by consuming services.
  - If you maintain separate User status, keep it aligned with Person status (e.g., User `DISABLED` while Person `SUSPENDED`).
- **Duplicate Detection Policy (NEW):**
  - Hard-block (409) for high-confidence matches: exact email, exact phone (E.164), exact externalEmployeeNumber, government ID
  - Soft warning for ambiguous matches: name similarity, partial phone, same address
  - Configuration: `duplicatePolicy = STRICT | BALANCED` (default: BALANCED)
  - Duplicate keys at launch: email, phone, externalEmployeeNumber
- **Contact Info Requirement (NEW):**
  - No required fields at persistence level (progressive onboarding)
  - At least one reliable contact method required **before activation/assignment** (workflow gate, not persistence)
  - Recommended: primaryEmail, primaryPhone, emergencyContact (for field staff)
  - Normalization: lowercase emails, E.164 for phones

## Data Requirements

### EmployeeProfile Entity
| Field           | Type          | Constraints                                       | Description                                       |
|-----------------|---------------|---------------------------------------------------|---------------------------------------------------|
| `employeeId`    | UUID          | Primary Key, System-Generated, Not Null           | Unique identifier for the employee profile.       |
| `legalName`     | String        | Not Null                                          | The employee's full legal name.                   |
| `preferredName` | String        | Nullable                                          | The name the employee prefers to be called.       |
| `employeeNumber`| String        | Not Null, Unique                                  | The unique number assigned to the employee.       |
| `status`        | Enum          | Not Null, `ACTIVE \| ON_LEAVE \| SUSPENDED \| TERMINATED` | The current employment status of the employee. |
| `hireDate`      | Date          | Not Null                                          | The date the employee was hired.                  |
| `terminationDate`| Date         | Nullable, >= hireDate                            | The date the employee's employment was terminated.|
| `contactInfo`   | JSON / Object | Structured (see below)                           | Structured contact information.                   |
| `createdAt`     | Timestamp     | Not Null, System-Managed                          | Timestamp of when the record was created.         |
| `updatedAt`     | Timestamp     | Not Null, System-Managed                          | Timestamp of the last update to the record.       |

### ContactInfo Structure (Nested)
```json
{
  "primaryEmail": "string (optional, unique if provided, normalized lowercase)",
  "primaryPhone": "string (optional, unique if provided, normalized E.164)",
  "secondaryEmail": "string (optional, normalized lowercase)",
  "secondaryPhone": "string (optional, normalized E.164)",
  "address": {
    "line1": "string (optional)",
    "line2": "string (optional)",
    "city": "string (optional)",
    "region": "string (optional)",
    "postalCode": "string (optional)",
    "country": "string (optional)"
  },
  "emergencyContact": {
    "name": "string (optional)",
    "relationship": "string (optional)",
    "phone": "string (optional)",
    "email": "string (optional)"
  }
}
```

## Acceptance Criteria

### AC-1: Successfully Create a New Employee Profile
**Given** an HR Administrator is authenticated and authorized
**When** they submit a valid request to the `POST /employees` endpoint with all required fields
**And** the `employeeNumber` and `primaryEmail` are unique
**Then** the system shall create a new employee profile
**And** return an HTTP `201 Created` status with the new profile data
**And** an `EmployeeProfileCreated` audit event is published.

### AC-2: Successfully Update an Existing Employee Profile
**Given** an HR Administrator is authenticated and authorized
**And** an employee profile with ID `E123` exists
**When** they submit a valid request to `PUT /employees/E123` to change the `preferredName`
**Then** the system shall update the employee profile
**And** return an HTTP `200 OK` status with the updated profile data
**And** an `EmployeeProfileUpdated` audit event is published with before/after snapshot of changed fields.

### AC-3: Fail to Create a Profile with a Duplicate (High-Confidence)
**Given** an employee profile exists with `primaryEmail` "jane@example.com"
**And** an HR Administrator is authenticated and authorized
**When** they submit a request to `POST /employees` with `primaryEmail` "jane@example.com"
**Then** the system shall reject the request
**And** return an HTTP `409 Conflict` response with a relevant error message.

### AC-4: Create with Soft Warning on Ambiguous Duplicate
**Given** an employee profile exists with name "Jane Smith" and phone "+1-555-0100"
**And** an HR Administrator submits a new profile for "Jane Smythe" with phone "+1-555-0101" (similar but not exact)
**When** the request is submitted
**Then** the system shall create the profile
**And** return `201 Created` with warning payload: `warnings: [{ code: "POSSIBLE_DUPLICATE", matches: [...] }]`

### AC-5: Fail to Create a Profile with Missing Required Fields
**Given** an HR Administrator is authenticated and authorized
**When** they submit a request to `POST /employees` that is missing the `legalName`
**Then** the system shall reject the request
**And** return an HTTP `400 Bad Request` response detailing the missing field.

### AC-6: Enforce Contact Info Completeness Before Assignment
**Given** an employee profile exists with no `primaryEmail` or `primaryPhone`
**When** a downstream service (workexec, shopmgr) attempts to assign this employee to a schedule or work
**Then** the assignment is rejected with a message: "Employee must have at least one contact method (email or phone) before assignment."

### AC-7: Audit Event Includes Before/After Snapshot
**Given** an update to employee profile status from `ACTIVE` to `ON_LEAVE`
**When** the update is successfully completed
**Then** an `EmployeeProfileUpdated` audit event is published containing:
- `employeeId`, actor ID, timestamp
- `beforeState: { status: "ACTIVE", ... }`
- `afterState: { status: "ON_LEAVE", ... }`

## Audit & Observability
- **Audit Events:**
  - `EmployeeProfileCreated`: Triggered on successful creation. Must include the new profile data, actor ID, timestamp, and optional duplicate warning details.
  - `EmployeeProfileUpdated`: Triggered on successful update. Must include the `employeeId`, actor ID, timestamp, and before/after snapshot of all changed fields.
  - Include in event: `duplicatePolicy` setting used (STRICT or BALANCED), any warnings returned to caller
- **Metrics:**
  - Monitor latency and error rates (4xx/5xx) for the `POST /employees` and `PUT /employees/{employeeId}` endpoints.
  - `employee.profile.created` (Counter): Incremented on successful creation.
  - `employee.profile.updated` (Counter): Incremented on successful update.
  - `employee.profile.duplicate_detected` (Counter): Incremented when duplicate detection triggers (hard-block or soft warning).
  - `employee.profile.contact_incomplete` (Counter): Incremented when contact completeness workflow gate is triggered.
- **Logging:**
  - INFO: Profile creation/update success
  - WARN: Duplicate detection (soft warning)
  - ERROR: Profile creation/update failure, validation errors

## Original Story (Unmodified – For Traceability)
# Issue #88 — [BACKEND] [STORY] Users: Create/Update Employee Profile (pos-people)

[Original story body preserved as provided in previous issue snapshot]


------------------------------------------------------------

====================================================================================================

END BACKEND REFERENCES