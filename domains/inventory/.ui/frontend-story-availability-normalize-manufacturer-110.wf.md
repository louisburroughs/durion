# [FRONTEND] [STORY] Availability: Normalize Manufacturer Inventory Feeds (Stub via Positivity)
## Purpose
Provide an Ops-facing UI under Inventory to view and navigate manufacturer availability feed processing: runs, unmapped parts, normalized records, and exceptions. Enable filtering (notably by manufacturerId and date range) and deep links into specific run detail and filtered lists. Support cursor-based pagination and schema-tolerant rendering of optional/JSON fields without UI crashes. Optionally allow triage actions for unmapped parts and exceptions based on permissions.

## Components
- Inventory navigation entry: “Availability Feeds (Ops)”
- Sub-navigation/tabs: Runs, Unmapped Parts, Normalized, Exceptions
- Global filter bar (per list view):
  - manufacturerId picker/select (fallback: free-text allowed only for manufacturerId)
  - date range (start/end)
  - status filter (where applicable)
  - search input (where applicable)
  - Apply/Reset buttons
- Runs list (cursor-paged):
  - Table/list rows with key run fields (id, manufacturerId, startedAt, finishedAt, feedType, status, counts, correlationId/message)
  - “Load more” button (uses next cursor)
  - Empty state panel
- Run detail view:
  - Summary header with run fields
  - Safe JSON renderer for any extra/unknown fields (truncate + expand + copy)
  - CTA links/buttons: “View unmapped parts”, “View exceptions”, “View normalized”
- Unmapped Parts list (cursor-paged):
  - Columns: identifiers (part/manufacturer), status, timestamps, mapping fields (when present), message/notes
  - Row action: Update status (triage) (permission-gated)
  - Update status modal/drawer (status select, optional note if supported)
- Normalized records list (cursor-paged, schema-tolerant):
  - Columns for minimum expected fields (id, manufacturerId, part identifiers, mappingId if present, created/updated timestamps, status)
  - Safe JSON renderer for raw/normalized objects (truncate + expand + copy)
- Exceptions list (cursor-paged + optional triage):
  - Columns: id, type, status, message, correlationId/runId (if supported), createdAt, resolvedAt
  - Row expand to show payload/details (safe render)
  - Triage action (permission-gated): update status/notes
- Toast/inline error banners for API failures
- Loading states (skeleton/inline spinner) for lists and detail

## Layout
- Top: Inventory header + breadcrumb “Inventory → Availability Feeds (Ops)”
- Left (or top secondary nav): Runs | Unmapped Parts | Normalized | Exceptions
- Main: Filter bar (sticky) above content; below is list/table or detail panel
- Main (detail): Summary card at top; below: sections for fields + JSON panels; right-aligned CTAs to related lists

## Interaction Flow
1. Navigate: User opens Inventory → Availability Feeds (Ops) → Runs; UI loads runs via Moqui proxy with default page size and cursor paging.
2. Filter runs: User sets manufacturerId and/or date range; clicks Apply; list refreshes from first cursor; “Load more” appends results using nextCursor (no total count).
3. Empty state: If no runs, show “No feed runs found for selected filters.” and suggest expanding date range (within retention).
4. View run detail: User clicks a run row; route supports deep link by runId; detail renders required fields and safely renders any additional JSON fields (truncate + expand/copy).
5. From partial-success run to unmapped: In run detail, user clicks “View unmapped parts”; navigates to Unmapped Parts with runId (or correlationId if runId filter unsupported) pre-applied; list loads and shows key unmapped fields.
6. Triage unmapped part (permission-gated): User selects a row action “Update status”; modal opens; on submit, UI calls update endpoint and updates the row in-place (or refetches current cursor page).
7. View normalized records: User opens Normalized; filters by manufacturerId and date range; UI loads normalized list; renders even if optional fields missing; JSON-like fields are truncated with explicit expand/copy controls.
8. View exceptions: User opens Exceptions; filters by manufacturerId and optionally runId/correlationId; list loads; row expand shows payload/details safely.
9. Triage exception (optional, permission-gated): User updates exception status/notes; UI sends only backend-exposed fields; updates row and resolvedAt when returned.
10. Deep links: User can open direct URLs to run detail, unmapped list filtered by runId/correlationId, and exceptions list filtered by manufacturerId and runId/correlationId; filters reflect in UI controls.

## Notes
- Screen tree under Inventory namespace (per repo conventions): Availability Feeds (Ops) container/menu; Runs list; Run detail; Normalized list; Unmapped Parts list + triage; Exceptions list; Exception detail (or expandable detail panel).
- Cursor pagination required (DECISION-INVENTORY-003): default page size (e.g., 25) and “Load more” UX; do not rely on total counts.
- Data models are read-only except triage actions:
  - FeedRun: render required fields; tolerate additional fields.
  - Normalized records: schema-tolerant; optional fields may be absent; must not crash.
  - FeedException: render required contract fields; payload may be object/string/nullable; always render safely.
- Safe JSON rendering (DECISION-INVENTORY-015): truncate long content; provide expand/collapse and copy-to-clipboard; ensure redacted-safe display (no special parsing assumptions).
- manufacturerId control: use picker/select if backend provides list; otherwise allow free-text only for manufacturerId (not for other filters unless explicitly supported).
- Permissions: triage actions require DECISION-INVENTORY-010; hide/disable actions when unauthorized.
- If backend does not support runId filtering for exceptions, use correlationId-only deep link/filter behavior.
