## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

# 1. Story Header

**Title:** Catalog: Search Catalog by Keyword/SKU and Filter (POS)  
**Primary Persona:** POS Clerk  
**Business Value:** Reduce time-to-find items during checkout by enabling fast, accurate catalog search with filters and pagination.

---

# 2. Story Intent

## As a / I want / So that
**As a** POS Clerk,  
**I want** to search the catalog by keyword, SKU/MPN, and category and refine results using common filters,  
**So that** I can quickly find and add the correct items during checkout.

## In-scope
- A Moqui screen for catalog search with:
  - Search input supporting keyword, SKU, and MPN entry
  - Category selection
  - Filters: tire size/spec, manufacturer, price range
  - Results list rendering `ProductSummary` including `availabilityHint`
  - Cursor-based pagination using `nextCursor`
- Frontend validation for obviously invalid inputs (e.g., negative prices; malformed tire spec fields if specified)
- Handling of empty results, validation errors, unauthorized responses, and service unavailability

## Out-of-scope
- Adding items to cart/work order/ticket (handled by another story)
- Editing product master data or inventory quantities/costs
- Advanced search syntax (quotes, boolean operators) unless explicitly supported by backend
- Multi-site inventory availability breakdowns (only `availabilityHint` as provided)

---

# 3. Actors & Stakeholders

- **Primary Actor:** POS Clerk
- **System Actors:**
  - Moqui Frontend (Screens in `durion-moqui-frontend`)
  - Inventory Service (catalog search endpoint; source of `SearchResult`)
- **Stakeholders:**
  - Product domain (SoR for product definitions/categories; consumed by search)
  - Work Execution domain (downstream user of selected product to add to work order)

---

# 4. Preconditions & Dependencies

- User is authenticated in the POS frontend.
- User has permission to access catalog search (permission name not provided; see Open Questions).
- Backend search API exists and matches (or is compatible with) backend story #17:
  - Request shape: `SearchQuery`
  - Response shape: `SearchResult` with cursor pagination (`nextCursor`)
- Backend provides `availabilityHint` in `ProductSummary` (may be degraded if inventory enrichment unavailable per backend note).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Primary: POS navigation menu item “Catalog Search” (or equivalent POS landing action).
- Secondary: From checkout/search bar entry point (if exists) deep-linking into same screen with prefilled `queryTerm`.

## Screens to create/modify
- **Create** `apps/pos/screen/CatalogSearch.xml` (name/path illustrative; align to repo conventions)
  - Screen includes a search form and results section.
- **Create/Reuse** a reusable results component (Vue/Quasar) used within the screen (if project uses SPA widgets embedded in Moqui screens).
- **Modify** navigation/menu screen to add route/link to Catalog Search screen.

## Navigation context
- Screen should preserve search state in URL parameters (query term, category, filters) to support refresh/back navigation **without losing context**.
- Cursor (`nextCursor`) should be stored in view state, not necessarily in URL (cursor is opaque and can be long).

## User workflows
### Happy path
1. Clerk opens Catalog Search.
2. Clerk enters query term (keyword or SKU/MPN) and optionally selects category + filters.
3. Clerk submits search.
4. Results render with key fields and availability hint.
5. Clerk navigates to next page using “Load more” / “Next” (cursor-based) and continues scanning.

### Alternate paths
- Clerk applies filters after initial search; results refresh from first page (cursor reset).
- Clerk clears filters; results refresh accordingly.
- Clerk searches and gets no results; sees empty state and can adjust query/filters.

---

# 6. Functional Behavior

## Triggers
- User presses Enter in search input or clicks “Search”.
- User changes category or any filter and triggers “Apply filters” (explicit apply) or auto-apply (see Open Questions; default to explicit apply only if unclear).

## UI actions
- Input fields:
  - `queryTerm` (free text)
  - `categoryId` (single select)
  - `manufacturer` (select or text)
  - `priceMin`, `priceMax`
  - `tireSpecs` fields (width, aspectRatio, diameter, and any additional fields supported)
