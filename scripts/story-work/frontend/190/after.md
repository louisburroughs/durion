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
[FRONTEND] [STORY] Adjustments: Create Manual Journal Entry (JE) with Controls

### Primary Persona
Accountant / Controller (authorized financial user)

### Business Value
Enable controlled, auditable manual GL adjustments directly from the POS frontend while preventing unbalanced entries, enforcing accounting period controls, and ensuring immutability once posted.

---

## 2. Story Intent

### As a / I want / So that
**As an** authorized financial user (Accountant/Controller),  
**I want** a frontend workflow to create and post a manual Journal Entry with a required reason code and balanced debit/credit lines,  
**So that** necessary accounting adjustments can be recorded accurately, within open periods, with a complete audit trail.

### In-scope
- A “Create Manual Journal Entry” UI to enter header + multiple lines.
- Client-side and server-side validation handling:
  - reasonCode required
  - at least 2 lines
  - each line must have either debit or credit (not both)
  - totals must balance before posting
- Posting submission and resulting immutable “POSTED” record behavior in UI.
- Error display for:
  - unbalanced entry
  - closed period
  - invalid/inactive GL account
  - missing required fields
  - insufficient permissions
- Read-only view of a posted JE (no edit/delete actions).

### Out-of-scope
- Editing or deleting posted JEs (explicitly disallowed).
- Creating reversing/reversal JEs (mentioned as correction mechanism, but not implemented here unless separately specified).
- Managing GL accounts, accounting periods, or reason code master data (assumed existing).
- Reporting screens (trial balance, JE listings) unless required for navigation entry points.

---

## 3. Actors & Stakeholders
- **Accountant/Controller (Primary)**: creates and posts manual JEs.
- **Auditor (Stakeholder)**: needs immutable records and visible audit metadata.
- **System**: enforces period status, balancing, immutability, and permissions.

---

## 4. Preconditions & Dependencies
1. User is authenticated in the frontend and has permission to create manual JEs (permission name TBD; backend reference uses `ACCOUNTING_ADJUSTMENT_CREATE`).
2. Backend exposes:
   - list/lookup of active GL accounts
   - list/lookup of valid reason codes for manual adjustments
   - create+post manual JE endpoint (single command or create then post)
   - (optional) retrieve JE by id for post-submit view
3. Accounting period status is enforced server-side based on `postingDate`.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Adjustments → Manual Journal Entry → Create**
- Optional secondary entry: “Create Manual JE” action from an accounting dashboard.

### Screens to create/modify
- **New screen**: `accounting/adjustments/manualJe/Create`  
  - Form for JE header + repeating line entry grid.
- **New screen**: `accounting/adjustments/manualJe/View`  
  - Read-only detail view (primarily for POSTED JEs; may also display just-created result).
- **Optional**: add menu entry in Accounting screens/menu definition.

### Navigation context
- Breadcrumb: Accounting > Adjustments > Manual Journal Entry > Create
- After successful post: redirect to View screen for created JE.

### User workflows
**Happy path**
1. User opens Create Manual JE screen.
2. Selects postingDate, enters description, selects reason code.
3. Adds 2+ lines, selecting GL account and entering debit or credit amounts.
4. UI shows running totals (debits, credits, difference).
5. User submits “Post Journal Entry”.
6. On success, user is redirected to View screen showing JE status `POSTED` and audit fields.

**Alternate paths**
- User attempts submit with missing header fields → inline validation + blocking submit.
- User enters both debit and credit on one line → inline validation, block submit.
- User enters unbalanced totals → UI blocks submit (client-side) and also handles server validation error if it occurs.
- Posting date in closed period → server rejects; UI shows error and keeps draft form values.

---

## 6. Functional Behavior

### Triggers
- Screen load: fetch reference data (reason codes, GL account search capability).
- Line edits: recalculate totals/difference.
- Submit: call backend to validate+post JE.

### UI actions
- Add line / remove line (cannot remove below 2 lines; if attempted, show validation).
- GL account selection per line:
  - Prefer searchable select (typeahead) to avoid loading entire CoA if large.
