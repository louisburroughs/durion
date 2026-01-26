# [FRONTEND] [STORY] Workexec: Display Work In Progress Status for Active Workorders
## Purpose
Provide a Work In Progress (WIP) board for Counter Associates to quickly see active (non-terminal) work orders and their canonical workexec execution status. The view surfaces assignment context (mechanic and bay/location), a last-updated/staleness indicator, and supports drilldown into a work order detail panel/view with status history. Data stays current via polling refresh (and optional event-driven updates if available) with clear loading/empty/error/unauthorized states.

## Components
- Page header/title: “Work In Progress”
- Location scope indicator (permitted locations) / location selector
- Filter controls
  - Status filter (workexec status taxonomy)
  - Mechanic filter
  - Location filter
- Actions
  - Refresh button
  - Auto-refresh/polling indicator (e.g., “Auto-refreshing”)
- Staleness/health banner (data old or refresh failing)
- “Last updated” timestamp (list-level)
- WIP list/table
  - Columns: Work Order ID, Status, Mechanic (or “Unassigned”), Bay/Location (or “Unassigned”), Last Updated (row-level)
  - Row selection/click target for drilldown
- Loading state (skeleton/spinner)
- Empty state (no active work orders match filters)
- Error state (non-403 fetch errors)
- Full-page authorization error state (403 on board load)
- Work Order Detail view/panel
  - Header: Work Order ID + current status
  - Assignment context fields (mechanicId, bayId, appointmentId when present)
  - Updated timestamp (UTC)
  - Transition history list (if available)
  - Not authorized state (403 on detail load)
  - Back to board navigation

## Layout
- Top: Page title + permitted location context; right-aligned Refresh button and list “Last updated”
- Below top: Filter row (Status | Location | Mechanic)
- Below filters: Staleness banner (only when applicable)
- Main: WIP list/table (full width); row click opens detail
- Detail: Either right-side panel (preferred) or separate detail route/view; includes Back control

## Interaction Flow
1. Board load (happy path)
   1. User opens WIP board screen.
   2. UI shows loading state; fetch active work orders (non-terminal statuses) scoped to user’s permitted location(s).
   3. Render list with each row showing Work Order ID, canonical status, assignment context (mechanic/bay/location or “Unassigned”), and row “Last updated”.
   4. Display list-level “Last updated” timestamp when fetch completes.
2. Filtering (status/location/mechanic)
   1. User changes one or more filters.
   2. If server-backed filtering is available, re-query server with filters; otherwise apply client-side only when supported by returned dataset.
   3. Update list results; persist filter state across manual refresh and polling refresh.
   4. If no matches, show empty state with current filter summary.
3. Manual refresh
   1. User clicks Refresh.
   2. Re-fetch list using current filters and permitted location scope.
   3. Update list and list-level “Last updated”.
4. Polling refresh / live updates
   1. On timer tick, re-fetch list (primary/fallback mechanism).
   2. If a visible row’s status/updated timestamp changed, update that row in place.
   3. If user is viewing detail, returning to board reflects latest list data.
   4. If transition history is displayed and refresh occurs, re-render history best-effort while preserving scroll position.
5. Drilldown to detail
   1. User selects a work order row.
   2. Load detail (panel or navigation) showing Work Order ID, current status, updated timestamp (UTC), assignment context fields, and transition history (if available).
6. Unauthorized access
   1. If board load returns 403, show full-page authorization error; do not display cached rows.
   2. If detail load returns 403, show “Not authorized” within detail and provide navigation back to board.
7. Error / staleness handling
   1. If refresh fails (network/server error), show error state and keep prior data only if allowed (except 403).
   2. If data becomes old or repeated refresh failures occur, show staleness banner indicating data may be outdated.

## Notes
- Canonical status must follow the workexec status taxonomy; list shows only active/non-terminal statuses.
- Minimum list fields: work order id, status, updated timestamp, and assignment context when available (mechanicId, bayId, appointmentId); show “Unassigned” when mechanic/bay context is missing.
- Optional display fields only if already available without adding new cross-domain joins (e.g., mechanicName, bayName, appointmentStartAt).
- Detail minimum: work order id, current status, updated timestamp (UTC), and transition history when available (strongly preferred for “recent changes” value).
- Polling is required; event-driven updates are optional only if already available.
- Security: backend enforces authorization; UI must handle 403 distinctly (full-page for board, inline for detail).
- Moqui implementation: screens/forms/transitions plus Vue/Quasar components integrated via Moqui; include correlation IDs and status history visibility to support audit/troubleshooting where available.
- Acceptance criteria emphasis: filters persist across refresh; staleness indicator appears when refresh failing or data is old; drilldown works; loading/empty/error states are present.
