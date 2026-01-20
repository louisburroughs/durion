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

### Title
[FRONTEND] [STORY] Reconciliation: Bank/Cash Reconciliation Matching (Import/Enter Statement Lines, Match, Adjust, Finalize & Report)

### Primary Persona
Accountant (or Finance Manager acting as accountant)

### Business Value
Ensure cash/bank balances are accurate and auditable by matching bank statement activity to recorded POS payments/receipts, recording controlled adjustments (fees/interest) with correct accounting traceability, and producing a finalized reconciliation report.

---

## 2. Story Intent

### As a / I want / So that
**As an** Accountant,  
**I want** a reconciliation workspace to import or manually enter bank/cash statement lines and match them to system-recorded payments/receipts (or create controlled adjustments),  
**so that** I can reconcile a bank/cash account for a period with a complete audit trail and produce a finalized reconciliation report.

### In-scope
- Create a reconciliation for a selected bank/cash account and date range.
- Import statement lines (file upload) and/or manually enter statement lines.
- View unreconciled system transactions and match/unmatch to statement lines (manual; suggestions optional but not required unless backend provides).
- Create controlled adjustments (fees/interest) that generate proper accounting entries (frontend flow + validations + submission).
- Track matched/unmatched counts and reconciliation difference and prevent finalization if non-zero.
- Finalize reconciliation (immutability/read-only behavior).
- Generate/download a reconciliation report (or navigate to report screen) after finalization.
- Full audit visibility (who/when) for reconciliation actions.

### Out-of-scope
- Defining GL account mappings, posting rules, or journal entry debit/credit structure (owned by accounting backend configuration).
- Automated matching tolerance rules or auto-match heuristics unless explicitly provided by backend.
- Re-opening finalized reconciliations (explicitly disallowed by provided reference).
- Multi-currency reconciliation behavior (unless backend contract explicitly supports it).
- Bank connectivity / live bank feeds.

---

## 3. Actors & Stakeholders
- **Accountant (Primary):** Performs reconciliation, matching, adjustments, finalization, report.
- **Auditor (Secondary):** Reviews finalized reconciliation and audit trail.
- **Finance Admin (Secondary):** Ensures bank accounts and GL accounts exist (configuration outside this story’s UI unless already present in app).
- **Payment/Receipt Domain (Data Provider):** Supplies reconcilable transactions for matching (read-only from frontend perspective).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- User has permission to access reconciliation features and to create adjustments and finalize reconciliations (exact permission IDs are currently unspecified; see Open Questions).
- At least one bank/cash account exists and is selectable.
- Backend supports persistence and retrieval of reconciliations, statement lines, matches, adjustments, and report generation.

### Dependencies (blocking where unknown)
- **Backend API contracts** for:
  - Creating/loading reconciliations, listing statement lines, listing reconcilable system transactions.
  - Performing match/unmatch operations.
  - Creating adjustments (fees/interest) and resulting journal entry creation/posting behavior.
  - Finalization rules and report generation/download.
- **Supported import formats** and parsing rules (CSV/OFX/etc.).
- **GL account selection source** for adjustments (list/search endpoint).
- **Audit event visibility** (what endpoint returns history).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → Reconciliation → Bank/Cash Reconciliation**
- Deep link (if supported): `/accounting/reconciliation` (list) and `/accounting/reconciliation/{reconciliationId}` (detail workspace)

### Screens to create/modify (Moqui)
1. **`Accounting/Reconciliation/ReconciliationList.xml`** (new)
   - List prior reconciliations with filters (account, date range, status).
   - Action: “Start Reconciliation”.

2. **`Accounting/Reconciliation/ReconciliationCreate.xml`** (new)
   - Form: select bankAccountId, periodStartDate, periodEndDate (and optionally statementClosingBalance if required by backend).
   - Create action transitions to detail workspace.

3. **`Accounting/Reconciliation/ReconciliationDetail.xml`** (new)
   - Workspace with:
     - Reconciliation header (account, period, opening/closing balance fields as read-only unless backend allows edits).
     - Tabs/sections:
       - Statement Lines (import/enter, matched/unmatched, select line)
       - System Transactions (unreconciled payments/receipts, select txn)
       - Matches (view/remove)
       - Adjustments (create/view)
       - Audit Trail (view events)
     - Primary actions: Import Statement, Add Statement Line, Match, Unmatch, Create Adjustment, Finalize, Download Report (enabled by state).

4. **`Accounting/Reconciliation/ReconciliationReport.xml`** (optional new)
   - If report is rendered as a screen; otherwise “Download” triggers file response.

