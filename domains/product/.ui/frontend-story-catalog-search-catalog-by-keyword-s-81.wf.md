# [FRONTEND] [STORY] Catalog: Search Catalog by Keyword/SKU and Filter
## Purpose
Provide a Catalog Search screen for POS Clerks to find products by keyword, exact SKU, or MPN and refine results using common catalog filters. The screen must call Moqui proxy endpoints only, render backend-provided results without client-side reordering/filtering, and support cursor-based pagination. It must handle validation, empty results, and error states (unauthorized, service unavailable) with correlation ID visibility.

## Components
- Page header/title: “Catalog Search”
- Search form
  - Text input: Keyword / SKU / MPN (single field)
  - Category selector (conditional; hidden if category list contract not available)
  - Filter group: Tire size/spec fields (numeric inputs)
  - Filter: Manufacturer (select)
  - Filter: Price range (min/max decimal inputs)
  - Button: Search (submit)
  - Button/link: Clear/Reset (optional but implied by state reset needs)
- Results section
  - Results list (rows/cards)
    - Product name
    - SKU
    - MPN (if present)
    - Manufacturer (if present)
    - Price (if present)
    - Availability/status (as provided by backend; display-only)
  - Button: Load more (cursor pagination)
  - Loading indicator (for initial search and load-more)
- Empty state panel (no results)
- Inline validation error summary (form-level) and/or field-level messages
- Error banner/panel for API errors (message + status + correlationId)
- Unauthorized state message (e.g., “Not authorized”)
- Service unavailable state message with retry affordance (reuse Search)

## Layout
- Top: Page title + brief helper text (“Search by keyword, SKU, or MPN; refine with filters.”)
- Main (stacked):
  - Search/Filter form (grouped sections: Query, Category (conditional), Filters, Actions)
  - Results header row (e.g., “Results” + count of loaded items if desired)
  - Results list (cards/rows)
  - Bottom of list: [Load more] button + small spinner area
  - Below/overlay: Empty state or Error banner when applicable

## Interaction Flow
1. Screen load
   1. Initialize state: query/filters empty, results empty, cursor null, loading false, error null, empty-state hidden.
   2. If category list contract exists, fetch/receive categories and show Category selector; otherwise keep it hidden.
2. Fresh search (keyword/SKU/MPN)
   1. User enters text in “Keyword / SKU / MPN” (trim on submit) and optionally sets filters (category, tire spec, manufacturer, price range).
   2. On Search:
      1. Run client-side validation:
         - Price min/max must be non-negative decimals; min ≤ max if both present.
         - Tire spec numeric fields must be well-formed integers (no letters/negative).
      2. If validation fails: show inline validation messages; do not call API; keep prior results as-is (or optionally keep but do not append).
      3. If validation passes:
         - Set loading true; clear structured error; reset results list and cursor; hide empty state.
         - Call Moqui proxy catalog search endpoint (POST preferred) with cursor omitted and requestId header generated if not already present.
         - On success: render returned ProductSummary list; set cursor from response; set loading false; show empty state if list is empty.
         - On 401/403: show unauthorized message; set loading false; show correlationId if returned.
         - On 5xx/service unavailable/network: show error banner with message + status + correlationId; set loading false.
3. Manufacturer filter restricts results
   1. User selects Manufacturer and submits Search.
   2. UI sends manufacturer in request and renders only backend-returned items (no client-side filtering).
4. Exact SKU search
   1. User enters exact SKU and submits.
   2. UI renders backend ordering as-is; no client-side re-ordering.
5. Load more (cursor pagination)
   1. User clicks Load more.
   2. If cursor is missing/null: disable Load more or no-op with a small message (optional).
   3. If cursor exists:
      - Set loading true; clear structured error.
      - Call search endpoint with same current query/filters plus cursor.
      - Append returned items to existing list; replace cursor with returned cursor; set loading false.
      - If returned list is empty or cursor indicates end: hide/disable Load more.
6. Empty results
   1. After a successful search returning zero items: show empty state (“No products found. Try adjusting search or filters.”) and keep form values intact.
7. Error handling display
   1. For API errors: show error panel including correlationId (from response or requestId if applicable) and status; allow user to retry Search.

## Notes
- Integration constraint: UI must call Moqui proxy endpoints only (no direct calls to downstream services).
- Pagination: cursor-based; first page omits cursor; subsequent pages send cursor from prior response; do not depend on total count.
- Rendering: results list must display backend-provided availability/status; UI must not compute availability.
- Validation: block obviously invalid inputs (negative prices; malformed numeric tire spec fields) before API call.
- State management (frontend):
  - Maintain current query/filters, results list, cursor, loading boolean, structured error (message + correlationId + status), and empty-state visibility boolean.
  - Fresh search resets results and cursor; load more appends and replaces cursor.
- Logging (client): record request start/end timestamps, HTTP status class, correlation ID, and pagination action (fresh vs load more). Treat keyword as potentially sensitive; do not log it until policy is confirmed.
- Error UX: include correlationId in error display; handle unauthorized and service unavailability distinctly.
- Category selector is conditional: only show if a category list contract exists in this frontend; otherwise hidden.
