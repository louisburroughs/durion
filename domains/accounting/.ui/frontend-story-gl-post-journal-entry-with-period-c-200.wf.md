# [FRONTEND] [STORY] GL: Post Journal Entry with Period Controls and Atomicity
## Purpose
Enable users to post an existing, read-only Journal Entry (JE) from its detail screen while enforcing accounting period eligibility and backend atomicity. The UI must clearly show JE status, totals, and period status (Open/Closed) and allow posting only when permitted and eligible. When posting fails (closed period, validation, conflict, or server error), the UI must present actionable feedback and avoid implying partial posting.

## Components
- Page header: “Journal Entry” + JE ID (canonical reference)
- Status badge (e.g., Draft/Ready/Posted; backend-authoritative)
- Read-only JE header fields: Posting Date, Description, Memo/Notes (optional), Source Reference (optional), Organization/Scope (if present)
- Period status panel: Open/Closed for posting date; optional period id/date range; fallback warning if unavailable
- Totals summary: Total Debits, Total Credits, Balanced indicator (only if backend provides)
- Lines table (read-only): Line #/ID, Account, Description (optional), Debit, Credit, Memo (optional)
- Posting references panel (read-only): journalEntryId (canonical), optional ledgerTransactionId (secondary)
- Posted metadata (visible only if Posted): Posted Timestamp, Posted By
- Informational banner (visible only if Posted): “Posted entries cannot be modified; corrections require a reversing entry.”
- Primary action button: “Post Journal Entry” (gated/disabled/hidden per rules)
- Inline reason text when Post is disabled (e.g., “Posting disabled: period is closed”)
- Confirmation dialog/modal:
  - Summary: JE ID, Posting Date, Period Status, Balanced status (if provided), Source Reference (if present)
  - Optional input: Justification (only if backend explicitly requires)
  - Actions: Confirm Post, Cancel
- Error/alert components:
  - Blocking error banner/toast for closed period rejection (no override)
  - Validation error list (400/422) with field/line references when available
  - Conflict prompt (409) with “Refresh” action
  - Generic failure message for 5xx/timeout with “Retry” option

## Layout
- Top: Page header (JE ID) + status badge; right-aligned primary action area (Post button)
- Main (stacked sections):
  - JE Header (read-only fields) | Period Status panel (adjacent or directly below header)
  - Totals summary row (Debits, Credits, optional Balanced)
  - Lines table (full width)
  - References panel (posting references + source linkage, read-only)
  - Posted metadata + immutable banner (only when status = Posted)

## Interaction Flow
1. On page load:
   1) Call JE detail service by journalEntryId; render header, lines, totals, status, and any optimistic lock token (etag/version) if provided.
   2) If period status is embedded in JE response, render Open/Closed (and optional period details).
   3) If not embedded, attempt period-status endpoint using postingDate (and required scope identifiers if needed); if unavailable/error, show warning: “Period status unavailable; posting will be validated on submit.”
2. Determine Post button availability (evaluate continuously after data load):
   1) If user lacks explicit posting permission/capability flag, hide or disable Post with reason “You do not have permission to post.”
   2) If JE status is Posted, hide/disable Post; show posted metadata and immutable banner.
   3) If period status is available and Closed, disable Post and show reason “Posting disabled: accounting period is closed.” (no override path).
   4) If backend provides “postingAllowed” (or equivalent) and it is false, disable Post and show backend-provided reason if available; otherwise rely on backend validation at submit.
3. Primary flow: Successful posting (Open period, valid, permitted):
   1) User clicks “Post Journal Entry.”
   2) Show confirmation dialog summarizing JE ID, posting date, period status, balanced status (if provided), and source reference (if present).
   3) User clicks “Confirm Post.”
   4) Call Post Journal Entry endpoint with journalEntryId and optimistic lock token (etag/version) if available.
   5) On success: refresh JE detail (or update from response) to show status = Posted; display posted timestamp/user if returned; disable/hide Post action; display posting references (journalEntryId canonical; optional ledgerTransactionId secondary).
4. Edge case: Closed period rejection (Decision AD-012):
   1) If user attempts post and backend rejects with period-closed errorCode, show blocking error message including backend reason/code.
   2) Refresh JE detail to confirm it remains not Posted; keep Post disabled if period status is known Closed; do not offer override or redirect.
5. Edge case: Validation errors (400/422):
   1) Display actionable error list (e.g., unbalanced, missing lines, invalid/disabled account), mapping to header/lines when possible.
   2) Keep JE in pre-post status; do not show Posted; allow user to retry after underlying data is corrected elsewhere (editing is out of scope).
6. Edge case: Concurrency/conflict (409):
   1) Show message “This journal entry has changed or was already posted. Please refresh.”
   2) Provide “Refresh” action; on refresh, render latest status and disable/hide Post if now Posted.
7. Edge case: Forbidden (403):
   1) Show permission error; ensure Post remains hidden/disabled; do not retry automatically.
8. Edge case: 5xx/timeout (atomicity expectation):
   1) Show generic failure and allow retry.
   2) Do not assume partial posting; on manual refresh, JE should still show not Posted unless backend confirms Posted.

## Notes
- Read-only scope: creating/editing JEs and lines, period management, account setup, and reversal workflows are out of scope.
- Period control: If period status is known Closed, posting must be blocked in UI; no “override closed period” option is allowed (Decision AD-012).
- Atomicity: On any non-success response (including timeout), UI must not display Posted unless confirmed by backend; encourage refresh to verify state.
- Posting references: journalEntryId remains the canonical reference; show optional ledgerTransactionId as secondary if returned (Decision AD-011).
- Optimistic locking: Include etag/version token in post request when provided; handle 409 with refresh prompt.
- Justification field: Do not add by default; include only if backend explicitly requires it for posting.
- Permission gating: UI must gate by an explicit permission token or backend-provided capability flag; do not infer permission from role names.
- If period-status endpoint is unavailable, show warning and rely on backend validation during posting.
