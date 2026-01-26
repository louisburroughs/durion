# [FRONTEND] [STORY] Invoicing: Support Authorized Invoice Adjustments
## Purpose
Enable authorized users to initiate and submit adjustments to Draft invoices while capturing required reason code and justification. Provide a backend-driven preview of before/after totals when available, and clearly communicate when totals will only be recalculated on submit. Ensure robust handling of backend rejections (authorization, status conflicts, validation, negative totals requiring credit memo, and concurrency) without losing user edits, and display adjustment audit history.

## Components
- Invoice detail header (invoice number/id, status, currency)
- Authorization gate states
  - Hidden “Adjust Invoice” action when unauthorized
  - “Not Authorized” page/state for direct navigation
- “Adjust Invoice” entry point (button/link)
- Editable invoice fields area (backend-permitted only)
  - Line items editor (allowed fields only)
  - Invoice-level discount editor (if supported)
- Reason code selector (dropdown; required when backend indicates)
- Justification input (textarea; required when backend indicates; non-empty validation)
- Totals panel
  - Current totals (subtotal, tax, fees, grand total)
  - Preview totals (before/after delta) when supported
  - Fallback message: “Totals will be recalculated on submit”
- Inline field validation messages (reason/justification; line item fields when mapped)
- Global banners/alerts area (credit memo required, conflict/reload needed, generic validation)
- Submit/Cancel controls
  - Submit Adjustment button (disabled when invalid or stale)
  - Cancel/Back button
- Concurrency/staleness indicator (e.g., “Invoice changed, reload required”)
- Adjustment audit history panel
  - Latest adjustment outcome summary (minimum)
  - Ideally list of adjustment events (actor, timestamp, reason, before/after totals)
- Expandable “Details” section for backend error message and trace identifiers

## Layout
- Top: Page header with invoice identifier + status; right-aligned “Adjust Invoice” (if authorized)
- Main (two-column): Left = editable adjustment form; Right = totals panel + preview/delta
- Below main: Adjustment audit history list (latest first) + expandable error “Details” section
- Footer area: Sticky action bar with Cancel/Back (left) and Submit Adjustment (right)

## Interaction Flow
1. User opens Invoice Detail (by UUIDv7); frontend loads invoice details (status, line items, totals, currency, concurrency token).
2. Frontend checks authorization/permission:
   1. If unauthorized: hide “Adjust Invoice”; if user navigates directly to adjust route, show “Not Authorized” and no editable controls.
   2. If authorized: enable “Adjust Invoice” entry point.
3. User enters Adjust Invoice view:
   1. Load adjustment reason codes; show selector (and required indicator only if backend indicates requirement).
   2. Render only backend-permitted editable fields (line items and/or invoice-level discount).
   3. Show current totals; attempt to show preview before/after totals if backend supports it; otherwise show “Totals will be recalculated on submit.”
4. User edits allowed fields:
   1. Keep edits locally; do not mutate persisted invoice until submit succeeds.
   2. If preview supported, update preview totals display based on backend response; if not supported, do not attempt client-side tax math.
5. User selects reason code and enters justification when required:
   1. If required fields missing: show inline errors on reason selector/justification; keep edits intact; disable submit until minimally valid.
6. User submits adjustment:
   1. Send adjustment command with edits, reason/justification (if required), and concurrency token (if required).
   2. On success: replace invoice totals with backend recalculated totals; show “adjusted” indicator (field if present and/or audit history); append/show new audit event entry.
7. Key edge cases on submit (no loss of edits unless explicitly reloading):
   1. Conflict/not-draft or optimistic lock: show conflict banner prompting reload; disable submit until refreshed; preserve edits but indicate they may be stale.
   2. Negative total requires credit memo: show “Credit memo required” banner; keep edits intact; no local commit.
   3. Missing reason/justification (backend validation): map errors to fields when possible; otherwise show banner; keep edits intact.
   4. Invalid line item edits / field not allowed: show inline error if backend provides field mapping; otherwise show banner with backend message.
   5. Unauthorized response: show “Not Authorized” state and remove editable controls.
   6. Not found: show safe “Not Found” messaging without leaking existence.
8. Error details: for any backend error, allow expanding “Details” to view backend message and trace identifiers (when available) for support.

## Notes
- Preconditions: invoice must be retrievable by UUIDv7; user authenticated; permission to adjust invoices is backend-authoritative; invoice must be Draft at submit (server revalidates).
- Data requirements: invoice read includes status, line items, totals (subtotal/tax/fees/grand total), currency, optional adjusted flag, and a concurrency token (timestamp or version) if optimistic locking is enforced.
- Reason codes: must load from backend; backend validates “active” status; UI should only present active codes and handle empty list gracefully (disable submit with guidance).
- Totals preview: only display before/after impact when backend supports; otherwise explicitly communicate recalculation on submit.
- Audit history: at minimum show latest adjustment outcome; ideally list events with actor, timestamp, reason, and before/after totals (structured totals preferred).
- Acceptance criteria highlights:
  - Authorized Draft invoice adjustment succeeds and updates totals from backend recalculation.
  - Required reason code/justification enforced (UI + backend mapping) without losing edits.
  - Rejections handled: unauthorized, conflict/not-draft, negative-total-requires-credit-memo, invalid edits, concurrency conflict; submit disabled appropriately after conflict until refresh.
  - Error “Details” section includes backend message and trace identifiers when available.
