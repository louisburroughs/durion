STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:crm
- status:draft

### Recommended
- agent:crm
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** crm-pragmatic

---

## 1. Story Header

**Title:** [FRONTEND] [STORY] Party: Create Commercial Account

**Primary Persona:** Fleet Account Manager

**Business Value:** Enables consistent association of workorders/estimates/invoices to a single, stable commercial customer entity (CRM Party/Account), reducing duplicate customer creation and downstream billing errors.

---

## 2. Story Intent

### As a / I want / So that
**As a** Fleet Account Manager,  
**I want** to create a commercial customer account with legal name, billing profile (default billing terms), and optional identifiers,  
**So that** workorders and invoices can be consistently tied to the correct business entity via a stable CRM ID.

### In-scope
- A “Create Commercial Account” UI flow in the Moqui frontend (Vue 3 + Quasar) to create a new Commercial Account/Party.
- Capture fields: legal name (required), DBA (optional), tax ID (optional), account status (default Active), default billing terms (required), external identifiers (optional).
- Duplicate warning UX at creation time based on “name + phone/email match” (non-blocking warning with user override).
- Post-create confirmation and ability to copy/view the newly created stable CRM ID (PartyId).
- Provide a predictable route/screen that downstream UX (e.g., workorder execution screens) can navigate back to or deep-link into (view page is optional but recommended as a follow-up; see Open Questions).

### Out-of-scope
- Editing/updating existing accounts (separate story).
- Full CRM account search UI (unless already exists; only ensure created accounts are retrievable by existing search).
- Defining billing terms master data management (assumed provided by backend/domain).
- Implementing backend services/entities (frontend story consumes Moqui services/endpoints).
- Complex dedupe algorithms beyond the specified basic match.

---

## 3. Actors & Stakeholders
- **Fleet Account Manager (primary)**: creates accounts and resolves duplicate warnings.
- **Workorder Execution users/systems (downstream)**: search/select account by name/ID; store CRM ID on estimate/workorder.
- **Billing/Invoicing stakeholders (downstream)**: rely on default billing terms and identifiers being correct.
- **System Admin / Support (secondary)**: may troubleshoot duplicates via audit trail (read-only concerns in this story).

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui frontend.
- User has permission to create commercial accounts (exact permission artifact TBD; see Open Questions).
- Moqui backend provides:
  - A service to create a commercial account (and returns PartyId / CRM ID).
  - A service to run duplicate check (or create service performs it and returns potential matches).
  - A service to load billing terms list for selection.
- Connectivity to backend is available; standard error handling should surface service errors.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Primary entry from a CRM/Accounts area: “Create Commercial Account” button.
- Secondary entry from Workorder Execution (optional): “Create new account” from an account picker/search screen (if such a screen exists).

### Screens to create/modify
1. **New Screen:** `apps/durion/screen/party/CommercialAccountCreate.xml` (name illustrative; final path must follow repo conventions)
   - Presents a form for commercial account creation.
   - Performs duplicate check flow and confirmation/override if duplicates found.
2. **Optional/New or Existing Screen:** `.../party/CommercialAccountView.xml`
   - Display created account details including PartyId.
   - If an existing Party/Account view already exists, route to it after creation.

### Navigation context
- Breadcrumb/toolbar should clearly indicate “Create Commercial Account”.
- Cancel returns to prior screen (or Accounts list if no referrer).

### User workflows
**Happy path**
1. User opens Create Commercial Account.
2. Enters required fields and optional identifiers.
3. Submits.
4. If no duplicates: account created; user sees success confirmation with PartyId; navigates to view screen.

**Alternate path: duplicates found**
1. User submits.
2. Duplicate warning displayed with list of potential matches (minimum: name + PartyId; more fields if available).
3. User chooses:
   - “Review match” (opens view in new route/tab) OR
   - “Cancel” (return to form, no create) OR
   - “Create anyway” (explicit override → proceed to create).

---

## 6. Functional Behavior

