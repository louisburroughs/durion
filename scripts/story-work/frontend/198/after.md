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
[FRONTEND] [STORY] GL: Provide Trial Balance and Drilldown to Source (Moqui Screens)

### Primary Persona
Controller (Finance)

### Business Value
Enable finance to validate General Ledger integrity and support period-end close/audits by generating a Trial Balance with traceable drilldown from balances to ledger lines, journal entries, and originating source events, including CSV export for offline workflows.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Controller  
- **I want** to generate a Trial Balance for a selected accounting period with optional account and dimension filters, and drill down from balances to underlying ledger/journal/source details  
- **So that** I can reconcile the ledger, investigate anomalies, and produce evidence for audit and close.

### In-scope
- Moqui **screens, forms, transitions** for:
  - Trial Balance criteria selection and on-screen results
  - Drilldown flow: Trial Balance → account ledger lines → journal entry → source event view
  - CSV export of the displayed Trial Balance
- Frontend enforcement of permissions via server responses (do not rely on UI-only hiding)
- Basic empty/error states for no data, validation errors, and access denied

### Out-of-scope
- Implementing accounting calculations/posting logic
- Defining or changing the Chart of Accounts, posting rules, or period close logic
- Designing/implementing canonical source event schemas (beyond displaying returned fields)
- PDF export (not requested; CSV only)

---

## 3. Actors & Stakeholders
- **Controller (primary):** runs Trial Balance, exports CSV, drills into sources.
- **Finance Manager/Accountant (secondary):** uses reports for reconciliation/close.
- **Auditor (stakeholder):** consumes exported CSV and drilldown evidence (may be read-only access).
- **System (GL/Accounting services):** provides report and drilldown data; enforces access control.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend.
- User has appropriate permissions to view GL reporting (exact permission names TBD; see Open Questions).
- Accounting periods exist and posted journal entries/ledger entries exist for selected period.

### Dependencies
- Backend endpoints/services must exist for:
  - Listing available periods (or accepting a period ID)
  - Generating Trial Balance with filters
  - Retrieving ledger lines for an account/period/filter context
  - Retrieving a journal entry by ID
  - Retrieving a source event by reference (type/id), or returning a URL/reference payload
- Backend must enforce access controls at API level and return appropriate error codes/status.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Accounting → General Ledger → Trial Balance**
- Deep-link support:
  - `/gl/trial-balance`
  - `/gl/trial-balance/ledger?glAccountId=...`
  - `/gl/journal-entry?journalEntryId=...`
  - `/gl/source-event?sourceType=...&sourceId=...` (or equivalent; depends on backend contract)

### Screens to create/modify (Moqui Screen definitions)
1. **`apps/accounting/screen/gl/TrialBalance.xml`**
   - Criteria form + results table + export action
2. **`apps/accounting/screen/gl/LedgerLines.xml`**
   - List ledger lines for selected account/context
3. **`apps/accounting/screen/gl/JournalEntryView.xml`**
   - Journal entry header + lines; link to source event
4. **`apps/accounting/screen/gl/SourceEventView.xml`**
   - Generic “source event details” renderer (based on returned payload)

### Navigation context
- Maintain a “report context” so drilldown preserves filters (period + dimensions + account range if applicable).
- Provide “Back to Trial Balance” action that restores criteria and results (server-side reload permitted).

### User workflows
#### Happy path
1. Open Trial Balance screen.
2. Select Period (required), optionally set account filter and dimension filters.
3. Generate report → view results with totals.
4. Click an account balance (e.g., closing balance) → view ledger lines.
5. Click a ledger line → view journal entry.
6. Click source reference → view source event details.
7. Export CSV of Trial Balance.

#### Alternate paths
- Generate report with no results → show “No data” state and allow criteria adjustment.
- Attempt drilldown to missing source → show “Source unavailable” with reference ID.
- Permission denied → show access denied state; do not leak restricted data.

---

## 6. Functional Behavior

### Triggers
- User submits “Generate Trial Balance”
- User clicks on a Trial Balance line (drilldown)
- User clicks on a ledger line (drilldown)
- User clicks on a source reference (drilldown)
- User clicks “Export CSV”

### UI actions
- **Generate:** validate criteria; call backend; render table with debit/credit totals.
- **Drilldown 1:** navigate to LedgerLines screen with context params.
- **Drilldown 2:** navigate to JournalEntryView with `journalEntryId`.
- **Drilldown 3:** navigate to SourceEventView with reference payload/params.
- **Export:** request CSV from backend using same filters; trigger file download.

