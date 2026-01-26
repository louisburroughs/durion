# [FRONTEND] [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage
## Purpose
Provide a read-only POS/Moqui frontend screen that lets a Service Advisor request inventory availability for a single SKU at either a parent location (aggregated across storage locations) or an optional specific storage location (bin-level). The screen submits the request through Moqui proxy endpoints, then displays returned On-hand, Available-to-Promise, and Allocated quantities with the product base UOM. It must support deep-linking via canonical query params with a single auto-run on load, and handle “no inventory history” as a successful response showing zeros with a clear empty-state message.

## Components
- Page header/title: “Ledger Availability” (or repo-standard title pattern)
- Query form
  - SKU text input (trimmed)
  - Location picker (LocationRef; required)
  - Storage Location picker (optional; disabled until Location selected)
  - Helper text indicating scope: location aggregated vs storage-location specific
- Action buttons
  - Primary: “Check Availability”
  - Secondary: “Clear”
  - Tertiary (conditional): “Retry”
- Inline validation messages (SKU required, Location required)
- Loading indicator/spinner (shown within 100ms) + disabled form state during request
- Results panel (read-only)
  - On-hand quantity
  - Available-to-Promise quantity
  - Allocated quantity
  - UOM label (base UOM from response)
  - “Stale results” indicator when inputs change after a success
- Empty-state success message: “No inventory history” (when quantities are 0 and backend indicates this condition via success response)
- Error panel (user-friendly)
  - Summary message
  - Field-level errors (when provided by deterministic error schema)
  - Technical details accordion/expander (correlation ID, status/code when available)
- Deep-link indicator (optional subtle text): “Loaded from link parameters” (non-blocking)

## Layout
- Top: Page title + brief description (“Check availability for a single SKU by location or storage location.”)
- Main (single column)
  - Query Form card
    - Row 1: SKU input (left) | Location picker (right)
    - Row 2: Storage Location picker (full width; disabled until Location chosen)
    - Row 3: Actions aligned right: [Clear] [Check Availability]
  - Below form (stacked panels, only one prominent at a time)
    - Loading state inline under actions (spinner + “Checking…”)
    - Results panel (quantities + UOM) with optional “stale” badge
    - Empty-state note (“No inventory history”) shown within results area when applicable
    - Error panel with Retry button and technical details expander

## Interaction Flow
1. Initial load (no deep-link params)
   1. Show empty form; no results.
   2. Storage Location picker is disabled until a Location is selected.
2. Primary flow: manual availability check
   1. User enters SKU (trim whitespace) and selects a Location from the picker.
   2. (Optional) User selects a Storage Location (picker filtered by selected Location).
   3. User clicks “Check Availability”.
   4. Frontend validates required inputs; if missing, show inline validation and do not call network.
   5. Set state to Loading: disable inputs/buttons; show spinner within 100ms.
   6. Call Moqui proxy availability endpoint with sku + locationId + optional storageLocationId; include X-Correlation-Id header.
   7. On success: render Results panel with On-hand, ATP, Allocated, and UOM (read-only).
3. Deep-link auto-run (single run per load)
   1. If required query params (sku, locationId) are present on initial load, prefill form.
   2. Auto-submit exactly once (guard against reactive re-runs); show Loading state.
   3. Display results or error as per response; user may edit inputs and manually re-run.
4. Input change after success (stale behavior)
   1. If user changes SKU, Location, or Storage Location after a successful result, keep last result visible but mark it “stale”.
   2. Do not auto-refresh; require explicit “Check Availability” to update.
5. Clear
   1. User clicks “Clear” to reset inputs, results, errors, and stale indicator to initial state.
6. No inventory history (success with zeros)
   1. Backend returns success with quantities = 0.
   2. Show Results panel with 0 values and UOM, plus a visible note: “No inventory history” (not an error).
7. Error handling (deterministic + defensive parsing)
   1. If response includes fieldErrors, map them to SKU/Location/Storage Location fields and keep inputs preserved.
   2. If 404 (SKU/location not found): show user-friendly error; preserve inputs; offer Retry.
   3. If 403: show forbidden state; do not display any quantities; preserve inputs.
   4. If 401: follow standard login/session refresh behavior (no custom quantities display).
   5. If timeout/network error: show error panel with “Retry” (no auto-retry).
   6. In error technical details, display correlation ID from response headers when present.

## Notes
- Read-only screen: no editing of inventory, allocations, receipts, or ledger events.
- Single-SKU per request only; no batch queries.
- No expected receipts in ATP v1; UI displays backend-returned values only (no client-side computation).
- No UOM conversions; request/response in product base UOM only.
- No polling/auto-refresh loops; refresh is user-initiated only.
- Picker data sources must be via Moqui proxy:
  - Location picker uses locations endpoint; support search/filter/pagination as available.
  - Storage Location picker uses storage-locations-by-location endpoint; disabled until Location selected.
- Networking constraint: no direct Vue → inventory backend calls; all availability and picker calls go through Moqui proxy/service wiring.
- Correlation ID:
  - Send X-Correlation-Id on availability requests (generate per request if not already present in app context).
  - Read correlation ID from response headers when present; show in error technical details.
- Sensitive data handling:
  - Do not log availability quantities or raw payloads client-side.
  - Telemetry events: query initiated (include whether storageLocationId present), query success (timing only), query failure (HTTP status, deterministic error code if present, correlation ID).
- Acceptance criteria highlights:
  - Missing required inputs prevents submit (no network call).
  - Deep-link with required params auto-runs once per load.
  - “No inventory history” is a success state with zeros and explicit messaging.
  - Deterministic error handling with correlation ID shown when available.
  - 403 forbidden shows forbidden state without leaking quantities.