- Buttons:
  - Search
  - Clear
  - Apply filters (if separate)
  - Load next page (uses `nextCursor`)
- Results list actions:
  - View details (optional; if there is a product details screen—otherwise omit)
  - (No “Add” action in this story)

## State changes (frontend)
- Local view state:
  - `searchRequest` (current query/filters)
  - `results.items`
  - `results.metadata.totalItems/pageSize/nextCursor`
  - `loading`, `errorBanner`, `validationErrors`
- When any search criteria changes and a new search is submitted:
  - Reset results and cursor (fresh search)
- When “Load next page”:
  - Append items; update `nextCursor`

## Service interactions
- Call backend search service with:
  - Normalized request parameters (frontend should not implement ranking/normalization logic; backend owns search semantics)
  - Cursor pagination params

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Price range:
  - `minPrice` and `maxPrice` must be numeric decimals when provided
  - `minPrice >= 0`, `maxPrice >= 0`
  - If both provided, `minPrice <= maxPrice`
- Pagination:
  - Page size defaults to 25
  - If UI exposes page size selector, enforce max 100 client-side; otherwise do not expose
- Tire specs:
  - If tire spec fields are present, they must be integers and positive
  - Do not guess additional spec fields beyond width/aspect/diameter without backend contract (see Open Questions)

## Enable/disable rules
- Disable “Search” while request in-flight.
- Disable “Load next page” when `nextCursor` is null/empty or while loading.

## Visibility rules
- Show “No results found” only after a successful search with `totalItems = 0` (or empty items and no error).
- Show error banner on non-2xx responses; keep last successful results visible unless the search request was a fresh search (see Error Flows).

## Error messaging expectations
- 400 validation errors: show inline messages near relevant inputs when possible; otherwise show banner with backend message.
- 401/403: show “You don’t have access to catalog search. Please sign in or contact a manager.”
- 5xx/timeouts: show “Catalog search is temporarily unavailable. Try again.”

---

# 8. Data Requirements

## Entities involved (frontend view models)
- `SearchQuery` (request DTO)
- `SearchResult` (response DTO)
- `ProductSummary` (response item)

## Fields (type, required, defaults)

### SearchQuery (frontend → backend)
- `queryTerm` (string, optional)
- `categoryId` (string, optional)
- `filters` (object, optional)
  - `manufacturer` (string, optional)
  - `priceRange.min` (decimal, optional)
  - `priceRange.max` (decimal, optional)
  - `tireSpecs` (object, optional)
    - `width` (int, optional)
    - `aspectRatio` (int, optional)
    - `diameter` (int, optional)
    - `...` (unknown; do not send extra keys unless backend contract defined)
- `pagination` (object, optional)
  - `cursor` (string, optional)
  - `pageSize` (int, optional; default 25; cap 100)

### SearchResult (backend → frontend)
- `metadata.totalItems` (int, required)
- `metadata.pageSize` (int, required)
- `metadata.nextCursor` (string|null, required)
- `items[]` (array<ProductSummary>, required)

### ProductSummary
- `productId` (string/UUID, required)
- `sku` (string, required)
- `mpn` (string, optional)
- `name` (string, required)
- `descriptionShort` (string, required)
- `listPrice` (decimal, required)
- `manufacturer` (string, required)
- `availabilityHint` (enum string, optional but expected)

## Read-only vs editable
- All `ProductSummary` fields are read-only.
- Search input fields are editable prior to submit; after submit they remain editable for new searches.

## Derived/calculated fields (frontend)
- Display-only:
  - Availability badge derived from `availabilityHint` value mapping
  - “Showing N results” derived from `items.length` and `totalItems` (if provided)

---

# 9. Service Contracts (Frontend Perspective)

> Backend endpoint path is not provided in the inputs; contract below defines required behavior but not exact URL. Implementation must bind to the existing Moqui service naming conventions in this repo.

## Load/view calls
- (Optional) Load categories list for category filter:
  - `GET categories` or Moqui service call to retrieve category options
  - If not available, category filter must be hidden/disabled until contract exists (see Open Questions)