### State changes (frontend)
- Store current criteria and last-generated timestamp in screen state (request parameters or session; Moqui context).
- No financial state mutations in frontend.

### Service interactions (Moqui)
- Use Moqui `service-call` (or REST via Moqui tools if backend is separate) for:
  - Loading filter dropdown data (periods, dimensions)
  - Fetching trial balance results
  - Fetching ledger lines
  - Fetching journal entry
  - Fetching source event details
  - Export request returning CSV

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Period is **required** before generating report.
- If dimensions are supported, each selected dimension must have a value (no “dimension name only”).
- Account filter validation (if provided):
  - If range-based: `fromAccount <= toAccount` (lexicographic or numeric per backend definition; clarify).
  - If list-based: at least one account selected.

### Enable/disable rules
- “Generate” disabled until required period provided.
- “Export CSV” disabled until a report has been successfully generated (has `reportRunId` or equivalent context).
- Drilldown links disabled/hidden if the corresponding identifiers are missing (e.g., ledger line without journalEntryId).

### Visibility rules
- Show imbalance warning banner if backend indicates debits ≠ credits (or includes a boolean `isBalanced=false`).
- Hide/redact restricted accounts/dimensions only if backend already filtered them; UI must not attempt to infer.

### Error messaging expectations
- Validation errors: inline field errors + summary at top.
- Permission errors (401/403): show “Access denied” and stop rendering sensitive data.
- Not found (404) for journal entry/source: show “Not found” with the requested ID/reference.
- Backend business-rule errors should display backend error code and message in a user-friendly wrapper, without exposing sensitive payload.

---

## 8. Data Requirements

> Note: Exact entity names in Moqui may differ; below is a frontend contract view. Backend contract must confirm.

### Entities involved (conceptual)
- AccountingPeriod
- GLAccount
- TrialBalanceReport (computed/view)
- LedgerEntry / GLTransactionLine (posted lines)
- JournalEntry + JournalEntryLine
- SourceEvent reference (from canonical event envelope or source system)

### Fields (type, required, defaults)

#### Trial Balance Criteria (inputs)
- `periodId` (string/ID, **required**)
- `accountFilterType` (enum: `NONE|RANGE|LIST`, default `NONE`) **(clarify if supported)**
- `fromAccountCode` (string, required if RANGE)
- `toAccountCode` (string, required if RANGE)
- `glAccountIds[]` (array<ID>, required if LIST)
- `dimensions` (map<string,string> or array of `{dimensionTypeId, dimensionValueId}`, optional; **requires clarification**)

#### Trial Balance Result (display)
- `generatedAt` (datetime, required)
- `filtersApplied` (object/string, required)
- `lines[]` (required)
  - `glAccountId` (ID, required)
  - `accountCode` (string, required)
  - `accountName` (string, required)
  - `openingBalance` (decimal, required)
  - `totalDebits` (decimal, required)
  - `totalCredits` (decimal, required)
  - `closingBalance` (decimal, required)
- `reportTotalDebits` (decimal, required)
- `reportTotalCredits` (decimal, required)
- `isBalanced` (boolean, optional but preferred)

#### Ledger Lines (display)
- `ledgerLines[]`
  - `ledgerEntryId` (ID, required)
  - `postingDate` (date, required)
  - `description` (string, optional)
  - `debitAmount` (decimal, required)
  - `creditAmount` (decimal, required)
  - `journalEntryId` (ID, optional but needed for drilldown)
  - `sourceEventRef` (object/string, optional)

#### Journal Entry (display)
- `journalEntryId` (ID, required)
- `postingDate` (date, required)
- `status` (string enum: `DRAFT|POSTED|...`, required)
- `description` (string, optional)
- `sourceEventRef` (required for drilldown to source if available)
- `lines[]`
  - `glAccountId` (ID, required)
  - `accountCode` (string, preferred)
  - `accountName` (string, preferred)
  - `debitAmount` (decimal, required)
  - `creditAmount` (decimal, required)

#### Source Event (display)
- `sourceSystem` (string, required)
- `sourceEntityType` (string, required)
- `sourceEntityId` (string, required)
- `occurredAt` (datetime, optional)
- `displayFields` (map<string, any>), optional (generic renderer)

### Read-only vs editable
- All fields in these screens are **read-only** (reporting).

### Derived/calculated fields
- None calculated client-side besides formatting; all balances are backend-authoritative.

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking**: actual service names/paths are not provided. Below are required contracts to implement the frontend; backend must confirm.

