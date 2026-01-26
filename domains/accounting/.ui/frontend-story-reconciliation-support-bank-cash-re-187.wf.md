# [FRONTEND] [STORY] Reconciliation: Support Bank/Cash Reconciliation Matching

## Purpose
Enable accountants to match imported bank/cash statement lines to system transactions within a reconciliation, optionally creating adjustments when needed. The UI must enforce backend-supported matching cardinality (many statement lines to one system transaction; no one-to-many; no partial matching) and reflect backend-authoritative statuses and computed balances. Users can finalize a reconciliation only when backend indicates it is eligible, after which all mutation actions are disabled and reporting becomes available.

## Components
- Page header: Reconciliation title + status badge (DRAFT/IN_PROGRESS vs FINALIZED)
- Reconciliation summary panel (read-only fields): name/number, account, date range, ending balances, GL ending balance (calculated), difference, counts (matched/unmatched), last updated, updated by
- Primary actions toolbar:
  - Import Statement (disabled when FINALIZED)
  - Finalize (enabled only when DRAFT + permission + backend-provided canFinalize)
  - View/Download Report (enabled only when FINALIZED)
- Statement Lines list/table:
  - Columns: date, description, reference, amount, status (UNMATCHED/MATCHED), matched set indicator
  - Row selection (multi-select) + filters (all/unmatched/matched)
  - Row action: Unmatch (enabled when selection is matched + permissions + reconciliation is DRAFT)
- System Transactions list/table (“Unreconciled”):
  - Columns: id/ref, date, amount, counterparty/memo (optional), type/status (read-only)
  - Single-select (to support many-to-one matching)
  - Search/filter (date range within reconciliation, amount, text)
- Match action button (contextual; enabled only when rules satisfied)
- Adjustment modal/form (shown after successful match when adjustment is required/desired):
  - Adjustment Type (optional if backend provides enum/list; otherwise omit)
  - Description (required)
  - Amount (required; default to statement line amount)
  - Reference/Reason (optional or required per backend)
  - Posting account/context (pre-filled, read-only if provided)
  - Submit / Cancel buttons
- Adjustments list/table:
  - Columns: type (if available), description, amount, posting reference link (if provided), created at/by
  - Delete adjustment (disabled when FINALIZED; enabled with permission)
- Audit / Activity panel:
  - Events: started/imported/matched/unmatched/adjusted/finalized with user + timestamp
- Inline alerts/toasts:
  - Success/failure messages
  - Concurrency conflict banner with reload behavior

## Layout
- Top: Page header + status badge + primary actions toolbar
- Main (two-column): Left = Statement Lines table; Right = System Transactions (Unreconciled) table
- Below main: Adjustments list (full width) + Audit/Activity panel (full width or right-side drawer)
- Inline ASCII hint: [Header+Actions] / [Statement Lines | System Transactions] / [Adjustments] / [Audit]

## Interaction Flow
1. Load reconciliation detail page.
   1. Fetch reconciliation header + computed fields (difference/balances/counts) and lists (statement lines, system transactions, adjustments, audit).
   2. Render status-driven UI: if FINALIZED, disable all mutation actions and enable report actions.
2. Match statement lines to a system transaction (many-to-one).
   1. User selects one or more statement lines (must be eligible/unmatched).
   2. User selects exactly one system transaction from the unreconciled list.
   3. UI validates enablement: reconciliation is DRAFT, user has match permission, selection meets backend cardinality rules (prevent one-to-many), and amounts must fully match (no partials).
   4. User clicks Match; UI submits match command to backend.
   5. On success: update statement line(s) status to MATCHED (backend-authoritative), mark system transaction matched/cleared for this reconciliation (removed from unreconciled list), refresh reconciliation header computed fields.
   6. UI presents Adjustment modal/form (only if required by UX/back-end flow); user completes and submits.
   7. On adjustment submit success: backend creates adjustment and returns posting reference(s); adjustment appears in Adjustments list with link (if provided); header fields refresh again.
3. Unmatch previously matched items.
   1. User selects matched statement line(s) (or matched set) and clicks Unmatch.
   2. UI submits unmatch command; on success: statement lines revert to UNMATCHED, system transaction returns to unreconciled list, header computed fields refresh.
4. Finalize reconciliation.
   1. Finalize button is enabled only when reconciliation is DRAFT, user has finalize permission, and backend-provided canFinalize is true (do not compute locally).
   2. User clicks Finalize; UI submits finalize request.
   3. On success: status becomes FINALIZED; disable import/add line/match/unmatch/create/delete adjustment; enable report view/download; audit shows finalization event with user + timestamp.
5. Concurrency conflict handling (match/unmatch/adjustment/finalize).
   1. If backend returns optimistic lock/stale status error: UI reloads reconciliation detail and all lists.
   2. Show message: “This reconciliation was updated by another user.” and reflect latest backend state.

## Notes
- Matching constraints: allow N statement lines → 1 system transaction; disallow 1 system transaction → N system transactions; no partial matching or tolerance—amounts must match exactly (signed amounts: negative=debit, positive=credit).
- Status naming: issue references DRAFT; backend may use IN_PROGRESS—UI should map backend status to displayed labels consistently.
- Finalize eligibility must come from backend (e.g., difference must be 0.00); do not compute finalize readiness locally.
- Adjustment types: if backend provides an enum/list (e.g., BANK_FEE, NSF_FEE, INTEREST_EARNED, FLOAT_ADJUSTMENT, OTHER), render a dropdown; otherwise omit type field and rely on description.
- Adjustment fields: Description and Amount required; Amount defaults to statement line amount; reference/reason requiredness is backend-driven; posting reference link shown when provided (AD-011 pattern).
- After any mutation (match/unmatch/adjustment/delete/finalize), refresh reconciliation computed fields and affected lists from backend to avoid drift.
- Permissions gate UI actions: view, create/import, match, adjust, finalize, report; hide or disable controls accordingly.
- Reporting: enable View/Download Report only when FINALIZED; report delivered via backend endpoint.
- Audit trail must display key events (import, match/unmatch, adjustment create/delete, finalize) with user and timestamp.