### Triggers
- User clicks “Create Commercial Account” entry point.
- User clicks “Submit/Create” on the form.

### UI actions
- Form input for account fields.
- “Submit/Create” initiates:
  1) client-side required checks (presence only; no policy guessing), then
  2) server-side duplicate check / create attempt sequence (see Service Contracts).

### State changes (frontend)
- Local UI states:
  - `idle` → `validating` → `checkingDuplicates` → (`duplicateWarning` | `creating`) → (`success` | `error`)
- Duplicate override requires explicit user action (e.g., confirmation dialog with checkbox or secondary confirm step).

### Service interactions
- Load billing terms list on screen load (or lazy load on field focus).
- On submit:
  - Call duplicate check (if separate) and display warning when matches exist; OR
  - Call create service that may return a “duplicates found” response requiring confirmation (preferred if backend contract is like this; currently unclear).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Legal Name**: required, non-empty string.
  - UI: mark as required; block submit if empty.
  - Error message: “Legal name is required.”
- **Default Billing Terms**: required selection.
  - UI: required dropdown/select; block submit if not selected.
  - Error message: “Default billing terms is required.”
- **Tax ID**: optional.
  - UI: no format assumptions unless backend returns validation errors; display backend error message mapped to field if provided.
- **Account Status**:
  - Defaults to `Active` on create.
  - UI: either show as prefilled `Active` (editable only if permitted—unclear) or hide and let backend default (see Open Questions).

### Enable/disable rules
- Disable Submit while any service call in-flight.
- Disable “Create anyway” until user explicitly confirms they understand duplicates may exist (confirmation step required for auditability).

### Visibility rules
- Duplicate warning UI only appears when backend indicates potential duplicates.
- External identifiers section is optional and collapsible (safe UX default; no policy impact).

### Error messaging expectations
- Field-level errors should be shown inline when backend returns structured validation errors.
- Non-field errors shown as a banner/toast: “Unable to create account. Please try again.” plus correlation id if available (do not expose sensitive details).

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Party/Account (Commercial Account)**: the core CRM entity; returns stable `partyId` (CRM ID).
- **BillingTerms**: reference/master entity providing selectable billing terms.
- **ExternalId / Identifiers**: optional set of external identifiers (ERP/customer number).
- **Audit fields**: createdBy/createdAt etc (display only post-create if view screen exists).

### Fields
**Commercial Account Create Input**
- `legalName` (string, required)
- `dbaName` / `doingBusinessAs` (string, optional)
- `taxId` / `taxIdentifier` (string, optional)
- `accountStatus` (enum/string, default `Active`) **(unclear if user-editable)**
- `defaultBillingTermsId` (id/string, required)
- `externalIdentifiers` (structure TBD; see Open Questions)
- **Duplicate check inputs** (if required by backend): `phone` and/or `email` **(not mentioned in capture list but required for dedupe rule; see Open Questions)**

**Read-only outputs**
- `partyId` (string/UUID): stable CRM ID created by system.

### Read-only vs editable by state/role
- Create form: all input fields editable.
- Post-create: `partyId`, audit fields read-only.
- Status editability is unclear (policy/permissions).

### Derived/calculated fields
- None defined on frontend. Any normalization for duplicate detection must be owned by backend unless explicitly specified (not specified).

---

## 9. Service Contracts (Frontend Perspective)

> Naming is intentionally conservative because backend Moqui service names are not provided. These must be mapped to actual Moqui services during implementation.

### Load/view calls
1. **Load billing terms list**
   - Service: `BillingTerms.list` (placeholder)
   - Request: `{ activeOnly: true }` (if supported)
   - Response: `[{ billingTermsId, description, ... }]`
   - UI: populate select options.

2. **(Optional) Load duplicate candidates detail**
   - If duplicate check returns only IDs, UI may need a service to load candidate summaries.
   - Service: `Party.getAccountSummariesByIds` (placeholder)