### Load/view calls
1. `AccountingPeriod.list` (or REST `GET /api/accounting/periods`)
   - Response: periods with `periodId`, `name`, `startDate`, `endDate`, `status`

2. `GL.Dimension.listSupported` (optional)
   - Response: dimension types + allowed values (if UI needs picklists)

### Create/update calls
- None (reporting only).

### Submit/transition calls
1. `GL.TrialBalance.generate`
   - Request: criteria fields above
   - Response: trial balance result payload + optionally `reportRunId`

2. `GL.LedgerLines.getByAccount`
   - Request: `periodId`, `glAccountId`, plus same dimension filters
   - Response: list of ledger lines + totals for that account (optional)

3. `GL.JournalEntry.get`
   - Request: `journalEntryId`
   - Response: journal entry header + lines

4. `GL.SourceEvent.get`
   - Request: `sourceSystem`, `sourceEntityType`, `sourceEntityId` (or a single `sourceEventId`)
   - Response: details for display

5. `GL.TrialBalance.exportCsv`
   - Request: same criteria or `reportRunId`
   - Response: `text/csv` stream + filename

### Error handling expectations
- 400: field validation errors with machine-readable codes per field.
- 401/403: access denied (no partial data returned).
- 404: missing journal entry/source event.
- 409: stale context/reportRunId invalid (if used).
- 500/503: show retryable error and keep criteria intact.

---

## 10. State Model & Transitions

### Allowed states (screen-level)
- `Idle` (criteria not yet run)
- `Loading` (fetching report/lines)
- `Loaded` (results displayed)
- `Error` (non-field error)
- `AccessDenied`

### Role-based transitions
- Only authorized users can transition from `Idle` → `Loaded` by successfully calling report services.
- Unauthorized transitions result in `AccessDenied`.

### UI behavior per state
- `Idle`: show criteria form; no results table; export disabled.
- `Loading`: disable actions; show loading indicator.
- `Loaded`: show results + drilldown links + export enabled.
- `Error`: show error summary + allow retry.
- `AccessDenied`: show access denied message; no data tables rendered.

---

## 11. Alternate / Error Flows

1. **No data**
   - If report returns empty `lines[]`, show “No data found for selected criteria” and keep export disabled (unless export of empty is required—clarify).

2. **Unbalanced report**
   - If `reportTotalDebits != reportTotalCredits` (or `isBalanced=false`), display prominent warning banner and still render results.

3. **Broken source link**
   - If journal entry lacks source reference or source lookup returns 404, show non-blocking message: “Source event not available” with reference details if safe.

4. **Concurrency / stale params**
   - If backend uses `reportRunId` and it expires, show “Report expired, please regenerate” and route back with criteria preserved.

5. **Unauthorized access**
   - If any drilldown call returns 403, show access denied and do not render previously cached sensitive details for that view.

---

## 12. Acceptance Criteria (Gherkin)

### AC1: Generate Trial Balance by period
Given I am authenticated as a Controller with permission to view trial balance  
And posted journal entries exist for period "2024-08"  
When I open the Trial Balance screen  
And I select period "2024-08"  
And I click "Generate"  
Then the system displays a Trial Balance with account lines for the selected period  
And the report shows total debits and total credits.

### AC2: Balanced totals indication
Given a Trial Balance is generated for period "2024-08"  
When the report totals are returned  
Then if total debits equal total credits, the UI shows the report as balanced  
And if total debits do not equal total credits, the UI shows a prominent imbalance warning while still displaying results.

### AC3: Drilldown from Trial Balance to ledger lines
Given a Trial Balance is displayed for period "2024-08"  
When I click the closing balance (or account line drilldown action) for account "Revenue"  
Then I am navigated to the Ledger Lines view for that account and period  
And I see the list of posted ledger lines that comprise that balance.

### AC4: Drilldown from ledger line to journal entry
Given I am viewing ledger lines for an account  
And at least one ledger line has a journalEntryId  
When I select a ledger line with a journalEntryId  
Then I am navigated to the Journal Entry view  
And I see the journal entry header and all journal entry lines.

### AC5: Drilldown from journal entry to source event
Given I am viewing a journal entry that includes a source event reference  
When I click the source event reference  
Then I am navigated to the Source Event view  
And I see source event details returned by the backend for that reference.

### AC6: Export Trial Balance to CSV
Given a Trial Balance report is displayed for selected criteria  
When I click "Export CSV"  
Then a CSV file downloads  
And the CSV contains a header row  
And the CSV rows match exactly the data displayed in the Trial Balance table for the same criteria.

