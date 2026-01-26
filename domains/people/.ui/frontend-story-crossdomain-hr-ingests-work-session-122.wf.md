# [FRONTEND] [STORY] CrossDomain: HR Ingests Work Sessions from Shopmgr
## Purpose
Provide a People/HR UI for Payroll Clerks to review timekeeping entries ingested from ShopMgr work sessions. The UI must support confident payroll preparation and compliance auditing by showing session times, approval status, and source identifiers, while ensuring entries are deduplicated and read-only. The experience must follow People-domain standards for paging, error handling, timezone display, and sensitive-field masking.

## Components
- Global People navigation (People → Timekeeping → Timekeeping Entries)
- Page header: “Timekeeping Entries”
- Filter form
  - Employee/person selector (search/select by personId; show display name if available)
  - Date range inputs (Start, End)
  - Approval status dropdown (Pending/Approved/Rejected)
  - Location selector (optional)
  - Work order selector (optional)
  - Primary button: Apply filters
  - Secondary button: Refresh
- Results table/list
  - Columns: Employee, Session start, Session end, Duration (optional), Approval status, Source session id
  - Row click / “View details” action
  - Empty state message
- Pagination controls
  - Page index controls (prev/next, page number)
  - Page size selector (default 25)
  - Total count / range indicator (if provided)
- Loading indicators (initial load, filter apply, paging)
- Error banner/inline error state (standard People error schema)
- Detail page header: “Timekeeping Entry” + Back to list link (preserve list params)
- Detail fields panel (read-only)
  - Approval status (with label mapping)
  - Session start/end timestamps (timezone rules applied)
  - Duration (backend-provided or computed for display)
  - Employee/person info
  - Location/work order (if present)
  - Source metadata: source system, source session id, source keys/identifiers as provided
- Status history/timeline (conditional; only if backend provides)
  - Items: status/outcome, actor, timestamp, comments/reason

## Layout
- Top: Breadcrumb + Page title + (optional) short helper text
- Main: [Filter form (top)] → [Results table (middle)] → [Pagination (bottom)]
- Detail: Top row [Back to list (left)] [Entry title/status badge (right)] → Main [Two-column read-only fields] → Bottom [Status history timeline (if present)]

## Interaction Flow
1. Navigate to People → Timekeeping → Timekeeping Entries.
2. System loads list with default paging (page=0, size=25) and current/empty filters; show loading state.
3. List renders rows with employee (display name else personId), session start, session end, approval status, and source session id; no duplicates should appear for the same source key.
4. User sets filters (employee, date range, approval status, optional location/work order) and selects Apply filters.
5. System validates date range (must be valid); on success, re-queries list with filter params and resets to first page; show loading state.
6. User changes page or page size; system re-queries with updated paging while preserving current filters.
7. User selects Refresh; system re-runs the current query (same filters + paging).
8. User selects a row / “View details”; system navigates to detail screen for that entry.
9. Detail screen renders all available fields from the detail endpoint, including approval status and source metadata (including source session id and any source identifiers); timestamps follow timezone display rules.
10. If approval status is Pending/Approved/Rejected, display corresponding read-only label; if rejection metadata exists, render it read-only.
11. If backend provides status history, render a read-only timeline; otherwise omit the section entirely.
12. User selects Back to list; system returns to list preserving prior filters, paging, and scroll position (where feasible).
13. Edge cases:
    1. Empty results: show “No timekeeping entries match your filters” with option to adjust filters/refresh.
    2. API error: show standard error state/banner and allow retry via Refresh.
    3. Missing optional fields (duration/location/work order/history): omit or show “—” without fabricating data.

## Notes
- Read-only story: no approve/reject actions; payroll review visibility only.
- Deduplication is a backend invariant; UI must not display duplicates for the same source key (DECISION-PEOPLE-005).
- Paging and error handling must follow People standards (DECISION-PEOPLE-021, DECISION-PEOPLE-018).
- Timestamp display must follow People timezone rules (DECISION-PEOPLE-015); ensure consistent formatting across list and detail.
- Sensitive field (SSN) must not be displayed by default (DECISION-PEOPLE-019); do not include it in list or detail even if present in payload unless explicitly allowed later.
- Duration: prefer backend-provided value; if absent, frontend may compute from start/end instants for display only (do not treat as authoritative).
- Approval status mapping:
  - Pending → “Pending approval”
  - Approved → “Approved”
  - Rejected → “Rejected” (render rejection comments/reason if provided)
- Query params supported include page/size and optional filters (personId, approval status, date range, locationId, workOrderId); omit any unsupported/undocumented params from UI.
