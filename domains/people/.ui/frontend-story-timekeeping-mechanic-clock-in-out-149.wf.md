# [FRONTEND] [STORY] Timekeeping: Mechanic Clock In/Out
## Purpose
Provide a dedicated Timekeeping screen for mechanics to quickly view their current clock status and perform a single-tap Clock In or Clock Out action. The screen must prevent invalid actions (double clock-in, clock-out when not clocked in) via frontend enforcement and handle backend validation/errors safely. It should also display the last/open TimeEntry context (timestamps + timezone) and expose audit fields (created/updated + actor) when returned by the backend.

## Components
- Page header with title “Timekeeping: Clock In/Out”
- Status summary panel (Clocked In / Clocked Out)
- Last/Open TimeEntry summary (read-only)
  - Start time (clock-in timestamp) + timezone
  - End time (clock-out timestamp, if present) + timezone
  - TimeEntry identifier/reference (if provided)
- Location context (read-only label or selectable control depending on backend policy)
  - Location display name/id
  - Optional location selector (dropdown) if required/allowed
- Primary action button (toggles label/state: “Clock In” or “Clock Out”)
- Inline validation/error banner area (non-blocking)
- Loading state (skeleton/spinner) for initial status load and action submission
- Optional “Shift context” read-only panel (only if endpoint/data exists; non-blocking)
- Audit details (read-only, only if present in response)
  - Created at, created by
  - Updated at, updated by
- Retry control for network/5xx failures (e.g., “Retry” button/link)

## Layout
- Top: Header (Title) + optional back navigation
- Main (stacked cards):
  - Card 1: Current Status (large status text) + Primary Action button aligned right/bottom
  - Card 2: Location Context (label or selector)
  - Card 3: Last/Open TimeEntry Summary (timestamps + timezone, id/reference)
  - Card 4 (optional): Shift Context (read-only)
  - Card 5: Audit Details (created/updated + actor, if available)
- Bottom: Error/notice banner area (or directly under header), plus Retry when applicable

## Interaction Flow
1. Enter screen from POS navigation “Timekeeping → Clock In/Out”.
2. On load, fetch current clock status and last/open TimeEntry context.
3. While loading, show loading state; disable primary action.
4. If backend reports Clocked Out:
   1. Display status “Clocked Out”.
   2. Show primary action button “Clock In”.
   3. Do not present any clock-out action.
5. If backend reports Clocked In (open entry exists):
   1. Display status “Clocked In”.
   2. Show primary action button “Clock Out”.
   3. Display open entry start timestamp + timezone; end timestamp absent/blank.
6. Clock In action:
   1. User taps “Clock In”.
   2. Frontend checks if already clocked in; if yes, block action and show message; then reload status.
   3. If location is required by backend, ensure location is selected/present; otherwise show inline validation and do not submit.
   4. Submit Clock In request (no timestamps from frontend; include required headers; include location only if required).
   5. On 200/201 success: update UI using returned TimeEntry if provided; otherwise reload status endpoint; show updated status and entry context.
7. Clock Out action:
   1. User taps “Clock Out”.
   2. Frontend checks if not clocked in; if not, block action and show message; then reload status.
   3. Submit Clock Out request (no timestamps from frontend; include required headers; include location only if required).
   4. On success: update UI using returned TimeEntry if provided; otherwise reload status; show closed entry timestamps + timezone and status “Clocked Out”.
8. Backend error handling:
   1. 409 (e.g., already clocked in / no open entry): show mapped message from standard envelope (safe message), then reload status.
   2. 400 with field errors: show first field error; do not change status; allow user to correct (e.g., location) and retry.
   3. 403: show unauthorized message; disable primary action; keep current displayed status.
   4. 5xx/network: show generic error; keep current displayed status; allow retry.
9. Optional dashboard entry point (if implemented): embedded “Clock status” card links to this screen or provides a compact read-only status with a single action consistent with the main screen.

## Notes
- Single-screen flow; primary action must toggle based on current status (Clock In vs Clock Out).
- Frontend must enforce basic invalid transitions (double clock-in; clock-out when not clocked in) but still rely on backend as source of truth; always reload status after 400/409 and after successful actions if response lacks full TimeEntry.
- Display timestamps in ISO-8601-derived user-friendly format and include timezone context; show last/open entry context after load and after actions.
- Audit-oriented exposure is read-only: show created/updated timestamps and actor identifiers only when returned by backend; do not invent fields.
- Location handling is policy-dependent:
  - If backend requires explicit location in request, UI must collect it (blocking).
  - If location is returned as part of TimeEntry/status, display it read-only at minimum.
- Respect backend authorization: on 401/403, do not attempt to define roles; show not authorized and disable actions.
- Optional shift context display is a non-blocking enhancement only if an endpoint/data already exists; no new business logic.
- Error envelope expected to include code/message/details; UI should prefer safe backend message where appropriate and fall back to generic messaging for 5xx/network.
- Out of scope: payroll rules, manager edits, broader TimeEntry CRUD, notifications, new state machine behavior beyond open/close.
