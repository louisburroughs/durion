# [FRONTEND] [STORY] Reporting: Produce Core Financial Statements with Drilldown
## Purpose
Enable users to generate core financial statements (Profit & Loss for a date range, Balance Sheet as-of a date) from posted ledger/journal lines with deterministic, reproducible results. Provide a guided drilldown path from statement lines to contributing accounts, then to journal/ledger lines, and finally to a source event reference. Support export of generated statements with permission enforcement, and handle empty states, validation errors, unauthorized access, and service failures.

## Components
- Page header: “Reporting” + statement type selector (P&L / Balance Sheet)
- Parameter form
  - `statementType` (enum, required)
  - P&L: `fromDate` (date, required), `thruDate` (date, required)
  - Balance Sheet: `asOfDate` (date, required)
  - Optional filters (only if backend supports): `businessUnitId` (string/UUID), `legalEntityId` (string/UUID), `includeUnposted` (boolean)
  - Primary button: Run Report
  - Inline validation messages (required/invalid ranges)
- Results area (statement renderer)
  - Statement lines table/tree: line label, amount (currency formatted), drilldown indicator
  - Report audit/metadata strip (if provided): `reportRunId` or `reportContextId` (copyable), `currency` (display), `generatedAt` (display)
- Drilldown panel (nested panel or modal) with breadcrumb navigation
  - Breadcrumb: Statement → Accounts → Journal Lines → Source Event
  - Accounts list/table: account label/number + amount
  - Journal/ledger lines table:
    - `postingDate` and/or `effectiveDate`
    - `journalLineId` (UUIDv7; preferred navigation reference when available)
    - `ledgerLineId` (UUIDv7; optional)
    - `description` (string)
    - `debitAmount` / `creditAmount` OR signed `amount`
    - `sourceEventId` (UUIDv7; optional)
    - `reference` / `externalRef` (string; optional)
    - `counterpartyName` (string; optional)
  - Source event details panel:
    - `sourceEventId` (UUIDv7), `sourceEventType` (string)
    - `createdAt` / `postedAt` (datetime; optional)
    - `summary` (string; optional)
    - `payload` (object/string; optional; redacted)
    - Permission-gated sensitive field (per backend permission, e.g., AD-009)
- Export control
  - Export button (optionally format selector if multiple)
  - Disabled states per rules (no result, loading, no permission)
- Global banners/toasts
  - Error banner with Retry
  - Unauthorized/access denied banner
  - Conflict/stale context banner (“Report context is no longer valid. Please rerun the report.”)
- Loading indicators
  - Report loading skeleton/spinner
  - Drilldown loading state

## Layout
- Top: Header + statement type selector
- Main (stacked): Parameter form → action row (Run Report, Export) → results renderer
- Right or overlay: Drilldown panel/modal with breadcrumb and step content
- Inline banners above results; empty state centered in results area

## Interaction Flow
1. Initial state (`idle`): show parameter form; results area shows “No result yet” placeholder; Export disabled.
2. User selects `statementType`.
3. If `statementType = P&L`: user enters `fromDate` and `thruDate`; validate required fields and date order; show inline errors if invalid.
4. If `statementType = Balance Sheet`: user enters `asOfDate`; validate required field.
5. User clicks Run Report:
   1. Set state to `loading`; disable Run Report, Export, and drilldown interactions.
   2. Persist parameters to URL query params (e.g., `statementType`, `fromDate`, `thruDate`, `asOfDate`, plus optional filters if present).
   3. On success: render statement lines with amounts formatted using backend-provided `currency`; show audit/metadata if returned; set state to `success`; enable Export only if user has export permission.
   4. On empty result: show “No data available for selected period/as-of date.”; keep Export disabled.
   5. On unauthorized (401/403): show access denied banner; keep results cleared; Export disabled.
   6. On service failure: show error banner + Retry; keep prior successful result visible if applicable, otherwise show empty placeholder.
   7. On 409 conflict or stale context errorCode: show banner “Report context is no longer valid. Please rerun the report.” and provide a Rerun action that re-executes with current URL params.
6. Drilldown from statement line to accounts:
   1. User clicks a drilldown-enabled statement line (disabled while report is loading or when line indicates no drilldown / backend returns empty).
   2. Open drilldown panel/modal at “Accounts” step; breadcrumb shows Statement → Accounts.
   3. Fetch and display contributing accounts for the same report context; if empty, show “No underlying entries for this selection.”
   4. User can navigate back to Statement without losing report parameters/results.
7. Drilldown from account to journal/ledger lines:
   1. User clicks an account row; breadcrumb updates to Statement → Accounts → Journal Lines.
   2. Fetch and display journal/ledger lines; show loading state; handle empty list message.
8. Drilldown from journal line to source event reference:
   1. User clicks a journal/ledger line; breadcrumb updates to Statement → Accounts → Journal Lines → Source Event.
   2. Display source event reference fields if supported; redact/gate sensitive payload fields based on permission.
9. Export:
   1. Export button enabled only when a report result is present, user has export permission, and report is not loading.
   2. On click: initiate export for the current report context/parameters; show progress; handle failure with banner/toast and allow retry.

## Notes
- Determinism/reproducibility: frontend must not introduce nondeterminism; rely on backend reproducibility for identical parameters; preserve parameters in URL query params for refresh/back behavior (SD-UI-URL-PARAM-STATE).
- Access control: enforce export permission; enforce permission gating for sensitive source event fields (per backend policy such as AD-009); handle unauthorized responses for report and drilldown endpoints.
- Drilldown path must follow: statement line → contributing accounts → journal lines → source event reference, with breadcrumb navigation and back behavior that preserves report context.
- Disabled rules:
  - Export disabled when no report loaded, user lacks permission, or report is loading.
  - Drilldown disabled when report is loading or line is not drilldown-capable / backend returns empty.
- Empty states:
  - No posted lines for parameters: “No data available for selected period/as-of date.”
  - Drilldown empty: “No underlying entries for this selection.”
- Error handling: show error banner + Retry for service failures; show stale context banner for 409/conflict with explicit rerun action.
- Optional filters (`businessUnitId`, `legalEntityId`, `includeUnposted`) must be omitted from UI unless backend explicitly supports them.
- UI-facing field names must match backend exactly (e.g., `statementType`, `fromDate`, `thruDate`, `asOfDate`, and drilldown fields like `journalLineId`, `ledgerLineId`, `sourceEventId`).
