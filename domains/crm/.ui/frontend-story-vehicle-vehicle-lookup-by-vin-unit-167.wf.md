# [FRONTEND] [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate

## Purpose
Enable a Service Advisor to quickly find a vehicle by VIN, unit number, or license plate using partial search and then select the correct match. After selection, the UI loads a full vehicle + owner snapshot and returns to the calling screen with the selected vehicleId (and ownerId when available). The experience must prevent duplicate actions during in-flight requests and avoid logging sensitive identifiers in client telemetry.

## Components
- Page header/title: “Vehicle Lookup”
- Search form
  - Search input (supports partial text; placeholder like “VIN, Unit #, or Plate”)
  - Search button (disabled while request in-flight)
- Results area
  - Results table/list (paginated)
  - Columns (as available): VIN, Unit #, License Plate, Year/Make/Model/Trim, Owner name (plain text when provided), additional display-only fields (e.g., inventory decision fields)
  - Row action: Select (disabled while snapshot load in-flight or while that row is being selected)
  - Empty state message (no matches)
  - Error state message (search failure)
- Pagination controls
  - Page size selector (default 25)
  - Page navigation (uses page index; default 0)
  - Result count / range display
- Loading indicators
  - Search loading state (near Search button and/or results area)
  - Snapshot loading state (on selected row and/or global overlay)
- Navigation/return handling (implicit; no visible component unless a Back/Cancel link is standard)

## Layout
- Top: Page title + brief helper text (“Search by VIN, unit number, or license plate”)
- Main (stacked):
  - Search form row: [Search input (wide)] [Search button]
  - Results section: table/list fills width beneath search
  - Bottom of results: pagination controls aligned right; result count aligned left

## Interaction Flow
1. Screen opens with route parameters available: query (optional, default empty), pageIndex (optional, default 0), pageSize (optional, default 25), plus required caller context parameter(s) used to return to the originating screen.
2. User enters a partial VIN/unit/plate query and clicks Search.
3. While search request is in-flight:
   1. Disable Search button.
   2. Show loading indicator in results area.
4. On successful search response:
   1. Render returned vehicle summary items in a paginated table/list.
   2. Render owner name as plain text when owner context is included.
   3. Update pagination UI using pageIndex and pageSize; show total/result count if available.
5. If search returns no matches:
   1. Show an empty state (“No vehicles found for this search.”).
   2. Keep pagination hidden or disabled.
6. If search fails:
   1. Show an inline error message and allow retry (Search button re-enabled when not in-flight).
7. User selects a result row via Select action.
8. While snapshot load is in-flight:
   1. Disable all Select actions (or at minimum the selected row) and prevent double-click by disabling the row being selected immediately.
   2. Show a loading indicator on the selected row and/or a global loading state.
9. On snapshot load success:
   1. Navigate back to the caller route, including vehicleId in the return URL.
   2. If snapshot includes ownerId, also include ownerId in the return URL.
10. On snapshot load failure:
   1. Show an inline error (row-level or page-level).
   2. Re-enable Select actions once not in-flight.

## Notes
- Enable/disable rules:
  - Disable Search button when search request is in-flight.
  - Disable Select actions when snapshot load is in-flight OR when a row is being selected (prevent double-click).
- Route/screen parameters:
  - Support query (string, optional; default empty), pageIndex (int, optional; default 0), pageSize (int, optional; default 25), and required caller context parameter(s) for return navigation.
- Results DTO variability:
  - Many fields are optional and may be null/omitted due to permission/redaction; UI must render gracefully with missing values.
  - Some fields are display-only (per decision notes) and should be treated as non-editable text.
- Snapshot response:
  - Minimum required: vehicleId.
  - Optional but preferred: ownerId; include in return URL when present.
  - Additional snapshot sections are opaque; UI must not depend on unknown fields.
- Telemetry/privacy acceptance criteria:
  - Client telemetry/logging must not include VIN, license plate, or raw query text.
  - Telemetry may include query length, result count, and selected vehicleId (and/or other non-sensitive identifiers as allowed).
- Pagination acceptance criteria:
  - UI must display pagination using pageIndex and pageSize and reflect backend-provided paging behavior.
- Risk/implementation TODOs:
  - Confirm exact return route format and required caller parameter name(s).
  - Confirm which columns are required vs. optional for the results table and any redaction rules for VIN/plate display.