### Navigation context
- Breadcrumb: Accounting > Reconciliation > {Bank Account Name} > {Period}
- Back to list from detail.

### User workflows
- **Happy path**
  1. Create reconciliation (Draft).
  2. Import statement lines (or manually enter).
  3. Load unreconciled system transactions.
  4. Select statement line + transaction(s) → Match.
  5. For lines without system transactions → Create Adjustment → line becomes matched.
  6. Difference becomes zero → Finalize.
  7. Download/view report.

- **Alternate paths**
  - Manual entry only (no import).
  - Many-to-one match (multiple system txns match one statement line) if backend allows.
  - One-to-many match (one system txn matched to multiple statement lines) **likely disallowed**; must be explicit (Open Question).
  - Unable to finalize due to non-zero difference; user continues matching/adjusting.

---

## 6. Functional Behavior

### Triggers
- User starts reconciliation from list.
- User imports statement file or adds statement line manually.
- User matches/unmatches.
- User creates adjustment.
- User finalizes reconciliation.
- User requests report.

### UI actions & expected system behavior

#### 6.1 Create reconciliation
- UI submits create form.
- On success, navigate to reconciliation detail workspace in **Draft**.

#### 6.2 Import statement lines
- User selects file and submits.
- UI shows import progress and results summary (imported count, failed count, failure reasons).
- Imported lines appear in Statement Lines list with status **Unmatched** by default.

#### 6.3 Manually enter statement line
- User opens “Add Statement Line” modal/form.
- Required fields validated client-side (date, amount, debit/credit or signed amount, description).
- On save, line appears in Statement Lines list as **Unmatched**.

#### 6.4 Load unreconciled system transactions
- UI loads a list filtered by:
  - bankAccountId (or payment account mapping)
  - periodStartDate..periodEndDate
  - unreconciled only
- Transactions display key match fields: date, amount, reference, counterparty/payer/payee if available, transactionId.

#### 6.5 Match / unmatch
- **Match** action enabled when:
  - reconciliation is Draft
  - at least 1 statement line selected and at least 1 system transaction selected (cardinality rules TBD by backend; see Open Questions)
- UI submits match command; on success:
  - statement line(s) status becomes Matched
  - system transaction(s) marked “matched/cleared” within this reconciliation (and excluded from “unreconciled” list)
  - reconciliation difference recalculated/displayed

- **Unmatch** action enabled when:
  - reconciliation is Draft
  - a selected statement line or match record is currently matched
- UI submits unmatch command; on success, items return to Unmatched/unreconciled pools and difference updates.

#### 6.6 Create adjustment (fee/interest)
- From a statement line (typically unmatched), user chooses “Create Adjustment”.
- UI presents adjustment form:
  - adjustmentType (e.g., Bank Fee, Interest Earned) (source TBD)
  - glAccountId (required)
  - amount (required; default to statement line amount)
  - description (required or optional per backend)
  - linkToStatementLineId (pre-filled)
- On submit:
  - backend creates adjustment and journal entry reference
  - statement line becomes Matched (per provided backend reference)
  - adjustment appears in Adjustments list with journalEntryId link (if provided)

#### 6.7 Finalize
- Finalize action enabled only when:
  - reconciliation is Draft
  - reconciliation difference == 0.00 (as computed by backend; UI should display backend-provided “difference”)
- On finalize success:
  - status becomes Finalized
  - all mutation actions disabled (import, add line, match/unmatch, create adjustment)
  - report actions enabled (download/view)
  - audit shows finalization event with user and timestamp.

---

## 7. Business Rules (Translated to UI Behavior)

> Note: Accounting domain rules prohibit inventing posting semantics; UI enforces workflow gating and required inputs.

### Validation
- Cannot finalize unless **difference is zero**.
- Adjustments require **glAccountId**.
- If backend rejects adjustment because it would make totals negative / invalid, show backend error verbatim with friendly prefix (see Error handling).

### Enable/disable rules
- If status == `FINALIZED`:
  - All editing and command actions disabled.
  - Screen is read-only.
- Match/unmatch/adjustment actions disabled when required selections missing.

### Visibility rules
- Show matched vs unmatched sections or filters for both statement lines and system transactions.
- Show audit trail section always; if backend doesn’t provide, show “Audit unavailable” with error code.

### Error messaging expectations
- Import parsing errors must show:
  - file-level error (unsupported format) OR
  - row-level errors (line number + reason)
- Finalize attempt with non-zero difference must show:
  - “Reconciliation cannot be finalized with a non-zero difference.” (exact message may come from backend; ensure consistent user-visible text)
- Unauthorized operations show “You do not have permission to perform this action.”

---

## 8. Data Requirements

