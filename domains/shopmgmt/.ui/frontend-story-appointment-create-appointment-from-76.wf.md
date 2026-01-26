# [FRONTEND] [STORY] Appointment: Create Appointment from Estimate or Order
## Purpose
Enable a Scheduler to create an appointment directly from an eligible Estimate (APPROVED/QUOTED) or Work Order (not COMPLETED/CANCELLED) within a specific facility. The screen must capture scheduled time in the facility timezone, submit an idempotent create request, and route to Appointment Detail on success. The UI must handle conflict responses (HARD vs SOFT) and enforce permission-gated override behavior without leaking cross-facility or unauthorized resource details.

## Components
- Page header: “Create Appointment”
- Context summary panel: Source type (Estimate/Order), source identifier, facility identifier, facility timezone (read-only)
- Appointment form
  - Facility (read-only or preselected) + facilityTimeZoneId display (read-only)
  - Scheduled start date/time input (timezone-aware; ISO-8601 with offset on submit)
  - Scheduled end date/time input (timezone-aware; ISO-8601 with offset on submit)
  - Assignment selector (mechanic/assignee) using shopmgmt-provided display fields (no direct People calls)
  - Client request ID (hidden; UUID generated client-side)
- Primary actions
  - Submit button: “Create Appointment”
  - Secondary button/link: “Back to Source”
  - Secondary button: “Cancel”
- Inline validation messages (field-level)
- Global error banner/toast with correlationId display
- Conflict handling UI (shown on 409)
  - Conflict summary list with severity badges (HARD/SOFT), code, message
  - Alternative slots list (start/end + reason)
  - SOFT conflict override section (permission-gated)
    - Checkbox/toggle: “Override soft conflicts”
    - Textarea: “Override reason” (required when override enabled)
    - Resubmit button: “Create Anyway”
- Loading state (submit in progress) + disabled controls
- Accessibility helpers: ARIA labels, focus target for errors/conflicts

## Layout
- Top: Header + breadcrumbs/back link to source
- Main (single column)
  - Source/Facility summary card (includes facilityTimeZoneId)
  - Appointment form card (start/end, assignment)
  - Actions row (Create, Cancel, Back to Source)
  - Conditional sections below form:
    - Error banner (with correlationId)
    - Conflict panel (409 only) with alternatives + override controls (if allowed)
- Footer: Support hint text including correlationId when present

## Interaction Flow
1. Entry from Source (Estimate/Order): user clicks “Create Appointment” on an eligible source; creation screen loads with facilityId and facilityTimeZoneId displayed.
2. User enters scheduledStartDateTime and scheduledEndDateTime using facility timezone UI; selects assignment (if applicable/available).
3. On submit:
   1. Generate clientRequestId (UUID) if not already set; include in request (and Idempotency-Key header if supported).
   2. Send CreateAppointmentRequest with sourceType, sourceId, facilityId, scheduledStartDateTime (offset), scheduledEndDateTime (offset), clientRequestId.
4. Success (200/201): show brief success state (optional) and navigate to Appointment Detail using returned appointmentId; ensure response includes sourceId/sourceType and facilityTimeZoneId.
5. Validation error (400): show field-level errors; focus first invalid field; show correlationId in global banner.
6. Permission/deny (403): show generic access denied message (no resource existence hints) with correlationId; keep user on page with actions disabled only as needed.
7. Not found (404): show generic “Unable to create appointment” (no resource hint) with correlationId; offer “Back to Source”.
8. Eligibility failure (422): show eligibility message and provide “Back to Source” link; do not expose extra details beyond safe message + correlationId.
9. Conflict (409):
   1. Render conflict panel listing conflicts with severity (HARD/SOFT), code, message, overridable flag, and alternative slots (start/end + reason).
   2. If any HARD conflicts: disable override controls; block creation; user may adjust times or choose an alternative slot and resubmit.
   3. If only SOFT conflicts and user has override permission: allow “Override soft conflicts” toggle; require overrideReason when enabled; resubmit with overrideSoftConflicts=true and overrideReason.
   4. If only SOFT conflicts but user lacks override permission: show conflicts and alternatives; no override option; user must adjust schedule.
10. Server error (5xx): show generic failure with correlationId; allow retry (idempotent) without duplicating appointments.

## Notes
- Facility scoping is explicit: facilityId is required; deny-by-default; do not leak cross-facility or unauthorized resource existence in 403/404 messaging.
- Timezone handling: display and input in facilityTimeZoneId; submit scheduled times as ISO-8601 with offset; createdAt/lastUpdatedAt are UTC (display optional).
- Conflict rules: HARD conflicts are not overridable (including operating-hours violations); SOFT conflicts may be overridable only with permission and required overrideReason.
- Idempotency: clientRequestId required; support safe retry behavior; keep clientRequestId stable across retries from the same form session.
- Error handling: always surface correlationId (from X-Correlation-Id) in error UI for support reference.
- Data privacy: do not display customer PII; override reason must not be logged client-side; ensure any displayed strings are “safe”.
- Accessibility: WCAG 2.1 AA; keyboard navigation; ARIA labels; focus management to error banner/conflict panel on submit failures; responsive layout for tablet/desktop.
- TODO (implementation): confirm exact permission identifiers for CREATE_APPOINTMENT and OVERRIDE_SCHEDULING_CONFLICT; wire to authorization checks to gate form access and override UI.