### AC7: Access control enforcement (restricted accounts/dimensions)
Given my user is restricted from viewing certain accounts or dimension values  
When I generate a Trial Balance for a period containing restricted activity  
Then the UI does not display any restricted account lines or restricted amounts  
And if the backend returns 403, the UI shows “Access denied” and displays no report data.

### AC8: Error handling for missing source event
Given I am viewing a journal entry with a source reference  
When the source event request returns 404 Not Found  
Then the UI displays a “Source event not found” message including the reference identifier  
And the UI allows me to navigate back to the journal entry without losing context.

---

## 13. Audit & Observability

### User-visible audit data
- Display on report header:
  - `generatedAt`
  - “Generated by <current user>” if backend returns (otherwise omit; do not guess)
- Display on journal entry:
  - `journalEntryId`, `postingDate`, `status`
  - source reference identifiers

### Status history
- Not required to display full audit trail; but the drilldown chain must preserve identifiers:
  - TrialBalance criteria → glAccountId → ledgerEntryId → journalEntryId → source reference.

### Traceability expectations
- Include identifiers in client logs (non-PII):
  - `periodId`, `glAccountId`, `journalEntryId`, `sourceEntityId` (only if not sensitive; otherwise hash or omit per security policy—clarify).

---

## 14. Non-Functional UI Requirements

### Performance
- Trial Balance generation may be slow; UI must:
  - show loading state
  - prevent double-submit
  - allow cancellation via navigation (no special cancel API assumed)

### Accessibility
- All actionable elements keyboard accessible.
- Table supports screen reader labels for totals and warnings.
- Error summary uses ARIA live region (or Quasar equivalent).

### Responsiveness
- Works on tablet widths; tables may scroll horizontally.

### i18n/timezone/currency
- Currency formatting must use currency code returned by backend (or a single system currency—clarify).
- Dates/times displayed in user locale/timezone as configured by frontend/Moqui.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Display a standard “No data found” empty state when the report returns zero lines; qualifies as safe UI ergonomics; impacts UX Summary, Error Flows, Acceptance Criteria.
- SD-UI-LOADING-STATE: Use standard loading/disable interactions during service calls to prevent duplicate requests; qualifies as safe UI ergonomics; impacts Functional Behavior, Non-Functional Requirements.
- SD-ERR-GENERIC-RETRY: For 500/503, show a generic retryable error while preserving criteria; qualifies as safe error-handling boilerplate; impacts Error Flows, Service Contracts.

---

## 16. Open Questions

1. **Dimensions scope (blocking):** Which dimensions must be supported for filtering in the initial release (e.g., Location, Department, Project, Cost Center)? Are multiple dimensions combinable (AND) and can each have multiple values (IN)?
2. **Access control model (blocking):** What is the exact authorization model for “sensitive accounts/dimensions”? Is restriction applied by GL account, account type, posting category, dimension value, business unit, or combinations? Should unauthorized data be filtered (200 with redaction) or hard-denied (403)?
3. **Period definition/selection (blocking):** How does the frontend select a “period”—by `periodId` (AccountingPeriod entity), by date range, or both? Must we support custom fiscal calendars (e.g., 4-4-5)?
4. **Backend service/API contracts (blocking):** What are the actual Moqui service names or REST endpoints, request/response schemas, and error codes for:
   - trial balance generation
   - ledger lines query
   - journal entry fetch
   - source event fetch
   - CSV export
5. **CSV format (blocking):** What is the exact required column order, naming, and numeric formatting (e.g., separate debit/credit columns vs signed balance)? Must totals appear as rows in the CSV?
6. **Source event rendering (blocking):** What fields must be displayed on the Source Event view? Is it a generic key/value display from a canonical envelope, or do we need type-specific renderers (Invoice, Repair Order, Inventory Move)?
7. **Currency handling (blocking):** Is trial balance always single-currency per business unit, or can it include multiple currencies? If multi-currency is possible, what is the display and export requirement?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] GL: Provide Trial Balance and Drilldown to Source  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/198  
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] GL: Provide Trial Balance and Drilldown to Source

**Domain**: general

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
GL: Provide Trial Balance and Drilldown to Source

## Acceptance Criteria
- [ ] Trial balance can be generated by period/account/dimensions
- [ ] Drilldown exists: balance → ledger lines → journal entry → source event
- [ ] Exports supported (CSV) for controller workflows
- [ ] Access controls enforced for sensitive accounts/dimensions


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