### Entities involved (frontend view models)
> Exact entity names may differ; use Moqui services/entities as implemented by backend. Frontend must not assume entity schema beyond contracts returned.

- `Reconciliation`
- `BankStatementLine`
- `ReconciliationMatch`
- `ReconciliationAdjustment`
- `SystemTransaction` (from payment/receipt domain; read-only projection)
- `GLAccount` (for adjustment selection)
- `ReconciliationAuditEvent` (or generic audit log)

### Fields (type, required, defaults)

#### Reconciliation
- `reconciliationId` (string/UUID, required, read-only)
- `bankAccountId` (string, required, read-only after create)
- `periodStartDate` (date, required, read-only after create)
- `periodEndDate` (date, required, read-only after create)
- `openingBalance` (decimal, read-only; source backend)
- `statementClosingBalance` (decimal, **unknown if required**; see Open Questions)
- `bookClosingBalance` (decimal, read-only; source backend)
- `difference` (decimal, read-only; source backend)
- `status` (`DRAFT`|`FINALIZED`, read-only)
- `finalizedAt` (datetime, read-only)
- `finalizedByUserId` (string, read-only)

#### BankStatementLine
- `statementLineId` (string/UUID, read-only)
- `transactionDate` (date, required)
- `description` (string, required)
- `amount` (decimal, required)
- `direction` (`DEBIT`|`CREDIT` OR signed amount; **TBD**)
- `status` (`UNMATCHED`|`MATCHED`, read-only except via match/unmatch commands)
- `reference` (string, optional; if import provides)

#### SystemTransaction (reconcilable payment/receipt)
- `systemTransactionId` (string, read-only)
- `transactionDate` (date/datetime, read-only)
- `amount` (decimal, read-only)
- `type` (enum/string, optional)
- `reference` (string, optional)
- `counterpartyName` (string, optional)
- `reconciliationStatus` (unreconciled/matched, read-only)

#### ReconciliationMatch
- `matchId` (string, read-only)
- `statementLineId` (string, required)
- `systemTransactionId` (string, required)
- `createdAt`, `createdByUserId` (read-only)

#### ReconciliationAdjustment
- `adjustmentId` (string, read-only)
- `statementLineId` (string, optional/likely required in this UX path)
- `glAccountId` (string, required)
- `amount` (decimal, required)
- `description` (string, optional/required TBD)
- `journalEntryId` (string, read-only)
- `createdAt`, `createdByUserId` (read-only)

### Read-only vs editable by state/role
- Draft: statement lines editable only via create/import; match/unmatch and adjustments allowed.
- Finalized: all above are read-only.
- Role-based editing depends on permissions (Open Question).

### Derived/calculated fields
- `difference` and balances should be treated as backend-calculated authoritative values; UI must not compute financial difference beyond display formatting.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui implementation should call services via screen transitions/actions. Exact service names are TBD; below are required capabilities and suggested service naming conventions.

### Load/view calls
- `Accounting.Reconciliation.getReconciliationDetail`
  - Inputs: `reconciliationId`
  - Returns: reconciliation header + computed fields (openingBalance, bookClosingBalance, statementClosingBalance?, difference, status)
- `Accounting.Reconciliation.listStatementLines`
  - Inputs: `reconciliationId`, optional filters (status)
- `Accounting.Reconciliation.listSystemTransactionsForReconciliation`
  - Inputs: `bankAccountId`, `periodStartDate`, `periodEndDate`, `reconciliationId` (to exclude already matched), pagination
- `Accounting.Reconciliation.listMatches`
  - Inputs: `reconciliationId`
- `Accounting.Reconciliation.listAdjustments`
  - Inputs: `reconciliationId`
- `Accounting.GLAccount.search`
  - Inputs: query, activeOnly=true, effectiveDate (optional)
- `Accounting.Reconciliation.listAuditEvents`
  - Inputs: `reconciliationId`

### Create/update calls
- `Accounting.Reconciliation.createReconciliation`
  - Inputs: `bankAccountId`, `periodStartDate`, `periodEndDate`, (maybe) `statementClosingBalance`
  - Returns: `reconciliationId`
- `Accounting.Reconciliation.createStatementLine`
  - Inputs: `reconciliationId`, `transactionDate`, `description`, `amount`, `direction`/signedAmount, `reference?`
- `Accounting.Reconciliation.importStatementLines`
  - Inputs: `reconciliationId`, `file` (binary), `format` (optional)
  - Returns: summary + per-line errors

### Submit/transition calls
- `Accounting.Reconciliation.createMatch`
  - Inputs: `reconciliationId`, `statementLineId(s)`, `systemTransactionId(s)`
  - Returns: updated statuses + updated reconciliation computed fields
