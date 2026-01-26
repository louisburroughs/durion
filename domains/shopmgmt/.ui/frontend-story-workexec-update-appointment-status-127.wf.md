# [FRONTEND] [STORY] Workexec: Update Appointment Status from Workexec Events
## Purpose
Enable real-time, auditable appointment status updates driven by Workexec events so dispatchers and service advisors always see accurate execution state. Provide an Ops/Support-facing inbox to review event processing outcomes, inspect sanitized payload identifiers, and reprocess failures (admin-only). Ensure idempotent handling so duplicate deliveries do not create duplicate timeline entries or inbox records.

## Components
- Appointment details panel/section
  - Current appointment execution status badge (color-coded)
  - Reopen flag indicator (read-only)
  - “Status Timeline” list (append-only audit)
  - Empty state message for timeline
- Dispatch board appointment card enhancements
  - Status color/label reflecting latest execution status
  - Live update indicator (optional)
- Ops “Workexec Event Inbox” screen
  - Filters: status (PENDING/SUCCESS/FAILED), eventType, date range, workOrderId, appointmentId, eventId
  - Events table/list: receivedAt, eventTimestamp, eventType, eventId, workOrderId, resolved appointmentId, processing status, processedAt, failureReason/outcome
  - Event detail drawer/modal
    - Identifiers: eventId, correlationId, workOrderId, appointmentId (if resolved)
    - Sanitized payload preview (read-only)
    - Processing outcome summary (SUCCESS/FAILED + reason)
    - Attempts count, lastAttemptAt
    - Admin-only “Reprocess” button
- Toast/inline notifications
  - “Reprocess queued” / “Reprocess not allowed” / “No changes applied (ignored)” messages

## Layout
- Top: Page header with title and global filters/search (on Inbox screen)
- Main (Appointment view): left/main column shows appointment summary + status badge; below it “Status Timeline” list
- Main (Inbox view): filters row above an events table; right-side detail drawer opens on row click
- Inline ASCII hint: Header → [Filters] → [Events Table | Detail Drawer]

## Interaction Flow
1. Live appointment status update (primary)
   1. User views dispatch board or appointment details.
   2. System receives a backend-published `appointment.status_changed` event and updates the status badge/color in-place.
   3. Timeline list prepends/appends a new entry showing status, appliedAt, eventTimestamp, sourceEventId, and correlationId (if present).
2. View appointment status timeline
   1. User opens an appointment.
   2. If entries exist, show chronological list with: status applied, appliedAt, eventTimestamp, sourceEventId, workOrderId, correlationId (if present).
   3. If none, show empty state: “No status changes recorded yet.”
3. Ops review inbox events
   1. Ops opens “Workexec Event Inbox.”
   2. Apply filters (e.g., FAILED only) and view matching events.
   3. If no results, show empty state: “No events found for selected filters.”
   4. Click an event row to open detail drawer/modal with sanitized payload and identifiers.
4. Reprocess a failed event (admin-only)
   1. Admin opens a FAILED event detail.
   2. Click “Reprocess.”
   3. UI confirms action (lightweight confirm) and submits reprocess request.
   4. UI updates attempts/lastAttemptAt and shows “Reprocess queued”; event remains FAILED/PENDING until backend updates status.
5. Edge case: duplicate delivery / idempotency
   1. Ops sees repeated deliveries with same eventId are not duplicated in inbox (single record).
   2. Timeline does not show duplicate entries for the same sourceEventId.
6. Edge case: terminal/precedence rule (CANCELLED)
   1. Appointment is CANCELLED; a new event maps to a non-reopen status.
   2. UI shows inbox outcome SUCCESS with note “ignored due to precedence.”
   3. Appointment status remains CANCELLED; no new timeline entry is shown for that event.
7. Edge case: validation failures surfaced as outcomes
   1. Ops opens a FAILED event and sees failureReason such as: unsupported eventType, work order not found, missing appointment link, appointment not found, missing status mapping, missing required fields.
   2. UI remains read-only for validation logic; it only displays backend outcomes and reasons.

## Notes
- UI is not responsible for validation; it must display processing outcomes and failure reasons returned by backend ingestion/processor.
- Inbox must support viewing sanitized payload (redacted) and key identifiers (eventId, correlationId, workOrderId, appointmentId when resolved).
- Reprocess control is admin-only; non-admin users should not see the button or should see it disabled with “Not authorized.”
- Real-time behavior: frontend listens to `appointment.status_changed` to update dispatch board and appointment views without refresh.
- Acceptance criteria highlights:
  - Successful WorkorderStatusChanged updates appointment execution status and appends a single timeline entry per eventId.
  - REOPENED sets reopenFlag true and status REOPENED; reflected in badge and timeline.
  - CANCELLED blocks non-reopen updates; show “ignored due to precedence” outcome and no timeline entry.
- TODOs/blocked decisions to reflect in UI copy/fields:
  - Final appointment status field name and whether `reopenFlag` exists or must be added.
  - Ingest endpoint path/shape is backend-defined; UI assumes inbox records exist with statuses PENDING/SUCCESS/FAILED.
  - Mapping table (Workexec status → Appointment status) is backend-owned; UI should display mapped result/status text only.
