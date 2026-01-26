# [FRONTEND] [STORY] Timekeeping: Export Approved Time for Accounting/Payroll
## Purpose
Enable an Accounting Clerk to export approved time entries for a selected inclusive date range and one or more authorized locations, in CSV or JSON format. The screen requests an asynchronous export job, shows the latest export status, and allows download when ready. It also provides user-visible auditability and access to prior export requests via an export history view.

## Components
- Page header with title: “Export Approved Time”
- Breadcrumb navigation: Accounting → Timekeeping Exports → Export Approved Time
- Timezone note (always visible): “Dates interpreted in selected location timezone(s).”
- Warning banner (conditional): multiple selected locations with different timezones
- Export parameter form
  - Date picker: Start date (required)
  - Date picker: End date (required)
  - Location multi-select (required; min 1; options loaded from Location domain, already authorization-filtered to active + permitted)
  - Format selector (required): CSV | JSON
- Actions
  - Primary button: Request Export (requires execute export permission)
  - Secondary button: Refresh Status (view permission sufficient)
  - Secondary button/link: View History
- Current/Last request status panel
  - Export ID (UUIDv7; copyable)
  - Status (PENDING | RUNNING | READY | FAILED)
  - Requested at / Started at / Completed at timestamps (as available)
  - Records exported count (when available)
  - Records skipped count due to missing mappings (when available)
  - Correlation/trace identifiers display (if provided)
  - Failure message (user-safe, if FAILED)
  - Download button (enabled only when READY)
- Export history list screen (separate view)
  - List/table of prior export requests (from backend history endpoint)
  - Columns: exportId, requestedAt, status, format, date range, locations summary, counts (if available)
  - Row action: View details (and Download if READY)
- Export detail view (within history or dedicated panel)
  - Status panel (same fields as current/last request)
  - Download action when READY

## Layout
- Top: Breadcrumb + Page title
- Below title: Always-visible timezone note; conditional warning banner beneath it
- Main content (single column):
  - Export parameter form (date range, locations, format) + action buttons row
  - “Last Export Request” status panel with exportId/status/counts/timestamps + Refresh/Download
  - Link/button to “View History”
- History view:
  - Top: Breadcrumb + title “Export History”
  - Main: History list/table; right-side or below: selected export detail panel (optional)

## Interaction Flow
1. User navigates: Accounting → Timekeeping Exports → Export Approved Time.
2. UI loads location options from Location domain (already filtered to active + user-authorized).
3. User selects Start date and End date (inclusive), selects one or more locations, and selects format (CSV or JSON).
4. UI always displays timezone note; if selected locations span multiple timezones, show warning banner about per-location timezone semantics (no UI conversion).
5. User clicks Request Export:
   1. If user lacks execute export permission, disable/hide Request Export and show permission-required messaging near the button.
   2. Validate required fields (start/end dates present; at least one location; format selected).
   3. Submit async export request to backend; propagate W3C Trace Context headers (traceparent, tracestate) unchanged; generate traceparent only if absent.
6. On backend response, display/update the “Last Export Request” status panel:
   1. Show exportId (UUIDv7; validate UUID format client-side before displaying as copyable).
   2. Show status (PENDING/RUNNING/READY/FAILED) and any available timestamps.
7. User clicks Refresh Status to poll/refresh the last requested exportId status (view permission sufficient).
8. When status becomes READY:
   1. Enable Download button.
   2. Display records exported count and records skipped count (if provided by backend).
   3. Display correlation/trace identifiers (if provided).
9. If status becomes FAILED:
   1. Show user-safe failure message (if provided) and technical message (if provided and deemed user-safe by backend).
   2. Keep inputs editable so user can adjust parameters and request a new export (new exportId).
10. User clicks View History:
   1. UI calls backend history endpoint and lists prior export requests with requested by/when/parameters/status.
   2. User selects an export request to view details; if READY, allow Download.
11. Returning to Export Approved Time:
   1. Inputs remain editable for new requests.
   2. Status panel remains tied to the last requested exportId unless user selects a different exportId from history (then status panel reflects the selected export).

## Notes
- New screen path/name must match repository Accounting screen conventions (final route TBD but must align with Accounting → Timekeeping Exports).
- Backend-authoritative response fields only; UI must not invent additional export fields beyond:
  - exportId (UUIDv7, required)
  - status (PENDING | RUNNING | READY | FAILED, required)
  - recordsExportedCount (optional until READY/FAILED)
  - recordsSkippedCount (optional until READY/FAILED; skipped due to missing mappings if provided)
  - requestedAt/startedAt/completedAt (timestamps; optional but recommended)
  - message (optional; present when FAILED)
  - userMessage (optional; user-safe failure message)
- Timezone semantics: export date range interpreted in the location timezone; label clearly and always. If multiple timezones selected, show warning banner; no conversion performed in UI.
- Permissions:
  - View screen required to access and refresh status/history.
  - Execute export permission required to request export (button gated accordingly).
- Audit visibility requirement: show “Export requested by/when/parameters/status” in history; treat history as immutable record from backend.
- Download availability: only when status is READY; otherwise disabled with explanatory hint (e.g., “Available when export is READY”).
- After requesting an export, do not lock the form; allow immediate new requests while keeping the status panel anchored to the last requested (or history-selected) exportId.
