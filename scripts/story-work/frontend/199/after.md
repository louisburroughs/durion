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

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Accounting Policy: Configure Accrual vs Cash Basis per Business Unit (AR scope)

### Primary Persona
Finance Manager / Accountant (with accounting admin privileges)

### Business Value
Enable correct GL posting behavior and reporting consistency by configuring and auditing whether a business unit operates on accrual or cash basis for AR/revenue-cycle postings.

---

## 2. Story Intent

### As a / I want / So that
**As a** Finance Manager / Accountant,  
**I want** to view and change the accounting basis (Accrual or Cash) for a business unit with an effective fiscal-period boundary,  
**so that** journal entry posting behavior is consistent, permission-controlled, and auditable.

### In-scope
- A Moqui UI to:
  - View current accounting basis for a business unit
  - View basis history (effective-dated)
  - Propose a new basis change with an effective-from date/time aligned to fiscal period start
  - Save the change (server-side validated)
- Permission-gated access and UI affordances (read vs edit)
- User-visible audit/trace fields for changes (who/when/old/new/effectiveFrom)

### Out-of-scope
- Defining debit/credit mappings, GL accounts, posting rule configuration UI
- Journal entry creation/posting UI
- AP behavior (explicitly out of scope per backend reference)
- Retroactive re-posting and “catch-up conversion” workflows

---

## 3. Actors & Stakeholders
- **Finance Manager / Accountant (interactive user):** configures basis and reviews history
- **Auditor (interactive user):** reviews basis change history/audit records (read-only)
- **Accounting Service (system):** validates and persists basis policy and audit trail
- **Billing/Payments services (external producers):** referenced only for downstream behavior context; not directly controlled in this frontend story

---

## 4. Preconditions & Dependencies
- Business unit(s) exist and are selectable in the UI.
- Backend provides endpoints/services to:
  - Read current basis for a business unit
  - Read basis policy history (effective-dated)
  - Create a new basis policy change with validation:
    - allowed values: `ACCRUAL` or `CASH`
    - effective date must align to fiscal period start
    - permission-controlled
- User authentication/authorization is configured in Moqui.
- Fiscal period boundary concept exists in backend (at least “start of fiscal month” or equivalent boundary rule).

