## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Inventory Availability: View On-hand and ATP by Location / Storage Location

### Primary Persona
Service Advisor

### Business Value
Enable accurate quoting and scheduling by showing real-time on-hand and available-to-promise (ATP) quantities for a part at a selected location (optionally narrowed to a storage/bin location), with consistent UOM.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to query and view on-hand and ATP for a part by location and optionally by storage location  
- **So that** I can quote and schedule customer work realistically based on current stock availability

### In-scope
- A frontend screen/workflow to request availability for a single product SKU at:
  - a parent **location** (aggregated across storage locations), and/or
  - a specific **storageLocation** (bin-level scope)
- Display returned quantities: `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity`, `unitOfMeasure`
- Handle “no inventory history” as a valid empty result (0s)
- Handle not-found and authorization errors in the UI
- Moqui screen(s), transitions, and service-call wiring to backend availability endpoint

### Out-of-scope
- Editing inventory, allocations, receipts, or ledger events
- Expected receipts in ATP calculation (ATP v1 excludes expected receipts)
- UOM conversions (request/response in product base UOM only)
- Batch/multi-SKU availability queries (single SKU per request only)

---

## 3. Actors & Stakeholders
- **Primary Actor:** Service Advisor
- **Stakeholders:** Inventory Manager (data correctness), Work Execution (scheduling), Pricing (may consume ATP), Front Desk/Estimator users
- **System Actors:** Moqui frontend, Inventory backend service (availability)

---

## 4. Preconditions & Dependencies
- Backend provides an availability API consistent with backend story #36:
  - Request: `productSku` (required), `locationId` (required), `storageLocationId` (optional)
  - Response: `AvailabilityView` with on-hand, allocated, ATP, UOM in base UOM
  - Error cases: `404 product not found`, `404 location not found`, success with 0s if no history
- Frontend has a way to select/enter:
  - `productSku`
  - `locationId`
  - optional `storageLocationId`
- Authentication is already in place; backend enforces authorization (403 on forbidden)

**Dependency Note:** The story does not define how SKU/location/storageLocation are looked up (search pickers). If they do not exist, this story will use minimal inputs and/or existing pickers, but see Open Questions.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Inventory module navigation: “Availability” (or similar) under Inventory
- Optional: contextual link/button from estimate/work order part selection UI (if exists). If not present, entry is via Inventory menu only.

### Screens to create/modify
- **New Screen:** `apps/pos/screen/inventory/Availability.xml` (name illustrative; follow repo conventions)
  - Purpose: query and display availability for one SKU at a location (optionally storage location)
- **Optional Reusable Section:** `inventory/AvailabilityQueryForm.xml` for embedding elsewhere (if repo uses sections)
- **No changes** to backend; frontend only calls backend.

### Navigation context
- From POS main menu → Inventory → Availability
- Screen supports direct navigation with query parameters for deep links:
  - `productSku`, `locationId`, `storageLocationId` (optional)

### User workflows
**Happy path (location-level):**
1. User enters/selects SKU
2. User selects location
3. User leaves storage location blank
4. User clicks “Check Availability”
5. UI displays on-hand, allocated, ATP, and base UOM

**Happy path (storage-level):**
1. Same as above, but user selects a storage location
2. UI displays values scoped to that storage location

**Alternate paths:**
- Missing required inputs → inline validation prevents submit
- SKU/location not found (404) → user-friendly error and keep inputs
- No inventory history → show quantities as 0 and explain “No ledger history found”
- Forbidden (403) → show “You do not have access” and do not display sensitive details

---

## 6. Functional Behavior

### Triggers
- User presses “Check Availability” (form submit)
- Screen loads with query params present (auto-run query once)

### UI actions
- Input fields:
  - Product SKU (text or picker)
  - Location (picker/selector)
  - Storage Location (optional picker/selector; enabled only when location selected)
- Action buttons:
  - Submit query
  - Clear/reset

### State changes (frontend)
- `idle` → `loading` → `success` or `error`
- Persist last successful result on screen until user changes inputs or clears
- When user changes SKU/location/storageLocation after a success, mark result “stale” until re-queried

