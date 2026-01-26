# [FRONTEND] [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution

## Purpose
Provide CRM Data Stewards / Support Engineers with operational screens to monitor VehicleUpdated ingestion processing health via ProcessingLog list and detail views. Enable fast filtering, paging, and safe inspection of identifiers and outcomes without exposing raw payloads or PII. Support navigation from a processing record to an existing (read-only) Vehicle detail screen while enforcing role/permission-gated access and consistent error/empty-state handling.

## Components
- Global navigation entry: CRM → Integrations → VehicleUpdated Processing Logs
- Permission gate wrapper (authorized vs. forbidden state)
- Page header: title, brief description, environment/context (optional)
- Filter/search form
  - ProcessingLog ID (UUID) input
  - Correlation ID input with mode selector: Exact / Starts with (no Contains)
  - Vehicle ID (UUID) input
  - Workorder Execution event ID (UUID) input (when present)
  - Optional UUID filters: sourceId / externalId (as provided by DTO)
  - Status dropdown (enum)
  - Date/time range: createdAt from/to; processedAt from/to (optional)
  - Retry metadata filters (optional): retryCount, nextRetryAt range
  - Buttons: Search/Apply, Clear filters
  - Inline validation messages (UUID format, correlation contains blocked)
- Results area
  - Loading state (spinner/skeleton)
  - Empty state panel with “No processing logs match your filters” + Clear filters
  - Error banner area (400/401/403/404/5xx) with server message + request correlation ID (if provided)
  - Paginated table (ProcessingLog list)
    - Columns: id, correlationId, vehicleId, status, createdAt, processedAt, eventId, sourceId, externalId, errorCode, errorMessage, retryCount, nextRetryAt, lastRetryAt (render optional fields only if present; otherwise “—”)
    - Default sort: createdAt descending
    - Row action: View details
- ProcessingLog detail view
  - Breadcrumbs: CRM → Integrations → VehicleUpdated Processing Logs → Processing Log {id}
  - Summary panel (key fields + status badge)
  - Copy-to-clipboard controls for ProcessingLog id and correlationId
  - Field groups:
    - Identifiers (vehicleId with link to Vehicle detail; eventId/sourceId/externalId)
    - Timing (createdAt, processedAt)
    - Outcome (status, errorCode, errorMessage)
    - Retry metadata (only if present)
    - Additional details section (details string/object; show “No additional details provided” if absent)
    - Conflict metadata section (redacted server-side; shown when applicable)
    - Payload-related fields (redacted server-side; never show raw payload)
  - Back to list button (preserve filters)
- Vehicle detail navigation link (read-only; reuse existing screen)

## Layout
- Top: CRM header + breadcrumbs + page title
- Main (List): Filter form (top) → banners (below form) → table (main) → pagination (bottom)
- Main (Detail): Breadcrumbs/title (top) → summary + copy actions (top-right) → grouped fields (stacked sections) → back link (bottom)

## Interaction Flow
1. Navigate to CRM → Integrations → VehicleUpdated Processing Logs.
2. If user lacks permission, show 403 forbidden state/banner and do not render operational data.
3. On initial load, request ProcessingLog list scoped to VehicleUpdated; show loading state; then render table sorted by createdAt desc.
4. User applies filters:
   1) Validate UUID fields client-side; if invalid, block submit and show inline “Must be a valid UUID”.
   2) Correlation filter: allow Exact or Starts with; if user attempts “contains” (e.g., selects/enters a contains mode), block submit and show “Contains search is not supported; use exact match or starts with”.
   3) Submit triggers list refresh; show loading; then results or empty state.
5. Empty results: show “No processing logs match your filters” and a Clear filters action that resets form and reloads default list.
6. Row selection: user clicks View details to open ProcessingLog detail view for that id.
7. Detail view renders all safe DTO fields:
   - Missing optional fields display “—”.
   - Missing details section displays “No additional details provided”.
   - Payload/conflict sections render only redacted server-provided content; never display raw payload.
8. User copies identifiers: click copy for ProcessingLog id and correlationId; show transient confirmation (non-blocking).
9. User navigates to Vehicle detail: click vehicleId link; open existing read-only Vehicle screen; user can return via back/breadcrumb preserving list context.
10. Error handling (defensive):
   - 400 validation errors: show banner with message; if fieldErrors map present, map to inline messages.
   - 401/403: show standard auth/forbidden banner; prevent data display.
   - 404 (missing log): show not-found banner with navigation back to list.
   - 5xx: show standard error banner; include request correlation ID if provided.

## Notes
- Must use dedicated backend read services returning redaction-safe DTOs; no direct entity-find from browser.
- List is scoped to VehicleUpdated processing logs; default sort createdAt descending; pagination required.
- Correlation search supports exact and prefix only; UI must prevent contains and also handle backend 400 defensively.
- Redaction requirement: never render raw payload; any payload-related or conflict metadata must be redacted server-side before reaching UI.
- Access control: operational screens must be permission-gated; 403 should be handled per standard banners.
- Detail view must support copying ProcessingLog id and correlationId.
- Contracted error payload fields: code, message, optional fieldErrors map; display request correlation ID when provided.
- Vehicle screen is out of scope for new fields; link only (read-only sufficient).
- TODO (implementation): confirm exact DTO field names for optional UUIDs, error fields, retry metadata, and conflict metadata structure to map into labeled UI groups.
