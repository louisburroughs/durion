# [FRONTEND] [STORY] Counts: Execute Cycle Count and Record Variances
## Purpose
Enable an auditor to execute a cycle count task by entering an actual quantity (blind to expected quantity) and submitting it to create an immutable count entry. Provide clear task context (identifiers, location, product, UOM), backend-driven action gating (count/recount/confirm), and a read-only history of submitted counts. Reflect task status and recount eligibility, and handle concurrency conflicts and missing history states safely.

## Components
- Page header: “Cycle Count” + task identifier
- Task context panel (read-only):
  - Task ID, task type/status
  - Site label/barcode (if provided)
  - Bin label/barcode (if provided)
  - Product SKU + description (fallback to productId if name unavailable)
  - UOM label (if provided)
- Status indicator (read-only):
  - Current task status
  - Recount eligibility / recount remaining (if provided)
  - Escalated/cap-exceeded state messaging
- Count entry form:
  - Numeric quantity input (decimal-capable)
  - Inline validation messaging (required/number/range as applicable)
  - Helper text: “Expected quantity is hidden until after submission.”
- Action buttons (backend-driven gating):
  - Submit Count (initial; enabled only when task is countable and user authorized)
  - Request Recount (enabled only when backend indicates recount is allowed)
  - Confirm Recount (visible/enabled only when recount mode is active)
- History section (read-only list of immutable count entries):
  - Sequence number (1-based), timestamp, counted quantity
  - Optional: counted by (user display if available)
  - Optional post-submit fields (e.g., variance) displayed only after entry is recorded
- Banners/alerts:
  - Concurrency conflict (409) refresh prompt
  - Missing history error (when unexpected)
  - “No counts submitted yet.” empty state
- Modal or inline prompt:
  - “Refresh to load latest task state” (for 409 or stale status)

## Layout
- Top: Header (Cycle Count) + Task ID
- Main (two-column on desktop; stacked on mobile):
  - Left/Main: Task context panel → Count entry form → Action buttons
  - Right/Below: Status indicator card → History list
- Footer area (optional): Support/help link or brief policy note (e.g., blind count)

## Interaction Flow
1. Load task details and flags (countable, recountAllowed, recountModeActive, recountRemaining, status).
2. Render task context (site/bin/product/UOM) with fallbacks (productId if product name missing).
3. Render status indicator reflecting current task status and recount eligibility.
4. If task is countable and user authorized, enable numeric input and show “Submit Count”; otherwise disable input and show reason via status/banner.
5. Auditor enters actual quantity (numeric/decimal) and submits.
6. UI calls backend (Moqui proxy) to create a CountEntry for the task.
7. On success, UI refreshes task state and history; show new immutable entry with sequence = 1 (or next sequence).
8. Ensure expected quantity is not displayed prior to submission; only show post-submit fields returned for recorded entries (e.g., variance) in history.
9. If backend indicates recount is allowed, show/enable “Request Recount”; when recount mode becomes active, show/enable “Confirm Recount” and adjust messaging.
10. If recount cap exceeded or task escalated, backend sets status (e.g., ESCALATED); UI reflects status, disables count input/actions, and shows an explanatory banner.
11. Concurrency edge case: if backend returns 409 (task changed since load), show refresh prompt; on refresh, reload task and history.
12. History empty handling:
    1. If task is in a state where no counts are expected yet, show “No counts submitted yet.”
    2. Otherwise show error banner: “Counts missing; refresh or contact support.”

## Notes
- Backend-driven gating is preferred: UI should rely on provided flags/status enums rather than duplicating policy logic.
- Numeric input must validate as a number (decimal-capable); show inline errors and prevent submission when invalid/empty.
- Blind count requirement: do not display expected quantity during entry; only show recorded outcomes after submission in history.
- History entries are immutable and read-only; display sequence (1-based), timestamp, counted quantity, and any optional metadata returned.
- When status indicates escalated/cap exceeded (or equivalent), disable count inputs and all count/recount actions; status indicator must clearly communicate why.
- Concurrency: handle 409 with a clear refresh action; avoid silently overwriting or continuing with stale task state.
- Missing history is only acceptable in specific “not yet counted” states; otherwise treat as an error condition with guidance to refresh/contact support.
- Risk: incomplete requirements—design should allow for optional fields (site/bin labels/barcodes, product details, UOM, recount counters) and graceful fallbacks.
