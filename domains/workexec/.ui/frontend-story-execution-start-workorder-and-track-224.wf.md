# [FRONTEND] [STORY] Execution: Start Workorder and Track Status
## Purpose
Enable users to start a work order from its detail screen and track execution status changes via an append-only transition history. Provide an in-progress reason selector only when the work order is actively in progress, and ensure the UI reflects backend gating rules (including blocking starts when an advisor-review change request is pending). After successful mutations, keep the user on the same screen and refresh work order details and history.

## Components
- Page header with work order identifier and current status
- Work order summary panel (key fields: status, started flag, startedAt, inProgress flag)
- Primary action button: Start
- Blocking/eligibility message area (inline alert)
- In-progress reason section
  - Reason dropdown/select (enum)
  - Save/Update reason button (or auto-save on change, if supported)
  - Field-level validation error display for invalid reason
- Transition history list/table
  - Rows: timestamp (user TZ), fromStatus, toStatus, reason (optional), actor/userId, message/notes (optional)
- Loading/refresh indicator for work order + history
- Error toast/alert for backend error envelope messages

## Layout
- Top: Header (Work Order ID) + status badge + optional started/in-progress indicators
- Main (two stacked panels): [Summary + Actions] above [Transition History]
- Summary panel: left = details; right = actions (Start button) and reason selector area
- History panel: full-width list/table with newest-first ordering

## Interaction Flow
1. Load screen (work order detail route) and fetch:
   1. Work order details (status, isStarted, startedAt, inProgressReason, isInProgress)
   2. Transition/history events dataset
2. Render state-based controls:
   1. If status is pre-start (e.g., CREATED/PLANNED): show Start button; hide reason selector
   2. If status is IN_PROGRESS: hide Start button; show enabled reason selector
   3. If status is started-but-not-in-progress (e.g., STARTED): hide Start button; hide or disable reason selector
   4. Always show history list
3. Start work order (primary flow):
   1. User clicks Start
   2. UI sends start request with Idempotency-Key header (new key per attempt; reuse on retry)
   3. On success: remain on the same screen; refresh work order + history
   4. UI shows updated status (and startedAt if returned) and history includes a new transition entry for the start
   5. If backend returns startedAt, display it in the summary
4. Blocked start due to pending advisor-review change request (edge case):
   1. User clicks Start while a ChangeRequest is pending advisor review
   2. Backend rejects; UI displays backend-provided message indicating approval is required
   3. UI refreshes work order + history; status remains unchanged
5. Start attempted when not start-eligible (validation/consistency edge case):
   1. Backend returns 400/409 with message
   2. UI displays message and refreshes work order + history to reconcile state
6. Update in-progress reason (when IN_PROGRESS):
   1. User selects a reason and submits (or triggers save)
   2. UI sends reason update with Idempotency-Key header (new key per attempt; reuse on retry)
   3. On success: remain on the same screen; refresh work order + history
7. Reason update attempted when not IN_PROGRESS (edge case):
   1. Backend returns 400/409; UI displays message and refreshes work order + history
8. Invalid reason enum (field validation):
   1. Backend returns 400 with field error for inProgressReason
   2. UI shows field-level error on the reason selector and keeps current selection visible for correction

## Notes
- Treat all identifiers as opaque strings; do not validate UUID/numeric formats client-side.
- Prefer backend-provided booleans (e.g., isStarted, isInProgress) to avoid client-side status mapping drift; “Started” means STARTED or later.
- Start must be rejected if there is a pending change request requiring advisor review; surface backend message verbatim via normalized error envelope.
- All mutation requests (start, reason change) must include Idempotency-Key and reuse it on retry for the same attempt.
- Transition history timestamps are stored UTC and must be displayed in the user’s timezone.
- After any successful mutation or backend validation failure, remain on the same screen and refresh both work order details and history to ensure UI consistency.
- Ensure history is always visible regardless of status; include new transition entries after refresh.