### Create/update calls
1. **Create commercial account**
   - Service: `Party.createCommercialAccount` (placeholder)
   - Request payload:
     - `legalName`
     - `doingBusinessAs` (optional)
     - `taxIdentifier` (optional)
     - `defaultBillingTermsId`
     - `accountStatus` (optional; backend default Active)
     - `externalIdentifiers` (optional; structure TBD)
     - `duplicateOverride` (boolean, default false)
     - `duplicateCandidateIdsShown` (array, required when override true) (for audit) **(needs backend support; see Open Questions)**
   - Response (success): `{ partyId, ... }`
   - Response (duplicates): either
     - `{ duplicatesFound: true, candidates: [...] }` with 200, or
     - error code requiring confirmation (e.g., 409 with payload). **Needs clarification.**

### Submit/transition calls
- None beyond create.

### Error handling expectations
- Validation errors returned by service should map to fields when possible.
- Authorization errors (401/403): show “You don’t have permission to create accounts.” and do not retry.
- Duplicate conflict: handled as warning/confirm flow, not a hard failure (unless backend enforces).
- Network/timeouts: show retryable error; keep form data intact.

---

## 10. State Model & Transitions

### Allowed states (account status)
- At minimum: `Active` (default), plus any others if backend supports (`Inactive`, `OnHold` mentioned in backend reference but not authoritative for frontend without confirmation).

### Role-based transitions
- This story only covers creation.
- If status is editable at create time, role/permission gating is required (unclear).

### UI behavior per state
- New account defaults to `Active` in UI or backend; UI should display resulting status after creation if a view screen is shown.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing legal name / billing terms: block submit client-side.
- Backend validation errors: show inline; preserve all user-entered values.

### Concurrency conflicts
- Not applicable for create, except duplicate logic. If backend returns “already exists” (e.g., tax ID unique), show error with field mapping to tax ID when possible.

### Unauthorized access
- If user lacks permission:
  - Entry point may be hidden/disabled if permission is detectable client-side.
  - If accessed directly via URL, backend 403 should render an unauthorized message and prevent form usage.

### Empty states
- Billing terms list empty/unavailable:
  - Show “No billing terms available. Contact admin.” and disable submit.

---

## 12. Acceptance Criteria

### Scenario 1: Create commercial account successfully with required fields
**Given** I am authenticated as a Fleet Account Manager with permission to create commercial accounts  
**And** billing terms are available to select  
**When** I enter a legal name and select default billing terms  
**And** I submit the create form  
**Then** the system creates a commercial account  
**And** I am shown a success confirmation containing the new stable CRM ID (PartyId)  
**And** I am navigated to the account view screen (or shown a link) for the created account

### Scenario 2: Required field validation blocks submission (legal name)
**Given** I am on the Create Commercial Account screen  
**When** I submit the form with legal name empty  
**Then** the UI prevents submission  
**And** I see “Legal name is required.”  
**And** no create service call is made

### Scenario 3: Required field validation blocks submission (billing terms)
**Given** I am on the Create Commercial Account screen  
**When** I submit the form without selecting default billing terms  
**Then** the UI prevents submission  
**And** I see “Default billing terms is required.”  
**And** no create service call is made

### Scenario 4: Duplicate warning is shown when close matches exist
**Given** an existing commercial account exists that matches the duplicate rule for my entered data  
**When** I submit the create form  
**Then** I am shown a duplicate warning  
**And** I can see a list of potential matching accounts (at least name and ID)  
**And** I can choose to cancel or proceed with “Create anyway”

### Scenario 5: Duplicate warning override creates account
**Given** a duplicate warning is shown with candidate matches  
**When** I explicitly choose “Create anyway” (override)  
**Then** the system creates a new commercial account  
**And** I am shown the new PartyId  
**And** the override action is sent to the backend in a way that can be audited (e.g., override flag + candidate IDs)

