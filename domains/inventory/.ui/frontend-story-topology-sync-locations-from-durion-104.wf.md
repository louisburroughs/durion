# [FRONTEND] [STORY] Topology: Sync Locations from durion-hr
## Purpose
Provide read-only topology screens to browse Inventory Locations synced from durion-hr and the associated Sync Logs, with cross-linking between the two. Update the shared Inventory location picker used by stock movement flows to allow selection only of Active locations for new movements. Ensure historical movements that reference inactive/pending locations remain viewable with clear “not eligible for new movements” messaging, and handle backend defense-in-depth errors gracefully.

## Components
- Global header with page title and breadcrumbs
- Locations List
  - Filters: Status dropdown, Name text search
  - Table/list rows: name, code, status, timezone, updatedAt (optional), sync/score indicator (optional)
  - Row actions: open Location detail
- Location Detail (read-only)
  - Summary fields: id (UUIDv7), name, code, status, timezone
  - Metadata fields: createdAt/updatedAt, syncedAt, lastHrEventAt (if present), score (display only)
  - Safe JSON viewer for metadata (preview + expand)
  - Link/button: “View Sync Logs for this Location” (pre-filtered)
- Sync Logs List
  - Filters: outcome, date range, locationId, hrEventId (if present), correlationId (if present)
  - Table/list rows: occurredAt, outcome, locationId (if present), hrEventId (if present), correlationId (if present)
  - Copy-to-clipboard controls for IDs when present
  - Payload preview (safe JSON snippet) with expand affordance
- Sync Log Detail (read-only)
  - Fields: id, locationId (link to Location detail when present), hrEventId, occurredAt, outcome, message
  - Safe JSON viewer for payload (permission-gated; expand/collapse)
  - Copy-to-clipboard for id/locationId/hrEventId/correlationId when present
- Shared Component: Inventory Location Picker (used in “new stock movement” flows)
  - Search/select input with option list
  - Disabled/hidden options for Pending/Inactive with inline explanation
  - Read-only display state for pre-populated ineligible location with message
- Error/empty states
  - 404 not found with link back to list
  - 409 “Data changed; refresh”
  - 5xx/timeout retry panel showing correlationId if present

## Layout
- Top: Header + breadcrumbs (e.g., Locations / Location Detail; Sync Logs / Sync Log Detail)
- Main (List pages): Filters bar above a full-width table/list; right-aligned “Reset filters”
- Main (Detail pages): Two-column read-only layout: left = key fields; right = JSON viewers + related links
- Inline hint: [Header/Breadcrumbs] → [Filters] → [Table/List] ; Detail: [Fields | JSON/Links]

## Interaction Flow
1. User opens Locations List.
2. User filters by Status and/or searches by Name; list updates to matching locations.
3. User selects a location row to open Location Detail.
4. Location Detail displays read-only LocationRef fields; any JSON metadata renders via safe viewer (preview, expand).
5. User clicks “View Sync Logs for this Location” to navigate to Sync Logs List pre-filtered by locationId.
6. User opens Sync Logs List directly and filters by outcome, date range, locationId, hrEventId, and/or correlationId.
7. User uses copy-to-clipboard on available IDs in list rows (locationId/hrEventId/correlationId) without exposing payload contents.
8. User opens a Sync Log row to view Sync Log Detail; if locationId is present, user can navigate to the linked Location Detail.
9. New stock movement flow: user opens any “new stock movement” screen using the shared location picker.
10. Location picker shows only Active locations as selectable; Pending/Inactive are hidden or shown disabled with “Not eligible for new movements.”
11. If a movement screen is pre-populated with an Inactive/Pending location (historical record), the picker renders read-only and shows “Not eligible for new movements.”
12. Defense-in-depth: if user submits a movement referencing an Inactive/Pending location due to stale UI, and backend returns HTTP 422 with deterministic ineligible-location error, show inline/banner error: “Location is not eligible for new stock movements.”
13. Error handling: on 404 show not-found state with link back to the relevant list; on 409 show “Data changed; refresh”; on 5xx/timeout show retry with correlationId if present.

## Notes
- LocationRef fields are read-only: id (UUIDv7, canonical), name, code, status (Active/Pending/Inactive), timezone (IANA, optional), metadata (safe JSON), createdAt/updatedAt (optional), syncedAt (optional), lastHrEventAt (optional), score (display only).
- SyncLog fields are read-only: id (UUIDv7), locationId (optional but preferred for linking/filtering), hrEventId (optional), occurredAt (required), outcome (enum), message (optional), payload (JSON, optional; permission-gated; render safely and never log), correlationId (optional; display/copy when present).
- Safe JSON viewer requirements: render defensively (no HTML injection), provide preview in lists and expandable full view in details; never log payloads/quantities client-side.
- Copy-to-clipboard: support id/locationId/hrEventId/correlationId when present; provide subtle confirmation UI.
- Location picker acceptance criteria: only Active selectable for new movements; Pending/Inactive must be disabled/hidden with explanation; pre-populated ineligible location must be read-only with “not eligible for new movements.”
- Backend error mapping: HTTP 422 ineligible location → user-facing message; keep deterministic matching to avoid false positives.
- Generic error states must include navigation back to list and retry where applicable; include correlationId in UI when available.
