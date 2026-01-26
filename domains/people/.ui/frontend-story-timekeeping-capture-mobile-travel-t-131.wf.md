# [FRONTEND] [STORY] Timekeeping: Capture Mobile Travel Time Separately
## Purpose
Enable Mobile Technicians to capture travel time as discrete travel segments tied to the current mobile assignment (or work order context) directly from the mobile detail screens. The screen should clearly show whether a travel segment is currently in progress, allow starting/ending a segment, and display completed/cancelled segments with timestamps and duration. This separates travel time from other timekeeping and supports billable/non-billable calculations and downstream mileage/expense workflows.

## Components
- Top app bar/header with title “Travel Time” and back navigation
- Context summary (Assignment/Appointment/Work Order identifier and key details)
- Loading state (skeleton/spinner) and empty state
- Active segment banner/card
  - Status indicator (e.g., “In Transit”)
  - Start timestamp (and optional segment type)
  - Primary button: “End Segment”
- Segments list (for current assignment)
  - Segment row: type, status, start/end timestamps, derived duration (when completed)
  - Visual state styling for Active / Completed / Cancelled
- Primary action button: “Start Segment” (visible only when no active segment)
- Start Segment modal/dialog
  - Segment Type selector (enum-driven from backend if available)
  - Confirm and Cancel buttons
  - Inline validation messaging (field-level when details provided)
- End Segment confirmation modal/dialog
  - Confirm and Cancel buttons
- Error state components
  - Inline error banner/toast with correlationId when present
  - Not found state
  - Access denied state (with disabled actions)

## Layout
- Top: App bar (Back) + “Travel Time”
- Below header: Context summary (Assignment/Appointment/Work Order)
- Main:
  - If active: Active segment banner at top of content + “End Segment” primary action
  - Segments list below (most recent first)
- Bottom (sticky footer area): “Start Segment” primary button when no active segment; otherwise hidden/disabled

## Interaction Flow
1. Screen entry (from Mobile Assignment/Appointment detail or Work Order detail via “Travel Time” action/tab)
   1. Fetch assignment context and travel segments on route load.
   2. Show loading state until data returns; then render active segment (if any) and segments list.
2. Start Segment (happy path)
   1. Preconditions: no active segment exists for the assignment (or backend indicates no activeSegment).
   2. User taps “Start Segment”.
   3. Start Segment modal opens; user selects Segment Type.
   4. User confirms; submit create request with required mutation header.
   5. On success: refresh/re-render list; show active “In Transit” indicator and enable “End Segment”.
3. End Segment (happy path)
   1. Preconditions: exactly one active segment exists (or backend provides activeSegment).
   2. User taps “End Segment”.
   3. Confirmation modal opens; user confirms.
   4. Submit end request with required mutation header.
   5. On success: refresh/re-render list; segment shows completed with end timestamp and derived duration; “Start Segment” becomes available.
4. State rendering rules
   1. Active segment: show active banner + “End Segment”; hide “Start Segment”.
   2. Completed segment: show start/end timestamps and derived duration; no actions on the row.
   3. Cancelled segment: show cancelled styling/status; no actions.
5. Error and edge-case handling
   1. Validation errors: show message; highlight field when field details are provided.
   2. Access denied: show access denied state; disable Start/End actions.
   3. Assignment/segment not found: show not found state; no actions.
   4. Conflict (active segment exists / stale state): refresh segments list and show conflict message (include correlationId if present).
   5. Domain errors to surface (as applicable): TRAVEL_OUTSIDE_SESSION, INVALID_TRAVEL_RANGE.

## Notes
- TravelSegment fields to display/use:
  - id (opaque string), assignmentId (opaque), technicianId (opaque, may be implied), type (enum), startTime (ISO), endTime (ISO nullable), status (enum: ACTIVE/COMPLETED/CANCELLED), durationMinutes (derived; display when completed).
- Segment Type list should be enum-driven from backend when available; do not ship a static list if backend enum is not available (blocked).
- All UI mutations (start/end) must include the required mutation header pattern.
- Include correlationId in user-facing error details when present to aid supportability.
- Ensure only one active segment is actionable; if multiple actives appear, treat as conflict and force refresh + message.
- Implementation notes to accommodate (may be separate UI surfaces but must not be contradicted here): billability toggle for travel, miles capture, location picker/auto-fill suggestions, and integration with mileage/expense reimbursement; billable minutes inclusion/exclusion depends on billable flag and supervisor approval rules.
