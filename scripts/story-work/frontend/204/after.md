STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:accounting
- status:draft

### Recommended
- agent:accounting-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] CoA: Create and Maintain Chart of Accounts (GL Accounts)

**Primary Persona:** Finance Manager / Accountant (authorized CoA maintainer)

**Business Value:** Maintain an accurate, auditable Chart of Accounts so downstream posting, reporting, and controls (effective dating, deactivation) behave predictably and compliantly.

---

## 2. Story Intent

**As a** Finance Manager / Accountant,  
**I want** a POS admin UI to create, view, edit (limited fields), and deactivate GL accounts with effective dating and audit visibility,  
**so that** the Chart of Accounts stays clean, controlled, and usable for financial classification and posting.

### In-scope
- List/search GL accounts with filters (type, status, effective dating windows)
- View GL account details including audit metadata
- Create GL account (code, name, type, description, activeFrom, optional activeThru)
- Update allowed fields only (name/description)
- Deactivate GL account by setting `activeThru` effective date/time (subject to backend policy)
- Show and handle backend validation errors (duplicate codes, invalid dates, policy violations, auth)

### Out-of-scope
- Defining/deleting chart structures (parent/child hierarchies) unless provided by API
- Editing `accountCode` or `accountType` after creation
- Any GL posting, balances display, reconciliation, or reporting screens
- Posting Categories / mappings / rule sets (explicitly out unless separately specified)

---

## 3. Actors & Stakeholders

- **Primary Actor:** Finance Manager / Accountant
- **Secondary Actors:** Auditor (read-only needs), System Administrator (may grant permissions)
- **System Dependencies:** Accounting backend service (GL account endpoints), AuthN/AuthZ provider, Audit log provider (if separate)

---

## 4. Preconditions & Dependencies

- User is authenticated.
- User has permission `CoA:Manage` for create/update/deactivate; view permission is **unclear** (see Open Questions).
- Backend provides APIs for:
  - listing GL accounts (paged)
  - retrieving GL account by id
  - creating GL account
  - patching/updating allowed fields
  - deactivating with effective date
- Backend enforces:
  - unique account codes
  - canonical account types: ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE
  - effective dating constraints
  - deactivation policy constraints (not fully defined here)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Accounting → Chart of Accounts**
- Direct route (proposed): `/accounting/coa` (list), `/accounting/coa/account/{accountId}` (detail)

### Screens to create/modify (Moqui)
- **New Screen:** `apps/pos/screen/accounting/Coa.xml` (container + sub-screens)
  - `CoaList.xml` (list/search)
  - `CoaCreate.xml` (create form)
  - `CoaDetail.xml` (view + edit + deactivate action)
- Add menu entry in the app’s navigation (per repo conventions)

### Navigation context
- Breadcrumbs:
  - Accounting → Chart of Accounts → (List | Create | Account Detail)

### User workflows
**Happy path: Create**
1. User opens CoA list.
2. Clicks “Create GL Account”.
3. Fills required fields and submits.
4. On success, navigates to detail view for the new account with confirmation banner.

**Happy path: Update name/description**
1. From detail screen, user enters edit mode for allowed fields.
2. Saves changes; sees updated metadata (updatedAt/updatedBy).

**Happy path: Deactivate**
1. From detail screen, user selects “Deactivate”.
2. Provides effective deactivation date/time (defaults to now, editable).
3. Confirms; on success, detail shows status “Inactive/Scheduled” based on dates.

**Alternate: View-only**
- User can list and view details without edit actions if lacking `CoA:Manage`.

---

## 6. Functional Behavior

### Triggers
- Screen load triggers data fetch (list or detail)
- Form submit triggers create/update/deactivate service calls

### UI actions
- List:
  - filter by `accountType` and “status” (Active / Inactive / Scheduled)
  - search by `accountCode` or `accountName` (if supported by API; otherwise client-side search is **not allowed**)
  - pagination controls
- Detail:
  - display fields read-only by default
  - “Edit” toggles editable fields (name, description only)
  - “Deactivate” opens confirmation + effective date input

