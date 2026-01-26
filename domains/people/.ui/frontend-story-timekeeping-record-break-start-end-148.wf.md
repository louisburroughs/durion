# [FRONTEND] [STORY] Timekeeping: Record Break Start/End
## Purpose
Enable Mechanics to start and end breaks during an active work session so their timecard reflects true working time and breaks are auditable. The UI must clearly show whether a break is currently in progress, allow starting a break with a required type (and optional notes), and allow ending the active break. It must enforce server-derived constraints (clock-in required, no overlapping breaks) and handle backend conflict responses by refreshing context and showing actionable error details.

## Components
- Timekeeping screen (existing) enhancements
  - Current Break Status panel (none vs in-progress)
  - Active break details (type, start time, elapsed timer)
  - Primary actions: Start Break, End Break
  - “Today’s breaks” read-only list (recent entries for the day)
  - Inline success toast/alert area
  - Inline error banner with expandable “Details” (includes request/correlation id when available)
  - Loading/refresh indicator for context reload
- Start Break modal/dialog (or inline form)
  - Break Type picker (required)
  - Notes text field (optional; required/prompted when type = OTHER)
  - Buttons: Cancel, Start Break (submit)
  - Field-level validation messages

## Layout
- Top: Timekeeping header + current clock-in/session indicator (read-only, server-derived)
- Main (stacked):
  - Current Break Status panel
    - Left: status label (No active break / Break in progress)
    - Right: action buttons (Start Break / End Break) with enabled/disabled states
    - Below (when active): type, start timestamp, elapsed time
  - Divider
  - “Today’s breaks” list (table/list rows with start/end timestamps, type, notes preview, auto-ended indicator)
- Bottom: transient success toast + persistent error banner (with expandable details)

## Interaction Flow
1. Load/refresh break context on screen entry and after any start/end action.
2. No active break state (clocked in):
   1) Show status “No active break”.
   2) Enable Start Break; disable End Break.
   3) User clicks Start Break → open Start Break modal.
   4) User selects Break Type (required); optionally enters Notes.
   5) If type = OTHER, prompt/require Notes before enabling submit.
   6) User submits Start Break → call backend start-break operation (no client-provided session/timecard ids).
   7) On success: show confirmation; close modal; reload break context; show active break details; disable Start Break and enable End Break.
3. Active break state:
   1) Show status “Break in progress” with type, start time, and elapsed time (display-only).
   2) Disable Start Break; enable End Break.
   3) User clicks End Break → call backend end-break operation (no payload required by default).
   4) On success: show confirmation; reload break context; show “No active break”; completed break appears in Today’s breaks with end time.
4. Edge case: attempt to end when none active (server 409 refresh-required):
   1) UI should normally keep End Break disabled when no active break.
   2) If an end-break request still returns HTTP 409 (no active break/already ended): display server message; capture correlation/request id if present; reload break context.
5. Edge case: attempt to start when already active / overlap prevention:
   1) UI disables Start Break when active break exists.
   2) If start-break returns HTTP 409 (already in progress/overlap): show server message + correlation id; reload break context to reconcile.
6. Edge case: not clocked in:
   1) If break context indicates no active clock-in session: disable both Start Break and End Break.
   2) If backend rejects with auth/session-related error: show message + correlation id; reload context.
7. Auto-ended break visibility:
   1) When a break entry indicates it was auto-ended at clock-out, show “Auto-ended at clock-out” indicator on that row in Today’s breaks.

## Notes
- UI must rely on server-derived identity and current session; do not send timecard/session identifiers from the client.
- Enforce “exactly one active break at a time” via UI gating plus backend error handling; always reload break context after start/end or conflict errors.
- Break fields are read-only in the UI except for start payload: Break Type (required) and Notes (optional; required/prompted when type = OTHER). No editing/deleting of completed breaks; no timestamp edits.
- “Today’s breaks” list is read-only and should display start/end timestamps, break type, and an auto-ended indicator when applicable; do not display tenantId if present in responses.
- Error handling must surface server message and include an expandable Details area that shows request/correlation id when response headers provide it (store in UI error state).
- Risk/TODO: backend contract details (exact endpoints/field names) are incomplete; design should accommodate minor schema changes while preserving required behaviors and states.
