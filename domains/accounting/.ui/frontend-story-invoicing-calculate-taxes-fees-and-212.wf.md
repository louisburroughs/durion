# [FRONTEND] [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice

## Purpose
Enable users to calculate and view invoice taxes, fees, and totals for Draft invoices using backend-authoritative results. Ensure the UI renders totals exactly as returned (including 0.00 values) and clearly indicates whether the invoice is “calculated/current.” When required tax basis data is missing, the UI must block calculation-dependent actions and guide the user to fix specific fields highlighted by backend validation.

## Components
- Page header: Invoice identifier + status (Draft) + calculation status indicator (“Calculated/Current” vs “Not calculated/Out of date”)
- Invoice summary fields (read-only): invoice id, currency, invoice type/status (backend authoritative)
- Estimate linkage section (optional, read-only): linked estimate id
- Invoice items table/list:
  - Line identifier (read-only)
  - Product/service reference (read-only or editable per existing UI contract)
  - Tax basis fields (editable only if backend/UI contract allows): jurisdiction/country/region field(s), tax category/code, etc.
  - Amounts: line amount (read-only display), calculated tax/fee amounts per line (read-only after calculation)
  - Inline field-level validation messages (from backend)
- Totals panel (read-only, backend authoritative):
  - Subtotal
  - Taxes (display even when 0.00)
  - Fees (display even when 0.00)
  - Total (and any other backend-provided totals)
- Calculation metadata (read-only; may be null):
  - Snapshot reference id (optional)
  - Calculated at timestamp (optional)
  - Backend calculation status field (drives UI indicator)
- Variance section (optional, read-only):
  - Variance amount
  - Variance type (backend enum)
  - Variance timestamp
  - Optional approval fields (read-only if present; render as provided)
- Actions:
  - “Recalculate totals” / “Calculate totals” button (enabled only when invoice is Draft/editable)
  - “Issue invoice” action (hidden/disabled until calculation succeeds and status is current)
- Error summary banner (for calculation failures, especially missing tax basis)
- Loading states: page skeleton/spinner; per-action spinner on calculate

## Layout
- Top: Page header (Invoice title/ID) + Draft badge + calculation status indicator
- Main (two-column): Left = Invoice items table + inline edits; Right = Totals panel + calculation metadata + variance (if present)
- Footer/action bar: Calculate/Recalculate button (primary) + Issue invoice (secondary; gated) + any existing navigation actions

## Interaction Flow
1. Load invoice detail
   1. UI calls SVC1: Load Invoice Detail with invoiceId.
   2. On success, render header, items, totals, calculation status, optional snapshot ref, and optional variance.
   3. On 403, show access denied message without confirming invoice existence.
   4. On 404, show safe “Invoice not found” message.
2. Standard flow: edit line and recalculate (Draft taxable invoice)
   1. User edits a line item field (e.g., tax basis field) and commits the change per existing UI behavior.
   2. UI marks totals as potentially out-of-date (until backend confirms current) and enables “Recalculate totals.”
   3. User triggers recalculation (or recalculation is triggered by the existing contract on commit).
   4. UI calls SVC3: Calculate Invoice Totals (Draft) with invoiceId.
   5. On success, UI updates:
      - Invoice totals exactly as returned (including taxes/fees shown even when 0.00)
      - Per-line calculated values returned by backend
      - Snapshot reference and calculated timestamp if provided
      - Variance section if returned
      - Calculation status indicator to “calculated/current” based on backend status field
   6. UI enables/shows “Issue invoice” only when backend indicates calculation is current and no blocking validation remains.
3. Missing tax basis blocks calculation and issuance
   1. User triggers recalculation.
   2. Backend returns 422 with structured errors (invoice-level and/or per-line keyed by item id).
   3. UI shows error summary: “Cannot calculate totals: missing tax basis information.”
   4. UI highlights missing fields using backend-provided error mapping (inline on the specific line/field; also list in summary).
   5. UI disables/hides “Issue invoice” until a successful calculation occurs.
4. Non-editable/conflict and service failures
   1. If SVC3 returns 409 (not Draft/editable or optimistic lock conflict), show a non-blocking banner explaining the invoice can’t be recalculated right now; prompt user to refresh/reload.
   2. If SVC3 returns 502/503, show “Tax service unavailable” message; keep existing totals displayed but mark status as not current/unknown; allow retry.
   3. If SVC3 returns other 5xx, show generic unexpected error; allow retry and preserve user edits.

## Notes
- Totals and calculated line values are backend-authoritative and must be rendered exactly as returned; do not compute or round independently in the UI.
- Taxes and fees totals must be displayed even when 0.00 (no hiding zero values).
- Calculation metadata may be null; UI must handle absence gracefully (hide snapshot/timestamp rows when not provided).
- Tax basis field editability varies by existing invoice UI contracts and backend rules; UI must respect read-only vs editable states and still display values.
- Validation errors from SVC3 (422) may include invoice-level missing fields and per-line missing fields keyed by invoice item id; UI must map and highlight accordingly.
- “Issue invoice” action on this screen is gated: disabled/hidden until calculation succeeds and backend status indicates “calculated/current.”
- Access control: 403 must not reveal whether the invoice exists; 404 should be a safe not-found message.
- Variance and any approval-related fields are read-only and backend-provided; render without inferring additional states beyond what is returned.
