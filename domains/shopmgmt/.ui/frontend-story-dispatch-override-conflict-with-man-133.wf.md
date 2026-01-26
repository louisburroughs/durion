# [FRONTEND] [STORY] Dispatch: Override Conflict with Manager Permission
## Purpose
Enable dispatch users to resolve scheduling conflicts during appointment save/reschedule by either adjusting the schedule or (if authorized) overriding the conflict with a required reason. Present clear conflict details returned by the backend and prevent completion when a conflict exists unless an allowed override is submitted successfully. Provide visible audit indicators when an override occurs.

## Components
- Page header: Appointment / Dispatch context (appointment identifier, status)
- Scheduling editor section
  - Date/time picker(s)
  - Resource selector (e.g., Bay / Mechanic)
  - Save/Reschedule button
  - Cancel/Back button
- Conflict panel (inline, shown on conflict)
  - Conflict summary (type + affected resource)
  - Conflict details list (resource type, resource id, optional related id, optional human-readable message, optional timestamp)
  - Concurrency/help text (based on conflict timestamp if present)
- Override action area (permission-gated)
  - “Override conflict” button/link (only if user has capability)
  - Override reason input (required, non-empty)
  - Submit override button
  - Dismiss/Close override UI
- Success indicators
  - “Conflict overridden” badge/indicator on appointment detail
  - Override audit metadata display (override id, timestamp; or created/updated metadata)
- Error messaging
  - Inline validation for empty reason
  - Backend error banner/toast for failed override/save

## Layout
- Top: Header with appointment context + primary actions (Save/Reschedule, Cancel)
- Main (two stacked sections): Scheduling Editor (top) → Conflict Panel (appears below editor when conflict exists)
- Conflict Panel: left-aligned summary; right/underneath details list; bottom row actions (Adjust schedule / Override if allowed)
- Footer area (optional): audit indicator row when override exists (override id + timestamp)

## Interaction Flow
1. User edits scheduling fields (date/time/resource) and selects Save/Reschedule.
2. Backend responds with scheduling conflict and conflict details payload.
3. UI enters “Conflict detected” state:
   1. Show Conflict Panel with conflict summary and details.
   2. Block completion (Save remains disabled or re-attempt continues to return conflict) until user resolves via adjustment or override.
4. Resolution path A — Adjust schedule (all users):
   1. User changes date/time/resource to a non-conflicting slot.
   2. User selects Save/Reschedule again.
   3. On success, conflict panel disappears and appointment updates normally.
5. Resolution path B — Override conflict (authorized users only):
   1. If user has override capability, show “Override conflict” action in the Conflict Panel.
   2. User selects “Override conflict”; UI reveals reason input and Submit override.
   3. User enters a non-empty reason (e.g., “Emergency customer vehicle”).
   4. User submits override with an override token.
   5. On success:
      1. Appointment schedules successfully.
      2. Appointment detail response includes conflictOverridden = true.
      3. UI displays “Conflict overridden” indicator and shows audit metadata (override id + timestamp, or equivalent created/updated metadata).
6. Unauthorized user behavior:
   1. If user lacks override capability, do not render override action or reason input.
   2. User cannot complete scheduling in the conflicting slot; must adjust schedule to proceed.
7. Edge cases:
   1. Override reason empty → inline validation error; prevent submit.
   2. Backend override/save fails (including stale conflict timestamp) → show error message; keep Conflict Panel visible for retry/adjustment.
   3. Conflict details missing optional fields → render only available fields; avoid blank labels.

## Notes
- Conflict Panel is read-only and driven by backend conflict details payload; minimum render fields: conflictType, resourceType, resourceId, optional relatedId, optional message, optional timestamp.
- “Conflict detected” state must block completion unless conflict is resolved by schedule change or successful override (authorized only).
- Override UI must be permission-gated; absence of capability must remove the override affordance entirely.
- Override requires a non-empty reason; store/display audit metadata returned (override identifier + timestamp) or equivalent metadata sufficient for audit.
- Appointment detail should surface: conflictOverridden (boolean, read-only), overrideId (optional), overrideReason (optional, read-only if policy allows), override display name (optional), override timestamp (optional).
- Work order may reference appointment; UI should not require work order data to render conflict/override, but may display denormalized appointment scheduling fields if present.
- TODO: Define exact placement/format of audit metadata and whether “Conflict overridden” persists as a badge on subsequent views/loads.
