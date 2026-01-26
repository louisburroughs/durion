# [FRONTEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)
## Purpose
Enable inventory receivers to find a PO/ASN, view expected receiving lines, enter actual received quantities, preview variances, and confirm a receipt into staging via a single atomic backend submit. Provide clear success feedback (items now in staging, updated line states, correlation id, variance summary) and robust error handling for not found, validation failures, conflicts, and transaction/timeout failures while preserving user edits.

## Components
- Breadcrumb: Inventory / Receiving / Receive into Staging
- Step container / screen flow: FindDocument → ReceiveLines → Result
- FindDocument panel
  - Document type selector (PO or ASN)
  - Document identifier input (trimmed, non-empty)
  - Search/Find button (enabled per rules)
  - Inline field errors + top-level error banner area
- Document header summary (loaded)
  - Document id, document type, supplier (display), site id (UUIDv7), optional reference
  - Staging destination display (default staging for site)
- Receiving lines table/list
  - Columns: Line id, Item/SKU, Description, Expected Qty, Expected UOM, Actual Qty input, (Optional) Actual UOM selector (if editable), Line state, Receivable indicator/hint
  - Read-only rows for non-receivable lines with hint “Not receivable in current state”
  - Empty-state panel when no receivable lines returned
- Variance preview section (client-side only)
  - Per-line variance (expected vs actual) and summary totals (if applicable)
- Action bar
  - Back button (with unsaved edits warning when applicable)
  - Review Variances button
  - Confirm Receipt button (disabled/enabled per rules)
  - In-flight spinner/disabled state for Confirm
  - “Receive another” button (post-success)
- Error/notification UI
  - Conflict banner with “Reload document”
  - Validation banner with field mapping
  - Forbidden state panel
  - Retryable error panel for timeout/5xx with Retry action
- Result summary (read-only)
  - Correlation id (and request id if provided)
  - Receipt timestamp (rendered in user locale from UTC)
  - Received by (user id / display name from user context)
  - Variance summary (if provided)
  - Per-line updated state and variance fields (if provided)

## Layout
- Top: Breadcrumb + page title; global banner area for errors/conflicts
- Main: Step content (FindDocument form → Document header + lines table + variance preview → Result summary)
- Bottom sticky action bar: Back | Review | Confirm (or Receive another on success)

## Interaction Flow
1. Land on screen with breadcrumb and FindDocument form; Search disabled until document type selected and identifier is trimmed non-empty.
2. User selects PO/ASN and enters identifier; clicks Search.
3. Frontend calls Moqui proxy load service; on success, render Document header, staging destination, and receiving lines.
4. For each line:
   1) If receivable, show editable Actual Qty (required, numeric, ≥ 0) and optional editable UOM only if backend supports; no UI-side UOM conversion.
   2) If not receivable, show row read-only with hint; exclude from confirm payload.
5. User edits Actual Qty for all receivable lines; inline validation updates; Review enabled only when all receivable lines are valid.
6. User clicks Review Variances; compute and display client-side variance preview (no backend call, no persistence); Confirm remains available if enabled.
7. User clicks Confirm Receipt:
   1) Disable Confirm immediately; show spinner within 100ms.
   2) Submit single atomic receipt payload (document id/type/site/staging + receivable lines with actual qty and optional UOM) with client-generated correlation id header.
   3) Timeout at 8s; on timeout show retryable error and keep edits intact (no auto-retry).
8. On 200 success:
   1) Lock all inputs; show Result section with correlation id, timestamp, received-by, variance summary, and backend-returned updated line states (and variance fields when provided).
   2) Show clear confirmation that items are now “in staging”.
   3) Provide “Receive another” to reset to FindDocument (clears loaded data and edits).
9. Back behavior:
   1) From ReceiveLines, Back returns to FindDocument; if unsaved edits exist, warn before discarding.
   2) After Confirm success, Back is de-emphasized; primary action is “Receive another”.
10. Edge/error cases:
   1) Document not found / not eligible on load: show error banner; remain on FindDocument with inputs preserved.
   2) 401: follow app convention (redirect/session refresh); do not show partial document/line data.
   3) 403 on load: show forbidden state; do not leak document/line data.
   4) 422 on confirm: show validation banner; map field errors to specific line/field when possible; remain on ReceiveLines with edits preserved.
   5) 409 on confirm: show conflict banner; offer “Reload document” (re-fetch and re-render; preserve edits only if safe, otherwise prompt).
   6) 5xx/timeout on confirm: show retry affordance; keep edits; do not auto-retry mutations.

## Notes
- Confirm Receipt enablement: document loaded + at least one receivable line exists + all receivable lines have valid Actual Qty (numeric, ≥ 0) + not in-flight.
- Under/over receipt is allowed; variance preview is informational and must not block confirm.
- No UI-side UOM conversion; if UOM is editable, submit selected value only.
- Confirm is a single atomic backend operation that creates immutable ledger entries, updates on-hand in staging, records variances, and updates receiving line states.
- Error schema is deterministic; map field errors to document identifier and per-line actual qty/UOM fields when provided.
- Non-receivable lines must be visible but excluded from confirm payload; show “Not receivable in current state”.
- Applied safe default: show standard empty-state panel when no receivable lines are returned.
- Performance/UX: spinner within 100ms; confirm request timeout at 8s; keep user edits intact on retryable failures; never auto-retry mutations.
- Result display must include correlation id (and request id if available), UTC timestamp rendered in user locale, and received-by from user context.