- `Accounting.Reconciliation.removeMatch`
  - Inputs: `reconciliationId`, `matchId` OR (`statementLineId`,`systemTransactionId`)
- `Accounting.Reconciliation.createAdjustment`
  - Inputs: `reconciliationId`, `statementLineId?`, `glAccountId`, `amount`, `description`, `adjustmentType?`
  - Returns: `adjustmentId`, `journalEntryId`, updated reconciliation fields, updated statement line status
- `Accounting.Reconciliation.finalizeReconciliation`
  - Inputs: `reconciliationId`
  - Errors: non-zero difference -> conflict/validation with specific code

### Error handling expectations
- Moqui screen should map service errors into:
  - Field-level errors for validation (missing glAccountId, invalid amount).
  - Top-level notification for conflicts (finalize when difference != 0).
  - Import errors displayed as structured results.
- Error codes are not defined in provided inputs for reconciliation; must be confirmed (Open Question). UI should still display backend `errorMessage` and log `errorCode` if present.

---

## 10. State Model & Transitions

### Allowed states
- `DRAFT`
- `FINALIZED`

### Role-based transitions
- `DRAFT -> FINALIZED` requires permission (TBD).
- No transitions out of `FINALIZED` (immutable).

### UI behavior per state
- **DRAFT**
  - Editable workspace; actions enabled as per selection rules.
  - Show “Difference” prominently; show what remains unmatched.
- **FINALIZED**
  - Read-only; show finalized metadata; allow report download/view and audit review.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields on create reconciliation: show inline errors.
- Import file missing/unsupported: show file-level error and keep prior data unchanged.
- Create adjustment without glAccountId: prevent submit; if backend rejects, show returned validation.

### Concurrency conflicts
- If match/unmatch/adjustment/finalize fails due to concurrent update (optimistic lock / stale status):
  - UI refreshes reconciliation detail and shows “This reconciliation was updated by another user. Please review the latest status.”

### Unauthorized access
- If user lacks permission for create/match/adjust/finalize:
  - Disable action buttons when permission flags are known.
  - If backend rejects, show 403-style message and keep UI state unchanged.

### Empty states
- No statement lines: show “No statement lines yet. Import a file or add one manually.”
- No reconcilable system transactions: show “No unreconciled transactions found for this period/account.”
- No matches/adjustments: show empty placeholders.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create a draft reconciliation
**Given** I am an authenticated Accountant with permission to create reconciliations  
**When** I start a new reconciliation and select a bank/cash account and a period start and end date  
**Then** a new reconciliation is created in `DRAFT` status  
**And** I am navigated to the reconciliation detail workspace showing the selected account and period.

### Scenario 2: Import bank statement lines successfully
**Given** I have a `DRAFT` reconciliation  
**When** I upload a supported bank statement file for that reconciliation  
**Then** the system imports statement lines and displays them as `UNMATCHED` statement lines  
**And** any line-level import failures are listed with reasons without importing invalid lines.

### Scenario 3: Manually add a statement line
**Given** I have a `DRAFT` reconciliation  
**When** I add a statement line with a transaction date, description, and amount  
**Then** the new statement line appears in the statement lines list as `UNMATCHED`.

### Scenario 4: Match a statement line to a system transaction (1-to-1)
**Given** I have a `DRAFT` reconciliation with an imported statement line of amount 55.50 on 2026-07-10  
**And** the system shows an unreconciled payment/receipt transaction of amount 55.50 on 2026-07-10  
**When** I select the statement line and the system transaction and perform “Match”  
**Then** the statement line is marked `MATCHED`  
**And** the system transaction is no longer shown as unreconciled for this reconciliation  
**And** the reconciliation difference shown in the header is updated based on backend-calculated values.

### Scenario 5: Create an adjustment for a bank fee and auto-match the line
**Given** I have a `DRAFT` reconciliation with an unmatched statement line described as “Monthly Service Fee”  
**When** I choose “Create Adjustment” for that statement line  
**And** I select a GL account and submit the adjustment with the amount equal to the statement line amount  
**Then** an adjustment is created and listed under Adjustments  
**And** the adjustment includes a reference to a journal entry identifier if provided by the backend  
**And** the statement line is marked `MATCHED`.

### Scenario 6: Prevent finalize when difference is non-zero
**Given** I have a `DRAFT` reconciliation where the displayed difference is not 0.00  
**When** I attempt to finalize the reconciliation  
**Then** the finalize action is rejected  
**And** I see an error message indicating finalization is not allowed with a non-zero difference  
**And** the reconciliation remains in `DRAFT` status.