### Service interactions
- On submit/auto-run: call backend availability endpoint with the three identifiers
- Map response to a view model shown on screen
- Map HTTP errors to UI messages (see Error Flows)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `productSku` is required and trimmed; reject empty/whitespace
- `locationId` is required
- `storageLocationId` is optional
- If `storageLocationId` is provided, it must belong to selected `locationId` (UI should prevent selection outside location if picker supports it; otherwise rely on backend validation and show error)

### Enable/disable rules
- Disable “Check Availability” until `productSku` and `locationId` are present
- Disable storage location selector until a location is selected

### Visibility rules
- Results panel hidden until a query succeeds or returns “no inventory history” (still success with 0s)
- Error banner shown only on failed query

### Error messaging expectations
- 404 product not found: “Part/SKU not found. Verify the SKU and try again.”
- 404 location/storage not found: “Location not found or no longer active.”
- 400 validation: show backend message if safe and user-actionable; otherwise show “Invalid request.”
- 403: “You do not have permission to view availability.”
- 5xx/network: “Availability service is unavailable. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Read-only view model:** `AvailabilityView` (returned by backend)
- Frontend may also rely on existing entities/services for location and storage location selection if available (not defined in provided inputs).

### Fields (type, required, defaults)
**Request**
- `productSku`: string, required
- `locationId`: string/UUID, required
- `storageLocationId`: string/UUID, optional (default null/empty)

**Response: AvailabilityView**
- `productSku`: string, required (echo)
- `locationId`: UUID/string, required
- `storageLocationId`: UUID/string, nullable
- `onHandQuantity`: decimal, required (default display 0.0 if missing is not allowed; backend should always return)
- `allocatedQuantity`: decimal, required
- `availableToPromiseQuantity`: decimal, required
- `unitOfMeasure`: string, required (product base UOM)

### Read-only vs editable
- All response fields are read-only
- Only request inputs are editable

### Derived/calculated fields (frontend)
- None; ATP is computed server-side
- Optional formatting only (e.g., fixed decimals for display), without altering values

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
- If query params are present on screen load (`productSku`, `locationId`, optional `storageLocationId`), perform availability call automatically once.

### Create/update calls
- None.

### Submit/transition calls
- **Availability query call**
  - Method/path: **TBD** (must align to backend implementation)
  - Input: `productSku`, `locationId`, optional `storageLocationId`
  - Output: `AvailabilityView`

**Moqui implementation guidance**
- Implement a Moqui service (client-facing) that performs the HTTP call to the backend (or uses existing integration service in the repo).
  - Example service name (illustrative): `inventory.Availability.get#Inventory`
- Screen transition `checkAvailability` calls the service and places result in context for rendering.

### Error handling expectations
- HTTP 404 mapped to specific not-found messages (see Business Rules)
- HTTP 403 mapped to permission message
- Other 4xx/5xx mapped to generic error with correlation/trace id if available in response headers/body (do not assume; see Open Questions)

---

## 10. State Model & Transitions

### Allowed states (UI)
- `idle` (no query yet)
- `loading`
- `success` (result displayed)
- `error` (error displayed, inputs preserved)

### Role-based transitions
- If backend denies access (403), user remains in `error` with no results shown.
- No frontend-only role logic is assumed; permission enforcement is backend-led.

### UI behavior per state
- `idle`: show form only
- `loading`: disable inputs and submit; show spinner/progress
- `success`: show results panel + form (form remains editable)
- `error`: show error banner + form for correction

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Missing SKU → inline field error; no network call
- Missing location → inline field error; no network call

### Backend validation (400)
- Show message returned if it references input fields (SKU/location/storage) and is safe; otherwise generic “Invalid request.”

### Concurrency conflicts
- Not applicable (read-only query). If backend returns 409 for some reason, show generic error and allow retry.

### Unauthorized access
- 401: prompt re-authentication (existing app pattern)
- 403: show permission error; do not show cached/previous results for different SKU/location

### Empty states
- Backend “no inventory history” returns success with all quantities 0:
  - Show 0 values and a note: “No inventory history for this item at the selected scope.”

---

## 12. Acceptance Criteria

