# [FRONTEND] [STORY] Mapping: Configure EventType → Posting Rule Set
## Purpose
Provide Accounting users with screens to manage Posting Rule Sets that map an EventType to a versioned posting rules definition. Users must be able to list rule sets, view rule set metadata and versions, create new rule sets (version 1) and new versions, and publish/archive versions according to backend policy. The UI must surface server-side validation (including “unbalanced” details) and prevent publishing when the backend reports invalid/imbalanced rules.

## Components
- Global page header: “Accounting → Posting Rules → Posting Rule Sets”
- Rule Set List page
  - Filters (optional): EventType, status, search by ID/name (as supported)
  - Paginated table: Rule Set ID, EventType, Latest Version, Latest Status, Updated At (if available)
  - Row action: View
  - Primary action button: Create Posting Rule Set
  - Empty state + loading + error banners
- Rule Set Detail page
  - Metadata panel: Rule Set ID (copyable), EventType (display)
  - Versions table: Version #, Version ID, Status, Updated At/By (if available)
  - Row action: View (version detail)
  - Action: Create New Version (requires base version selection)
  - Base version selection modal (defaults to latest PUBLISHED else latest)
- Version Detail page
  - Header: EventType + Rule Set ID + Version #
  - Status badge: DRAFT / PUBLISHED / ARCHIVED
  - Rules definition viewer (JSON read-only; editable only for DRAFT if edit supported)
  - Audit metadata: Created At/By, Updated At/By
  - Validation results panel (server-side): codes/messages; “unbalanced” details
  - Actions (conditional): Edit (if supported), Publish, Archive, Create New Version
  - Toasts/banners for 400/401/403/404/409/5xx outcomes
- Version Create/Edit page (DRAFT only)
  - Mode selector: “New Rule Set (v1)” vs “New Version from Base”
  - Fields (New Rule Set v1): EventType (select from recognized values), Rules Definition (JSON editor)
  - Fields (New Version): Base Version selector (read-only once chosen), EventType (read-only), Rules Definition (JSON editor prefilled)
  - Buttons: Save (creates new DRAFT version), Cancel

## Layout
- Top: Breadcrumbs + page title + primary action (Create Posting Rule Set / context actions)
- Main (List): Filters row above a full-width table; pagination at bottom
- Main (Rule Set Detail): Left/Top metadata panel; below it versions table; right/top action button “Create New Version”
- Main (Version Detail): Header with status + actions; below: Rules Definition panel (main) and Validation/Audit side panel (right) or stacked below on narrow screens

## Interaction Flow
1. List Posting Rule Sets
   1) User navigates to Posting Rule Sets list.
   2) UI calls list endpoint; shows loading state then paginated rows with latest version summary.
   3) User applies filters/search (if supported); UI refreshes list and preserves inputs on transient errors.
   4) User clicks “View” to open Rule Set Detail.
2. View Rule Set Detail + Versions
   1) UI loads rule set detail (metadata + versions list).
   2) User copies Rule Set ID; EventType is displayed read-only.
   3) User clicks “View” on a version row to open Version Detail.
   4) User clicks “Create New Version”; modal prompts base version (default latest PUBLISHED else latest); confirm routes to Version Create (from base).
3. Create New Posting Rule Set (Version 1 as DRAFT)
   1) From list, user clicks “Create Posting Rule Set”.
   2) UI loads recognized EventType values; user selects EventType and enters Rules Definition JSON.
   3) User clicks Save; UI creates a new DRAFT version 1 record.
   4) On success, route to Version Detail for the created version.
4. Create New Version from Existing
   1) From Rule Set Detail or Version Detail, user selects “Create New Version”.
   2) UI pre-fills Rules Definition from base version; EventType is read-only (inherits from rule set).
   3) User edits JSON and clicks Save; UI creates a new DRAFT version (never modifies existing published/archived).
   4) On success, route to new Version Detail; prior versions remain unchanged/read-only.
5. Publish DRAFT Version (with validation gating)
   1) On Version Detail (DRAFT), user clicks Publish.
   2) UI calls publish endpoint; if backend returns validation errors/unbalanced details, show Validation panel and block publish completion.
   3) If publish succeeds, status updates to PUBLISHED; actions update (Publish hidden; Archive shown per policy).
6. Archive Version
   1) On Version Detail (PUBLISHED; or per backend policy), user clicks Archive.
   2) UI calls archive endpoint; on success, status becomes ARCHIVED and archive action is removed.
7. Error/edge handling (all screens)
   1) 400: show field/summary errors; do not auto-retry.
   2) 401/403: show “Not authorized”; disable/hide actions; handle safely without revealing existence beyond safe messaging.
   3) 404: show “Not found” and return to list.
   4) 409: show conflict message (e.g., non-DRAFT publish, concurrent changes) and prompt reload.
   5) 5xx: show generic error; allow retry; preserve user inputs/filters.

## Notes
- State-based editability: DRAFT allows editing Rules Definition; EventType editable only during new rule set (v1) creation. PUBLISHED/ARCHIVED are fully read-only; only “Create New Version” is allowed (archive/publish visibility per policy).
- Role/permission enforcement: frontend must hide/disable unauthorized actions and handle 403 responses gracefully; exact permission tokens are blocking and must not be invented.
- Validation UX is server-driven: display actionable codes/messages; include “unbalanced” details; prevent publish when backend reports imbalance/invalid references.
- Traceability: show IDs (copyable where specified), status, version number, and audit metadata clearly on detail views.
- API/telemetry: propagate correlation/tracing headers on all calls.
- Blocking open questions to reflect in UI as TODOs:
  - Source/endpoint for recognized EventType values (required for create).
  - Whether EventType can change per version (assumed no; UI treats as rule-set-level).
  - Whether ARCHIVED versions can be used as base for “Create New Version” (policy TBD).
  - Whether inline “Edit” on Version Detail is supported vs separate edit screen.
