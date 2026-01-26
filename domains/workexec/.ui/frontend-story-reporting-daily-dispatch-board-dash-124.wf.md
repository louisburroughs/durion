# [FRONTEND] [STORY] Reporting: Daily Dispatch Board Dashboard
## Purpose
Provide dispatchers a read-only Daily Dispatch Board dashboard for a selected location and dispatch date (default: today) to view work orders and server-provided exception indicators. Enable efficient dispatching by highlighting warnings vs blocking issues and (optionally) mechanic availability signals without making scheduling decisions in the UI. Keep data current via auto-refresh polling and clear timestamps (“Last updated” and server “As of”).

## Components
- Page header/title: “Daily Dispatch Board”
- Filter bar
  - Location picker/search (opaque locationId)
  - Date picker (ISO date; default today)
  - “Refresh now” button
- Status line
  - “Last updated” timestamp (client)
  - “As of” timestamp (server-provided, if present) with timezone label
  - Stale/refreshing indicator (e.g., “Refreshing…”, “Stale”)
- Exceptions summary/panel
  - Counts by severity (WARNING vs BLOCKING)
  - List of exception indicators with message and target context
- Board list/grouping (read-only)
  - Work order rows/cards with minimum fields (workOrderId, status)
  - Optional fields: appointmentId, scheduled start/end, assignee(s), customer/unit, exception badges
- Optional Availability panel (People SoR signal)
  - “As of” timestamp for availability
  - Mechanic list with availability status (read-only)
  - Empty state and failure warning messaging
- Empty/error states
  - No work orders message
  - Availability empty message
  - Non-blocking availability failure warning
  - Primary board load failure state (with retry)

## Layout
- Top: Page header/title
- Below header: Filter bar (Location | Date | Refresh now) + Status line aligned right/under filters
- Main content: Two-column layout
  - Left/main (primary): Board list/grouping of work orders for the day
  - Right (secondary): Exceptions panel/summary; below it optional Availability panel
- Inline ASCII hint: Top: Filters+Status; Body: [Board (wide)] | [Exceptions + Availability (narrow)]

## Interaction Flow
1. Initial load (authenticated dispatcher)
   1. User opens the Dispatch Board screen.
   2. Screen computes default viewDate = today and requires a locationId to load data.
   3. On valid location/date, frontend requests board data for location/day.
   4. Render board rows and exception indicators; show “Last updated”.
   5. If server provides “asOf” timestamp, display as “As of” in user timezone with timezone label.
2. Change filters (location/date)
   1. User selects a location via picker/search (IDs treated as opaque strings).
   2. User selects a date via date picker (ISO date).
   3. Frontend reloads board data for the new location/day and updates the board + exceptions.
3. Manual refresh
   1. User clicks “Refresh now”.
   2. Frontend requests updated board data; on success updates board and “Last updated”.
4. Auto-refresh polling (every 30 seconds)
   1. While the screen is visible/active, start a 30s polling timer after last successful refresh.
   2. On each interval, request updated board data.
   3. On success, update board/exceptions and “Last updated”.
   4. If screen becomes hidden/inactive, pause polling; resume when visible/active.
5. Empty state: no work orders
   1. If board data returns zero work orders, show: “No work orders for {viewDate} at {location}.”
   2. Exceptions panel still renders (may be empty/zero counts).
6. Optional People availability integration (non-blocking)
   1. In parallel (or after board load), request People availability for the same date/location context (read-only signal).
   2. If availability returns empty, show: “No availability data returned for this date.” (board unaffected).
   3. If availability request fails (network/5xx), show a warning: “Mechanic availability is unavailable.” and keep board visible (no blocking error).
7. Primary board load failure
   1. If initial board request fails, show an error state with retry (and/or allow “Refresh now”).
   2. If a refresh fails after a successful load, keep the last rendered board (cached view) and indicate data may be stale.

## Notes
- Read-only dashboard: no editing, no dispatch actions; UI must not compute overdue/exception logic client-side if scheduled timestamps are absent.
- Required parameters to load board: locationId and viewDate (ISO date; default computed to today).
- Board row minimum render fields: workOrderId (opaque string) and status (enum). Optional: appointmentId, scheduledStart/scheduledEnd, assignee(s), customer/unit, exceptionIndicators array.
- ExceptionIndicator minimum fields: code (stable enum), severity (WARNING/BLOCKING), message, targetType (e.g., LOCATION/WORK_ORDER/APPOINTMENT/MECHANIC), optional targetId (required when targetType ≠ LOCATION), optional per-indicator timestamp (else use board-level asOf).
- Timestamps:
  - “Last updated” = client time of last successful refresh.
  - “As of” = server-provided timestamp if present; display in user timezone with timezone label.
- Polling: every 30 seconds only while screen is active/visible; include a subtle “Refreshing…” indicator during fetch.
- Degrade gracefully on secondary dependency failure (People availability): show warning only; do not block board rendering (SD-ERR-NETWORK-DEGRADE).
- Data sources: integrate with existing Moqui workexec dispatch board screen actions/services (or REST read model if present); People availability endpoint is optional and read-only; avoid logging PII and render only mechanic display names as provided.