### State changes (frontend)
- Local UI state only (loading, saving, error)
- No client-side derivation of “active” beyond displaying based on backend fields; UI may compute a badge based on `activeFrom/activeThru` for display only.

### Service interactions
- Load list via Moqui service call (see Service Contracts)
- Create via service call; handle duplicate code conflict
- Update via patch service call
- Deactivate via dedicated action call

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, non-authoritative)
- Required fields on create:
  - `accountCode` (non-empty)
  - `accountName` (non-empty)
  - `accountType` (must be one of allowed enum values)
  - `activeFrom` (non-empty)
- Effective date validation:
  - if `activeThru` provided, must be strictly after `activeFrom`
- Deactivate validation:
  - `effectiveDate` required; must be >= current time? (**unclear; enforce only “required” client-side**)
- Immutability:
  - After creation, UI must not allow editing `accountCode` or `accountType` (hide/disable fields).

### Enable/disable rules
- Show “Create”, “Edit”, “Deactivate” only if user has `CoA:Manage` (permission check method depends on frontend auth integration).
- Deactivate action disabled if already deactivated with `activeThru` in the past; if scheduled deactivation exists (`activeThru` in future), allow “Change deactivation date” is **unclear** → do not implement unless backend supports.

### Visibility rules
- Always show audit fields (createdAt/createdBy/updatedAt/updatedBy) if returned by API.
- Show status badge computed from dates:
  - **Active:** now >= activeFrom AND (activeThru is null OR now < activeThru)
  - **Scheduled:** now < activeFrom OR (activeThru not null AND now < activeThru AND deactivation action already set?) (**ambiguous**) → display only “Active/Inactive” unless backend provides explicit status.

### Error messaging expectations
- Duplicate code: show inline error on `accountCode` with message from backend; also top-level banner.
- Policy violation on deactivate: show modal error with backend reason; do not change UI state.
- Auth failure: show “You do not have permission” and disable actions; for 401 redirect to login.

---

## 8. Data Requirements

### Entities involved (frontend view models)
- `GLAccount` (owned by accounting domain)

### Fields
| Field | Type | Required | Editable | Default | Notes |
|---|---|---:|---:|---|---|
| accountId | UUID | yes (response) | no | n/a | route param for detail |
| accountCode | string | yes | create-only | none | unique; treat as case-insensitive for duplicate detection messaging (backend authoritative) |
| accountName | string | yes | yes | none | editable post-create |
| accountType | enum | yes | create-only | none | one of ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE |
| description | string | no | yes | empty | optional |
| activeFrom | datetime (tz) | yes | create-only (assumed) | now | create-time effective start |
| activeThru | datetime (tz) | no | set via deactivate action | null | do not expose as free-edit unless specified |
| createdAt | datetime (tz) | yes | no | n/a | display |
| updatedAt | datetime (tz) | yes | no | n/a | display |
| createdBy | string/UUID | yes | no | n/a | display (formatting depends on backend) |
| updatedBy | string/UUID | yes | no | n/a | display |

### Read-only vs editable by state/role
- If user lacks `CoA:Manage`: all fields read-only, actions hidden/disabled.
- If account is inactive (activeThru <= now): edit name/description is **unclear**; default to **allowed** unless backend rejects (but this may violate policy). Because this is domain-policy adjacent, treat as **Open Question**.

### Derived/calculated fields
- `uiStatusBadge` derived for display only (non-authoritative): Active vs Inactive based on `activeFrom/activeThru`.

---

## 9. Service Contracts (Frontend Perspective)

> **Note:** Exact Moqui service names/endpoints are not provided in inputs. This story requires confirmation of backend REST paths or Moqui service facades. Until confirmed, treat these as placeholders.

### Load/view calls
- **List GL accounts**
  - `GET /v1/gl-accounts?page=<n>&pageSize=<n>&accountType=<...>&status=<...>&q=<...>`
  - Response: `{ data: GLAccount[], pageInfo: { pageIndex, pageSize, totalCount } }`
- **Get GL account**
  - `GET /v1/gl-accounts/{accountId}` → `GLAccount`

