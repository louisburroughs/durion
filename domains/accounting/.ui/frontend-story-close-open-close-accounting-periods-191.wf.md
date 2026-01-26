# [FRONTEND] [STORY] Close: Open/Close Accounting Periods with Locks
## Purpose
Enable finance/admin users to manage accounting periods per business unit by creating periods and transitioning them between OPEN and CLOSED with appropriate locks and audit visibility. Provide clear, permission-gated Close/Reopen actions on the period detail screen, including mandatory reopen justification. Ensure users attempting postings in closed periods receive a deterministic, support-friendly error that references business unit/date when available.

## Components
- Global page header with breadcrumb/navigation context (List → Detail; List → Create → Detail)
- Accounting Period List
  - Business Unit selector (required)
  - Filters: date from/to, status (OPEN/CLOSED/ALL)
  - Table/list rows (clickable) with key columns (business unit, start/end, status)
  - Empty state panel with “Clear filters” and optional “Create Period”
  - “Create Period” CTA (permission-gated)
- Create Accounting Period form
  - Fields: Business Unit (required), Start Date (required), End Date (required)
  - Inline validation messages
  - Submit and Cancel buttons
  - Banner error area for server-side overlap/conflict
- Accounting Period Detail view
  - Summary panel: business unit, start/end (inclusive), status
  - Metadata panel: created by/at; closed by/at (if any); reopened by/at (if any)
  - Copyable identifiers: accountingPeriodId; audit event ids (if present); backend error code display where relevant
  - Action buttons: Close Period (permission + status gated), Reopen Period (permission + status gated)
  - Reopen modal/dialog with mandatory Reason textarea (>= 10 chars), Confirm/Cancel
  - Close confirmation modal/dialog (Confirm/Cancel)
  - Read-only Audit/History list (created/closed/reopened entries; includes reopen reason text)
- Deterministic “Posting blocked” error UI (used elsewhere in app)
  - Error banner/panel with message, contextual fields (business unit/date if provided), copyable error code

## Layout
- Top: Page title + breadcrumb; right-aligned primary CTA on list (“Create Period” if permitted)
- List screen: Filters row (BU selector required + date/status) above main table; empty state replaces table when no results
- Create screen: Single-column form (BU, start date, end date) with actions at bottom (Cancel left, Submit right)
- Detail screen: Two-column header area (left: summary + IDs; right: status badge + actions), below: metadata then audit/history list

## Interaction Flow
1. View periods list
   1) User navigates to Accounting Periods List.
   2) User must select a Business Unit (required) before results are shown/queried.
   3) User optionally sets date from/to and status filter (OPEN/CLOSED/ALL).
   4) System displays matching periods; if none, show empty state: “No periods found for selected business unit/date range.” with “Clear filters” and (if permitted) “Create Period”.
2. Open period detail
   1) User clicks a period row.
   2) System navigates to Detail screen and displays summary, metadata, copyable IDs, and audit/history (read-only).
   3) System shows Close button only if status=OPEN and user has close permission.
   4) System shows Reopen button only if status=CLOSED and user has reopen permission.
3. Create a new period
   1) From List, user clicks “Create Period” (visible only with create permission).
   2) User enters Business Unit, Start Date, End Date.
   3) Client-side validation blocks submit if any required field missing or if End Date < Start Date; show inline errors and do not send request.
   4) On submit, server validates non-overlap for the business unit.
   5) If overlap conflict returned, show banner error describing conflict clearly; keep form values for correction.
   6) On success, redirect to the new period Detail screen.
4. Close an open period (Scenario 4)
   1) On Detail with status OPEN, user clicks “Close Period”.
   2) Show confirmation modal; on confirm, call Close Period endpoint (include optimistic lock token if required by backend).
   3) On success, refresh detail state/history; status becomes CLOSED; display closedBy/closedAt if provided.
   4) Hide “Close Period” action after transition; show “Reopen Period” only if permitted.
5. Reopen a closed period with reason (Scenario 5)
   1) On Detail with status CLOSED, user clicks “Reopen Period”.
   2) Show modal with Reason textarea; reason is mandatory and must be >= 10 characters (client-side).
   3) If reason missing/too short, show inline error and do not send request.
   4) On confirm, call Reopen Period endpoint with reason (and optimistic lock token if required).
   5) On success, refresh detail state/history; status becomes OPEN; audit/history includes REOPENED entry with user/time and reason (if provided by backend).
6. Posting attempt blocked by closed period (Scenario 8)
   1) In a posting flow elsewhere, backend returns error code “ERR_ACCOUNTING_PERIOD_CLOSED”.
   2) UI shows deterministic error message indicating posting is blocked due to a CLOSED accounting period.
   3) If provided, display business unit and effective/transaction date in the error.
   4) Display copyable error code “ERR_ACCOUNTING_PERIOD_CLOSED” for support; advise next action: contact finance or request reopen (no reopen action from that screen unless already implemented).

## Notes
- Permissions/visibility: Create Period CTA requires create permission; Close requires close permission and status=OPEN; Reopen requires elevated reopen permission and status=CLOSED (Decision AD-013). Hide actions entirely when not permitted.
- Reopen justification: Reason is mandatory and minimum length >= 10 characters (client + server); display reason read-only in history.
- Data display requirements: Detail must show business unit, start/end inclusive, status, created metadata, closed metadata (nullable), reopened metadata (nullable), and copyable accountingPeriodId; audit event IDs copyable if present.
- Validation: Client-side blocks missing required fields and endDate < startDate; server authoritative for overlap and transition constraints; show server errors clearly without losing user input.
- Optimistic locking: If backend requires an optimistic lock token/version, UI must store and submit it on close/reopen and handle conflict errors (refresh + retry guidance).
- Audit/history: Read-only list should include created/closed/reopened entries; for reopened, show who/when/why.
- Deterministic error UX: Posting-blocked error must be consistent and support-friendly; include copyable backend error code and contextual BU/date when available.
- Risk/requirements gaps: Field names for create inputs and exact permission tokens/endpoints are not fully specified; implement with placeholders mapped to backend contract once finalized.
