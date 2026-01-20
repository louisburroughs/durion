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

**Title:** [FRONTEND] Reporting: Produce Core Financial Statements (P&L, Balance Sheet) with Drilldown and Export

**Primary Persona:** Financial Controller / Accountant

**Business Value:** Enable finance users to reliably generate reproducible financial statements from posted ledger data, investigate balances via drilldown to source, and export reports for review/audit.

---

## 2. Story Intent

### As a / I want / So that
**As a** Financial Controller or Accountant,  
**I want** to generate a Profit & Loss and Balance Sheet for a selected period/as-of date with drilldowns to underlying accounts, journal lines, and source events,  
**So that** I can analyze financial performance/position, support period close activities, and satisfy audit/review needs.

### In-scope
- UI flows to:
  - Generate **P&L** for a date range
  - Generate **Balance Sheet** as-of an end date
  - Drilldown path: **statement line → contributing accounts → journal/ledger lines → source event reference**
  - Export generated statement output (format(s) TBD) with access control enforcement
- Deterministic/reproducible report results for identical parameters (frontend must not introduce nondeterminism; rely on backend reproducibility)
- Handling empty states, validation errors, unauthorized access, and service failures

### Out-of-scope
- Defining or editing Chart of Accounts, statement definitions, posting rules, or accounting periods
- Performing postings, closing periods, or making adjustments
- Multi-entity consolidation and multi-currency presentation (unless backend explicitly supports it; TBD)
- Custom report builder / ad-hoc reporting beyond basic P&L and Balance Sheet

---

## 3. Actors & Stakeholders

- **Financial Controller / Accountant (Primary):** Runs reports, drills into balances, exports for review.
- **Auditor (Indirect):** Consumes exports and drilldown evidence; needs traceability to source events.
- **System Administrator / Security Admin:** Configures permissions for viewing and exporting reports.
- **Support/Operations:** Needs logs/trace IDs to troubleshoot report generation failures or latency.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- Posted ledger/journal data exists (or UI must show “no data” state).
- Backend provides endpoints/services to:
  - Retrieve statement results for given parameters
  - Retrieve drilldown levels (line→accounts→journal lines→source event)
  - Export statement output (or provide export-ready payload)

### Dependencies (blocking if absent)
- **Backend reporting contract** for statement + drilldown is not defined in provided inputs (must be clarified).
- **Authorization model/permissions** for:
  - Viewing statements
  - Drilling down
  - Exporting  
  is not defined in provided inputs (must be clarified).
- **Statement structure/mapping** (how accounts map to statement lines) is not defined here; must be backend-defined or configured elsewhere (clarify).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: `Accounting → Reports → Financial Statements`
- Direct routes (proposed, final routes must align with repo conventions; see Open Questions):
  - `/accounting/reports/financial-statements`
  - Optional deep links with query params for reproducibility:
    - `?reportType=PL&from=YYYY-MM-DD&thru=YYYY-MM-DD`
    - `?reportType=BS&asOf=YYYY-MM-DD`

### Screens to create/modify (Moqui)
1. **Screen:** `apps/pos/screen/accounting/reports/FinancialStatements.xml` (container)
2. **Subscreens (or embedded sections):**
   - `ProfitLoss.xml` (parameter form + results)
   - `BalanceSheet.xml` (parameter form + results)
   - `StatementDrilldown.xml` (reusable drilldown panel/modal or nested screen)
3. **Shared components/widgets:**
   - Parameter form (date pickers, business unit selector if applicable)
   - Results table/tree renderer (statement lines)
   - Breadcrumb drilldown navigation component

### Navigation context
- Breadcrumb: `Accounting > Reports > Financial Statements > {P&L|Balance Sheet}`
- Results maintain state in URL query params to support refresh/reload and reproducibility.

### User workflows
#### Happy path: Generate P&L
1. User selects **Profit & Loss** tab.
2. User enters **From date** and **Thru date**.
3. User clicks **Run Report**.
4. UI loads statement results and displays lines with totals.
5. User clicks a statement line to drill down.

#### Happy path: Drilldown
1. Click statement line → UI loads contributing accounts list.
2. Click an account → UI loads posted journal/ledger lines for that account within parameter context.
3. Click a journal line → UI shows source event reference details (minimum: event id/type + link if available).

#### Happy path: Export
1. User clicks **Export** from report view.
2. UI prompts for format (if multiple) and triggers export download.

#### Alternate path: No data
- UI shows explicit “No data available for selected period” with guidance to adjust filters.

---

## 6. Functional Behavior

### Triggers
- Screen load:
  - If URL contains valid query params, auto-run report (optional; see safe defaults applied).
- User action:
  - Run Report
  - Click statement line
  - Click account row
  - Click journal line
  - Export

