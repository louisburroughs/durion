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

## Alternate / Error Flows
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