### Create/update calls
- **Create**
  - `POST /v1/gl-accounts`
  - Body: `{ accountCode, accountName, accountType, description?, activeFrom, activeThru? }`
  - Success: `201` + `GLAccount`
  - Errors:
    - `409` duplicate code
    - `400` validation (invalid type, invalid dates)
    - `403` forbidden
- **Update mutable fields**
  - `PATCH /v1/gl-accounts/{accountId}`
  - Body: `{ accountName?, description? }`
  - Success: `200` + `GLAccount`
  - Errors: `400`, `403`, `404`, `409?` (unlikely), `409` for optimistic lock if used (**unclear**)

### Submit/transition calls
- **Deactivate**
  - `POST /v1/gl-accounts/{accountId}/deactivate`
  - Body: `{ effectiveDate }`
  - Success: `200` + `GLAccount` (or minimal response)
  - Errors:
    - `422` policy violation (non-zero balance/usage)
    - `400` invalid effectiveDate
    - `403` forbidden

### Error handling expectations (frontend)
- Map HTTP errors to UI:
  - 400: field-level errors if provided; else banner “Fix validation errors”
  - 401: redirect to login
  - 403: show not-authorized callout; keep user on screen
  - 404: show “Account not found” empty state with back-to-list
  - 409: show conflict (duplicate code) inline on `accountCode`
  - 422: show policy violation message (deactivate)

---

## 10. State Model & Transitions

### Allowed states (UI-level)
Since backend does not provide an explicit state machine, use **date-based** status for UI display:
- **Active:** within effective window
- **Inactive:** `activeThru` in past
- **Not yet active:** `activeFrom` in future (if allowed)

### Role-based transitions
- `CoA:Manage`:
  - can create
  - can update name/description
  - can request deactivation (set activeThru) subject to backend rules
- Without `CoA:Manage`:
  - view only

### UI behavior per state
- Inactive: show status badge “Inactive”; disable “Deactivate”.
- Future activeFrom: show “Scheduled” badge is optional; if implemented, must be purely derived.

---

## 11. Alternate / Error Flows

- **Duplicate account code on create**
  - User submits; backend returns 409
  - UI shows inline error on `accountCode` and keeps form values
- **Invalid account type**
  - UI prevents selection outside enum; if backend rejects anyway, show field error
- **Invalid effective dates**
  - Client-side prevents submit when `activeThru <= activeFrom`
  - If backend returns 400, show error and keep form
- **Deactivation policy violation**
  - Backend returns 422 with reason
  - UI shows blocking dialog/banner with reason; no local changes
- **Concurrency conflict**
  - If backend returns 409/412 due to version mismatch (mechanism unclear), UI reloads entity and prompts user to re-apply edits
- **Unauthorized**
  - 403: actions disabled and message shown
  - 401: login flow
- **Empty states**
  - List returns 0 results: show “No accounts yet” with “Create” CTA if authorized

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View CoA list
**Given** I am an authenticated user  
**When** I navigate to Accounting → Chart of Accounts  
**Then** I see a paginated list of GL accounts showing account code, name, type, and effective dates  
**And** I can filter the list by account type

### Scenario 2: Create a new GL account successfully
**Given** I have `CoA:Manage` permission  
**When** I create a GL account with a unique account code, a name, an account type of `ASSET`, and an activeFrom date  
**Then** the system creates the account successfully  
**And** I am navigated to the account detail screen for the new account  
**And** the account detail shows the persisted values and audit metadata (createdAt/createdBy)

### Scenario 3: Prevent duplicate account code
**Given** I have `CoA:Manage` permission  
**And** a GL account exists with account code "1010-CASH"  
**When** I attempt to create a new GL account with account code "1010-CASH"  
**Then** I see an inline validation error indicating the account code is already in use  
**And** the form remains editable with my previously entered values preserved

### Scenario 4: Update name/description only
**Given** I have `CoA:Manage` permission  
**And** I am viewing an existing GL account  
**When** I edit the account name and description and save  
**Then** the changes are persisted  
**And** the account code and account type are not editable in the UI  
**And** updatedAt/updatedBy reflect the change (when provided by backend)

