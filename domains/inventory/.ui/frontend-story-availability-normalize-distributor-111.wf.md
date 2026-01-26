# [FRONTEND] [STORY] Availability: Normalize Distributor Inventory Feeds (Stub via Positivity)

## Purpose
Provide Moqui/Quasar Inventory screens to operate on distributor availability feed data: view ingestion runs, inspect normalized outputs, and triage unmapped parts and feed exceptions. Enable cursor-paginated list browsing with date range filtering, safe rendering of JSON/payload fields, and permission-gated update actions. Ensure consistent error UX with correlation ID propagation and deterministic error schema handling.

## Components
- Global
  - Inventory main navigation with new entry: “Availability Feeds” (permission-gated)
  - Section sub-navigation tabs: Runs, Normalized, Unmapped, Exceptions
  - Page header with title + breadcrumbs
  - Correlation ID display/copy (when available) in error states
- Shared list page components (all four lists)
  - Date range filter (from/to ISO-8601 datetime)
  - Optional filter inputs only if supported by backend (no invented filters)
  - Search/Apply button; Reset button
  - Results table/list with key columns per entity
  - Cursor pagination: “Load more” button; loading spinner; empty state
  - Row click → detail deep link
  - Banner area for non-field-mappable errors
- Shared detail page components (all four details)
  - Summary header with key identifiers + status chips
  - Read-only field grid (timestamps, enums, strings)
  - Safe JSON viewer for payload/raw/JSON-like fields (collapsed/expand, copy)
  - Back link to corresponding list (preserve filters when possible)
  - Error states: Not found, Forbidden, Timeout with Retry, Conflict with Reload
- Unmapped triage update components (list + detail)
  - Status chip (read-only always visible)
  - If user has update permission: status dropdown (allowed v1 statuses) + Save button
  - In-flight state: Save disabled; inline success/error feedback
- Exceptions triage update components (list + detail)
  - Severity chip + status chip
  - If user has update permission: status control (if supported) and operator notes editor + Save
  - Conflict handling UI (409): reload prompt; preserve typed notes until reload completes

## Layout
- Top: Inventory header + breadcrumbs; page title; sub-navigation (Runs | Normalized | Unmapped | Exceptions)
- Main: filter bar (date range + optional supported filters) above results or detail content
- Main (list): results table → “Load more” at bottom; banner area above table for errors
- Main (detail): left-aligned summary + field grid; below/aside: safe JSON viewers for raw/payload fields; bottom: update panel (if permitted)
- Inline sketch: Top Nav/Breadcrumbs → Tabs → [Filters] → [List/Table + Load more] OR [Detail + JSON viewers + Update panel]

## Interaction Flow
1. Navigate to Inventory → Availability Feeds.
2. If user lacks read permission for the section, do not show the menu entry; if deep-linked and 403 returned, show Forbidden state without rendering sensitive payload fields.
3. Runs list:
   1) Open Runs tab; default shows filter form requiring from/to.
   2) User enters valid date range and clicks Apply.
   3) UI calls List Feed Runs with from, to, limit (optional), cursor (optional).
   4) Render returned FeedRun items; show empty state if none.
   5) User clicks “Load more”; UI calls again using nextCursor as cursor and appends results (do not rely on offset).
4. Runs detail:
   1) User clicks a run row; navigate to run detail.
   2) Load run detail via Moqui proxy; render fields and any raw JSON safely.
   3) Handle 404 with “Not found” and back link to Runs list.
5. Normalized list/detail:
   1) Open Normalized tab; apply date range filters; list uses cursor pagination.
   2) Click a normalized record to open detail; render normalized fields plus raw fields.
   3) Any JSON-like fields render via safe JSON viewer (no unsafe HTML rendering).
6. Unmapped list + triage update:
   1) Open Unmapped tab; apply date range; list with cursor pagination and status chips.
   2) Open an unmapped record detail.
   3) If user has update permission, show status dropdown + Save; otherwise hide controls.
   4) On Save: send PATCH update (shape TBD) via Moqui proxy; disable Save while in-flight; no auto-retry.
   5) On success: update displayed status; on 403: show forbidden message and do not change record.
7. Exceptions list + triage update:
   1) Open Exceptions tab; apply date range; list with cursor pagination and severity/status.
   2) Open exception detail; render required fields including payload safely.
   3) If user has update permission, allow status and/or operator notes edits (per backend support) and Save.
   4) On 409 conflict: show “This record changed since you loaded it” with Reload action; preserve user-entered note in memory until reload completes; prevent blind overwrite.
8. Error UX (all screens):
   1) 400/422: show inline validation errors when field mapping is possible; otherwise show banner.
   2) 401: redirect to login/session refresh.
   3) Timeout: after 8 seconds show timeout state with Retry.
   4) Always propagate/display correlation ID when available for troubleshooting.

## Notes
- Must use Moqui proxy integration only; no direct backend calls.
- All lists must use cursor pagination (nextCursor) and append behavior; do not implement offset-based paging.
- Date range filters are required for list queries where specified; enforce basic validation before calling APIs.
- Safe rendering requirement: any payload/raw/JSON-like fields must use a safe JSON viewer; never render as HTML; avoid leaking sensitive data on 403.
- Permission gating:
  - Read vs update actions must be separated; hide update controls when user lacks update permission.
  - If backend returns 403 on mutation, show forbidden message and keep UI state unchanged.
- Deterministic error schema: design UI to map known field errors inline; otherwise show banner with correlation ID.
- FeedException required fields to display (at minimum): id, severity, status, message, payload (string/JSON; redacted-safe), correlationId, createdAt/updatedAt.
- FeedRun minimum fields to display (as available): id, startedAt/endedAt, status, correlationId.
- TODOs / open questions to resolve during implementation:
  - Final screen routes/paths must follow repo conventions (deep links).
  - Exact endpoints and response shapes for normalized/unmapped detail and PATCH payload shapes.
  - Confirm which optional filters are supported by backend for each list before adding UI controls.
  - Confirm optimistic locking mechanism for exceptions updates (version field vs ETag header).
