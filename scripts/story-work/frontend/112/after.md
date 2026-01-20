STOP: Clarification required before finalization

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

## 1. Story Header

### Title
[FRONTEND] [STORY] Availability: View On-hand and Available-to-Promise (ATP) by Location for a Product

### Primary Persona
Service Advisor (POS user creating quotes/estimates)

### Business Value
Enables accurate quoting and fulfillment expectation-setting by showing location-level on-hand and ATP for a selected product.

---

## 2. Story Intent

### As a / I want / So that
**As a** Service Advisor,  
**I want** to view on-hand and available-to-promise quantities by fulfillment location for a selected product,  
**So that** I can quote realistically and avoid committing inventory that is already reserved.

### In-scope
- A POS UI panel/page that, given a `productId`, loads and displays availability by location (on-hand and ATP).
- Handling of empty results, missing/invalid product, and unauthorized access per backend responses.
- Moqui screen + transitions + service-call integration to the Inventory availability endpoint.

### Out-of-scope
- Editing inventory, making reservations, allocations, transfers, or replenishment actions.
- Including expected replenishments in ATP (explicitly excluded in v1).
- Any changes to Product domain master data.
- Cross-product search UX beyond a minimal “enter/select productId” entry point (see Open Questions).

---

## 3. Actors & Stakeholders
- **Primary actor:** Service Advisor (end user)
- **System actor:** Moqui POS Frontend (screens calling backend)
- **Stakeholders:** Inventory Manager (trusts accuracy), Product/Catalog system (productId mapping), POS Leadership (quote accuracy/SLA)

---

## 4. Preconditions & Dependencies
- User is authenticated in the POS frontend.
- A valid `productId` is available in the current context (e.g., quote line item, product detail) or can be entered.
- Backend endpoint exists and is reachable:
  - `GET /api/v1/inventory/availability?productId=<id>`
