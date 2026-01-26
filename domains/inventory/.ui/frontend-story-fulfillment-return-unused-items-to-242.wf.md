# [FRONTEND] [STORY] Fulfillment: Return Unused Items to Stock with Reason
## Purpose
Enable users to initiate a “Return Items to Stock” flow from a Work Order and return unused/consumed items back into inventory in a single atomic transaction. The UI must display backend-authoritative max-returnable quantities, collect per-line return quantities and mandatory reasons, and submit via a Moqui proxy service. The screen must enforce destination selection rules via pickers and block inactive/pending locations, while providing deterministic field-level error mapping and correlation ID visibility on failures.

## Components
- Page header: “Return Items to Stock” + Work Order identifier/state
- Breadcrumb/back link to Work Order detail
- Eligibility/ineligible state banner (e.g., work order not Completed/Closed)
- Global error banner area (load/submit errors, conflict, correlation ID)
- Destination section (picker-based)
  - LocationRef picker (required if backend requires/permits)
  - StorageLocation/bin picker (optional; filtered by selected LocationRef)
  - Inline validation message area for destination fields
- Returnable items table/list
  - Columns: Item/SKU, Description, Consumed Qty (read-only), Max Returnable (read-only), Return Qty (editable), Return Reason (dropdown)
  - Per-line inline validation messages (qty/reason)
- Primary actions
  - Submit button: “Return to Stock”
  - Secondary button: “Cancel” / “Back”
- Submit in-flight indicator (button spinner/disabled state)
- Success confirmation panel/modal
  - Return transaction identifier
  - Timestamp (UTC)
  - Optional correlation ID
  - Returned line summary (sku/workOrderLineId, qty, reason)

## Layout
- Top: Back link + Title + Work Order meta (ID, state)
- Main: [Banner area] → [Destination pickers] → [Returnable items table]
- Footer/right-aligned actions: Cancel/Back (secondary) | Return to Stock (primary, disabled until valid)

## Interaction Flow
1. Entry from Work Order detail: user clicks “Return Items to Stock”.
2. Screen loads via Moqui proxy: work order header/state, returnable lines (with max-returnable), reason codes list, LocationRef list (and StorageLocation list when LocationRef selected).
3. If work order is ineligible (not Completed/Closed): show ineligible banner, disable all inputs and submit; provide back link.
4. If load fails:
   1. 401: follow standard session refresh/login redirect behavior.
   2. 403: show forbidden state; do not render partial data.
   3. 404: show “Work order not found” state with back link.
   4. 5xx/timeout: show generic error with retry; display correlation ID when available.
5. Destination selection:
   1. User selects LocationRef via picker (no free-text IDs).
   2. If bin-level returns are supported: user selects StorageLocation via picker filtered to the selected LocationRef.
   3. Picker must not allow INACTIVE/PENDING locations; if backend returns 422 anyway, show banner and highlight destination field.
6. Line entry:
   1. User enters integer Return Qty per line (default 0).
   2. Client validates: qty must be integer, > 0 to include, and <= maxReturnable (backend-authoritative).
   3. When qty > 0, Return Reason dropdown becomes required for that line (no free-text).
7. Submit enablement:
   1. Submit remains disabled until at least one line has qty > 0, all such lines have reasons selected, destination is valid (if required), and no validation errors exist.
   2. If reason codes list is empty or fails to load: show error banner “Return reasons are unavailable.” and keep submit disabled.
8. Submit:
   1. User clicks “Return to Stock”; UI locks to prevent duplicate submission (in-flight disabled state).
   2. Frontend sends a single request via Moqui proxy containing workOrderId, destination (location/bin as applicable), and only lines with qty > 0 including reason per line.
9. Submit response handling:
   1. Success: show confirmation with return transaction identifier, timestamp UTC, optional correlation ID, and returned line summary; keep submit disabled or otherwise prevent immediate duplicate submission.
   2. 409 conflict (state/concurrency): show conflict banner and offer reload action.
   3. 422 validation: parse deterministic error schema; map errors to specific line qty/reason fields and/or destination fields; also show a summary banner.
   4. 5xx/timeout: show generic error with retry; show correlation ID when available; do not auto-retry mutations.

## Notes
- Must use Moqui proxy integration pattern; no direct Vue → inventory backend calls.
- Max-returnable quantities are backend-authoritative; frontend must not compute availability/returnability beyond validating against returned values.
- Destination selection must be via pickers only; no free-text UUID entry. If StorageLocation is provided, it must belong to the selected LocationRef.
- Inactive/pending locations must be blocked for this movement flow (return-to-stock explicitly in-scope).
- Deterministic error schema required for mapping field errors to specific line items; include correlation ID propagation and display in error UI when available.
- Out of scope: accounting/financial reconciliation UI, reason code management, work order state changes, costing/valuation, ledger mutation UI.
- Submit payload should prefer stable per-line identifiers (e.g., workOrderLineId) when available; otherwise use SKU as key for mapping.
- Do not auto-refresh in a loop after success; provide clear confirmation and a path back to the Work Order.
