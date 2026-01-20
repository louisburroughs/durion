## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** crm-pragmatic

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Party: Create Individual Person Record

## Primary Persona
Customer Service Representative (CSR)

## Business Value
Enable CSRs to create an individual person (Party/Person) with contact information so the person can later be associated to commercial accounts, vehicles, and work orders (e.g., for notifications/approvals).

---

# 2. Story Intent

## As a / I want / So that
- **As a** CSR  
- **I want** to create an individual person record with contact information (phones/emails) and a preferred contact method  
- **So that** the person can be referenced by downstream workflows (account association, vehicle association, work order notifications/approvals).

## In-scope
- A Moqui/Quasar/Vue screen flow to create a **Person** with:
  - required name fields (`firstName`, `lastName`)
  - required `preferredContactMethod` enum
  - optional multiple emails and phones (zero-to-many contact points)
- Client-side validations aligned with backend rules (required fields, email format)
- Submission to backend and handling success/error outcomes
- UX states: initial, submitting, success, and validation/error display

## Out-of-scope
- Role tags (billing contact, driver, approver) including any placeholder UI/schema
- Customer deduplication, merge, or “golden record” workflows (duplicates explicitly allowed)
- Associating person to commercial accounts, vehicles, or work orders (only create the person record here)
- Editing an existing person record

---

# 3. Actors & Stakeholders

- **Primary Actor:** CSR (interactive user)
- **System of Record:** CRM (Party/Person master data)
- **Downstream Consumers (non-editing):** Workorder Execution, Billing (read/reference person identity)
- **Supporting Systems:** Moqui backend services handling Person and ContactPoint persistence

---

# 4. Preconditions & Dependencies

## Preconditions
- CSR is authenticated in the frontend and authorized to create person records.
- Moqui backend endpoints/services for “Create Individual Person” are reachable.

## Dependencies
- Backend contract as referenced by backend story **durion-positivity-backend#111** (Create Person, validations, enums, error codes).
- Controlled vocabulary for `preferredContactMethod`: `EMAIL | PHONE_CALL | SMS | NONE`.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- POS navigation entry: **CRM / Parties / Create Person** (exact menu location may follow existing frontend nav conventions).
- Optional secondary entry: from a “Select/Create Person” chooser flow (if exists) via a “Create New Person” action (link target to the same create screen).

## Screens to create/modify
- **Create:** `apps/pos/screen/crm/person/CreatePerson.xml` (screen name indicative; implement per repo conventions)
  - Contains a form for Person core fields and repeatable contact point inputs
- **Optional (if project uses separate components):**
  - Vue/Quasar component backing the form (e.g., `src/components/crm/person/CreatePersonForm.vue`) wired to Moqui screen via existing integration pattern in this repo.

## Navigation context
- Breadcrumb: CRM → Parties → Create Person
- After success: route/transition to a **Person Detail** screen if it exists; otherwise show an in-place success state with the new `personId` and a “Create another” action.

## User workflows
### Happy path
1. CSR opens Create Person screen.
2. CSR enters `firstName`, `lastName`.
3. CSR selects `preferredContactMethod` from allowed enum.
4. CSR optionally adds 0..N emails and 0..N phone numbers.
5. CSR clicks **Create**.
6. UI submits, shows loading, then success confirmation with returned `personId`.

### Alternate paths
- CSR submits with missing required fields → inline validation messages; no request or request rejected.
- CSR enters invalid email → inline validation; if still submitted backend returns 400; UI shows field-level error.
- CSR adds multiple emails/phones → UI allows dynamic add/remove rows.

---

# 6. Functional Behavior

## Triggers
- Screen load: initialize form model with empty values and defaults.
- User actions: add/remove contact point rows; submit.

## UI actions
- **Add Email**: appends an email contact row with fields:
  - `value` (email string)
  - `isPrimary` (boolean)
- **Add Phone**: appends a phone contact row with fields:
  - `contactType` (enum for phone type; see Data Requirements)
  - `value` (phone string)
  - `isPrimary` (boolean)
- **Remove row**: removes row from local model (not persisted until submit)
- **Set Primary**: when marking a row primary, UI enforces only one primary per kind (EMAIL vs PHONE) in the client model (and shows conflict if user tries to set multiple).

## State changes (frontend view-model)
- `idle` → `editing` (on load)
- `editing` → `submitting` (on Create)
- `submitting` → `success` (201)
- `submitting` → `error` (4xx/5xx)
- `error` → `editing` (on user correction or retry)