### Scenario 1: Location-level availability success
**Given** I am an authenticated Service Advisor  
**And** I am on the Inventory Availability screen  
**When** I enter `productSku = "SKU-123"` and select `locationId = "LOC-A"`  
**And** I submit “Check Availability”  
**Then** the UI calls the availability service with `productSku="SKU-123"` and `locationId="LOC-A"` and no `storageLocationId`  
**And** the UI displays `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity`, and `unitOfMeasure` from the response.

### Scenario 2: Storage-location scoped availability success
**Given** I have selected `locationId = "LOC-WAREHOUSE"`  
**When** I also select `storageLocationId = "BIN-1"` and submit  
**Then** the UI calls the availability service including `storageLocationId="BIN-1"`  
**And** the UI displays quantities scoped to that storage location.

### Scenario 3: No inventory history returns zeros (success state)
**Given** the backend returns a success response with `onHandQuantity=0`, `allocatedQuantity=0`, `availableToPromiseQuantity=0`  
**When** I submit a valid query  
**Then** the UI shows a success results panel with all three quantities displayed as 0 in the returned base `unitOfMeasure`  
**And** the UI indicates “No inventory history” (or equivalent) without treating it as an error.

### Scenario 4: Product not found (404)
**Given** I submit `productSku="SKU-DOES-NOT-EXIST"` with a valid `locationId`  
**And** the backend responds `404 Not Found` for the SKU  
**Then** the UI shows an error message indicating the SKU was not found  
**And** the previously entered inputs remain editable for correction  
**And** no results panel is shown.

### Scenario 5: Location not found (404)
**Given** I submit a query with `locationId` that does not exist  
**And** the backend responds `404 Not Found`  
**Then** the UI shows an error message indicating the location was not found  
**And** inputs remain for correction.

### Scenario 6: Forbidden (403)
**Given** I submit a valid query  
**And** the backend responds `403 Forbidden`  
**Then** the UI shows “You do not have permission to view availability.”  
**And** the UI does not display quantities.

### Scenario 7: Required field validation prevents submit
**Given** I am on the Inventory Availability screen  
**When** I leave `productSku` empty and click “Check Availability”  
**Then** the UI shows an inline validation error on the SKU field  
**And** no backend call is made.

---

## 13. Audit & Observability

### User-visible audit data
- Not required (read-only query).

### Status history
- Not applicable.

### Traceability expectations
- Frontend should include correlation ID in error display/logs when available (e.g., from response header) without exposing sensitive data.
- Log (frontend console/logger) a structured event for:
  - query initiated (SKU, locationId, storageLocationId presence)
  - query success (timing, quantities omitted from logs if considered sensitive; if allowed, include)
  - query failure (HTTP status, error code/message)

(Exact logging mechanism should follow repo conventions.)

---

## 14. Non-Functional UI Requirements
- **Performance:** UI should render results immediately upon response; avoid blocking UI thread; show loading indicator during call.
- **Accessibility:** Form fields labeled; error messages announced (ARIA) via Quasar standard patterns.
- **Responsiveness:** Usable on tablet resolutions typical of POS.
- **i18n/timezone/currency:** Not applicable (quantities + UOM only). Do not assume currency formatting.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show explicit empty/success-with-zeros messaging for “no inventory history”; qualifies as UI ergonomics and does not change domain logic; impacts UX Summary, Error Flows, Acceptance Criteria.
- SD-UX-LOADING-STATE: Disable submit and show loading indicator during network call; qualifies as UI ergonomics; impacts UX Summary, State Model.
- SD-ERR-HTTP-MAP: Standard HTTP→UI mapping (400/401/403/404/5xx) without inventing business policy; qualifies as standard error-handling mapping; impacts Business Rules, Error Flows, Acceptance Criteria.

---

## 16. Open Questions
1. What is the **exact backend endpoint path and auth mechanism** the Moqui frontend should call for availability (e.g., `/v1/inventory/availability` vs something else), and what is the expected request/response envelope (plain JSON vs wrapped `{data: ...}`)?
2. Does the frontend already have **existing pickers** for `locationId` and `storageLocationId` (and a known service to load them)? If not, should this story:
   - (a) use free-text UUID entry, or
   - (b) include building minimal location/storage lookup UI (which would expand scope)?
