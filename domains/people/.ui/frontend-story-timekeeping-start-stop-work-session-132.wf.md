# [FRONTEND] [STORY] Timekeeping: Start/Stop Work Session for Assigned Work
## Purpose
Enable a mechanic to start and stop a work session from an assigned work order task screen, with clear validation and state-driven controls. The UI must reflect the current active session (including break state) and prevent invalid actions when task context or an active session is missing. The experience should support server-driven rules for overlap/override and session locking.

## Components
- Page header: Work Order / Task context (task name/ID, work order ID)
- Active session panel (read-only details)
  - Status badge (IN_PROGRESS / ON_BREAK / COMPLETED / APPROVED / other backend statuses)
  - Clock-in time, clock-out time (if present)
  - Billable minutes (if present)
  - Lock indicator (locked/unlocked)
  - Optional override audit summary (if provided)
- Empty state panel: “No active session”
- Primary action buttons
  - Start Work
  - Stop Work
  - Start Break
  - End Break
- Inline validation/error banner area
- Confirmation modal/toast (optional) for start/stop/break success and failures
- Loading indicators (button-level spinners and/or panel skeleton)
- (Optional, if present in product) Link to session history / timekeeping summary

## Layout
- Top: Header with Work Order + Task identifiers and eligibility/context summary
- Main (stacked):
  - Error/alert banner area (shows “Task context missing.” / “No active session.” / API errors)
  - Active Session card (if active session loaded) OR Empty State card (if none)
  - Actions row (Start/Stop/Break buttons aligned horizontally; disabled states visible)
- Footer area (optional): small print for lock/override notes

## Interaction Flow
1. Page load / initialization
   1. Read required route/context values (taskId, workOrderId, mechanicId/assignee context as applicable).
   2. If required context missing, block all actions and show error banner: “Task context missing.”
   3. Fetch active session for the mechanic/task (per backend model) and render either Active Session card or “No active session.”
2. Start work session (happy path)
   1. User views task with no active session (or backend indicates overlap override is allowed).
   2. Start Work button is enabled only if: no active session OR server indicates override allowed; session not locked; current session for this task is not already IN_PROGRESS.
   3. On click Start Work, call Start Work Session API with minimum required identifiers; include optional fields (e.g., notes) and override reason only when using override path.
   4. On 201 response, render returned WorkSession as active; enable Stop Work and Start Break (if status is IN_PROGRESS and not locked).
3. Stop work session
   1. Stop Work button is enabled only if an active session is loaded and not locked.
   2. On click Stop Work, call Stop Work Session API using sessionId (preferred) or taskId (if supported).
   3. On 200 response, update Active Session card (clockOut/status/billableMinutes as returned); disable Start/Stop/Break actions as appropriate for completed/approved.
4. Start break
   1. Start Break enabled only when session status is IN_PROGRESS, no break currently active (backend-provided or inferred), and session not locked.
   2. On click Start Break, call backend break-start action (if separate) or update endpoint (as implemented); update UI to ON_BREAK on success.
5. End break
   1. End Break enabled only when session status is ON_BREAK, a break is active, and session not locked.
   2. On click End Break, call backend break-end action; update UI back to IN_PROGRESS on success.
6. Edge cases and error handling
   1. If no active session is loaded, disable Stop Work / Break actions and show “No active session.”
   2. If backend returns session locked, show lock indicator and disable all mutating actions.
   3. If overlap exists and override is not allowed (per server), keep Start Work disabled and show server-provided error on attempt.
   4. If backend returns unknown/new status, display status text and apply safest defaults (disable actions unless explicitly allowed by rules).

## Notes
- Validation rules:
  - Before Start Work: require task/work order context; otherwise show “Task context missing.” and block.
  - Before Stop Work / Break: require an active session loaded; otherwise disable and show “No active session.”
- Enable/disable rules must be server-driven for overlap/override; UI must not guess eligibility beyond provided flags/status.
- WorkSession fields to display (as available): sessionId, taskId, workOrderId, mechanicId, clockIn, clockOut, status, locked, billableMinutes; optional notes; optional override audit (overrideUsed, overrideReason, overrideBy, overrideAt).
- Empty state: “No active session” should still allow Start Work when task is eligible and context is present.
- Acceptance criteria focus: correct button states across statuses (IN_PROGRESS/ON_BREAK/COMPLETED/APPROVED), correct handling of locked sessions, and clear user-facing errors for missing context or missing active session.
- TODO (dev/design): define exact UI copy for overlap/override denial, and whether break actions are separate endpoints or part of session update; ensure consistent loading/disabled behavior during API calls.
