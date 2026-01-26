# [FRONTEND] [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope
## Purpose
Provide a read-only UI for admins to confirm which locations have been synced locally from durion-hr, including their ACTIVE/INACTIVE status. Surface recent sync outcomes and detailed sync logs so users can validate pricing scope targets and diagnose sync issues without backend access. Ensure robust handling for empty data, backend failures, and authorization errors.

## Components
- Global page header with title and breadcrumb/back navigation
- Tabs or secondary navigation: “Synced Locations” and “Location Sync Logs”
- Synced Locations list
  - “Last sync status” badge (derived from most recent SyncLog)
  - Status filter (default: ACTIVE; option: INACTIVE; option: ALL)
  - Search input (by location name/code)
  - Paginated table with columns: Location Code/ID, Location Name, Status
  - Row click to open Location Detail (read-only)
  - Loading state (skeleton/spinner)
  - Empty state (no locations match filter)
  - Error state with retry button
- Synced Location detail (read-only)
  - Key fields: Location Code/ID, Name, Status
  - Optional fields (display only if present): external reference/identifier; address (either structured or CSV; render-resilient)
  - Audit/recency fields: Created At (optional), Last Updated At (optional), Last Sync At and/or Last Sync Status (if available)
- Location Sync Logs list
  - Paginated table with columns: SyncLog ID, Started At, Ended At, Status, Created/Updated counts (created/updated/failed/total as available)
  - Row click to open SyncLog Detail (read-only)
  - Empty state message: “No sync runs recorded yet.”
  - Loading state
  - Error state with retry button
- SyncLog detail (read-only)
  - Fields: ID, Started At, Ended At (or “Running”), Status (SUCCESS/PARTIAL_FAILURE/FAILURE)
  - Metrics: created count, updated count, failed count, total count
  - Optional message/details text block
- Auth/permission handling UI
  - 401: redirect to login or “Session expired” message
  - 403: “Access denied” message with no data rendered

## Layout
- Top: Page title + breadcrumb; right side shows “Last sync status” badge on Synced Locations view
- Below header: Tabs (Synced Locations | Location Sync Logs)
- Main (Synced Locations): Filter row (Status dropdown + Search) above paginated table; detail opens on separate route/page
- Main (Sync Logs): Table with pagination; detail opens on separate route/page
- Inline states: loading/empty/error blocks replace table area as needed

## Interaction Flow
1. Navigate to “Synced Locations” screen (authenticated with required permission).
2. Screen loads with default filter = ACTIVE; fetch synced location list.
3. Display paginated list with columns (code/ID, name, status); hide INACTIVE rows while ACTIVE filter is applied.
4. User changes filter to INACTIVE or ALL; list refreshes accordingly.
5. User selects a location row; navigate to Location Detail.
6. Location Detail loads read-only fields; show optional fields only if present; show recency/audit fields (Last Sync/Updated timestamps) if available.
7. User navigates to “Location Sync Logs” tab; fetch sync log list.
8. If logs exist, show paginated list; user selects a log row to view SyncLog Detail (status, timestamps, counts, optional message).
9. If sync logs are empty, show: “No sync runs recorded yet.”
10. Backend failure (locations): show error state “Locations could not be loaded” with Retry; Retry re-attempts fetch.
11. Backend failure (sync logs): show error state “Sync logs could not be loaded” with Retry; Retry re-attempts fetch.
12. Unauthorized (401) on either screen: redirect to login or show session expired message; do not display data.
13. Forbidden (403) on either screen: show access denied message; do not display data.

## Notes
- Read-only UI: no create/edit actions; all fields displayed as non-editable text.
- “Last sync status” badge on Synced Locations list header is derived from the most recent SyncLog (either included in list response or fetched separately).
- Address/external identifier fields are optional and must be render-resilient; if structured address is unavailable, display CSV/string form; if absent, omit the section.
- SyncLog Ended At may be null when a run is in progress; display “Running” and keep status visible.
- Ensure pagination is consistent across lists; preserve filter/search state when navigating back from detail.
- Error/empty/loading states must replace the table region and keep navigation/tabs usable.
- Authorization handling: 401 and 403 must prevent any location/sync log data from rendering.
