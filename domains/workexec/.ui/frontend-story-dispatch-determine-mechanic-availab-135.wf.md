# [FRONTEND] [STORY] Dispatch: Determine Mechanic Availability for a Time Window

## Purpose
Enable Dispatchers to check mechanic availability for a specified time window at a selected location to support assignment planning. Provide per-mechanic availability status and any conflicts within the window using standardized reason codes. Ensure clear timezone semantics (display in user timezone; interpret day/window in shop timezone when applicable) and resilient handling of upstream availability/People dependency failures with retry.

## Components
- Existing Dispatch/Board screen header (unchanged)
- “Availability Check” screenlet/panel
  - Location selector (required)
  - Start datetime input (required)
  - End datetime input (required)
  - Optional mechanic filter (multi-select or typeahead)
  - Timezone label/help text (user timezone display; shop timezone interpretation when applicable)
  - Submit button: “Check availability”
  - Secondary action: “Retry” (shown on failure states)
  - Inline validation messages (missing/invalid range)
- Results area (within the screenlet)
  - Overall status summary (e.g., count available/unavailable, or “No mechanics returned”)
  - Per-mechanic result list/cards
    - Mechanic name (if provided) and mechanic ID
    - Availability status badge: Available / Unavailable
    - Window start/end (as returned, displayed in user timezone)
    - Conflicts list (may be empty)
      - Conflict time range
      - Reason code (open enum)
      - Optional reference ID (e.g., workOrderId/appointmentId)
      - Optional description/notes if provided
- Error/warning banner area within the screenlet (non-blocking to rest of page)

## Layout
- Top: existing page header/navigation
- Main content: existing dispatch board/content (unchanged)
- Within main content: add “Availability Check” screenlet above or alongside board controls
  - [Availability Check Panel: Inputs row] → [Actions row] → [Results list / Error banner]
- Panel structure: Inputs at top, results directly below; error/warning banner appears above results but below inputs

## Interaction Flow
1. Dispatcher opens the Dispatch screen and sees the “Availability Check” panel.
2. Dispatcher selects a Location, enters Start datetime and End datetime, optionally selects one or more mechanics.
3. Dispatcher clicks “Check availability”.
4. Client validates inputs:
   1. Location is present.
   2. Start and End are valid datetimes.
   3. End is after Start; otherwise show inline validation and do not call API.
5. On successful API response:
   1. Render results list per mechanic returned.
   2. For each mechanic:
      - If availabilityStatus = AVAILABLE and conflicts is empty: show “Available” and “No conflicts in window.”
      - If availabilityStatus = UNAVAILABLE: show “Unavailable” and list all conflicts in the window with time range + reason code (and optional reference ID/description).
6. Scenario: Unavailable due to PTO
   1. If a conflict reasonCode = PTO appears, display it as a conflict block with its start/end and reason code.
7. Scenario: API returns empty mechanics array
   1. Show a neutral empty state (e.g., “No mechanics matched the filter for this window.”) while keeping inputs populated.
8. Scenario: Upstream People/HR dependency down (availability API HTTP 503)
   1. Show an error banner in the availability panel stating availability cannot be determined right now.
   2. Provide a “Retry” action that re-submits the same inputs.
   3. Keep the form inputs populated; do not block rendering of the rest of the dispatch board/work order data.
9. Scenario: Other API failure (non-503)
   1. Show an error banner with a generic failure message and Retry; keep inputs populated.

## Notes
- Timezone semantics:
  - Display returned datetimes in the user’s timezone.
  - If any “day” bucket presentation is used (optional), interpret the day/window in shop timezone when available and label the timezone used.
- AvailabilityResult expectations (UI-facing):
  - windowStart (required), windowEnd (required), generatedAt (recommended)
  - mechanics array may be empty
  - mechanic: id (required), name (optional), availabilityStatus (required: AVAILABLE/UNAVAILABLE), conflicts array (may be empty)
  - conflict: start/end (required), reasonCode (required; treat as open enum), referenceId (optional), description (optional)
- Acceptance criteria highlights:
  - Available mechanic with no conflicts shows “Available” and “No conflicts in window.”
  - Unavailable mechanic lists all conflicts with time ranges and reason codes (including PTO).
  - On HTTP 503, show non-blocking error banner + Retry; inputs remain populated; board remains usable.
- Design/implementation TODOs:
  - Confirm mechanic filter control type (multi-select vs typeahead) and how many mechanics are expected.
  - Define standardized visual treatment for conflict blocks (stacked list vs timeline-like blocks) while keeping reason codes prominent.
  - Ensure accessibility: status badges, error banners, and validation messages are screen-reader friendly.
