# [FRONTEND] [STORY] Appointment: Show Assignment (Location/Bay/Mobile + Mechanic)
## Purpose
Display an appointment’s current assignment (location type such as Bay or Mobile Unit, plus mechanic) on the Appointment Detail screen for authorized users. Ensure the assignment section is read-only while still showing key identifiers and any assignment notes. While the user is viewing the screen, reflect near-real-time assignment changes via a facility-scoped SSE stream and notify the user when updates occur.

## Components
- Appointment Detail header (appointment identifier/context)
- Assignment section container (read-only)
- Assignment type label/value (e.g., Bay, Mobile Unit, Location)
- Assigned bay display (identifier + optional name)
- Assigned mobile unit display (identifier + optional name; optional coordinates/time fields if present)
- Mechanic display (displayName or fallback “Mechanic ID: <id>”)
- Assignment notes display (read-only text, max 500)
- Update notification banner/toast (“Assignment updated”)
- Loading/empty state for assignment (when assignment is null/unavailable)
- SSE connection status (implicit; optional subtle indicator)
- Error state messaging (SSE disconnected / failed to load)

## Layout
- Top: Appointment Detail header
- Main (stacked sections):
  - Assignment (card/section)
    - Row: “Assignment” title + (optional) small status text (e.g., “Live updates on/off”)
    - Row(s): Assignment Type + corresponding details (Bay or Mobile Unit)
    - Row: Mechanic
    - Row: Notes (multi-line, read-only)
- Overlay/Transient: toast/banner near top of content area when assignment updates

## Interaction Flow
1. User opens Appointment Detail for an appointment in Facility A.
2. UI loads appointment data including AssignmentView (or shows “No assignment” if null).
3. UI renders Assignment section as read-only:
   1) Show assignment type (opaque enum string for display).
   2) If assignmentType = BAY: show bay identifier and bay name (if provided).
   3) If assignmentType = MOBILE_UNIT: show mobile unit identifier and any available descriptive fields (e.g., name); optionally show coordinates/time fields only if present.
   4) Show mechanic: displayName if present; otherwise show “Mechanic ID: <id>”.
   5) Show assignment notes if present; otherwise show empty/“—”.
4. While the user remains on the screen, UI subscribes to the facility-scoped SSE stream (facilityId required).
5. On receiving an assignment update event containing a full AssignmentView for the same appointmentId:
   1) If event version is greater than the currently displayed version, update the Assignment section with the new data.
   2) Show a visible toast/banner indicating the assignment was updated.
6. Edge case: if an SSE event arrives with version less than or equal to current, ignore it and do not overwrite the UI.
7. Edge case: if SSE disconnects or fails, keep the last known assignment displayed and optionally show a subtle “Live updates unavailable” message; do not block viewing.

## Notes
- Read-only requirement: the Assignment section must not allow edits in this story (no interactive fields for bay/mechanic/notes).
- Mechanic display acceptance criteria: show mechanic.displayName when available; otherwise show fallback text “Mechanic ID: <id>”.
- Notes constraints: assignment notes are optional/nullable; display up to 500 characters; preserve line breaks if present.
- SSE behavior:
  - Subscribe facility-scoped; exact endpoint path is TBD.
  - Filter updates by appointmentId.
  - Ignore out-of-order updates using version (preferred) or timestamp fallback if version absent.
  - Event type name is backend-defined (treat as opaque); payload is full AssignmentView.
- Data shape considerations (AssignmentView):
  - appointmentId and facilityId are required/derivable; facilityId required for SSE subscribe.
  - assignmentType is required; treat enum as displayable string.
  - BAY requires bayId and bayIdentifier; bayName optional.
  - MOBILE_UNIT requires mobileUnitId; other fields may be optional (e.g., coordinates, timestamps).
  - Mechanic object may be null; if null, omit mechanic row or show “Unassigned”.
- TODOs / backend contract TBD:
  - Confirm SSE endpoint path and event type(s).
  - Confirm version field name and whether timestamp fallback is needed.
  - Confirm whether assignment notes update operation is in scope later (operation name, idempotency key, and required audit fields); not implemented in this read-only story.