## Service interactions
- On submit, call backend create service with:
  - Person fields
  - contact points list (emails + phones)
- On success:
  - store returned `personId` in UI state
  - navigate to detail screen if available, else display success panel

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- `firstName` required (non-empty trimmed string)
- `lastName` required (non-empty trimmed string)
- `preferredContactMethod` required and must be one of: `EMAIL`, `PHONE_CALL`, `SMS`, `NONE`
- Each email `value` must match a valid email format (client-side validation); backend is authoritative
- Contact points are optional; zero contact points is valid

## Enable/disable rules
- Disable **Create** button while `submitting`
- Disable removing rows while `submitting` (to prevent model drift mid-request)

## Visibility rules
- Show contact point sections even if empty; provide empty-state guidance (“No emails added” / “No phones added”)
- Only show success panel after 201

## Error messaging expectations
- For `400 Bad Request`: show a banner “Please correct the highlighted fields” and map backend field errors to inputs where possible.
- For `500+`: show non-PII generic error (“Something went wrong creating the person. Please try again.”) and keep user inputs intact for retry.

---

# 8. Data Requirements

## Entities involved (conceptual; frontend-facing)
- **Person**
- **ContactPoint**

## Fields

### Person (create payload)
| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| firstName | string | yes | none | trimmed |
| lastName | string | yes | none | trimmed |
| preferredContactMethod | enum | yes | NONE? (UI should require explicit selection) | allowed: `EMAIL \| PHONE_CALL \| SMS \| NONE` |

### ContactPoint (create payload; zero-to-many)
Backend reference specifies:
- `contactType` enum examples include `EMAIL`, `PHONE_MOBILE`, `PHONE_HOME`
- `value` string
- `isPrimary` boolean default false

Frontend must support:
| Field | Type | Required | Default | Notes |
|---|---|---:|---|---|
| contactType | enum | yes (per row) | for email rows: `EMAIL` | phone rows: must be chosen from allowed phone contact types if more than one |
| value | string | yes (per row) | none | email validated; phone minimally validated unless backend specifies format |
| isPrimary | boolean | no | false | UI enforces max one primary per kind (EMAIL vs PHONE) |

## Read-only vs editable by state/role
- All fields editable in `editing`
- All fields read-only in `submitting`
- On `success`, fields are read-only; user may choose “Create another” to reset state

## Derived/calculated fields
- None in scope (no dedup indicators, no computed display name rules defined)

---

# 9. Service Contracts (Frontend Perspective)

> Note: exact service names/routes must match Moqui implementation in this repo; below is the required contract behavior consistent with backend story #111.

## Load/view calls
- None required (create-only screen)

## Create/update calls
### Create Individual Person
- **Action:** create Person + ContactPoints in a single transaction
- **Method:** (expected) `POST`
- **Request body:**
  - `firstName`, `lastName`, `preferredContactMethod`
  - `contactPoints`: array of `{ contactType, value, isPrimary }` (or equivalent structure used by backend)
- **Success:**
  - `201 Created`
  - response includes `personId` (UUID)
- **Error cases:**
  - `400 Bad Request` for missing required fields or invalid formats (e.g., email)
  - `403 Forbidden` if not authorized
  - `500 Internal Server Error` on persistence failure (transaction rolled back)

## Submit/transition calls
- None (no state machine transitions defined for Person creation)

## Error handling expectations
- Parse backend error response:
  - If field-level validation errors present, bind to respective inputs
  - Otherwise show banner error with request correlation id if available (do not display sensitive info)

---

# 10. State Model & Transitions

## Allowed states (UI state, not domain lifecycle)
- `editing`
- `submitting`
- `success`
- `error`

## Role-based transitions
- CSR with create permission can transition `editing → submitting`
- Unauthorized user:
  - can view screen shell but submit results in `403` handling (or screen access blocked depending on existing auth patterns)

## UI behavior per state
- `editing`: full form enabled; inline validations on blur and on submit
- `submitting`: disable inputs and show progress indicator
- `success`: show created `personId`; provide navigation actions
- `error`: show banner; preserve entered data; allow retry

---

# 11. Alternate / Error Flows

## Validation failures (client-side)
- Missing `firstName`/`lastName`/`preferredContactMethod`:
  - Prevent submit
  - Show inline required errors

## Validation failures (server-side 400)
- Invalid email format not caught client-side:
  - Show inline error on the offending email row if backend identifies it; otherwise show banner and keep values

