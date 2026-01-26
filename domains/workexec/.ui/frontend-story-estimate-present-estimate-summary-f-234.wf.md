# [FRONTEND] [STORY] Estimate: Present Estimate Summary for Review

## Purpose
Enable a Service Advisor to generate and view a read-only, customer-facing estimate summary based on an immutable EstimateSummarySnapshot. The summary must reflect authoritative snapshot totals and content, and display the snapshot timestamp in the user’s timezone with a timezone label. The UI must prevent navigation to the summary view when generation is blocked (e.g., estimate not Draft, or missing legal terms under FAIL policy).

## Components
- Page header: “Customer Summary” / “Estimate Summary for Review”
- Context header block: Estimate identifier, status, optional location/context, optional created-at (display-only)
- Primary action button: “Generate Customer Summary” (or triggered by “Customer Summary” entry point)
- Loading indicator/state for generation request
- Read-only summary content area (customer-facing rendering)
- Snapshot metadata strip: Snapshot ID, snapshot timestamp (user timezone + timezone label), optional expiration datetime (with timezone label if present)
- Totals panel (authoritative): subtotal/tax/fees/discounts/total (as provided by snapshot)
- Line items list/table (customer-facing DTOs)
  - Line: name/description, quantity, unit price, line total (as provided)
  - Optional customer-facing notes/fields (if present)
- Error alert banner (inline, non-blocking layout)
- Secondary navigation: “Back to Estimate” (returns to estimate screen)
- Optional: “Open PDF” / “View attachment” link (if snapshot provides URL)
- Optional: server-rendered HTML container (if provided by backend)

## Layout
- Top: Header + Back to Estimate
- Below header: Context header block (Estimate ID, Status, Location/Context)
- Main column:
  - Action row: Generate button (disabled when loading) + inline status text
  - Error alert banner (appears above summary content when applicable)
  - Snapshot metadata strip (shown only when snapshot exists)
  - Summary content area:
    - Totals panel (top/right within main content)
    - Line items list (below totals)
    - Optional rendering (HTML block) or structured fields
- Footer area (optional): legal/terms reference display if included in customer summary rendering

## Interaction Flow
1. User opens an Estimate (status DRAFT) and clicks “Customer Summary”.
2. UI calls “Generate Summary Snapshot (idempotent)” endpoint with required headers; request body is empty.
3. While request is in-flight, UI shows loading state and disables the generate action.
4. On success (200) or idempotent replay (201), UI transitions to the read-only summary view using the returned snapshotId (and inline snapshot DTO if provided).
5. UI renders the customer-facing summary strictly from the snapshot:
   1. Display snapshot timestamp in user timezone with a timezone label (per DECISION-INVENTORY-016).
   2. Display snapshotId and ensure displayed estimateId equals snapshot’s estimateId.
   3. Render authoritative totals from snapshot (do not recompute from estimate lines).
   4. Render customer-facing line items array (or render-only mode if supported).
6. If snapshot includes an expiration datetime, display it with a timezone label.
7. If snapshot includes a PDF/URL, show an “Open” link in the summary header area.
8. Edge case — Estimate not DRAFT:
   1. Generate endpoint returns 409.
   2. UI shows error: “Summary can only be generated for Draft estimates.”
   3. UI remains on the estimate screen (no navigation to summary view).
9. Edge case — Missing legal terms with FAIL policy:
   1. Generate endpoint returns 412 with error code TERMS_MISSING.
   2. UI shows error: “Cannot generate estimate summary: Legal terms and conditions not configured”.
   3. UI does not navigate to a summary view.

## Notes
- Read-only story: Estimate, EstimateLine, and EstimateSummarySnapshot data are not editable in this UI.
- Snapshot is immutable; summary must be based on the snapshot, not live estimate fields.
- Timestamp handling: snapshot stored in UTC; display in user timezone with explicit timezone label (DECISION-INVENTORY-016).
- Request rules: generate request body must be empty; server derives user from auth (do not send userId unless required by existing backend).
- Idempotency: treat 200 and 201 as success; both should result in showing the same snapshot-based summary.
- Validation/error handling:
  - 409 when estimate not DRAFT; show specified message and stay on estimate screen.
  - 412 with TERMS_MISSING when policy FAIL and terms not configured; show specified message and do not navigate.
  - 400 with details for malformed requests (rare); show a generic error banner and keep user on current screen.
- Internal-only fields must be absent or hidden (do not display internal notes, internal money/decimal fields).
- Totals display: show 0 only if backend explicitly provides 0; otherwise omit unsupported totals fields rather than defaulting.
