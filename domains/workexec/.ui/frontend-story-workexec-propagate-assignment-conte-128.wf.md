# [FRONTEND] [STORY] Workexec: Propagate Assignment Context to Workorder
## Purpose
Expose and propagate Work Order assignment context fields into the Work Order detail/edit experience so users can view and (when allowed) update assignment context. Ensure edits are permitted only pre-start, with clear locked-state messaging after work starts. Handle concurrency/status changes during editing with a clear error and a forced reload to reflect the latest Work Order state.

## Components
- Work Order Detail/Edit page container
- Header area
  - Work Order identifier (opaque id)
  - Work Order status (read-only enum)
  - Optional related id (read-only; may be displayed elsewhere if present)
- Assignment Context section
  - Display rows/fields for assignment context values (nullable)
  - “Not assigned” empty-state text for null values
  - Audit metadata display (last updated timestamp, actor; if provided)
  - Locked-state message banner/text (shown when started and no override)
- Edit controls (within Assignment Context section)
  - Edit button (shown only when editable)
  - Save button
  - Cancel button
- Inline validation/error area for save failures (standard error envelope mapped to user message)
- Loading/refresh indicator for reload after save or error

## Layout
- Top: Work Order header (ID + Status + optional related ID)
- Main: Sections stacked vertically
  - Assignment Context section (title + fields + audit metadata + edit controls or locked message)
  - Other Work Order details (existing content, not specified here)
- Inline within Assignment Context: fields in a simple two-column grid (Label | Value / Input)

## Interaction Flow
1. Open Work Order detail/edit
   1. User navigates from Work Order board/list and opens a Work Order.
   2. UI loads Work Order detail using the read contract and renders ID, status, and assignment context fields.
   3. For any null assignment context field, display “Not assigned”.
   4. If audit metadata is returned, display “Last updated {timestamp} by {actor}” (or equivalent).

2. Edit assignment context (Work Order not started / pre-start)
   1. If status indicates not started (pre-start), show Assignment Context as editable with an Edit button.
   2. User clicks Edit; fields become inputs (for whichever assignment context fields backend returns).
   3. User changes one or more fields and clicks Save.
   4. Frontend sends update request including an `If-Match` header (using the returned ETag/token if available).
   5. On success:
      1. Exit edit mode.
      2. Reload the Work Order detail.
      3. Display updated assignment context values and updated audit metadata (if provided).

3. Assignment context locked after work starts (no override capability)
   1. If status indicates started or later, render Assignment Context section read-only.
   2. Hide/disable edit controls.
   3. Show messaging: assignment context is locked after work starts.

4. Save rejected due to status change during edit (concurrency/status transition)
   1. User enters edit mode while Work Order is not started.
   2. Before Save, Work Order becomes started (status transition occurs elsewhere).
   3. User clicks Save; backend responds non-2xx (e.g., 409 or 400) with standard error envelope.
   4. UI shows an error indicating the update cannot be applied after work started.
   5. UI reloads the Work Order detail and exits edit mode to reflect current status/values.

## Notes
- Assignment context fields are backend-driven; render only the field(s) returned by the Work Order detail contract. Future multi-mechanic support must be additive; do not hardcode assumptions beyond current returned fields.
- Enforce started gating server-side; UI should reflect read-only state based on status but must handle backend rejection gracefully.
- Use `If-Match` for idempotency/concurrency when supported; if token is absent, still attempt update but expect higher likelihood of conflict handling via status gating.
- Error handling: map standard error envelope to a user-facing message; for 409/400 due to started gating, use a specific message (“Cannot update assignment context after work has started”).
- Empty state requirement (SD-UX-EMPTY-STATE): display “Not assigned” for null assignment fields (purely presentational).
- After any save attempt (success or gating failure), reload Work Order to ensure UI reflects latest status and audit metadata.
