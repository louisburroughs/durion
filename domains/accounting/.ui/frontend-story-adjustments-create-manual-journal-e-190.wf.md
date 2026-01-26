# [FRONTEND] [STORY] Adjustments: Create Manual Journal Entry with Controls
## Purpose
Enable authorized users to create and post balanced manual journal entries with required reason codes and period controls. The UI must prevent submission when configuration is missing or entries are invalid, and must surface backend validation (e.g., closed period) without losing user input. Once posted, the journal entry becomes read-only to satisfy audit and immutability requirements.

## Components
- Page header: “Create Manual Journal Entry”
- Permission/authorization gate (inline error/empty state)
- Manual JE form
  - Posting date picker (with period control validation feedback)
  - Description text field
  - Reason code dropdown (ManualJEReasonCode enum)
  - Currency display/selector (default USD; single-currency per JE)
- Journal lines table (minimum 2 lines)
  - GL account search/autocomplete per line
  - Debit amount input (BigDecimal 19,4)
  - Credit amount input (BigDecimal 19,4)
  - Line description/memo (optional)
  - Add line / remove line controls
- Totals summary (Total Debits, Total Credits, Difference)
- Inline validation messages (per-field and form-level)
- Primary action button: “Post Journal Entry” (disabled when invalid)
- Secondary actions: “Save Draft” (if supported), “Cancel”
- Toast/alert area for server errors (user-safe messaging)
- Loading state on submit (button spinner/disabled)
- Redirect target: Journal Entry View screen (read-only state)

## Layout
- Top: Page header + brief helper text (requirements: balanced, period open, reason code required)
- Main: Form section (Posting Date | Reason Code | Currency | Description)
- Main: Lines table below form with Add/Remove line controls and per-line inputs
- Right or bottom: Totals summary panel + Difference indicator
- Bottom sticky footer: [Cancel] [Save Draft] [Post Journal Entry]

## Interaction Flow
1. User navigates to “Create Manual Journal Entry”; system checks authentication and permission to create/post.
2. If no manual adjustment reason codes are configured:
   1. Show empty state message: “No manual adjustment reason codes configured; contact administrator.”
   2. Disable “Post Journal Entry” (and any submit actions).
3. User enters Posting Date, Description, and selects a Reason Code.
4. User adds journal lines (at least two):
   1. For each line, user searches/selects a GL account.
   2. If GL account search returns no results, show inline message: “No accounts match your search.”
   3. User enters exactly one of Debit or Credit > 0 per line; enforce mutual exclusivity (entering one clears/disables the other).
5. Client-side validation runs continuously:
   1. Require at least two lines with valid GL accounts.
   2. Require each line to have exactly one of debit/credit > 0.
   3. Compute totals; if Total Debits ≠ Total Credits, show form-level error and disable “Post Journal Entry.”
6. Successful post (balanced + valid):
   1. User clicks “Post Journal Entry.”
   2. UI sends create+post request to backend with Idempotency-Key header.
   3. On success, backend returns journalEntryId and status POSTED.
   4. UI redirects to Journal Entry View screen for that journalEntryId.
   5. View screen displays JE as read-only with no edit/delete actions (immutable; corrections via reversal).
7. Closed period rejection (server-side):
   1. User clicks “Post Journal Entry” with a posting date in a closed accounting period.
   2. Backend returns validation error indicating period is closed.
   3. UI shows a user-safe error message about closed periods.
   4. All entered form values remain populated for correction; “Post Journal Entry” remains disabled until resolved.
8. Duplicate submission/idempotency:
   1. If backend returns 409 due to duplicate Idempotency-Key, show non-destructive message and provide link/redirect to the existing JE if available; do not clear form automatically.

## Notes
- Constraints:
  - Workflow: DRAFT (editable) → POSTED (immutable); posted entries must not expose edit/delete actions.
  - Single currency per JE; default USD; amounts use BigDecimal(19,4) precision and should format consistently.
  - Dual-field line model: debitAmount/creditAmount; enforce “exactly one > 0” per line.
  - Period controls: posting must respect open accounting periods; server is source of truth.
  - Idempotency-Key header required for create+post; handle 409 duplicates gracefully.
- Acceptance criteria mapping:
  - Authorized users can create manual JEs with reason code (permission gating + required dropdown).
  - System blocks unbalanced manual JEs (totals + disabled submit).
  - Posted manual JEs are immutable (redirect to read-only view; reversal is correction path).
  - Posting respects period controls and audit requirements (closed period error handling; no data loss).
- Error handling:
  - Display user-safe messages for backend validation errors; keep field-level highlights where possible.
  - Ensure form state persistence on any server error (including closed period).
- TODO (design/dev):
  - Define exact copy for closed period error and any generic validation errors.
  - Confirm whether “Save Draft” is in scope for this screen; if not, remove secondary action.
  - Confirm behavior for 409 idempotency duplicates (auto-redirect vs. show link).