- Debit/Credit inputs:
  - Numeric currency input; enforce >= 0.
  - Mutually exclusive: entering debit clears credit and vice versa (or prevents entry).
- Submit button enabled only when client validations pass.

### State changes (frontend)
- Local “draft” state for form entry.
- On successful post:
  - transition to “posted view mode” by navigation to View screen with `journalEntryId`.

### Service interactions
- Load reason codes list.
- Search/select GL accounts.
- Submit JE create+post request.
- Load JE detail for view.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Header required**: postingDate, description, reasonCode must be present before submit.
- **Lines required**: minimum 2 JE lines.
- **Per line**:
  - glAccountId required
  - exactly one of debitAmount or creditAmount must be > 0 (or allow 0? see Open Questions)
  - memo optional
- **Balanced**:
  - Sum(debitAmount) must equal Sum(creditAmount) exactly (currency-scale precision).
  - UI shows difference and blocks submit if non-zero.
- **Period control**:
  - UI cannot determine open/closed reliably without API; must handle server rejection `PERIOD_CLOSED` with clear message.
- **Immutability**:
  - If viewing a `POSTED` JE, all fields read-only and no edit/delete actions present.

### Enable/disable rules
- “Post Journal Entry” disabled until:
  - header fields valid
  - >= 2 valid lines
  - balanced totals
- “Add line” always enabled.
- “Remove line” disabled if only 2 lines remain.

### Visibility rules
- Show totals panel (debits, credits, difference) whenever at least one line exists.
- Show server error banner on submission failures with error code + user-friendly message.

### Error messaging expectations
Map backend error codes (examples from backend reference) to UI messages:
- `VALIDATION_ERROR:UNBALANCED_ENTRY` → “Entry is not balanced. Debits must equal credits.”
- `VALIDATION_ERROR:PERIOD_CLOSED` → “Posting date is in a closed accounting period. Choose a date in an open period.”
- `VALIDATION_ERROR:INVALID_ACCOUNT` → “One or more selected accounts are invalid or inactive.”
- `VALIDATION_ERROR:MISSING_REQUIRED_FIELD` → “Complete all required fields before posting.”
- `IMMUTABLE_RECORD` → “This journal entry is posted and cannot be changed.”

(Exact error code strings require confirmation; see Open Questions.)

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `JournalEntry` (header)
- `JournalEntryLine` (lines)
- `GLAccount` (reference/lookup)
- `ReasonCode` (reference/lookup)
- `AccountingPeriod` (enforced server-side; optionally displayed)

### Fields

#### JournalEntry (Create payload)
- `postingDate` (date, required)
- `description` (string, required)
- `reasonCode` (string/enum, required)
- `lines[]` (array, required, min 2)

#### JournalEntryLine (Create payload)
- `glAccountId` (id/uuid/string, required)
- `debitAmount` (money/decimal string, required as 0 if credit used; see Open Questions)
- `creditAmount` (money/decimal string, required as 0 if debit used; see Open Questions)
- `memo` (string, optional)

#### JournalEntry (View)
- `journalEntryId` (id)
- `status` (expect `POSTED`)
- `transactionDate` (datetime)
- `createdByUserId` (id) and/or display name if available
- Echo of header + lines
- Optional audit metadata if exposed (createdAt, etc.)

### Read-only vs editable by state/role
- Create screen: editable for authorized users only.
- View screen:
  - if status `POSTED`: read-only for all users with view permission.
  - if other statuses exist (e.g., DRAFT) not defined here; if returned, treat as read-only unless explicitly supported.

### Derived/calculated fields (UI-only)
- `totalDebits` = sum of debitAmount across lines (currency-scale rounding consistent with backend)
- `totalCredits` = sum of creditAmount across lines
- `difference` = totalDebits - totalCredits

---

## 9. Service Contracts (Frontend Perspective)

> Moqui note: exact service names/paths must match the backend/Moqui component; these are contract placeholders pending confirmation.