- Backend response contract matches the backend story reference (#48) including:
  - `productId`
  - `locations[]` with `locationId`, `locationName`, `onHandQuantity`, `availableToPromiseQuantity`
- Authorization is enforced server-side; unauthorized yields `403`.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
At least one must be implemented (final selection depends on clarification):
1. **From Product context**: A “Availability” action in a product detail screen (productId already known).
2. **From Quote/Estimate line item**: “Check availability” for the selected productId.
3. **Standalone utility screen**: Enter `productId` and view availability.

### Screens to create/modify (proposed Moqui screen artifacts)
- **New screen**: `apps/pos/screen/inventory/Availability.xml` (name indicative; align to repo conventions)
  - Parameters: `productId` (required for query execution)
- **Optional embed/subscreen**: `.../inventory/AvailabilityPanel.xml` to include in product/quote screens.
- **Modify existing screens** (TBD by repo structure):
  - Product detail screen: add navigation to Availability screen with `productId` param.
  - Quote line item screen: add link/button passing `productId`.

### Navigation context
- Breadcrumb/back behavior returns user to the originating context (product/quote) preserving state.
- The screen supports direct navigation with `productId` in URL parameters.

### User workflows
**Happy path**
1. User opens availability view from a product context (or enters productId).
2. Frontend calls availability API.
3. UI renders list of locations with on-hand and ATP.

**Alternate paths**
- Valid product but no inventory anywhere → show empty-state with “No inventory records found for this product.”
- Invalid/missing productId → show validation error and do not call API.
- Product not found (404) → show “Product not found” message.
- Unauthorized (403) → show “You don’t have access to view availability” and provide guidance to contact admin.
- Network/server error → show retry affordance and keep last successful results (if any) clearly marked as stale.

---

## 6. Functional Behavior

### Triggers
- Screen loads with a non-empty `productId` parameter, OR user submits a “Lookup” form with `productId`.

### UI actions
- Action: `Lookup` (if productId not already provided)
- Action: `Refresh` (re-fetch availability for current productId)
- Action: `Change product` (clears current results and allows new productId)

### State changes (frontend UI state)
- `idle` → `loading` when request starts
- `loading` → `loaded` on 200 response (including empty locations)
- `loading` → `error` on 4xx/5xx or network error

### Service interactions (Moqui-side integration)
- Moqui screen transition invokes a Moqui service (or REST call via Moqui tools) to fetch availability:
  - Input: `productId`
  - Output: `AvailabilityView` object stored in screen context for rendering

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `productId` is required to execute lookup.
- `productId` is treated as an opaque string identifier; UI does not enforce formatting beyond non-empty (unless clarified).

### Enable/disable rules
- Disable `Refresh` while `loading`.
- Disable `Lookup` while `loading`.
- If no `productId`, disable `Refresh` and show prompt.

### Visibility rules
- Results table/list visible only in `loaded` state.
- Empty-state visible in `loaded` state when `locations.length == 0`.
- Error banner visible in `error` state.

### Error messaging expectations (mapped from backend)
- 400: “Product ID is required.” (or backend-provided message)
- 404: “Product not found.”
- 403: “Not authorized to view inventory availability.”
- 5xx/network: “Unable to load availability right now. Try again.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **No new persistent entities required** for v1 UI (read-only view).
- Screen context data structures:
  - `AvailabilityView`
  - `locations[]`

### Fields
**Input**
- `productId` (string, required, no default)

**Response**
- `productId` (string, required, read-only)
- `locations` (array, required; may be empty)
  - `locationId` (string, required, read-only)
  - `locationName` (string, required, read-only)
  - `onHandQuantity` (integer, required, read-only; expected non-negative)
  - `availableToPromiseQuantity` (integer, required, read-only; may be negative)

### Read-only vs editable
- All returned quantities and location details are read-only.

### Derived/calculated fields (UI-only)
- None required.
- Optional UI-only indicator: “ATP negative” if `availableToPromiseQuantity < 0` (presentation only; no policy implied).

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
- **HTTP**: `GET /api/v1/inventory/availability?productId={productId}`
- **Success (200)**: JSON matching backend story structure:
  - `{ productId: string, locations: LocationAvailability[] }`
- **Empty locations**: 200 with `locations: []`

### Create/update calls
- None.

### Submit/transition calls (Moqui)
- Transition: `lookupAvailability`
  - Validates `productId` present
  - Calls REST endpoint
  - Places result in context and renders the same screen with results

### Error handling expectations
- 400/404/403 handled with user-facing messages and no crash.
- 5xx/network shows retry; logs client-side error details without leaking sensitive data.

---

## 10. State Model & Transitions

### Allowed states (UI state machine)
- `idle`: no request yet
- `loading`: request in-flight
- `loaded`: last request succeeded (may be empty)
- `error`: last request failed

### Role-based transitions
- Viewing availability depends on backend authorization; UI does not hardcode roles.
- If backend returns 403, UI must not retry automatically; show access error.

### UI behavior per state
- `idle`: show productId entry or prompt; no results
- `loading`: show loading indicator; disable actions
- `loaded`: show results or empty-state
- `error`: show error banner + retry

---

## 11. Alternate / Error Flows

### Validation failures
- Missing `productId` on submit:
  - Do not call API
  - Show inline validation message

### Concurrency conflicts
- Not applicable (read-only).
- If multiple refreshes triggered quickly, latest response wins; earlier responses discarded (implementation detail; see Open Questions if repo has a standard pattern).

### Unauthorized access
- API returns 403:
  - Display not-authorized message
  - Do not display stale/previous product results if they belong to a different productId than current (prevent confusion)

### Empty states
- 200 with `locations: []`:
  - Show “No inventory records found for this product.”
  - Still show queried `productId` as context.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View availability for product with inventory in multiple locations
**Given** I am an authenticated Service Advisor  
**And** I navigate to the Availability view with `productId="SKU-123"`  
**When** the system loads availability successfully  
**Then** I see a list of locations for `SKU-123`  
**And** each location row shows `locationName`, `onHandQuantity`, and `availableToPromiseQuantity`  
**And** the values match the API response for each location.

### Scenario 2: Product has no inventory records anywhere
**Given** I am an authenticated Service Advisor  
**And** I lookup availability for `productId="SKU-456"`  
**When** the API responds `200` with an empty `locations` array  
**Then** I see an empty-state message indicating no inventory records were found  
**And** no location rows are displayed.

### Scenario 3: Missing productId is blocked client-side
**Given** I am on the Availability view without a `productId`  
**When** I attempt to run the lookup  
**Then** the UI shows a validation message that `productId` is required  
**And** no API request is sent.

### Scenario 4: Product not found
**Given** I am an authenticated Service Advisor  
**When** I lookup availability for `productId="SKU-999"`  
**And** the API responds with `404 Not Found`  
**Then** the UI shows a “Product not found” error message  
**And** no results are displayed.

### Scenario 5: Unauthorized user
**Given** I am authenticated but not authorized to view inventory availability  
**When** I lookup availability for a valid `productId`  
**And** the API responds with `403 Forbidden`  
**Then** the UI shows a not-authorized message  
**And** the UI does not display availability results.

### Scenario 6: Server/network failure with retry
**Given** I am on the Availability view with `productId="SKU-123"`  
**When** the API request fails due to a network error or `5xx` response  
**Then** the UI shows an error state with a Retry action  
**When** I click Retry and the API succeeds  
**Then** the UI displays the loaded availability results.

---

## 13. Audit & Observability

### User-visible audit data
- Not required (read-only query).

### Status history
- Not applicable.

### Traceability expectations
- Frontend should log (client-side) a structured event for:
  - availability lookup initiated (include productId)
  - lookup success (include latency, count of locations)
  - lookup failure (include HTTP status if present, latency)
- Correlation ID behavior depends on existing frontend conventions (see Open Questions).

---

## 14. Non-Functional UI Requirements

- **Performance/SLA:** The availability view should render results promptly after API response; no client-side heavy computation.
- **Accessibility:** All interactive controls keyboard accessible; loading/error states announced appropriately (ARIA where Quasar supports it).
- **Responsiveness:** Results must be usable on common POS tablet widths; allow horizontal scrolling or stacked layout as needed.
- **i18n/timezone/currency:** Not applicable (quantities are numeric counts). Use locale-safe number formatting if project standard supports it.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a standard empty-state message when `locations` is empty; safe because it does not change domain logic and is reversible. (Impacted: UX Summary, Alternate/ర్ Error Flows, Acceptance Criteria)
- SD-UX-LOADING-DISABLE: Disable lookup/refresh actions during in-flight request; safe UI ergonomics preventing duplicate calls. (Impacted: Functional Behavior, State Model)
- SD-ERR-MAP-HTTP: Map 400/403/404/5xx to standard user-facing banners; safe because it follows explicit backend status codes. (Impacted: Business Rules, Error Flows, Acceptance Criteria)

---

## 16. Open Questions
1. **Entry point selection (blocking):** Where should this Availability UI live in the POS?
   - Product detail screen, quote/estimate line item, standalone utility screen, or multiple?
2. **Moqui integration pattern (blocking):** Should the frontend call the backend Inventory API directly from the Vue client, or via Moqui screen transitions/services acting as a proxy (preferred in many Moqui setups)?
3. **Auth/correlation (blocking):** Is there an existing convention in this repo for propagating correlation IDs (e.g., `X-Correlation-Id`) and for handling 401 vs 403 in the UI?
4. **ProductId acquisition (blocking):** Do we have an existing product search/select component, or is the expected input strictly an existing `productId` passed from upstream screens?
5. **SLA requirement detail (blocking):** The original story mentions “SLA for estimate UI” but does not specify a target (e.g., p95 latency). What target should frontend enforce/monitor (if any)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/112


====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/112
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want on-hand/ATP by location so that I can quote with realistic fulfillment expectations.

## Details
- Query inventory for on-hand and ATP.
- Optional reservations and expected replenishment.

## Acceptance Criteria
- Availability API returns quantities by location.
- Consistent productId mapping.
- SLA for estimate UI.

## Integrations
- Product → Inventory availability query; Inventory → Product responses/events.

## Data / Entities
- AvailabilityView, LocationQty, ReservationSummary

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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