### UI actions
- Parameter input:
  - P&L: `fromDate`, `thruDate` (required)
  - Balance Sheet: `asOfDate` (required)
  - Optional filters (only if backend supports; otherwise omit): businessUnitId, accountingPeriodId
- Results:
  - Render statement lines with:
    - line name/label
    - amount (currency formatted)
    - indicator if drilldown available
- Drilldown:
  - Use nested panel or modal with breadcrumb:
    - `Statement Line` → `Accounts` → `Journal Lines` → `Source Event`
- Export:
  - Export button enabled only when a report result is present and user has export permission.

### State changes (frontend)
- Local UI state:
  - `idle | loading | loaded | error`
  - Drilldown state with breadcrumb selection and loaded datasets
- No domain state mutation in frontend (read/reporting only).

### Service interactions
- Run report → call backend to compute/fetch statement for parameters.
- Drilldown level calls:
  - statementLineId (or lineKey) → accounts
  - accountId → journal lines
  - journalLineId/sourceRef → source event details (or external link)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- P&L:
  - `fromDate` required
  - `thruDate` required
  - `fromDate <= thruDate` else show inline validation error and prevent call
- Balance Sheet:
  - `asOfDate` required
- If backend returns validation errors, surface them at form level and on relevant fields when possible.

### Enable/disable rules
- **Run Report** disabled until required parameters valid.
- **Export** disabled when:
  - no report currently loaded
  - user lacks export permission
  - report is loading

### Visibility rules
- Drilldown controls visible only when report loaded.
- For statement lines with no children (backend indicates `drilldownAvailable=false` or returns empty), clicking shows “No underlying accounts” message and does not error.

### Error messaging expectations
- Unauthorized (401/403): show “Access denied” page or inline blocking banner; do not display financial data remnants.
- No data: show friendly empty state (not an error).
- Backend error: show non-technical message + trace/request id if provided.

---

## 8. Data Requirements

### Entities involved (conceptual; frontend reads via services)
- `GeneralLedgerEntry` / `LedgerEntry` (posted lines)
- `JournalEntry` / `JournalLine`
- `GLAccount` (Chart of Accounts)
- `FinancialStatementDefinition` (line structure and mapping; may be implicit in backend)
- `AccountingPeriod` (only if used for filtering or reproducibility metadata)

### Fields (UI-facing; exact names TBD by backend contract)
#### Statement run parameters
- `reportType`: enum `{PL, BS}` (required)
- `fromDate`: date (required for PL)
- `thruDate`: date (required for PL)
- `asOfDate`: date (required for BS)
- `businessUnitId`: id (optional; **cannot assume** without clarification)
- `currencyUomId`: id/code (display-only; **cannot assume**)
- `includeZeroLines`: boolean (optional; UI can default off if backend supports)

#### Statement result
- `reportId` or `resultHash` (string; used for export and reproducibility) (TBD)
- `generatedAt` (datetime, display)
- `parameters` (echo of inputs)
- `lines[]` each:
  - `lineKey` (string, stable identifier for drilldown)
  - `label` (string)
  - `amount` (decimal)
  - `accountType` or section grouping (optional)
  - `drilldownAvailable` (boolean)

#### Drilldown: accounts
- `accounts[]` each:
  - `glAccountId`
  - `accountCode`
  - `accountName`
  - `amount`

#### Drilldown: journal/ledger lines
- `entries[]` each:
  - `postedDate` / `transactionDate`
  - `journalEntryId` / `ledgerEntryId`
  - `description`
  - `debitAmount` / `creditAmount` or signed `amount`
  - `sourceEventId` (or `sourceEntityRef`)
  - `sourceEventType` (if available)

#### Source event reference (minimum)
- `eventId`
- `eventType`
- `occurredAt`
- `sourceModule`
- `sourceEntityRef` (string)
- Link behavior:
  - If backend provides a URL, render as “View source” external/internal link.
  - If not, display identifiers only.

### Read-only vs editable
- All fields are read-only except report parameters.

### Derived/calculated fields (frontend)
- Currency formatting only; no financial calculations beyond displaying backend-provided totals.
- Breadcrumb labels derived from selected items.

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking:** actual Moqui service names, parameters, and response schemas are not provided. Below are required contracts; implementation must align with backend.

### Load/view calls
1. **Run statement**
   - Service (proposed): `accounting.reporting.FinancialStatementService.run`
   - Inputs:
     - `reportType`, date params
     - optional filters (businessUnitId, etc. if supported)
   - Returns:
     - `statementResult` (see Data Requirements)

2. **Fetch drilldown accounts for statement line**
   - Service (proposed): `accounting.reporting.FinancialStatementService.getLineAccounts`
   - Inputs: `reportType`, same parameters, `lineKey`
   - Returns: `accounts[]`