## Create/update calls
- None.

## Submit/transition calls
- **Catalog search call**
  - Method: `POST` (preferred) or `GET` with query params (if backend does)
  - Request body/params: `SearchQuery`
  - Response: `SearchResult`

## Error handling expectations
- `400 Bad Request`: backend returns validation details/message; frontend surfaces message and highlights fields when possible
- `401 Unauthorized`: redirect to login or show session-expired flow consistent with app conventions
- `403 Forbidden`: show access denied message
- `429 Too Many Requests` (if present): show “Please wait and try again” and throttle repeated submissions (see Applied Safe Defaults)
- `5xx`: show transient error; allow retry

---

# 10. State Model & Transitions

## Allowed states (screen-level)
- `Idle` (no search yet)
- `Loading` (request in-flight)
- `Loaded` (results available, may include 0 results)
- `Error` (last request failed)

## Role-based transitions
- Authenticated user with catalog search permission: may transition `Idle → Loading → Loaded/Error`, `Loaded → Loading` (new search), `Loaded → Loading` (load more)
- Unauthorized/forbidden: transitions to `Error` with access message; no backend retry until auth state changes

## UI behavior per state
- Idle: show inputs, no results
- Loading: show spinner; disable inputs/buttons that submit
- Loaded: show results + pagination controls
- Error: show banner; keep inputs enabled for correction/retry

---

# 11. Alternate / Error Flows

## Validation failures (client-side)
- If `minPrice > maxPrice`:
  - Prevent request; show inline error
- If negative price:
  - Prevent request; show inline error
- If tire spec fields non-integer:
  - Prevent request; show inline error (field-specific)

## Validation failures (server-side 400)
- Show banner with backend message
- If backend returns field-level errors, map to form fields (needs contract; otherwise banner only)

## Concurrency conflicts
- Not applicable (read-only search).

## Unauthorized access
- 401: user session expired → follow app’s standard re-auth flow
- 403: show access denied; do not keep retrying automatically

## Empty states
- No results: show “No results found” and suggest clearing filters
- No categories available: hide category filter or show disabled select with message (depends on category API availability)

---

# 12. Acceptance Criteria

## Scenario: Keyword search returns matching products
**Given** the POS Clerk is on the Catalog Search screen  
**When** the Clerk enters a keyword that exists in a product’s name or short description and submits the search  
**Then** the results list includes that product’s `ProductSummary`

## Scenario: Exact SKU search prioritizes exact match
**Given** the POS Clerk is on the Catalog Search screen  
**When** the Clerk enters an exact SKU for an existing product and submits the search  
**Then** the first result item has that SKU and matches the corresponding product

## Scenario: Manufacturer filter restricts results
**Given** a search returns products from multiple manufacturers  
**When** the Clerk applies a manufacturer filter for one manufacturer and runs the search  
**Then** every returned result has `manufacturer` equal to the selected manufacturer

## Scenario: Price range validation prevents invalid submission
**Given** the POS Clerk has entered `minPrice` greater than `maxPrice`  
**When** the Clerk attempts to submit the search  
**Then** the UI blocks the request and displays a validation error indicating the range is invalid

## Scenario: Cursor-based pagination loads next page
**Given** a search response includes a non-null `nextCursor`  
**When** the Clerk selects “Load next page”  
**Then** the UI requests the next page using that cursor and appends the additional items to the results list

## Scenario: Default page size used when none selected
**Given** the POS Clerk submits a search without selecting a page size  
**When** the UI calls the search service  
**Then** the request omits `pageSize` or sends `pageSize=25` (per backend contract), and the UI renders up to 25 results for the first page

## Scenario: No results displays empty state
**Given** the POS Clerk submits a valid search that matches no products  
**When** the response returns `totalItems = 0` and an empty `items` list  
**Then** the UI displays a “No results found” empty state and does not show a next-page control

## Scenario: Backend 400 shows actionable error
**Given** the POS Clerk submits a search request that the backend deems invalid  
**When** the backend responds with HTTP 400 and an error message  
**Then** the UI displays the error message and keeps the search inputs available for correction

