# [FRONTEND] [STORY] Execution: Issue and Consume Parts
## Purpose
Enable technicians and supervisors to view work order part line items and record a single parts usage event (ISSUE or CONSUME) against a selected line. The screen should load the work order header and parts totals, show derived remaining authorized quantity, and update totals/history in-place after a successful record. The UI must prevent recording when the work order is closed/cancelled or when the user lacks the capability to record usage.

## Components
- Page header: Work Order summary (ID, status, key header fields)
- Capability/status banner (read-only): “Recording disabled” reason when applicable
- Parts grid/table
  - Columns: Part/Product, Authorized Qty, Issued to Date, Consumed to Date, Remaining Authorized (derived), Totals/Status
  - Row select/highlight
  - Inline indicators: pending / usage_recorded / billable / declined
- Selected part details panel (or expandable row)
  - Read-only fields: Authorized, Issued to Date, Consumed to Date, Remaining Authorized
  - Usage event form
    - Event type selector: ISSUE / CONSUME (RETURN only if supported)
    - Quantity input (decimal, > 0)
    - Emergency checkbox
    - Decline toggle/checkbox (declined=true; removes from billing)
    - Optional note field (only if supported)
    - Primary action button: “Record Usage” (label may reflect type, e.g., “Record Issue”)
    - Secondary action: Cancel/Clear
- Usage history list (for selected part line)
  - Rows: timestamp (UTC source), type, quantity, flags (emergency/declined), recorded by (if available)
  - Empty state
- Inline validation + error alert/toast area

## Layout
- Top: Work Order header + status/capability banner
- Main: Left/center Parts grid; Right Selected part details + “Record usage” form; Bottom/right Usage history
- Inline sketch: Header → [Parts Grid (left)] | [Selected Part + Form + History (right)]

## Interaction Flow
1. On screen entry, fetch and render work order header (including status) and part line items with totals.
2. Default state: show parts grid; prompt user to select a part line to view details and history.
3. When a part line is selected, display authorized qty, issued-to-date, consumed-to-date, and remaining authorized (derived) for that line.
4. Enable/disable recording:
   1. If work order status is closed/cancelled, disable all “Record …” actions and show reason.
   2. If user lacks capability to record usage, disable inputs/actions and show reason.
   3. If work order status is pending parts, keep actions enabled (backend may still reject).
5. Record an ISSUE/CONSUME event (primary flow):
   1. User selects event type (ISSUE or CONSUME), enters quantity (> 0), optionally marks emergency, optionally sets decline, and submits.
   2. UI validates quantity is a positive decimal; block submit if invalid.
   3. Submit creates an immutable usage event with type and quantity; timestamp is sourced in UTC.
   4. On success, update the selected part line totals in the grid (e.g., issued-to-date increases by quantity for ISSUE) without full page reload.
   5. Append the new event to the history list immediately and keep the selected row context.
6. Real-time updates:
   1. Listen for parts_usage_recorded event and refresh affected row totals/history if the event pertains to the current work order/part line.
7. Key error/edge cases:
   1. If usage quantity exceeds available stock, show INSUFFICIENT_STOCK error and do not update totals/history.
   2. If work order ID is invalid, show WORKORDER_NOT_FOUND error and present a safe empty/error state.
   3. If backend rejects due to status (e.g., pending parts not allowed), show backend message and keep form values for correction/retry.

## Notes
- Remaining authorized quantity is derived and must respect business rule constraints; do not allow UI to display negative remaining (clamp/format per domain rules) and rely on backend for final enforcement.
- “Record …” actions must be disabled when status is closed/cancelled or capability flag is false (DECISION-INVENTORY-013).
- Permissions: Technician and Supervisor can record usage; Service Advisor is review-only (read-only UI state).
- Decline behavior: declined=true removes from billing; reflect in row status and history entry.
- Emergency usage: store/display as a flag in the event snapshot; show a clear indicator in history and/or row.
- Ensure updates occur without full page reload; prefer optimistic UI only if backend guarantees idempotency/immutability, otherwise update on success response.
- RETURN event type and note field are optional and must be feature-detected (only render if backend supports).
- Test fixtures to cover: standard usage, emergency usage, decline, insufficient stock error, invalid work order error.