3. **Fetch journal/ledger lines for account**
   - Service (proposed): `accounting.reporting.FinancialStatementService.getAccountEntries`
   - Inputs: `reportType`, parameters, `glAccountId`
   - Returns: `entries[]`

4. **Fetch source event details**
   - Service (proposed): `accounting.event.EventService.getEvent`
   - Inputs: `eventId` (or `sourceEntityRef`)
   - Returns: `event` summary

### Create/update calls
- None (reporting is read-only)

### Submit/transition calls
- Export request:
  - Service (proposed): `accounting.reporting.FinancialStatementService.export`
  - Inputs:
    - `reportType`, parameters or `reportId/resultHash`
    - `format` (TBD)
  - Returns:
    - File download response or `downloadUrl`

### Error handling expectations
- 400 validation: map field errors to inputs; show message.
- 401/403: redirect to access denied screen; clear sensitive state.
- 409 conflict (if reproducibility token expired): show “Report changed; rerun report” (only if backend uses tokens).
- 500/503: show retry option; preserve parameters.

---

## 10. State Model & Transitions

### Allowed states (UI)
- `idle` (no result yet)
- `loading` (running report or drilldown fetch)
- `loaded` (result displayed)
- `error` (error banner + retry)

### Role-based transitions
- If user lacks `report.view` permission (name TBD), they cannot enter `loaded` state; access denied screen shown.
- Export requires `report.export` permission (name TBD); otherwise export action hidden or disabled with tooltip.

### UI behavior per state
- `idle`: show parameter form, no results.
- `loading`: disable actions; show progress indicator.
- `loaded`: show results + export + drilldown affordances.
- `error`: show error summary + retry; keep parameters intact.

---

## 11. Alternate / Error Flows

### Validation failures
- From > thru (P&L): block run; show inline message.
- Missing dates: block run; required field indicators.

### Concurrency conflicts / reproducibility
- If backend indicates statement definitions changed or data changed:
  - If backend guarantees reproducibility for closed periods only, UI must display a warning banner for open periods: “Results may change until period is closed.” (**Requires clarification**)
  - If backend returns a `resultHash`/`asOfLedgerVersion`, UI displays it and reuses it for export (if supported).

### Unauthorized access
- Navigating directly to route without permission results in Access Denied screen.
- If permissions revoked mid-session and a call returns 403, UI clears current results and shows Access Denied.

### Empty states
- No posted lines for parameters: show “No data available for selected period/as-of date.”
- Drilldown returns empty list: show “No underlying entries for this selection.”

### Service unavailable
- Show retry; log requestId/traceId if provided.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Generate Profit & Loss for a date range
**Given** the user has permission to view financial statements  
**And** posted ledger/journal lines exist for the selected date range  
**When** the user selects “Profit & Loss” and enters a valid From date and Thru date  
**And** clicks “Run Report”  
**Then** the system displays a P&L statement generated from posted ledger lines only  
**And** each statement line shows an amount formatted in the reporting currency  
**And** the results remain visible on refresh when the same parameters are present in the URL (if implemented)

### Scenario 2: Generate Balance Sheet as of a date
**Given** the user has permission to view financial statements  
**And** posted ledger/journal lines exist up to the selected as-of date  
**When** the user selects “Balance Sheet” and enters an as-of date  
**And** clicks “Run Report”  
**Then** the system displays a Balance Sheet as of that date generated from posted ledger lines only

### Scenario 3: Validate invalid date range for P&L
**Given** the user is on the Profit & Loss report screen  
**When** the user enters a From date after the Thru date  
**And** attempts to run the report  
**Then** the UI prevents the request from being sent  
**And** displays an inline validation message indicating the date range is invalid

### Scenario 4: Drilldown from statement line to accounts
**Given** a financial statement is loaded  
**And** at least one statement line is drilldown-enabled  
**When** the user clicks a drilldown-enabled statement line  
**Then** the system displays the contributing GL accounts and their amounts for that same report context  
**And** the user can navigate back to the statement lines without losing the report parameters

### Scenario 5: Drilldown from account to journal/ledger lines
**Given** the user is viewing the contributing accounts for a statement line  
**When** the user selects a specific GL account  
**Then** the system displays the posted journal/ledger lines contributing to that account balance for the report context  
**And** each line includes a reference to its source event (at least an identifier)

### Scenario 6: Drilldown to source event reference
**Given** the user is viewing journal/ledger lines for a GL account  
**When** the user selects a journal/ledger line with a source event reference  
**Then** the system displays source event identifiers (event id and type at minimum)  
**And** if a navigable link is provided by the backend, the UI provides a “View source” link

### Scenario 7: Export a generated statement
**Given** the user has permission to export financial statements  
**And** a financial statement is loaded  
**When** the user clicks “Export” and selects an available export format  
**Then** the system downloads or provides a download link for the exported report  
**And** the exported content corresponds to the on-screen report parameters

