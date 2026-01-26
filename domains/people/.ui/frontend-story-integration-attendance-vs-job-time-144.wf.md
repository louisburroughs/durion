# [FRONTEND] [STORY] Integration: Attendance vs Job Time Discrepancy Report
## Purpose
Provide Managers a report screen to compare technician attendance hours vs job time hours and identify discrepancies by day, location, and technician. Users run the report by supplying required date range and timezone filters (and optional location/technician filters). The screen displays a paged results table including discrepancy calculations, threshold application, and a flagged indicator, and shows a clear error banner on any non-2xx response.

## Components
- Page header/title: “Attendance vs Job Time Discrepancy Report”
- Filter form
  - Start date (required)
  - End date (required; inclusive per backend intent)
  - Timezone selector/input (IANA TZ, required)
  - Location filter (optional)
  - Technician multi-select (optional; repeatable person identifiers)
  - “Include flagged only” toggle/checkbox (optional; default false)
- Primary action button: “Run Report”
- Results area
  - Loading indicator/spinner (during request)
  - Error banner (for any non-2xx)
  - Results table (one row per technician + location + day)
  - Pagination controls (next/prev, page size if supported)
  - Empty state message (no rows returned)
- Table columns (read-only)
  - Technician (name/identifier)
  - Location
  - Day (interpreted in requested timezone)
  - Attendance hours
  - Job hours
  - Discrepancy hours (can be negative)
  - Threshold applied
  - Flagged (boolean)
  - Minutes (int minutes)

## Layout
- Top: Page header/title
- Below header: Filter form in a single panel/card
  - Row 1: Start date | End date | Timezone
  - Row 2: Location | Technician multi-select | Include flagged only
  - Row 3 (right-aligned): Run Report button
- Main content: Results area beneath filters
  - Error banner at top of results area (when present)
  - Table fills width; pagination at bottom-right

## Interaction Flow
1. User navigates to the report screen as an authenticated Manager and sees the filter form and an initial empty/placeholder results state.
2. User enters required filters: start date, end date (inclusive intent), and timezone (IANA TZ).
3. User optionally selects a location, one or more technicians (multi-select), and/or toggles “Include flagged only”.
4. User clicks “Run Report”.
5. UI sends a GET request to the People domain endpoint using People REST conventions, passing query params for date range, timezone, and any optional filters (including repeatable technician/person identifiers).
6. While the request is in flight, UI shows a loading indicator and disables the Run button to prevent duplicate submissions.
7. On 2xx response:
   1. UI renders the results table with one row per technician+location+day.
   2. UI displays attendance hours, job hours, discrepancy hours, threshold applied, flagged status, and minutes as provided.
   3. UI shows pagination controls using paging metadata from the list response; user can change pages to fetch/display additional rows.
8. On empty results (2xx with no rows): UI shows an empty state message in the results area (table hidden or shown with “No results” row).
9. On any non-2xx response (including mapped upstream failures):
   1. UI stops the run, hides loading state, and shows an error banner.
   2. If available from the standard error schema, UI displays status-appropriate details (e.g., 400 validation info; 409 conflict info) in the banner.

## Notes
- Acceptance criteria: Running the report with required filters triggers a GET request with those parameters and renders a results table with one row per technician+location+day including attendance hours, job hours, discrepancy hours, threshold applied, and flagged status.
- Backend dependencies/constraints:
  - Endpoint exists and follows People REST conventions; list response includes paging metadata.
  - Rows include required fields; “day” must be interpreted in the requested timezone.
  - Discrepancy hours can be negative; minutes is an integer.
  - Error schema follows People standard; any non-2xx must surface as an error banner and abort the run.
- Default behavior: “Include flagged only” defaults to false.
- Risk/requirements gaps to handle defensively in UI:
  - Exact query param names/values are defined by backend; UI should map form fields to the finalized v1 path and param contract once confirmed.
  - Ensure technician filter supports repeatable query params (multiple person identifiers).
  - Validate required fields client-side (presence) to reduce 400s, but still handle 400/409 per standard schema.
