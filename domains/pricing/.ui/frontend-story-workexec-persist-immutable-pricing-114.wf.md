# [FRONTEND] [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line
## Purpose
Enable a Service Advisor to open a read-only “Pricing Snapshot” drilldown from an Estimate or Work Order line to explain how the line price was calculated at the time of creation. The drilldown must display an immutable snapshot exactly as returned by the Pricing API, without recalculating totals. The snapshot opens in a modal/drawer so the user can review details without navigating away or losing unsaved edits.

## Components
- Work Order screen line item row actions menu / inline action link: “View pricing snapshot”
- Estimate screen line item row actions menu / inline action link: “View pricing snapshot”
- Modal or right-side drawer: “Pricing Snapshot” drilldown (read-only)
- Loading state (spinner/skeleton) within modal/drawer
- Error state panel for “Pricing snapshot not found” (404)
- Read-only fields:
  - Snapshot ID
  - Snapshot created timestamp (fallback “Unknown” if missing)
  - Subtotal (Money)
  - Total (Money)
  - Taxes (Money)
- Optional read-only sections (render only if present):
  - Quantity (number/decimal)
  - Item identifier details (part number / SKU / name)
  - Discounts / adjustments (object/array)
  - Fees / taxes breakdown arrays (if provided)
  - Notes / reason string (if provided)
  - Raw metadata object (safe subset if applicable)
- Modal/drawer controls: Close (X) and/or “Close” button

## Layout
- Workexec document (Estimate/Work Order) main screen
  - Main: line items table/list → each line shows an action “View pricing snapshot” when snapshotId is non-empty
- Overlay: Pricing Snapshot modal/drawer
  - Header: “Pricing Snapshot” + Close (X)
  - Body: Status area (loading/error) then read-only snapshot details (grouped sections)
  - Footer: Close button (optional)

## Interaction Flow
1. Work Order line (happy path)
   1. User views a Work Order with line items; a line shows a non-empty pricingSnapshotId.
   2. User selects “View pricing snapshot” for that line.
   3. UI opens a modal/drawer without navigating away and requests PricingSnapshot by snapshotId.
   4. While waiting, modal/drawer shows a loading state.
   5. On success, UI renders snapshot fields exactly as returned:
      1. Show snapshotId.
      2. Show createdAt timestamp; if missing, display “Unknown”.
      3. Show subtotal and total exactly as returned (no recomputation).
      4. Show taxes and any other required Money fields exactly as returned.
      5. Render optional fields/sections only if present.
   6. User closes modal/drawer and returns to the Work Order screen unchanged.

2. Estimate line (happy path, preserve edits)
   1. User is editing an Estimate (may have unsaved changes) and a line shows a non-empty pricingSnapshotId.
   2. User selects “View pricing snapshot”.
   3. UI opens modal/drawer and loads snapshot by snapshotId without losing unsaved edits/state on the Estimate screen.
   4. User closes modal/drawer and remains on the Estimate screen in the same state (edits preserved).

3. Invalid reference (missing/empty snapshotId)
   1. If pricingSnapshotId is missing/empty, do not enable the drilldown action (or show disabled with tooltip “No pricing snapshot available”).
   2. If user somehow triggers it, treat as invalid reference and show an inline error in the modal/drawer (no details rendered).

4. Snapshot not found (404)
   1. User attempts to view a snapshot for a given snapshotId.
   2. Pricing API responds HTTP 404.
   3. Modal/drawer shows “Pricing snapshot not found” error state and does not display any snapshot details.

5. Other failures (403/5xx)
   1. On 403, show an access/permission error state (no details rendered).
   2. On 5xx/network error, show a generic “Unable to load pricing snapshot” error state with a retry action (optional) and Close.

## Notes
- Drilldown must be read-only and must not compute or display any recalculated totals; display values exactly as returned by the Pricing API snapshot.
- Required minimum UI fields: snapshotId (required; if missing treat as invalid reference), createdAt (required; if missing display “Unknown”), subtotal (Money), total (Money), taxes (Money).
- Optional fields must be rendered only if present; do not require them for the UI to function.
- Modal/drawer must not navigate away from Work Order/Estimate; on Estimate it must not lose unsaved edits.
- Logging/traceability per load attempt (safe identifiers only):
  - originating context identifiers (estimateId/workOrderId/lineId) if available
  - result status (success/403/404/5xx) and backend error code if present
  - do not log sensitive snapshot fields (e.g., cost) even if present in the response.
- Error states must not show partial/sensitive snapshot details; show only the error message and safe context if needed.