### Scenario 8: Unauthorized user cannot access financial statements
**Given** the user does not have permission to view financial statements  
**When** the user navigates to the Financial Statements route  
**Then** the system shows an Access Denied screen  
**And** no financial statement data is displayed

### Scenario 9: No data for selected period
**Given** the user has permission to view financial statements  
**And** there are no posted ledger/journal lines for the selected parameters  
**When** the user runs the report  
**Then** the system shows a “No data available for the selected period/as-of date” empty state  
**And** does not show an error

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided by backend) on the report results:
  - `generatedAt`
  - `report parameters`
  - `resultHash/reportId` (for reproducibility and support)

### Status history
- Not applicable (no stateful entity transitions in UI). If backend returns report run history, it is out-of-scope.

### Traceability expectations
- Each run/drilldown/export call should:
  - include correlation/request id headers if platform supports it
  - log (frontend console/log pipeline) minimal identifiers:
    - reportType, dates, businessUnitId (if used), and backend requestId/traceId
  - avoid logging financial amounts in client logs unless workspace standard allows (not specified; default to avoid).

---

## 14. Non-Functional UI Requirements

- **Performance:** Initial statement results should render progressively; drilldown calls should be lazy-loaded on click. Target: first results visible within 3 seconds for typical periods (exact SLA TBD).
- **Accessibility:** Keyboard navigable drilldown (tab/enter), proper table semantics, ARIA labels for expandable/drilldown controls.
- **Responsiveness:** Usable on tablet width; tables allow horizontal scroll when needed.
- **i18n/timezone/currency:**
  - Dates displayed in user locale; query params use ISO `YYYY-MM-DD`.
  - Currency formatting uses backend-provided currency code/uom when available; otherwise display as plain decimal with warning (**requires clarification**).

---

## 15. Applied Safe Defaults

- **SD-UI-EMPTY-STATE**
  - **Assumed:** Provide explicit empty states (“No data available…”) for no-results at statement and drilldown levels.
  - **Why safe:** UI-only ergonomics; does not affect accounting meaning.
  - **Impacted sections:** UX Summary, Alternate / Error Flows, Acceptance Criteria.
- **SD-UI-URL-PARAM-STATE**
  - **Assumed:** Persist report parameters in URL query params to support refresh/back and reproducibility UX.
  - **Why safe:** Navigation ergonomics only; does not change backend calculations or policies.
  - **Impacted sections:** UX Summary, Functional Behavior, Acceptance Criteria.
- **SD-UI-RETRY-ON-TRANSIENT**
  - **Assumed:** Provide a retry button for transient backend failures (5xx/503) without auto-retrying financial queries.
  - **Why safe:** Conservative error-handling; avoids unintended repeated queries.
  - **Impacted sections:** Alternate / Error Flows, Non-Functional UI Requirements.

---

## 16. Open Questions

1. **Routes / IA:** What are the canonical Moqui screen paths and menu locations in `durion-moqui-frontend` for accounting reports (exact route/screen naming conventions)?
2. **Backend service/API contracts:** What are the exact endpoints/services, request params, and response schemas for:
   - running P&L / Balance Sheet
   - drilldown (line→accounts, accounts→entries, entry→source event)
   - export (and whether it uses `reportId/resultHash` vs rerun-by-params)?
3. **Permissions:** What are the exact permission names/scopes for:
   - viewing financial statements
   - drilling down
   - exporting  
   Are these distinct permissions or one?
4. **Statement definition & mapping:** Is the statement line structure:
   - fixed and backend-defined, or
   - configurable per business unit / CoA, or
   - user-selectable “statement definition” parameter?
5. **Export formats:** Which formats are required for MVP (CSV, PDF, XLSX)? Any formatting standards (e.g., include headers, subtotals, sign conventions)?
6. **Reproducibility rules:** Are reports reproducible:
   - always (via ledger versioning/snapshots), or
   - only for closed periods?  
   If only closed periods, what UI indicator should be shown for open periods?
7. **Currency & multi-entity:** Is this strictly single business unit + single currency for this story, or should UI include businessUnit and/or currency selectors?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Reporting: Produce Core Financial Statements with Drilldown  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/189  
Labels: frontend, story-implementation, reporting

## Frontend Implementation for Story

**Original Story**: [STORY] Reporting: Produce Core Financial Statements with Drilldown

**Domain**: reporting

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
Reporting: Produce Core Financial Statements with Drilldown

## Acceptance Criteria
- [ ] Produce P&L and Balance Sheet (basic) from posted ledger lines
- [ ] Drilldown: statement line → accounts → journal lines → source events
- [ ] Reports are reproducible for the same parameters
- [ ] Exports supported and access controls enforced


### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x