### Load/view calls
1. **Get reason codes**
   - Operation: `GET /accounting/reason-codes?type=MANUAL_JE` (placeholder)
   - Returns: `[{ reasonCode, description, active }]`
2. **Search GL accounts**
   - Operation: `GET /accounting/gl-accounts?query=...&active=true` (placeholder)
   - Returns: `[{ glAccountId, accountCode, name, type, active }]`
3. **Get Journal Entry by id**
   - Operation: `GET /accounting/journal-entries/{journalEntryId}` (placeholder)
   - Returns: JE header + lines + status + audit fields.

### Create/update calls
- None (no “save draft” defined).

### Submit/transition calls
1. **Create and post manual JE**
   - Operation: `POST /accounting/journal-entries/manual:post` (placeholder)
   - Request body: header + lines
   - Response: `{ journalEntryId, status: "POSTED" }` plus full entity optionally.

### Error handling expectations
- 400 validation errors include machine-readable code(s) listed in Business Rules section.
- 403 for insufficient permissions.
- 409 for state/immutability conflicts (e.g., attempting to modify POSTED, if such endpoint exists).
- Network/timeouts: show retry-able error message and keep form state.

---

## 10. State Model & Transitions

### Allowed states (as visible to frontend)
- `POSTED` (required by story)
- Other possible states referenced by backend: `REVERSED` (mentioned conceptually)
- Unknown states: display as read-only with status badge.

### Role-based transitions
- Authorized user can perform: `Create → Post` (single action).
- No UI supports `Edit` or `Delete` after `POSTED`.

### UI behavior per state
- **Create (local draft)**: editable, validations active.
- **POSTED (server)**: view-only; show status and immutable notice.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing header fields: highlight required fields, prevent submit.
- Less than 2 lines: show message “At least two lines are required.”
- Per-line invalidity: show line-level error and prevent submit.
- Unbalanced totals: show difference prominently, prevent submit.

### Server-side validation failures
- Show error banner with mapped message.
- Keep user inputs intact for correction.
- If backend returns field-specific errors, map them to specific fields/lines where possible.

### Concurrency conflicts
- Not applicable for create-only flow; if backend indicates duplicate/idempotency conflict, show error and provide support reference (see Open Questions on idempotency).

### Unauthorized access
- If user lacks permission:
  - Hide navigation entry if possible (based on frontend auth model).
  - If direct URL access: show “Not authorized” screen/message.

### Empty states
- No reason codes returned: disable submit and show “No reason codes configured; contact administrator.”
- No GL accounts found for search: show “No accounts match your search.”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Successful creation and posting of a balanced manual JE
**Given** an authenticated user with permission to create manual journal entries  
**And** the accounting period for the selected posting date is Open  
**When** the user enters a posting date, description, and selects a reason code  
**And** the user adds at least two lines with valid GL accounts  
**And** the total debits equal the total credits  
**And** the user clicks “Post Journal Entry”  
**Then** the frontend submits the JE to the backend  
**And** the user is redirected to a Journal Entry view screen for the created JE  
**And** the JE status is displayed as `POSTED`  
**And** no edit/delete controls are available on the posted JE view.

### Scenario 2: Client blocks posting when entry is unbalanced
**Given** an authenticated user is on the Create Manual JE screen  
**When** the user enters lines where total debits do not equal total credits  
**Then** the UI shows the debit total, credit total, and a non-zero difference  
**And** the “Post Journal Entry” action is disabled  
**And** the user cannot submit until the difference is zero.

### Scenario 3: Server rejects posting date in a closed period
**Given** an authenticated user with permission to create manual journal entries  
**And** the user enters a posting date that is in a Closed accounting period  
**And** the entry is otherwise balanced and valid client-side  
**When** the user clicks “Post Journal Entry”  
**Then** the backend returns an error indicating the period is closed  
**And** the frontend displays an error message stating posting is not allowed in a closed period  
**And** the user’s entered form values remain available for correction.

