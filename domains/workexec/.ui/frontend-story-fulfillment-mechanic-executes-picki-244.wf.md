# [FRONTEND] [STORY] Fulfillment: Mechanic Executes Picking (Scan + Confirm)
## Purpose
Provide a Moqui UI screen for Mechanics to execute a work order picking task by scanning items, confirming picked quantities per line, and completing the overall picking task. The screen should guide the user through picking, reflect backend task/line statuses, and prevent common errors (invalid scans, invalid quantities, over-pick). It must support saving partial progress when allowed and handle network/API failures without losing in-progress confirmations.

## Components
- Page header
  - Work Order reference (ID/number)
  - Pick Task status badge (e.g., Ready/In Progress/Completed/Cancelled)
  - Optional Pick Task ID + version (for optimistic locking visibility/debug)
- Scan section (form)
  - Scan input field (barcode/QR text)
  - “Resolve/Find” button (optional if auto-submit on Enter)
  - Inline scan error message area
  - Optional “Manual search” link/button
- Pick lines list/grid
  - Columns: Product/Item name, SKU/code, Location (if provided), Required Qty, Picked Qty, Remaining Qty, Line Status
  - Per-line controls:
    - Qty to confirm input (decimal)
    - “Confirm” button
    - Per-line success/error indicator (toast or inline)
- Line selection UI (for scan ambiguity)
  - Modal/dialog: “Multiple matches” with list of candidate lines + select action
- Task actions (footer or top-right)
  - “Complete Picking” primary button
  - “Save Progress” / “Leave” (if partial pick allowed) with confirmation dialog
  - “Back to Work Order” link
- Global notifications
  - Success toast (line confirmed, task completed)
  - Error toast/banner (network/API error) with “Retry”
- Loading/empty states
  - Skeleton/spinner on initial load
  - Empty state if no pick lines

## Layout
- Top: Work Order reference + Pick Task status badge; secondary metadata (task id/version) aligned right
- Main (stacked):
  - Scan input row (full width) with inline error area beneath
  - Pick lines grid (full width) with per-line qty input + Confirm action at row end
- Bottom/right: Task actions area with “Complete Picking” prominent; secondary actions adjacent

## Interaction Flow
1. Load screen (“Pick Parts” for a Work Order)
   1. Call backend to load PickTask + PickLines.
   2. Render header (work order ref + task status) and pick lines with required/picked/remaining and line statuses.
   3. Enable/disable actions based on backend task/line state (completed/cancelled disables all confirm/scan; fully picked lines disable confirm).
2. Scan to select a line
   1. User scans/enters code and submits.
   2. UI calls backend “resolve scan” (preferred) and waits for match results.
   3. If exactly one line matches: highlight/select that line; prefill qty-to-confirm (default to remaining or 1 per configuration; do not auto-confirm by default).
   4. If multiple lines match: open “Multiple matches” modal; user selects intended line; then highlight/prefill qty.
   5. If no lines match: show inline scan error; keep focus in scan field; allow retry or manual search.
3. Confirm quantity for a line
   1. User enters qty and clicks “Confirm” on the selected line (or any line).
   2. Client-side validate:
      1. Qty > 0.
      2. Qty <= remaining required (unless partial/over-pick rules allow; treat as blocking until defined).
      3. Line/task state allows confirmation (not fully picked; task actionable).
   3. Submit “pick confirmation” for that line (include identifiers + version/locking token if required).
   4. On success: update line picked qty/remaining/status/version from response; show per-line success state; keep scan field ready for next scan.
   5. On deterministic backend validation error: show inline error on that line (and/or toast) without clearing user input.
4. Partial pick / save progress (alternate path)
   1. If partial pick is allowed and not all required qty is satisfied, user may leave/save.
   2. UI prompts for confirmation (“Progress saved; picking not complete”) and navigates back to Work Order detail (or stays).
5. Network/API error handling (alternate path)
   1. If resolve/confirm/complete fails due to network/server error: show global error with “Retry”.
   2. Preserve unsent changes in-memory (e.g., pending qty inputs and selected line) so user can retry without re-entry.
6. Complete picking
   1. User clicks “Complete Picking”.
   2. UI validates completion readiness:
      1. Task is in completable state per backend.
      2. No outstanding required qty remains (unless partial completion allowed; blocking).
   3. Call backend “complete picking” transition.
   4. On success: update task status to Completed; disable scan/confirm controls; show success message; navigate back to Work Order detail (or provide “Back” action).

## Notes
- New picking execution screen is required (path/name TBD) and is primarily tied to Work Order fulfillment; domain ownership/state machine dependencies are blocking.
- Backend contract is blocking: must define endpoints and JSON for load task/lines, resolve scan, confirm line, and complete task; include deterministic error schema and idempotency/locking (e.g., version field) behavior.
- Enable/disable rules:
  - Confirm disabled when line fully picked or task completed/cancelled.
  - Complete disabled when task not completable or outstanding required qty remains (unless partial completion allowed; blocking).
- Scan behavior should prefer backend resolution; if backend provides normalized codes list, client-side matching may be optional optimization.
- Role-based transitions: Mechanic can confirm picks and complete task; if backend restricts completion by role/permission, UI must honor returned permissions/state.
- Acceptance criteria (from scenarios): load displays header + all lines; scan resolves to correct line; confirm updates picked/remaining and shows success; complete transitions task to completed and disables further actions.
- Open questions (blocking): exact over-pick rule, partial pick/partial completion rules, and whether task moves to “In Progress” implicitly on first confirm.