**Dependency note:** The frontend cannot safely implement the fiscal boundary rule without a backend-provided “valid effectiveFrom options” or a validation error contract (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Policies → Accounting Basis**
- Alternative entry: **Business Unit detail → Accounting tab → Accounting Basis**

### Screens to create/modify
1. **Screen:** `Accounting/Policy/AccountingBasis.xml` (new)
   - Business unit selector/context header
   - Current basis summary card/section
   - Effective-dated history list
   - “Change basis” action (permission gated)
2. **Dialog/Subscreen:** `Accounting/Policy/AccountingBasisChangeDialog.xml` (new)  
   - Form to select new basis and effectiveFrom
3. (Optional) **Reusable component/form:** `Accounting/Policy/AccountingBasisForm.xml` (new) used by dialog

### Navigation context
- Screen requires a `businessUnitId` context; if missing, prompt user to select one.
- After successful save, return to basis screen with refreshed “current” + history and a confirmation message.

### User workflows
**Happy path**
1. User navigates to Accounting Basis screen.
2. Selects a business unit (if not already in context).
3. Views current basis and history.
4. Clicks “Change Basis”.
5. Chooses `ACCRUAL` or `CASH` and selects an effective-from date/time that is at a fiscal period start.
6. Submits; sees success toast; screen refreshes showing new policy scheduled/effective.

**Alternate paths**
- User lacks permission → screen shows read-only; change action hidden/disabled.
- Invalid effectiveFrom → inline error returned from backend displayed near effectiveFrom field.
- Backend rejects due to closed/locked period → show blocking error and do not update UI.
- Concurrency conflict (policy changed by someone else) → show conflict banner and reload history.

---

## 6. Functional Behavior

### Triggers
- Screen load with `businessUnitId`
- Business unit selection change
- User clicks “Change Basis”
- User submits basis change form

### UI actions
- **On load / BU change:** call load service to fetch:
  - current basis (as of now)
  - basis policy history (effective-dated, descending by effectiveFrom)
- **On Change Basis click:** open dialog with:
  - current basis prefilled read-only for reference
  - new basis selection required
  - effectiveFrom required (date/time)
- **On submit:**
  - client-side required-field validation (basis, effectiveFrom present)
  - call backend update/create policy service
  - on success: close dialog, refresh data, show confirmation
  - on error: keep dialog open, show field errors and/or top-level error

### State changes (frontend-local)
- Loading states for:
  - initial screen load
  - history reload after mutation
  - submit in progress (disable submit, show spinner)
- No client-side state machine beyond reflecting backend policy/historical records.

### Service interactions
- `loadAccountingBasisPolicy` (read)
- `createOrChangeAccountingBasisPolicy` (write)
- Optional: `loadFiscalPeriods` or `validateEffectiveFrom` if backend supports (see Open Questions)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `basis` must be one of: `ACCRUAL`, `CASH`.
- `effectiveFrom` is required and must align to fiscal period boundary rule.
- Only authorized users can create/change policy:
  - If unauthorized: UI hides “Change Basis” and backend returns 403 if attempted.

### Enable/disable rules
- Disable submit button while request in flight.
- Disable “Change Basis” if:
  - no businessUnit selected
  - user lacks permission
- If backend indicates there is a “pending future-dated change” and policy disallows stacking changes, UI must reflect that (unknown—needs clarification).

### Visibility rules
- Show “Audit/History” section to all users with read permission.
- Show “Change Basis” action only to users with admin permission.

### Error messaging expectations
- Map backend validation errors to:
  - field-level messages for `basis` and `effectiveFrom`
  - top-level alert for permission/period-locked/conflict/system errors
- Errors must not expose sensitive information.

---

## 8. Data Requirements

### Entities involved (frontend view models)
- **BusinessUnit**
  - `businessUnitId` (string/ID)
  - `businessUnitName` (string)
  - `accountingBasis` (enum) — if backend exposes directly on BU
- **BusinessUnitAccountingBasisPolicy** (recommended by backend)
  - `businessUnitId` (ID, required)
  - `basis` (enum `ACCRUAL|CASH`, required)
  - `effectiveFrom` (timestamp, required)
  - `changedByUserId` (ID, read-only)
  - `changedAt` (timestamp, read-only)

### Fields (type, required, defaults)
**Form fields**
- `businessUnitId`: ID, required (from screen context)
- `newBasis`: enum, required, no default (to avoid accidental changes)
- `effectiveFrom`: datetime, required, no default unless backend provides “next period start”

### Read-only vs editable
- Read-only:
  - current basis display
  - history rows
  - `changedByUserId`, `changedAt`
- Editable (only with permission):
  - `newBasis`, `effectiveFrom`

### Derived/calculated fields
- “Current basis” derived by backend as basis effective at “now” (or latest effectiveFrom <= now).
- “Status” for history row (derived in UI):
  - `EFFECTIVE` if effectiveFrom <= now and is the latest such record
  - `SCHEDULED` if effectiveFrom > now
(If backend provides explicit status, prefer backend value.)

---

## 9. Service Contracts (Frontend Perspective)

> **Clarification required:** exact Moqui service names/paths and request/response shapes are not provided. Below defines required contracts.

### Load/view calls
1. **Service:** `AccountingServices.getBusinessUnitAccountingBasis`
   - **Inputs:** `businessUnitId`
   - **Outputs:**
     - `currentBasis` (`ACCRUAL|CASH`)
     - `currentEffectiveFrom` (timestamp, optional)
     - `history[]` list of policy records (see Data Requirements)
   - **Errors:**
     - 401/403 unauthorized
     - 404 businessUnit not found

### Create/update calls
2. **Service:** `AccountingServices.changeBusinessUnitAccountingBasis`
   - **Inputs:**
     - `businessUnitId`
     - `basis`
     - `effectiveFrom`
   - **Outputs:**
     - `policyId` (optional)
     - updated `currentBasis` (optional)
   - **Errors:**
     - 400 invalid basis
     - 422 invalid effectiveFrom (not at fiscal boundary)
     - 409 conflict (concurrency / overlapping effective-dated record)
     - 403 insufficient permission
     - 423 locked/closed period (or 409 with specific code)

### Submit/transition calls
- None beyond the change service.

### Error handling expectations
- Backend error response must include:
  - stable `errorCode`
  - human-readable `message`
  - optional `fieldErrors` map: `{ fieldName: message }`
Frontend mapping:
- `fieldErrors.effectiveFrom` → show under effectiveFrom picker
- `fieldErrors.basis` → show under basis select
- other codes → show banner/toast and log correlation id if provided

---

## 10. State Model & Transitions

### Allowed states
Policy records are effective-dated; user-facing states:
- `EFFECTIVE` (current as-of now)
- `HISTORICAL` (past)
- `SCHEDULED` (future effective)

### Role-based transitions
- Users with `ACCOUNTING_ADMIN` (exact permission string TBD) can create a new scheduled/effective policy record.
- Non-admin users can only view.

### UI behavior per state
- If `SCHEDULED` exists:
  - display it distinctly in history
  - indicate “will become effective on <effectiveFrom>”
  - whether it can be edited/cancelled is **not defined** (Open Question)

---

## 11. Alternate / Error Flows

### Validation failures
- Missing basis/effectiveFrom → block submit client-side with required-field message.
- Backend rejects effectiveFrom boundary → keep dialog open, show backend message on field.

### Concurrency conflicts
- If backend returns 409 due to overlapping effective-dated policy or stale data:
  - show conflict banner: “Accounting basis was updated by another user. Reloading…”
  - refresh history and current basis
  - keep dialog open but require user to re-confirm values (do not auto-resubmit)

### Unauthorized access
- If load returns 403:
  - show “You do not have access to view accounting basis policy for this business unit.”
- If change returns 403:
  - show “You do not have permission to change accounting basis.” and keep dialog open read-only or close (TBD; recommend close and refresh permissions)

### Empty states
- No history returned:
  - show empty state “No basis history found. Current basis: <value or unknown>”
- No business units available to user:
  - show empty state with guidance to contact admin

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View current basis and history
**Given** I am an authenticated user with permission to view accounting policies  
**And** I am on the Accounting Basis screen with a selected business unit  
**When** the screen loads  
**Then** I see the current accounting basis for the business unit  
**And** I see a list of effective-dated basis policy history entries (or an empty-state message if none)

### Scenario 2: Authorized user changes basis with valid effective date
**Given** I have permission `ACCOUNTING_ADMIN` (or equivalent)  
**And** a business unit currently has basis `ACCRUAL`  
**When** I submit a change to basis `CASH` with an effectiveFrom aligned to the fiscal period start  
**Then** the system saves the new basis policy  
**And** the UI shows a success confirmation  
**And** the history list refreshes showing the new record with the correct effectiveFrom  
**And** audit fields (changedAt/changedBy) are visible for the new record (if returned by backend)

### Scenario 3: Unauthorized user cannot change basis
**Given** I do not have permission to change accounting basis  
**When** I view the Accounting Basis screen  
**Then** I do not see an enabled “Change Basis” action  
**And** if I attempt to submit a change via direct request, the backend returns 403 and the UI displays an authorization error

### Scenario 4: Effective date not aligned to fiscal period boundary is rejected
**Given** I have permission to change accounting basis  
**When** I submit a basis change with an effectiveFrom that is not at a fiscal period start  
**Then** the backend rejects the request with a validation error  
**And** the UI displays an error on the effectiveFrom field  
**And** no change is reflected in current basis or history after refresh

### Scenario 5: Conflict due to concurrent change
**Given** I have permission to change accounting basis  
**And** another admin changes the basis policy for the same business unit while my dialog is open  
**When** I submit my change  
**Then** the backend returns a 409 conflict (or equivalent)  
**And** the UI informs me of the conflict  
**And** the UI reloads current basis and history  
**And** my change is not applied unless I re-submit with updated context

---

## 13. Audit & Observability

### User-visible audit data
- For each history row, show:
  - `basis`
  - `effectiveFrom`
  - `changedAt`
  - `changedBy` (username if resolvable, else userId)
- If backend emits/returns an `AccountingBasisChanged` event reference or id, show it as read-only metadata (optional).

### Status history
- History list is the authoritative status history for basis changes.

### Traceability expectations
- All change requests include:
  - `businessUnitId`
  - `basis`
  - `effectiveFrom`
- Frontend logs (console/app log) must include correlation id if provided by backend response headers.

---

## 14. Non-Functional UI Requirements
- **Performance:** initial load should render within 2s on typical WAN once backend responds; show skeleton/loading indicator.
- **Accessibility:** dialog and form fields must be keyboard navigable; labels associated with inputs; errors announced via aria-live region (Quasar patterns).
- **Responsiveness:** screen usable on tablet resolutions; history list scrolls.
- **i18n/timezone:** effectiveFrom must be displayed and selected in the user’s timezone, but submitted as an unambiguous timestamp format expected by backend (needs clarification on timezone handling).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty-state messaging for no history / no business units; safe as UI-only and does not affect domain logic. Impacted sections: UX Summary, Alternate / Error Flows.
- SD-UX-LOADING: Use standard loading/skeleton and disable submit during requests; safe UI ergonomics. Impacted sections: Functional Behavior, Non-Functional.
- SD-ERR-MAP-STD: Standard mapping of backend `fieldErrors` and `errorCode` to field/banners; safe because it relies on backend-provided contract and does not invent business rules. Impacted sections: Service Contracts, Error Flows.

---

## 16. Open Questions
1. **Backend service/API contract:** What are the exact Moqui service names/paths, input/output fields, and error response schema for:
   - loading current basis + history
   - creating a basis change (effective-dated)?
2. **Authorization:** What is the exact permission(s)/authz condition for:
   - viewing basis policy
   - changing basis (is it `ACCOUNTING_ADMIN`, `CoA:Manage`, or another permission string)?
3. **Fiscal boundary rule details:** Is “start of fiscal period” always **start of fiscal month at 00:00:00** in the business unit’s timezone, or can fiscal periods be custom? Can the frontend fetch valid period start options?
4. **Timezone expectations:** In which timezone should `effectiveFrom` be interpreted and validated (business unit timezone vs user timezone vs UTC)? What timestamp format does backend expect?
5. **Future-dated changes policy:** Are multiple scheduled future changes allowed? If not, should UI block creation when a scheduled change exists, or allow overwrite/cancel?
6. **Closed/locked period handling:** Does backend return a specific error code/status for locked periods (e.g., 423) and should UI show a specialized message?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] GL: Support Accrual vs Cash Basis Modes  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/199  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] GL: Support Accrual vs Cash Basis Modes

**Domain**: payment

### Story Description

/kiro  
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Post Journal Entries to the General Ledger

## Story
GL: Support Accrual vs Cash Basis Modes

## Acceptance Criteria
- [ ] Business unit can be configured as accrual or cash basis
- [ ] Accrual: invoices post AR/AP and payments clear AR/AP
- [ ] Cash basis behavior is policy-defined and consistent across posting/reporting
- [ ] Basis changes are audited and permission-controlled

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