### Scenario 7: Finalize when difference is zero and enforce immutability
**Given** I have a `DRAFT` reconciliation where the displayed difference is 0.00  
**When** I finalize the reconciliation  
**Then** the reconciliation status becomes `FINALIZED`  
**And** matching, unmatching, importing, adding statement lines, and creating adjustments are no longer available  
**And** I can download or view the reconciliation report.

---

## 13. Audit & Observability

### User-visible audit data
In the reconciliation detail screen, provide an Audit Trail view that lists, at minimum:
- event type (Created, Statement Imported, Statement Line Added, Matched, Unmatched, Adjustment Created, Finalized)
- timestamp
- actor (userId/displayName if available)
- related entity references (statementLineId, systemTransactionId, adjustmentId)

### Status history
- Reconciliation status transitions visible with `finalizedAt` and `finalizedBy`.

### Traceability expectations
- UI should display identifiers (reconciliationId, statementLineId, adjustmentId, journalEntryId if present) in a copyable manner for support/audit.

---

## 14. Non-Functional UI Requirements

- **Performance:** Lists (statement lines, system transactions) must support pagination/virtual scroll if backend returns many rows; avoid loading everything at once.
- **Accessibility:** All actions accessible via keyboard; forms have labels; errors announced and associated to inputs.
- **Responsiveness:** Usable on tablet widths; two-list matching UI may stack vertically on small screens.
- **i18n/timezone/currency:** Display dates in user locale/timezone; display amounts with currency formatting using currencyUomId if backend provides. If not provided, default formatting must not assume currency (Open Question).

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATES: Added explicit empty-state messages for no data in lists; qualifies as safe because it affects only UX guidance and not business policy. Impacted sections: UX Summary, Alternate/waswo Flows.
- SD-UI-PAGINATION: Require pagination/virtual scroll for potentially large lists; safe because it’s UI ergonomics and does not change domain rules. Impacted sections: Non-Functional UI Requirements, UX Summary.
- SD-ERROR-GENERIC-MAPPING: Display backend errorMessage/errorCode when present; safe because it doesn’t invent business logic and preserves authoritative backend messaging. Impacted sections: Service Contracts, Error Flows.

---

## 16. Open Questions

1. **Domain label conflict cleanup:** The frontend issue currently carries a legacy `payment` label, but the capability is accounting reconciliation. Confirm we should label this story `domain:accounting` and treat payment/receipt as read-only data providers.
2. **Permissions/authorization:** What are the exact permissions/scopes to:
   - view reconciliation,
   - create reconciliation,
   - import/enter statement lines,
   - match/unmatch,
   - create adjustments,
   - finalize,
   - view/download reports?
3. **Import formats & schemas:** Which bank statement formats are supported (CSV/OFX/BAI2)? If CSV, what columns and date/amount conventions are required?
4. **Statement line amount model:** Does the backend represent statement line amount as:
   - signed `amount` (negative for debit), or
   - `amount` + `type/direction` (`DEBIT`/`CREDIT`)?
   The UI needs the authoritative representation for entry, display, and validation.
5. **Matching cardinality rules:** Are matches strictly 1-to-1, or do we support:
   - many system transactions to one statement line,
   - one system transaction to many statement lines (likely disallowed)?
   Also, must amounts sum exactly, or can there be partial matches?
6. **Closing/ending balance inputs:** Does reconciliation creation (or finalization) require capturing a **statement closing balance** entered by the user? If yes, at what step is it entered, and can it be edited in Draft?
7. **Adjustment types & defaults:** Are there predefined adjustment reason codes/types (e.g., Bank Fee, Interest Earned) and default GL accounts per type, or must the user always choose GL account manually?
8. **Report contract:** Is the report:
   - a downloadable file (PDF/CSV), or
   - a rendered screen view?
   What exact required report contents/sections/fields must be included?
9. **Reconcilable system transactions contract:** What is the precise API to retrieve “unreconciled payments/receipts”? What fields are guaranteed, and how does the backend determine “reconcilable” for the selected bank/cash account?
10. **Currency handling:** Will reconciliations always be single-currency per bank account? Will the backend return `currencyUomId` for formatting?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Reconciliation: Support Bank/Cash Reconciliation Matching  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/187  
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Reconciliation: Support Bank/Cash Reconciliation Matching

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Reconciliation: Support Bank/Cash Reconciliation Matching

## Acceptance Criteria
- [ ] Import/enter bank statement lines and match to payments/receipts
- [ ] Track matched/unmatched items with audit trail
- [ ] Allow controlled adjustments (fees/interest) via proper entries
- [ ] Produce reconciliation report


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