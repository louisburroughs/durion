# [FRONTEND] [STORY] Scheduling: Reschedule or Cancel Appointment with Audit

## Purpose
Enable Dispatchers to reschedule or cancel an existing appointment from the Appointment Edit screen while preserving an audit trail of changes. The UI must prevent modifications when the appointment is not in a modifiable status and provide clear feedback when actions are disabled or conflicts occur. After successful changes, the screen should reflect updated appointment details and show audit history (or a fallback message if audit is unavailable).

## Components
- Page header: “Appointment” + appointment identifier
- Read-only appointment summary panel
  - Status (read-only)
  - Scheduled window: start/end (read-only)
  - Assigned resource/technician (read-only)
  - Related references (read-only): work order / estimate (if present)
- Action buttons (primary actions)
  - Reschedule (enabled only when status == Scheduled)
  - Cancel (enabled only when status == Scheduled)
  - Helper text when disabled: “This appointment is <status> and cannot be modified.”
- Reschedule modal/dialog (or sub-screen)
  - Start datetime picker (prefilled)
  - End datetime picker (prefilled)
  - Resource/technician selector (prefilled if present; optional)
  - Reason field (text)
  - Validation messages (start required, end required, start < end)
  - Submit button + Cancel/Close button
- Cancel modal/dialog
  - Cancellation reason code dropdown (active codes only)
  - Optional notes textarea
  - Confirm Cancel button + Back/Close button
- Audit section
  - If audit endpoint available: list/table of audit entries (timestamp, actor, action, details)
  - Else: created/updated metadata + placeholder “Audit history not available”
- Inline alerts/toasts
  - Success messages (rescheduled/cancelled)
  - Error messages (409 stale update, scheduling conflict, invalid time)

## Layout
- Top: Header (Appointment title + ID)
- Main (single column):
  - Appointment Summary (read-only card)
  - Actions row (Reschedule, Cancel) + helper text area
  - Audit History section (list/table or fallback metadata)
- Modals overlay main content:
  - Reschedule dialog
  - Cancel dialog

## Interaction Flow
1. Page load (AppointmentEdit)
   1. Load appointment details and render read-only summary.
   2. Attempt to load audit history; if unavailable, show created/updated metadata and “Audit history not available.”
   3. Set action button states:
      1. If status == Scheduled: enable Reschedule and Cancel.
      2. Else: disable both and show helper text: “This appointment is <status> and cannot be modified.”
2. Reschedule a Scheduled appointment
   1. User clicks Reschedule.
   2. Open Reschedule modal with fields prefilled from current appointment (start, end, resource if present).
   3. (Optional) Load resource picker options / availability; show available slots based on technician schedule + work order estimates.
   4. User edits start/end/resource and enters reason.
   5. Client-side validate: start present, end present, start < end.
   6. On Submit:
      1. Generate idempotency key (UUID) for this submit attempt; reuse on retry of the same attempt.
      2. Call reschedule service.
   7. On success:
      1. Redirect (PRG) back to AppointmentEdit (GET) to avoid double-submit on refresh/back.
      2. Reload appointment details and audit section; show updated window/resource and new audit entry (action: Rescheduled) or updated metadata if audit not available.
   8. On errors:
      1. Scheduling conflict (SCHEDULING_CONFLICT): show error and keep modal open for adjustments.
      2. Invalid time (INVALID_APPOINTMENT_TIME, e.g., time in past): show error and keep modal open.
      3. 409 stale update: refresh appointment details, show message, and re-evaluate whether actions remain enabled.
3. Cancel a Scheduled appointment
   1. User clicks Cancel.
   2. Open Cancel modal.
   3. Lazy-load cancellation reason codes if not already loaded; show only active codes.
   4. User selects a reason code (required) and optionally enters notes.
   5. Confirm Cancel:
      1. Call cancel service.
      2. On success: redirect (PRG) to AppointmentEdit (GET), show status as Cancelled, display reason code and notes, and update audit section (action: Cancelled) or fallback metadata.
   6. If status changes before submit (stale UI):
      1. Handle 409 by refreshing and showing “This appointment is <status> and cannot be modified.”

## Notes
- Modifiable only when appointment status == Scheduled; expected statuses referenced include Scheduled, Cancelled, Completed, and other non-modifiable states (exact IDs TBD).
- Read-only fields: appointment id, status, created/updated timestamps and actors; work order/estimate references are read-only (exact relationship direction TBD).
- Reschedule edits: scheduledStart, scheduledEnd, and optionally assigned resource/technician (if supported).
- Cancel requires a valid cancellation reason code; notes optional; display both after cancellation.
- Audit behavior:
  - If audit endpoint exists: show entries with timestamp (UTC), actor, action (Created/Updated/Rescheduled/Cancelled as applicable), and details payload if present.
  - If not: show created/updated metadata and placeholder “Audit history not available.”
- PRG (Post/Redirect/Get) is required after successful reschedule/cancel to prevent double-submit on refresh/back.
- Include reason field in reschedule dialog and ensure it is logged into appointment history/audit where supported.
- Frontend should surface domain errors from fixtures: technician unavailable (SCHEDULING_CONFLICT) and new time in past (INVALID_APPOINTMENT_TIME).
- After reschedule success, UI should reflect technician + time updates; downstream effects (customer notification, WorkExec event) are not UI-visible but should not block UI refresh.
