# [FRONTEND] [STORY] Ledger: Record Stock Movements in Inventory Ledger
## Purpose
Provide a permission-gated Inventory Ledger UI that lets users search and browse read-only stock movement entries and open a detail view for any entry. Support deep links from related areas (Product, Location, Storage Location, Work Order Line) that pre-fill filters and may auto-query on load. Ensure safe rendering of nullable fields and consistent error handling (401 redirect, 403 forbidden with correlation ID).

## Components
- Page header: “Inventory Ledger”
- Permission gate wrapper (authorized vs forbidden state)
- Filter panel/form
  - SKU text input (trimmed; optional)
  - Location picker (LocationRef; optional)
  - Storage Location picker (optional; filtered by Location; disabled until Location selected)
  - Movement Type multi-select (enum; optional)
  - Date/Time range: From (ISO timestamp), To (ISO timestamp) with validation
  - Reference text input (optional)
  - Source Transaction ID text input (optional)
  - Work Order ID text input (optional)
  - Work Order Line ID text input (optional)
  - Page size selector (default 25)
  - Buttons: Search/Submit, Clear filters
- Results list/table
  - Sort indicator (Timestamp descending by default)
  - Row click affordance (navigates to detail)
  - “Load more” button (cursor pagination; no total count)
  - Empty state messaging
- Detail view screen (read-only)
  - Field list of all backend-returned properties
  - Linkage fields section (sourceTransactionId, workOrderId, workOrderLineId) when present
  - Null-safe placeholders (“—”)
  - Back navigation
- Error/empty states
  - 401 handling (redirect/session refresh per app convention)
  - 403 forbidden state with “Technical details” including correlation ID header value
  - Generic error fallback with correlation ID display
- Picker empty states
  - Locations: “No locations available.”
  - Storage locations: “No storage locations for this site.”

## Layout
- Top: Page header + brief subtitle/help text (optional: “Search stock movement history”)
- Main (two sections stacked):
  - Filters (card/panel) above results
  - Results list/table below with “Load more” at bottom
- Detail screen: header (Back + title) + single-column read-only field list
- Inline sketch: Header → [Filters panel] → [Results list + Load more] ; Detail: Header → [Read-only fields]

## Interaction Flow
1. Navigate to Inventory → Ledger.
2. On initial load, do not auto-query; show filters and an empty results state prompting the user to search.
3. User enters any combination of filters (SKU trimmed; optional fields allowed).
4. Storage Location control remains disabled until a Location is selected; once selected, Storage Location options are filtered to that Location.
5. User sets optional From/To timestamps; validate ISO format and range (prevent invalid submissions).
6. User clicks Search/Submit:
   1. Fetch ledger entries using current filters, default sort Timestamp descending, page size default 25.
   2. Render results list; if none, show an empty results state (no errors).
7. User clicks a row in the results list:
   1. Navigate to Ledger Entry Detail screen.
   2. Display all fields returned by backend as read-only; show linkage fields when present.
   3. Any nullable/missing fields render as “—” with no client error.
8. User clicks “Load more”:
   1. Request next page using cursor pagination.
   2. Append results; do not display total count.
9. User clicks Clear filters:
   1. Reset all filter fields to defaults (including page size back to 25 and cursor cleared).
   2. Results return to pre-search state (no auto-query).
10. Deep link entry points:
   1. From Product detail “View Ledger”: open Ledger with SKU pre-filled; if deep-link params present, auto-query on load.
   2. From Location detail “View Ledger”: open with Location pre-filled; auto-query on load.
   3. From Storage Location detail “View Ledger”: open with Location + Storage Location pre-filled; auto-query on load.
   4. From Work Order Line “View Movement History”: open with SKU and optionally Work Order / Work Order Line pre-filled; auto-query on load.
11. Auth/error handling:
   1. If list request returns 401: redirect to login/session refresh per app convention; do not show ledger data.
   2. If list request returns 403 with correlation ID header: show forbidden state; include correlation ID in “Technical details”; do not show ledger data.
   3. For other errors: show generic error fallback with correlation ID when available.

## Notes
- Scope is view-only: no create/edit/delete ledger mutations, no exports (CSV/PDF), and no client-side on-hand computation.
- Inventory Adjustment create/post UI is explicitly out of scope; this story covers ledger list + detail only.
- Default list behavior: no auto-query on initial load unless deep-link parameters are present (then auto-query).
- Results list: default sort by timestamp descending; cursor pagination with “Load more”; no total count displayed.
- Detail view must be robust to nullable fields; display “—” for missing values and avoid runtime errors.
- Permission-gated access required; unauthorized/forbidden states must not leak ledger data.
- Picker empty states must match copy:
  - Locations: “No locations available.”
  - Storage locations for a site: “No storage locations for this site.”
- Technical details section in error states should surface correlation ID from response headers for support/debugging.