### Scenario 6: Backend validation error is rendered inline
**Given** I submit the create form with a tax ID that the backend rejects  
**When** the backend returns a field validation error for tax ID  
**Then** the UI shows the backend error message associated with the tax ID field  
**And** my other entered values remain intact

### Scenario 7: Unauthorized user cannot create
**Given** I am authenticated but do not have permission to create commercial accounts  
**When** I attempt to access the Create Commercial Account screen or submit the form  
**Then** I am shown an authorization error  
**And** no account is created

---

## 13. Audit & Observability

### User-visible audit data
- On success confirmation (and/or view screen), display:
  - `partyId`
  - created timestamp and created by (if available from backend response or subsequent load)

### Status history
- Not in scope unless view screen already supports it.

### Traceability expectations
- Frontend should pass/propagate a correlation/request ID if the project convention supports it (e.g., header or request param). If backend returns a correlation id on error, show it in the error banner for support.

---

## 14. Non-Functional UI Requirements
- **Performance:** Billing terms load and duplicate check/create should complete under typical LAN conditions; UI must show loading state within 200ms of action.
- **Accessibility:** All inputs have labels; required fields announced; duplicate warning dialog is keyboard navigable and focus-trapped.
- **Responsiveness:** Form usable on tablet-sized viewport (POS-friendly).
- **i18n/timezone/currency:** No currency required. Text should be compatible with i18n if project uses it (no hard-coded concatenations that block translation).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty state messaging for missing billing terms; qualifies as safe because it does not change domain policy, only improves clarity. (Impacted: UX Summary, Error Flows)
- SD-UX-LOADING-DISABLE: Disable submit buttons during in-flight requests to prevent double-submit; safe because it prevents duplicate requests without changing business rules. (Impacted: Functional Behavior, Error Flows)
- SD-ERR-PRESERVE-FORM: Preserve user-entered values on backend error; safe because it’s purely UX ergonomics. (Impacted: Error Flows, Acceptance Criteria)

---

## 16. Open Questions
1. **Domain/System of Record confirmation:** This frontend story proposes `domain:crm` because it creates a Party/Account. The issue currently carries `payment` labeling. Can we confirm CRM is the SoR and domain label should be `domain:crm`?
2. **Duplicate detection inputs:** The dedupe rule references “name + phone/email match” but the capture list does not include phone/email. Should the Create Commercial Account form include primary phone and/or email fields, or is duplicate check performed against existing contact info captured elsewhere?
3. **Backend contract for duplicates:** Does the backend expose a separate `duplicateCheck` service, or does `createCommercialAccount` return a “duplicates found” response requiring confirmation? What is the exact response shape/status code?
4. **Account status field:** Is `accountStatus` user-selectable at creation time, or must it always default to `Active` and be immutable on create for this role?
5. **Billing terms source:** What Moqui service/entity provides “Default Billing Terms” options, and what fields should be displayed in the select (name vs description vs code)?
6. **External identifiers structure:** Are external identifiers free-form key/value, or constrained to a defined set (e.g., ERP customer number only)? If constrained, what keys and validation apply?
7. **Post-create navigation:** Is there an existing account view screen route in this frontend? If yes, provide route pattern. If not, should this story include implementing the view screen or just show success + PartyId?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Party: Create Commercial Account  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/176  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Create Commercial Account

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to create a commercial customer account with legal name, billing profile, and identifiers** so that **workorders and invoices can be consistently tied to the correct business entity**.

## Details
- Capture: legal name, DBA, tax ID (optional), account status, default billing terms.
- Support external identifiers (ERP/customer number) as optional fields.
- Basic duplicate warning on create (name + phone/email match).

## Acceptance Criteria
- Can create account with required fields.
- Account has stable CRM ID.
- Duplicate warning presented when close matches exist.

## Integration Points (Workorder Execution)
- Workorder Execution can search/select the account by name/ID.
- Selected account CRM ID is stored on Estimate/Workorder.

## Data / Entities
- Party/Account
- ExternalId (optional)
- Audit fields

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