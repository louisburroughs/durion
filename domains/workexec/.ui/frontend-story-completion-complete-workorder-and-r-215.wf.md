# [FRONTEND] [STORY] Completion: Complete Workorder and Record Audit
## Purpose
Enable Service Advisors/Shop Managers to explicitly complete a work order from eligible statuses, optionally recording completion notes and reason, and triggering the backend completion transition. After completion, the UI must reflect the updated status, show completion audit metadata and transition history when available, and enforce a locked/read-only execution experience. The UI must handle backend validation and concurrency/transition conflicts with clear, actionable feedback.

## Components
- WorkOrderEdit header
  - Work order identifier display
  - Current status badge/label
  - Primary action area (includes “Complete Work Order” when eligible)
- “Complete Work Order” action (button/menu item)
  - Disabled state with inline explanation when not eligible
- Confirmation modal/dialog: “Complete Work Order”
  - Optional Completion Reason (select/enum if provided by backend contract)
  - Optional Completion Notes (multiline text)
  - Confirm button (calls backend)
  - Cancel button
  - Inline field error messages (per-field)
  - Banner error area (top of modal or page-level)
  - Expandable “Details” disclosure for support (shows error details from envelope)
- Completion section (read-only)
  - Completed At (datetime)
  - Completed By (user/id)
  - Completion Notes (text, if present)
  - Completion Reason (if present)
- Status transition history (read-only list/table, when available)
  - Rows: from status → to status, timestamp, actor (if available)
- Execution-scope editing surfaces (existing WorkOrderEdit areas)
  - Parts/labor/services/fees/line edit actions (must be disabled/hidden when locked)
  - Read-only indicators/tooltips when locked

## Layout
- Top: WorkOrderEdit header (ID + Status badge) | Right-aligned actions (Complete Work Order when eligible)
- Main: Work order execution sections (parts/labor/services/fees/lines) with edit controls inline
- Lower main/right rail: Read-only “Completion” section (visible when status is COMPLETED or when completion fields exist)
- Bottom/main: “Status Transition History” read-only list/table (visible when available)

## Interaction Flow
1. Eligible completion (happy path)
   1. User opens WorkOrderEdit for a work order not in COMPLETED or CANCELLED.
   2. UI evaluates availability: show “Complete Work Order” only when capability/authorization indicates allowed; otherwise hide/disable.
   3. User clicks “Complete Work Order”; confirmation dialog opens.
   4. User optionally enters Completion Reason and/or Completion Notes.
   5. User clicks Confirm; UI sends completion command to backend including an idempotency key (and concurrency token like lastUpdatedStamp/etag if available).
   6. On 200 OK, UI refreshes work order details and renders updated status as COMPLETED.
   7. UI locks execution editing: disable/hide parts/labor/services/fees/line edit actions and any execution-scope edits.
   8. UI shows read-only Completion section (Completed At/By + any reason/notes) and shows transition history when available.

2. Work order already COMPLETED
   1. If status is COMPLETED on load, hide completion action.
   2. Show Completion section read-only and keep execution surfaces locked/read-only.

3. Work order CANCELLED
   1. If status is CANCELLED on load, hide completion action.
   2. Ensure execution surfaces are read-only.

4. Not eligible in current status (non-completed/cancelled)
   1. If backend/capability indicates not allowed, disable completion action with explanation when known (e.g., “Not eligible in current status”).
   2. Do not hardcode an eligibility list beyond excluding COMPLETED/CANCELLED; rely on backend response for other statuses.

5. Backend validation failure (400/422-style)
   1. User confirms completion; backend returns validation errors in standard envelope.
   2. Render field-level errors inline (e.g., reason field) when mapped.
   3. Render a banner error message summarizing failure.
   4. Provide an expandable “Details” area containing error codes/messages for support.
   5. Keep dialog open for correction; do not lock UI.

6. Concurrency/invalid transition conflict (409 or 400 with conflict code)
   1. User confirms completion; backend responds with conflict/invalid transition.
   2. UI shows an actionable conflict message (banner) indicating the work order changed and the action may no longer be allowed.
   3. UI refreshes work order details and re-evaluates action availability.
   4. If refreshed status is now COMPLETED, treat as success state (show completion metadata and lock edits).
   5. Otherwise, keep completion action disabled/hidden per refreshed eligibility.

7. Idempotency behavior on repeat attempt
   1. If backend returns 200 with the same completed representation for a matching idempotency key, treat as success and refresh UI state.
   2. If backend returns 409 “already completed,” refresh work order; if status is COMPLETED, treat as success state.

## Notes
- Completion must be explicit: always require user confirmation in a dialog before calling backend.
- Do not invent required fields: Completion Reason/Notes are optional unless the backend contract explicitly requires a reason; if required by contract, enforce client-side required validation.
- Locking rules after completion: once COMPLETED, disable/hide all execution-scope editing actions in WorkOrderEdit (parts/labor/services/fees/line edits and related execution UI).
- Status rules:
  - COMPLETED: hide completion action; show completion metadata; lock execution edits.
  - CANCELLED: hide completion action; execution surfaces read-only.
  - Other statuses: show completion action only if capability indicates allowed; otherwise disabled with explanation when known.
- Error handling requirements:
  - Inline field errors for validation issues.
  - Banner error for overall failure.
  - Expandable “Details” area with error envelope content for support/debug.
- Transition history and audit are read-only and should render only when data is available; treat transition records as append-only.
- Concurrency support: include optimistic concurrency token (lastUpdatedStamp/etag) if provided; on conflict, refresh and re-evaluate, treating “now completed” as success.
- Ensure frontend triggers only the completion transition/service; do not initiate billing/accounting side effects beyond what backend completion transition performs.