## Concurrency conflicts
- Not applicable for create-only (no optimistic locking required)

## Unauthorized access
- `403` on submit:
  - Show “You don’t have permission to create a person record.”
  - Do not clear form

## Empty states
- No emails/phones added:
  - Allowed; create proceeds with just Person fields

---

# 12. Acceptance Criteria

## Scenario 1: Minimal person create succeeds
**Given** I am an authenticated CSR with permission to create person records  
**And** I am on the Create Person screen  
**When** I enter `firstName` and `lastName`  
**And** I select `preferredContactMethod = NONE`  
**And** I click Create  
**Then** the system submits a create request  
**And** I receive a success response (201) with a `personId`  
**And** the UI shows confirmation including the new `personId`

## Scenario 2: Create with multiple emails and phones
**Given** I am an authenticated CSR with permission to create person records  
**When** I enter a valid name and select `preferredContactMethod = EMAIL`  
**And** I add 2 email contact rows with valid email values  
**And** I add 2 phone contact rows with values  
**And** I click Create  
**Then** the request includes 4 contact points  
**And** the create succeeds with 201 and returns a `personId`  
**And** the UI indicates success

## Scenario 3: Missing last name is blocked
**Given** I am on the Create Person screen  
**When** I enter `firstName` but leave `lastName` empty  
**And** I select a preferred contact method  
**And** I click Create  
**Then** the UI shows a required-field validation error for `lastName`  
**And** no create request is submitted (or if submitted, the UI displays backend 400 and no success state)

## Scenario 4: Invalid email is rejected and nothing is created
**Given** I am on the Create Person screen  
**When** I enter valid `firstName`, `lastName`, and preferred contact method  
**And** I add an email contact point with value `not-an-email`  
**And** I click Create  
**Then** the UI shows an email format error (client-side or from backend 400)  
**And** the UI remains in editing/error state with user input preserved  
**And** the UI does not show a created `personId`

## Scenario 5: Not authorized to create
**Given** I am authenticated but do not have permission to create person records  
**When** I attempt to create a person  
**Then** the backend returns 403 (or access is blocked before submit per app convention)  
**And** the UI shows an authorization error message  
**And** no success confirmation is shown

---

# 13. Audit & Observability

## User-visible audit data
- Not required in UI for this story (creation-only)

## Status history
- Not applicable (no lifecycle states defined for Person)

## Traceability expectations
- Frontend should include/request a correlation id where supported by existing Moqui frontend pattern.
- On successful create, backend emits `PERSON_CREATED` (reference), but UI does not need to display it.

---

# 14. Non-Functional UI Requirements

- **Performance:** Create submission should complete within acceptable interactive latency; UI must show loading state immediately on submit.
- **Accessibility:** All inputs have labels; validation errors are announced to screen readers; keyboard navigation supports add/remove contact point rows.
- **Responsiveness:** Usable on standard POS tablet/desktop breakpoints.
- **i18n/timezone/currency:** Not applicable (no monetary/time fields). Use existing i18n mechanism for labels/messages if present in repo.

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide explicit empty-state text and “Add Email/Add Phone” actions when no contact points exist; qualifies as safe because it does not change domain rules, only improves usability. Impacted sections: UX Summary, Alternate/Empty states.
- SD-UX-SUBMIT-DISABLE: Disable submit and inputs during in-flight request to prevent duplicate submits; safe because it’s standard UI ergonomics and does not alter business logic. Impacted sections: Functional Behavior, Business Rules, Error Flows.
- SD-ERR-GENERIC-5XX: Show generic non-PII error for 5xx while preserving form state; safe because it aligns with secure error-handling and doesn’t invent business policy. Impacted sections: Business Rules, Error Flows, Service Contracts.

---

# 16. Open Questions

- none

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Party: Create Individual Person Record  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/175  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Create Individual Person Record

**Domain**: payment

### Story Description

/kiro  
# User Story

## Narrative  
As a **CSR**, I want **to create an individual person record with contact information** so that **I can associate them to a commercial account and/or vehicles**.

## Details  
- Capture: name, phone(s), email(s), preferred contact method.  
- Support role tags later (billing contact, driver, approver).

## Acceptance Criteria  
- Can create person.  
- Can store multiple phones/emails.  
- Preferred contact method stored.

## Integration Points (Workorder Execution)  
- Workorder Execution can select a person for notifications/approvals.

## Data / Entities  
- Party/Person  
- ContactPoint  
- Preferences

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: CRM

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