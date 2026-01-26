# [FRONTEND] [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)
## Purpose
Provide Service Advisors a dedicated Availability screen that displays backend-sourced inventory quantities (On-hand, Available-to-Promise, and Available) by location without any frontend calculation. Support optional deep-linking from a product/quote line with pre-filled query parameters to speed quoting and reduce errors. Ensure clear loading, error, and refresh behaviors, including handling “zero” quantity responses and showing stale prior results when requests fail.

## Components
- Page header with title “Availability” and optional breadcrumb/back link (e.g., back to product/quote line)
- Context summary (read-only): Product ID/SKU (or product identifier), Location selection, optional Storage Location selection
- Filters / inputs:
  - Location picker (dropdown/search) populated from LocationRef list
  - Optional Storage Location picker (dependent dropdown/search) populated from StorageLocation list
  - Optional “Include/Show storage locations” toggle (if needed to reveal storage picker)
- Primary actions:
  - Refresh button
  - Retry button (shown on error)
- Status indicators:
  - Loading indicator (in-content spinner/skeleton)
  - Error banner (4xx/5xx/network)
  - Stale results banner (when showing prior results after an error)
  - “Last updated” timestamp (if provided)
- Results display:
  - Quantity cards/rows (read-only): On-hand, Available-to-Promise (if provided), Available
  - Optional metadata display: Unit of measure (if provided)
- Support/troubleshooting:
  - Correlation ID display/copy (if available from response/headers; display-only)

## Layout
- Top: Header (Title + Back link) | Right: Refresh
- Main: Filters panel (Location picker, optional Storage picker) → Status area (loading/error/stale/last updated) → Results (quantity cards/rows)
- Footer (optional): Correlation ID (small text + copy action)

## Interaction Flow
1. Entry to screen
   1. If deep-linked, read query params and pre-fill Location and optional Storage Location (and product context if present).
   2. If required inputs are present, auto-trigger availability fetch; otherwise wait for user selection.
2. Fetch availability (request start)
   1. Disable Refresh and show loading indicator in results area.
   2. Do not display or log quantity values during request; keep prior results visible only if already present (optional) with a subtle “Updating…” state.
3. Successful response (HTTP 200, including zeros)
   1. Clear loading/error states.
   2. Render read-only quantities exactly as provided:
      - On-hand (required)
      - Available-to-Promise (render only if present; do not infer)
      - Available (required)
   3. If “last updated” timestamp is present, display it near results.
   4. If unit of measure is present, display it as provided (no conversion).
4. Error response (4xx/5xx) or network error
   1. Show error banner with a Retry action.
   2. If prior results exist, keep them visible and show a “Stale results” banner indicating data may be outdated.
   3. If no prior results exist, show an empty-state message plus Retry.
5. Refresh
   1. User clicks Refresh to re-fetch with current selections.
   2. Follow the same loading/success/error behaviors as above.
6. Changing filters
   1. When Location changes, clear/refresh dependent Storage Location options; optionally clear selected Storage Location if it no longer applies.
   2. Trigger fetch automatically on change (or require explicit Refresh—choose one consistent behavior; default to auto-fetch if inputs are complete).

## Notes
- Frontend must not compute or infer Available-to-Promise; render only backend-provided values.
- Quantities are read-only and must not be logged (including analytics/debug logs).
- Required response fields for rendering: product identifier (string), locationId (UUID), quantities: onHand (number), available (number); atp (number) only if provided.
- Optional fields: storageLocationId (UUID), lastUpdated (ISO-8601 timestamp), uom (string), correlationId (if available).
- Picker data:
  - LocationRef items include id (UUID), name (string), and optional type badge (e.g., DC/Store/Other).
  - StorageLocation items include id (UUID), locationId (UUID), name (string), and optional type.
- Error handling acceptance: on error, show error banner + Retry; if prior results exist, show them with a stale banner.
- Deep-linking acceptance: screen supports query params to pre-fill context from product/quote line; should not break if params are missing/partial.
- Ensure “zero” values are displayed as valid results (not treated as empty/error).
