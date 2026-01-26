# [FRONTEND] [STORY] Fulfillment: Issue/Consume Picked Items to Workorder
## Purpose
Enable Parts Managers and Technicians to load a workorder’s picked items, enter/confirm quantities to consume (bounded by picked/remaining), and submit consumption through a same-origin Moqui proxy. The screen must clearly communicate eligibility, prevent over-consumption via client validation aligned to backend rules, and handle success, errors, and concurrency conflicts by reloading updated picked/consumed state.

## Components
- Page header: “Consume Picked Items” + Workorder identifier (read-only)
- Breadcrumb/back link: “Back to Workorder”
- Global status area:
  - Loading indicator (initial load + reload)
  - Success banner/confirmation summary (lines submitted; optional server timestamp)
  - Error banner (with correlation id in “Technical details” when available)
  - Conflict banner for HTTP 409 with “Reload picked items” action
- Picked items list/table (supports up to 50 lines; consider pagination/virtualization beyond)
  - Columns (read-only): SKU, Description (if available), Product ID (optional), Picked Qty, Already Consumed Qty (optional), Remaining Consumable Qty (derived when consumed qty provided), Status/Reason (optional), Traceability fields (optional)
  - Column (editable): “Consume now” quantity input per eligible line
  - Inline row validation messages (max exceeded, negative values)
  - Disabled state for ineligible lines (input disabled; excluded from submit)
- Primary actions:
  - “Consume” button (disabled until at least one line has quantity > 0 and no validation errors)
  - “Reload picked items” (shown on errors/conflicts; also available as secondary action)
  - “Back to Workorder” (secondary)
- Optional modal/expandable “Technical details” section for correlation id

## Layout
- Top: Header (title) + Workorder read-only summary; right-aligned “Back to Workorder”
- Below header: Banner stack area (loading/success/error/conflict)
- Main: Picked items table/list with sticky column headers; per-row quantity input on right
- Bottom (footer row): Left “Reload picked items” (secondary), right “Consume” (primary, disabled/enabled)

## Interaction Flow
1. Navigate to consume screen for a specific workorder.
2. UI shows loading indicator within 100ms and calls “Get picked items for workorder” via Moqui proxy (same-origin; no direct backend calls).
3. On success, render picked items list with read-only identity fields and quantities; compute “remaining” only when already-consumed is provided.
4. Determine eligibility:
   1. If workorder is in a disallowed state, show message and disable all inputs and “Consume”.
   2. For each ineligible line, disable its input and exclude it from submission.
   3. If no lines returned or all lines ineligible, show an empty/none-eligible state and keep “Consume” disabled.
5. User enters “Consume now” quantities:
   1. Quantity <= 0: treat as not selected (no error), except negative values show inline error and disable “Consume”.
   2. Quantity > max allowed (picked or remaining per backend-aligned rule): show inline row error and disable “Consume”.
   3. “Consume” becomes enabled only when at least one eligible line has quantity > 0 and there are no validation errors.
6. User clicks “Consume”:
   1. Submit selected lines (only rows with quantity > 0) via Moqui proxy “Consume picked items” endpoint (TBD).
   2. Include Idempotency-Key header in the request.
7. On successful response:
   1. Show success confirmation summary (count of lines submitted; include server timestamp when provided).
   2. Reload picked items list to reflect updated consumed/remaining/status.
   3. Keep user on the screen by default; “Back to Workorder” remains available.
8. On backend validation/state rejection (e.g., invalid workorder state, stale quantities):
   1. If HTTP 409, show conflict banner: “Picked quantities or workorder state changed.”
   2. Show correlation id in “Technical details” when available.
   3. Offer “Reload picked items”; on click, refetch and re-validate current inputs (clearing or re-checking any now-invalid entries).
9. On other failures (non-409):
   1. Show error banner with available correlation id.
   2. Keep user inputs where possible, but disable “Consume” if validation cannot be trusted until reload.

## Notes
- All backend communication must go through same-origin Moqui proxy routes (endpoints TBD); no direct Vue → backend calls.
- Frontend validation must align with backend rules: no over-consumption, only picked items, and respect remaining consumable quantity when available.
- Do not log or display aggregate quantities in telemetry; displaying quantities to the user in the UI is allowed.
- Read-only: workorder fields and picked item identity fields; editable only: per-line consume quantity when workorder/line is eligible.
- Concurrency: handle HTTP 409 explicitly with conflict messaging and a clear “Reload picked items” recovery path.
- Performance: render loading indicator within 100ms; handle up to 50 lines without jank; consider pagination/virtualization for larger lists.
- Data fields: prefer displaying SKU/description; canonical inventory/product identifier is productId (if provided). Include optional traceability linkage fields when present.
- TODOs:
  - Define Moqui proxy endpoints (GET picked items; POST consume) and required request/response shapes (minimum fields + correlation id + optional server timestamp).
  - Confirm max-allowed calculation when already-consumed is missing (picked-only vs remaining) and how ineligible reasons/status are represented.