### Scenario 5: Deactivate a GL account with an effective date
**Given** I have `CoA:Manage` permission  
**And** I am viewing an active GL account  
**When** I deactivate the account effective tomorrow  
**Then** the account detail displays an activeThru date equal to tomorrow  
**And** the UI reflects the account as inactive once the effective date passes (date-based display)

### Scenario 6: Deactivation blocked by policy
**Given** I have `CoA:Manage` permission  
**And** I am viewing an active GL account that violates deactivation policy  
**When** I attempt to deactivate the account  
**Then** I see an error explaining the policy violation  
**And** the account remains active in the UI with unchanged effective dates

### Scenario 7: Unauthorized user cannot manage CoA
**Given** I am authenticated  
**And** I do not have `CoA:Manage` permission  
**When** I navigate to a GL account detail screen  
**Then** I can view the account details  
**And** I do not see controls to create, edit, or deactivate accounts

---

## 13. Audit & Observability

- **User-visible audit data (read-only):**
  - createdAt, createdBy, updatedAt, updatedBy on detail screen
- **Status history:** not defined; if backend exposes audit events/history endpoint, show a tab; otherwise out-of-scope.
- **Traceability expectations:**
  - Frontend logs (console/telemetry per project convention) include `accountId` on save/deactivate failures.
  - UI should surface backend-provided error codes/messages verbatim when safe (no sensitive info).

---

## 14. Non-Functional UI Requirements

- **Performance:** list load under 2s for first page under normal conditions; show skeleton/loading state.
- **Accessibility:** all form inputs labeled; keyboard navigable dialogs; validation messages associated to fields.
- **Responsiveness:** usable on tablet; list collapses gracefully (no pixel-perfect requirements).
- **i18n/timezone:** display datetimes in user locale/timezone; input uses timezone-aware picker (do not assume UTC-only).
- **Security:** do not expose hidden actions; enforce permission-based rendering but rely on backend as source of truth.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide a standard empty-state panel on list screens with optional “Create” CTA when authorized; safe because it doesn’t change domain behavior (Impacted: UX Summary, Alternate/Empty states).
- SD-UX-PAGINATION: Default to paginated list view with page size control; safe because it’s purely UI ergonomics (Impacted: UX Summary, Functional Behavior).
- SD-ERR-HTTP-MAPPING: Standard HTTP→UI error mapping (400/401/403/404/409/422) using backend messages; safe because it follows implied REST semantics without inventing policies (Impacted: Service Contracts, Error Flows).

---

## 16. Open Questions

1. **Backend contract confirmation:** What are the exact backend endpoints (or Moqui services) for list/get/create/patch/deactivate GL accounts, and what are the exact request/response schemas and error payload formats?
2. **View permissions:** Is CoA list/detail view restricted to `CoA:Manage`, or is there a separate read-only permission (e.g., `CoA:View`)?
3. **Deactivation policy details:** Beyond “non-zero balance/usage,” what specific conditions block deactivation (recent activity window, mappings usage, open period constraints, etc.) and what error codes/messages are returned?
4. **Editing inactive accounts:** Are name/description edits allowed after an account becomes inactive, or must inactive accounts be fully immutable?
5. **Status filtering semantics:** If the list supports `status` filter, what are the canonical statuses and how are they computed (date-based vs explicit status field)?
6. **Optimistic locking:** Does `GLAccount` use a version field/ETag for concurrency? If so, what header/field is required and what error is returned on conflict?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] CoA: Create and Maintain Chart of Accounts  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/204  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] CoA: Create and Maintain Chart of Accounts

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Maintain Chart of Accounts and Posting Categories

## Story
CoA: Create and Maintain Chart of Accounts

## Acceptance Criteria
- [ ] Accounts support types: Asset/Liability/Equity/Revenue/Expense
- [ ] Accounts are effective-dated (activeFrom/activeThru) and audit-logged
- [ ] Duplicate account codes are blocked
- [ ] Deactivation rules are enforced per policy (e.g., balances/usage)


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