# [FRONTEND] [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment
## Purpose
Enable dispatch users to assign one or more mechanics and a primary resource (bay or mobile unit) to an appointment, creating a new active assignment via API. Provide clear validation and error handling for missing selections, role rules, and availability/skills conflicts. Support an override flow when permitted, capturing reason code, notes, and overridden checks, and display assignment history with audit details.

## Components
- Appointment header summary (status, start/end time, identifiers)
- Active Assignment summary panel (status, version, resource, mechanics + roles, override indicator)
- “Create / Reassign Assignment” form
  - Resource selector (bay/mobile unit dropdown/search)
  - Mechanics selector (multi-select list with availability + skill/cert badges)
  - Role assignment control per mechanic (LEAD/ASSIST or equivalent)
  - Submit button (Create Assignment / Reassign)
  - Inline validation messages
- Error banner/toast area (API errors + normalized codes)
- Override prompt + modal/panel (shown only when eligible)
  - Override toggle/checkbox
  - Reason code selector
  - Notes text area
  - Overridden checks multi-select
  - Resubmit button (requires new idempotency key)
- Assignment History list (newest-first) with empty state
- Empty states for: no assignments, no mechanics available, no resources available
- Loading states (skeleton/spinner) for rosters and history

## Layout
- Top: Appointment header summary + global error/banner area
- Main (two-column): Left = Create/Reassign form; Right = Active Assignment summary
- Below main: Assignment History list (full width) with empty state messaging

## Interaction Flow
1. Load appointment details (must include status + start/end time) and fetch assignment history/active assignment; render active summary if present, else “No active assignment.”
2. Fetch resource roster and mechanic roster (with availability + skills/certs); if empty, show explicit empty state and disable submit.
3. User selects a primary resource; if missing on submit, show inline “Select a resource.” and block submit.
4. User selects mechanics (min 1); if none on submit, show inline “Select at least one mechanic.” and block submit.
5. Single-mechanic create:
   1. User selects exactly one mechanic and a resource.
   2. User submits without specifying role.
   3. UI sends create assignment request (with appointment id, mechanics array, resource id; no role required for single mechanic).
   4. On success, show active assignment with status ACTIVE and mechanic displayed as LEAD (from API or inferred).
6. Multi-mechanic create:
   1. User selects 2+ mechanics.
   2. UI requires role assignment rules (e.g., exactly one LEAD); if missing roles or multiple leads, show role rule message and block submit.
   3. Submit create assignment; on success, update active summary and prepend to history.
7. Reassignment replaces active assignment:
   1. If an active assignment exists, user creates a new assignment for the same appointment.
   2. On success, UI shows only one active assignment (the new one) and moves the previous assignment into history with a non-active status (e.g., COMPLETED/CANCELLED) as returned by backend.
8. API error handling on create:
   1. 400 malformed/missing fields → show field-level errors when provided; otherwise show generic “Fix highlighted fields.”
   2. 403 permission denied → show blocking message; disable submit.
   3. 404 appointment not found → show blocking message and offer navigation back.
   4. 409 availability conflict / active assignment race → show conflict message; refresh active assignment + history.
   5. 422 skills/role validation → show specific validation message; highlight mechanics/roles.
9. Override flow (allowed and succeeds):
   1. If create fails with override-eligible error code, show “Override available” prompt.
   2. User enables override, selects reason code, enters notes, selects overridden checks.
   3. User resubmits with a new idempotency key.
   4. On success, show “Override used” indicator on active assignment and include override details in history/audit display.

## Notes
- Client-side validation must block submit for: missing resource, no mechanics, multi-mechanic role rule violations (including multiple leads).
- Assignment history must display (when provided): created timestamp, created by (display name/id), status + version, override indicator + reason code.
- Sorting: prefer newest-first from API; if not guaranteed, sort by created timestamp when available, else by updated timestamp when available.
- Override payload requirements: override flag must be true when override object present; overridden checks must be non-empty; reason code and notes required when override enabled.
- Reassignment behavior: backend is source of truth for which assignment is active; UI should refresh history/active after successful create and after conflict errors.
- Roster endpoints/entities for mechanics and resources are TBD; UI should be resilient to partial data (e.g., missing skills) and still allow selection unless blocked by validation rules.
- Safe defaults: always show explicit empty states for “no assignments,” “no mechanics,” and “no resources” to avoid blank panels.
- Implementation note alignment: show mechanic availability (available/busy/on-break) and certification/skill badges; disable assignment when missing required certifications if rules are available from roster data.