## Scenario: Service unavailable shows retryable error
**Given** the POS Clerk submits a search  
**When** the backend times out or returns HTTP 5xx  
**Then** the UI displays “Catalog search is temporarily unavailable” and provides a retry action

---

# 13. Audit & Observability

## User-visible audit data
- Not required (read-only search).

## Status history
- Not required.

## Traceability expectations
- Frontend logs (console/app logger per repo conventions) should include:
  - correlation/request id if provided by backend
  - search submission event with non-PII parameters (query term may be logged; confirm policy—see Open Questions)
- Emit frontend performance timings for:
  - time from submit → results rendered
  - error counts by status code class

---

# 14. Non-Functional UI Requirements

- **Performance:** UI must remain responsive during search; show loading indicator within 100ms of submission.
- **Accessibility:** All inputs labeled; keyboard submit supported; results list navigable via keyboard; ARIA for loading state.
- **Responsiveness:** Usable on standard POS resolutions; filters collapse appropriately on narrow widths.
- **i18n/timezone/currency:** Display `listPrice` using configured locale/currency formatting (do not assume currency code; use app defaults).

---

# 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATE-01**: Provide a standard “No results found” empty state after a successful search with 0 results; qualifies as safe UI ergonomics. Impacted sections: UX Summary, Error Flows, Acceptance Criteria.  
- **SD-UX-PAGINATION-01**: Use cursor-based “Load next page” behavior instead of offset pagination to match backend contract; safe because backend story explicitly defines cursor-first pagination. Impacted sections: Functional Behavior, Service Contracts, Acceptance Criteria.  
- **SD-ERR-MAP-01**: Map HTTP 400/401/403/5xx to standard banner messaging and retry affordance; safe because it’s generic error handling without changing domain policy. Impacted sections: Business Rules, Error Flows, Acceptance Criteria.  
- **SD-UX-THROTTLE-01**: Disable submit while in-flight to prevent accidental duplicate requests; safe UX ergonomics. Impacted sections: Functional Behavior, State Model.

---

# 16. Open Questions

1. **STOP (Blocking): What is the exact Moqui service name and route/URL for the catalog search API in this frontend repo?** (Needed to implement `transition`/service-call wiring.)  
2. **STOP (Blocking): Is there an existing category list API/service for the category filter? If yes, what are the identifiers and display fields (e.g., `categoryId`, `categoryName`) and does it support hierarchy?**  
3. **STOP (Blocking): What is the authorization/permission requirement to access catalog search in the frontend (permission string/role mapping)?**  
4. **STOP (Blocking): Tire specs filter contract: which fields are supported beyond `width/aspectRatio/diameter` (e.g., load index, speed rating, season, run-flat), and what are valid ranges/enums?**  
5. **Should filters auto-apply on change or require an explicit “Apply” action?** (Impacts UX and request frequency; choose based on POS expectations/performance.)  
6. **Should the query term be persisted/logged in frontend telemetry, or treated as sensitive?** (Defines safe logging behavior.)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Catalog: Search Catalog by Keyword/SKU and Filter  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/81  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Catalog: Search Catalog by Keyword/SKU and Filter

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **POS Clerk**, I want to search the catalog quickly so that I can find the right items during checkout.

## Details
- Search by keyword, SKU/MPN, category.
- Filters: tire size/spec, manufacturer, price range (basic).

## Acceptance Criteria
- Search returns results within target latency.
- Filters apply correctly.
- Pagination supported.

## Integrations
- Product domain provides definitions; inventory provides availability hints.

## Data / Entities
- SearchQuery, SearchResult, ProductSummary

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

### Frontend Requirements

- Implement Vue.js 3 components with TypeScript
- Use Quasar framework for UI components
- Integrate with Moqui Framework backend
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API
- TypeScript 5.x
- Quasar v2.x
- Moqui Framework integration

---
*This issue was automatically created by the Durion Workspace Agent*