3. Should the Availability screen support **deep-linking via URL query params** officially (bookmarked by advisors), and if so what are the canonical parameter names and routing conventions in this repo?
4. Are availability quantities considered sensitive such that frontend logs must **avoid logging the returned numeric quantities** (only status/timing), or is it acceptable to log them for debugging?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/100

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/100  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want on-hand/ATP so that we can quote and schedule realistically.

## Details
- On-hand computed from ledger; ATP = on-hand - allocations + expected receipts (optional).
- Provide per location and optionally per storage location.

## Acceptance Criteria
- Availability query returns on-hand and ATP.
- Consistent UOM handling.
- SLA suitable for estimate UI.

## Integrations
- Product/workexec query availability; product may surface ATP to pricing.

## Data / Entities
- AvailabilityView, AllocationSummary, ExpectedReceiptSummary

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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

====================================================================================================

BACKEND STORY REFERENCES (FOR REFERENCE ONLY)

----------------------------------------------------------------------------------------------------

Backend matches (extracted from story-work):

[1] backend/36/backend.md

    Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

----------------------------------------------------------------------------------------------------

Backend Story Full Content:

### BACKEND STORY #1: backend/36/backend.md

------------------------------------------------------------

Title: [BACKEND] [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage  
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/36  
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory

---
**Rewrite Variant:** inventory-flexible  
**Clarification Applied:** #233 (ATP formula, UOM scope, SLA, definitive ledger event types)
---

## Story Intent
**As a** Service Advisor,  
**I need to** know the real-time on-hand and available-to-promise (ATP) quantities for a specific part at a given location,  
**so that I can** provide accurate quotes and schedule customer work with confidence.

## Actors & Stakeholders
- **Primary Actor**: Service Advisor
- **System Actors**:
  - Inventory Service (computes and serves availability)
  - POS UI (consumes availability)
- **Stakeholders**:
  - Work Execution (confirms parts availability for scheduled jobs)
  - Pricing (may use ATP as an input)
  - Inventory Manager (operational planning and stock control)

## Preconditions
- An immutable inventory ledger exists and records stock movements with product, location, movement type, and quantity.
- A system for recording stock allocations (soft reservations for specific work orders/sales) exists and is accessible.
- Products (SKU + base UOM) are defined in Product domain.
- A location hierarchy exists (parent locations and optional storage/bin locations).

## Functional Behavior
The Inventory Service exposes an API endpoint providing on-hand and ATP for a specified product.

1. **Trigger**: Request for availability.
2. **Input**: `productSku`, `locationId`, optional `storageLocationId`.
3. **Processing**:
   - Compute `onHandQuantity` from the inventory ledger (net sum of physical stock movements; see Business Rules).
   - Query allocation system for `allocatedQuantity` for the same scope.
   - Compute `availableToPromiseQuantity` using the defined formula.
   - If only `locationId` is provided, aggregate across all child storage locations.
   - If `storageLocationId` is provided, scope is narrowed to that storage location.
4. **Output**: Return an `AvailabilityView` including `onHandQuantity`, `allocatedQuantity`, `availableToPromiseQuantity`, and `unitOfMeasure`.

## Alternate / Error Flows
- **Product Not Found**: If `productSku` is not found, return `404 Not Found`.
- **Location Not Found**: If `locationId` / `storageLocationId` is not found, return `404 Not Found`.
- **No Inventory History**: If product has no ledger entries for the scope, return success with quantities = 0.

## Business Rules

### On-Hand Calculation (Resolved)
On-hand is the net sum of **physical stock movements** (inbound/outbound) plus count variances. Allocations/reservations are **not** part of on-hand.

**Include in On-Hand (add/subtract by direction)**
- Inbound (positive):
  - `GOODS_RECEIPT`
  - `TRANSFER_IN`
  - `RETURN_TO_STOCK`
  - `ADJUSTMENT_IN`
  - `COUNT_VARIANCE_IN`
- Outbound (negative):
  - `GOODS_ISSUE`
  - `TRANSFER_OUT`
  - `SCRAP_OUT`
  - `ADJUSTMENT_OUT`
  - `COUNT_VARIANCE_OUT`

**Explicitly excluded from On-Hand**
- `RESERVATION_CREATED`, `RESERVATION_RELEASED`
- `ALLOCATION_CREATED`, `ALLOCATION_RELEASED`
- `BACKORDER_CREATED`, `BACKORDER_RESOLVED`
- `PICK_TASK_CREATED`, `PICK_TASK_COMPLETED` (unless these post one of the physical movement events above)

### ATP Calculation Formula (Resolved)
**ATP v1:** $\text{ATP} = \text{OnHand} - \text{Allocations}$  
**Expected receipts are out of scope** for this story.

Optional forward-compatibility: the API may return `expectedReceiptsQty` as nullable, but it MUST NOT be included in ATP.

### Allocation Definition
An allocation is a soft reservation for a specific purpose (e.g., scheduled work order) that has not yet been physically picked/issued.

### Unit of Measure (UOM) Consistency (Resolved)
All internal calculations and API responses are in the product’s **base UOM**.

Out of scope: request/response UOM conversions (e.g., case vs each).

### Location Aggregation
Querying a parent location aggregates all stock within the location across child storage locations.

## Data Requirements

### API Request
- `productSku` (string, required)
- `locationId` (UUID, required)
- `storageLocationId` (UUID, optional)

### API Response (`AvailabilityView`)
- `productSku` (string)
- `locationId` (UUID)
- `storageLocationId` (UUID, nullable)
- `onHandQuantity` (decimal, base UOM)
- `allocatedQuantity` (decimal, base UOM)
- `availableToPromiseQuantity` (decimal, base UOM)
- `unitOfMeasure` (string; product base UOM)

## Acceptance Criteria

**Scenario 1: Simple On-Hand Calculation**
- **Given** product `SKU-123` has ledger history at `LOC-A`: `GOODS_RECEIPT +10` and `GOODS_ISSUE -2`
- **When** availability is requested for `SKU-123` at `LOC-A`
- **Then** `onHandQuantity = 8`.

**Scenario 2: ATP Calculation with Allocations**
- **Given** `onHandQuantity = 8` for `SKU-123` at `LOC-A`
- **And** `allocatedQuantity = 3` at `LOC-A`
- **When** availability is requested for `SKU-123` at `LOC-A`
- **Then** `availableToPromiseQuantity = 5`.

**Scenario 3: Aggregate Calculation by Parent Location**
- **Given** `LOC-WAREHOUSE` contains `BIN-1` and `BIN-2`
- **And** ledger shows 5 units of `SKU-ABC` in `BIN-1` and 3 units in `BIN-2`
- **When** availability is requested for `SKU-ABC` at `LOC-WAREHOUSE` (no `storageLocationId`)
- **Then** `onHandQuantity = 8`.

**Scenario 4: Request for a Non-Existent Product**
- **Given** product catalog does not contain `SKU-DOES-NOT-EXIST`
- **When** availability is requested
- **Then** return `404 Not Found`.

**Scenario 5: Request for a Valid Product with No Inventory**
- **Given** `SKU-456` exists but has no ledger entries at `LOC-A`
- **When** availability is requested
- **Then** return success with `onHandQuantity = 0`, `allocatedQuantity = 0`, `availableToPromiseQuantity = 0`.

## Audit & Observability
- **Logging**: Log each availability request (`productSku`, `locationId`, optional `storageLocationId`) and resulting quantities. Log lookup/summation errors.
- **Metrics**:
  - Timer for endpoint latency.
  - Counters for success and error responses (4xx/5xx).
- **Tracing**: Distributed tracing from API gateway through Inventory Service to data stores.

## Performance Requirements (Resolved)
- Core endpoint (single product, single location, warm cache):
  - **P95 < 200ms**
  - P50 < 80ms
  - P99 < 400ms
- Measurement is at the service boundary, excluding caller network latency.

## Open Questions
None.

------------------------------------------------------------

====================================================================================================

END BACKEND REFERENCES