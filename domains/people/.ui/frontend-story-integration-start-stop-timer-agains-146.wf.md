# [FRONTEND] [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task
## Purpose
Enable users to start, pause/resume, and stop a time-tracking timer against an assigned workorder task, with a live HH:MM:SS display. On page load, the UI must detect whether any active timer exists and render appropriate controls. When timer actions fail (e.g., stopping with no active timer), the UI must show a clear error and offer recovery actions such as refreshing active timer state and/or stopping active timers.

## Components
- Page header/title: “Timer” / workorder context header
- Workorder context summary (e.g., workorder ID/name; current session/context indicator)
- Task selector
  - Dropdown of available tasks (from workorder)
  - Optional free-form task entry field (if allowed)
- Live timer display (HH:MM:SS)
- Primary controls
  - Start Timer button
  - Stop Timer button
  - Pause button
  - Resume button (shown when paused)
- Status indicator / visual feedback (button color/state: green running, gray stopped)
- Inline error banner/toast area (standard error envelope messaging)
- Error recovery actions
  - “Refresh” button (re-fetch active timer details via GET active)
  - “Stop Active Timer(s)” button (attempt stop for active timers)
- Loading state indicators (spinners/disabled states during API calls)

## Layout
- Top: Page header + workorder/session context summary
- Main: Task selector (dropdown + optional free-form) above live timer display
- Main (below timer): Control row [Start] [Pause/Resume] [Stop] with state-based enable/disable
- Below controls: Error banner area with action links/buttons (Refresh, Stop Active Timer(s))

## Interaction Flow
1. On load (initial render)
   1. Call GET active timer(s).
   2. If no active timer: show Start enabled (only if required context/session + task selection present); show Stop disabled; timer display at 00:00:00.
   3. If active timer(s) exist: show Stop enabled; disable Start by default (single-timer UX); show running timer display and associated task details.
   4. If multiple active timers returned (backend allows concurrency): present a warning in the error/status area and enable “Stop Active Timer(s)” to stop all; keep Start disabled by default.
2. Start timer (happy path)
   1. User selects a task (dropdown or free-form if available).
   2. User clicks “Start Timer”.
   3. UI disables Start, enables Pause and Stop, and begins live HH:MM:SS updates.
   4. If API returns TIMER_ALREADY_RUNNING: show error; offer “Refresh” to load active timer details; keep Start disabled until state reconciled.
   5. If API returns SESSION_NOT_ACTIVE: show error indicating session/context is not active; keep Start disabled until context becomes valid.
3. Pause / Resume (maintain cumulative time)
   1. While running, user clicks “Pause”.
   2. UI shows paused state (timer stops incrementing), swaps Pause → Resume, keeps Stop enabled.
   3. User clicks “Resume”; UI returns to running state and continues cumulative time.
4. Stop timer (happy path)
   1. User clicks “Stop Timer”.
   2. UI calls stop API; on success, UI captures and displays final elapsed time briefly (or resets to 00:00:00 per product convention) and returns to stopped state (Start enabled subject to context).
5. Edge case: Stop fails when no timer active (Scenario 5)
   1. Given no active timer, user clicks “Stop Timer”.
   2. API responds with an error code in the standard error envelope indicating no active timer to stop.
   3. UI shows an error message: “There is no active timer to stop.”
   4. UI offers “Refresh” and triggers GET active timer state to reconcile UI.
6. Recovery actions (from error banner)
   1. “Refresh”: calls GET active timer(s) and re-renders controls based on returned state.
   2. “Stop Active Timer(s)”: attempts to stop all active timers (for concurrency reconciliation), then refreshes active state.

## Notes
- Single-timer UX: Start controls should be disabled when any active timer exists, but UI must tolerate backend returning multiple active timers and provide a safe “Stop Active Timer(s)” recovery path.
- Visual feedback requirement: running state should be clearly indicated (e.g., green when running, gray when stopped); paused state should be distinct (e.g., amber/outlined) if supported by design system.
- Error handling: display API errors using the standard error envelope; include specific messaging for SESSION_NOT_ACTIVE, TIMER_ALREADY_RUNNING, and “no active timer to stop”.
- Acceptance criteria highlights:
  - On load, UI always calls GET active and renders correct enabled/disabled states.
  - Stop with no active timer shows explicit error and triggers/permits refresh via GET active.
  - Pause/Resume maintains cumulative time without stopping the session.
- Risk/implementation caution: requirements may still be incomplete; keep UI logic resilient to unexpected active timer states and ensure all actions are idempotent where possible (stop then refresh).
