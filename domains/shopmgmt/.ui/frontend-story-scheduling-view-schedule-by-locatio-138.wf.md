# [FRONTEND] [STORY] Scheduling: View Schedule by Location and Resource
## Purpose
Provide a read-only daily dispatch board that lets dispatchers view scheduled work by required location and day, with optional filtering by resource type and specific resource. The board loads authoritative execution data from a workexec-owned read model/endpoint, highlights conflicts/exceptions with stable codes, and supports quick inspection via a details panel. Optionally, the UI can overlay People availability as a non-blocking enhancement that must not prevent the board from rendering.

## Components
- Page header/title: “Scheduling / Dispatch Board”
- Filter bar
  - Location selector (required; opaque ID)
  - Day/date picker (required; shop timezone interpretation)
  - Resource Type dropdown (optional; enum)
  - Resource selector (optional; depends on resource type)
  - Toggle: “Include People availability” (optional; default off)
  - Button: “Load Board” (or auto-load on valid filters)
- Board region
  - Loading indicator/skeleton (board-only)
  - Error banner (board load failed; may show last known good data)
  - Resource lanes/columns (resource name + metadata)
  - Work item cards (work orders / appointment-backed work)
    - Summary text
    - Optional time window display (start/end) or “No time” state
    - Conflict/exception badge(s) with severity (blocking/warning)
    - Priority color coding (red/high, yellow/medium, blue/low) if provided
- Right-side details panel (read-only)
  - Selected item identifiers (opaque IDs)
  - Time window (if present)
  - Linked appointment section (if present) with “Open in Shopmgr” action
  - Conflicts/Exceptions section
    - Severity
    - Stable exception code(s)
    - Human-readable messages
    - Conflicting item IDs list (if provided)
- People availability overlay (non-blocking)
  - Lane-level availability indicators (e.g., available/unavailable blocks or summary)
  - Warning banner/toast if availability fails to load
- Tooltip/hover popover on resource header (optional)
  - Availability summary, current tasks, location (if data available)

## Layout
- Top: Header + Filter bar (Location*, Day*, Resource Type, Resource, Include People availability, Load)
- Main: Board region (horizontal lanes/columns by resource; cards within lanes)
- Right: Details panel (collapsible; opens on item selection)
- Inline sketch: Top Filters | Main Board (left) + Details (right)

## Interaction Flow
1. User lands on dispatch board; required filters (Location, Day) are empty → state: required filters missing; board region shows prompt to select required filters.
2. User selects Location and Day (and optionally Resource Type/Resource) and triggers Load (or auto-load once valid).
3. UI enters loading state for board region; prevent duplicate in-flight loads (latest request wins); keep filters usable.
4. On success, render resource lanes and work item cards:
   1. If an item has start/end, place/display on time grid; if not, still render card in lane with “No time” presentation.
   2. Apply conflict/exception indicators on flagged items (severity + badge).
   3. Apply priority color coding if priority is present in data model.
5. User clicks a work item card → open/update right-side details panel with identifiers, summary, time window, and conflicts/exceptions (codes + messages + conflicting IDs).
6. If the selected item includes a linked appointment, user clicks “Open in Shopmgr” → navigate to shopmgr appointment screen (read-only/edit per shopmgr permissions), preserving context via query params when possible.
7. User toggles “Include People availability” on:
   1. Board renders immediately from workexec data (no blocking).
   2. Availability request starts in parallel; overlay appears when available.
   3. If availability request fails, show a non-blocking warning; keep board fully usable (Scenario: People overlay failure).
8. Board load failure:
   1. Show error banner in board region; if last known good data exists, keep it visible with “stale” indication.
   2. Allow user to adjust filters and retry; ensure latest-wins behavior.

## Notes
- Scope is read-only: no drag/drop rescheduling, no assignment changes, no conflict resolution workflows.
- Required request parameters: locationId (required), day (required; interpret in shop timezone when available), resourceType (optional), resourceId (optional), includePeopleAvailability (optional; default false).
- Data source: workexec-owned read model/screen or read-only endpoint; shopmgr appointment details are accessed via navigation link when needed.
- Conflicts/exceptions must use stable exception code strings and show severity (blocking vs warning) plus any provided conflicting item IDs.
- People availability is a soft dependency: failures must not block board rendering; show warning only.
- Allowed UI states: required filters missing, board request in-flight, board rendered, board failed to load (may show last known good data).
- Do not implement authoring of location hours, resource definitions, or People availability rules/providers; consume if present.
- Ensure accessibility: clear loading states, keyboard focus to details panel on selection, and readable badges/colors (do not rely on color alone).