### Scenario 4: Server rejects invalid or inactive GL account
**Given** an authenticated user is creating a manual JE  
**When** the user submits an entry containing a GL account that is invalid or inactive  
**Then** the backend returns an invalid-account validation error  
**And** the frontend displays an error message indicating an account is invalid/inactive  
**And** the entry is not posted.

### Scenario 5: Posted JEs are immutable in the UI
**Given** a user navigates to a Journal Entry that is `POSTED`  
**When** the Journal Entry view loads  
**Then** all fields are displayed read-only  
**And** no UI actions to edit or delete the JE are present  
**And** if the user attempts to access any edit route (if it exists), the UI blocks it and shows an “immutable” message.

---

## 13. Audit & Observability

### User-visible audit data
On the JE View screen display (if provided by backend):
- `journalEntryId`
- `status`
- `transactionDate` (created timestamp)
- `createdBy` (user id or name)
- `postingDate`
- reason code + description (if reason code metadata available)

### Status history
- Not required unless backend provides. If available, show status history table (status, changedAt, changedBy).

### Traceability expectations
- After posting, the JE identifier must be visible and copyable.
- Frontend logs (console/telemetry if configured) should include `journalEntryId` on success and error code on failure (avoid sensitive data).

---

## 14. Non-Functional UI Requirements
- **Performance**: GL account lookup must be searchable; avoid loading entire chart if large.
- **Accessibility**: All form controls labeled; error messages associated to fields; keyboard operable line grid.
- **Responsiveness**: Works on typical POS tablet widths; line-entry table should scroll horizontally if needed.
- **i18n/timezone/currency**:
  - postingDate uses user locale date input but submits an unambiguous date (ISO).
  - Money inputs display currency consistent with business unit/currency context (currency selection not defined; see Open Questions).

---

## 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE-01**: Provide explicit empty-state messaging for missing reason codes / no GL accounts found; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows.
- **SD-UX-SEARCH-LOOKUP-01**: Use typeahead/search for GL account selection to avoid large payloads; qualifies as safe performance/ergonomics; impacts UX Summary, Service Contracts.
- **SD-ERR-MAP-01**: Standard mapping of backend validation/permission errors to inline/banner UI messages; qualifies as safe error-handling boilerplate; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. **Backend/Moqui contract**: What are the exact Moqui screen paths, service names, and/or REST endpoints for:
   - reason code list,
   - GL account search,
   - create+post manual JE,
   - JE retrieval by id?
2. **Permissions**: What is the authoritative permission string (frontend auth check) for creating manual JEs? Backend reference uses `ACCOUNTING_ADJUSTMENT_CREATE`; confirm mapping used in this Moqui frontend.
3. **Reason code source**: Is `reasonCode` an enum in Moqui, an entity list (e.g., `ReasonCode`), or pulled from another service? Is it scoped by business unit?
4. **Money & currency**: What currency applies to the JE? Is it implicitly the business unit’s base currency (`currencyUomId`) or user-selected? How should rounding/scale be enforced client-side?
5. **Line amount rules**: Are zero-amount lines allowed? Must exactly one of debit/credit be **> 0** vs allowing 0 with the other side set?
6. **Error code taxonomy**: What exact error codes/messages does the backend return for:
   - unbalanced,
   - period closed,
   - invalid account,
   - missing required fields,
   - immutable record?
7. **Draft vs post**: Is there a concept of saving a JE in DRAFT before posting (two-step), or is it strictly “create and post” in one action?
8. **Idempotency**: Does the create/post endpoint support idempotency keys to prevent double-posting if the user retries after a timeout? If yes, what header/key should frontend send?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Adjustments: Create Manual Journal Entry with Controls  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/190  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Adjustments: Create Manual Journal Entry with Controls

**Domain**: user

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Period Close, Adjustments, and Reporting

## Story
Adjustments: Create Manual Journal Entry with Controls

## Acceptance Criteria
- [ ] Authorized users can create manual JEs with reason code
- [ ] System blocks unbalanced manual JEs
- [ ] Posted manual JEs are immutable (corrections via reversal)
- [ ] Posting respects period controls and audit requirements


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