## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:positivity
- status:draft

### Recommended
- agent:positivity-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** integration-conservative

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Catalog: View Product Details with Price and Availability Signals

### Primary Persona
Service Advisor

### Business Value
Enable a Service Advisor to quickly explain product options, price, and availability signals to customers for a selected location, while safely handling partial backend outages via explicit “status” indicators.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** a product detail view that shows master details plus location-scoped pricing and availability signals (with explicit component status)  
- **So that** I can confidently explain options and availability without stitching multiple calls or misrepresenting unknown data.

### In-scope
- Product detail screen that:
  - Loads `ProductDetailView` from a single backend endpoint.
  - Displays product master data, pricing (MSRP/store price/currency), availability (on-hand/ATP), lead time hints, and substitutions.
  - Clearly indicates per-component status: `pricing.status` and `availability.status` (`OK | UNAVAILABLE | STALE` per backend story).
  - Reacts to **selected location** (query param `location_id`) and reloads when location changes.
  - Handles graceful degradation (pricing unavailable and/or inventory unavailable) without implying incorrect stock/price.
- Moqui screen routing, forms, and transitions needed to render and refresh the view.

### Out-of-scope
- Checkout/cart/quote commit flows that require “price finalization”.
- Any edits to product data, pricing, or inventory (read-only UI).
- Defining downstream service contracts beyond the aggregated POS endpoint.
- Authorization model design (permissions/roles naming) beyond wiring to existing auth.

---

## 3. Actors & Stakeholders
- **Service Advisor (Primary user):** consumes product details during customer interaction.
- **Store Manager (Stakeholder):** expects correct location-scoped data, may review degraded-state behavior.
- **POS Frontend (Moqui + Vue/Quasar):** renders read-only detail view.
- **POS Backend (domain:positivity):** provides aggregated endpoint `GET /api/v1/products/{productId}?location_id={locationId}` (authoritative for aggregation only).

---

## 4. Preconditions & Dependencies
- User is authenticated in the POS frontend.
- User has access to at least one location context (selected location must be known to the UI).
- Backend endpoint is available:
  - `GET /api/v1/products/{productId}?location_id={locationId}`
- Routing provides or can derive:
  - `productId` (UUID) from URL path.
  - `locationId` (UUID) from selected location state or query param.
- Dependency: an existing “location selector” mechanism OR a new minimal selector must be defined (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From catalog search/browse results: “View details” action navigates to product detail.
- From substitution list items: clicking a substitute product navigates to that product’s detail view (same screen, new `productId`).

### Screens to create/modify
- **Create** Moqui screen: `Catalog/ProductDetail`
  - Route includes `productId` as path parameter.
  - Uses a screen transition to load `ProductDetailView` from backend (service call).
- **Modify** existing catalog browse screen(s) to include navigation link to `Catalog/ProductDetail` (if browse screen exists; otherwise out-of-scope and tracked separately).

### Navigation context
- Breadcrumb or back navigation to prior catalog list (implementation-specific; behavior must not lose current `locationId` context).
- Location context displayed or at least reflected (e.g., “Pricing/Availability for: <Location Name>”).

### User workflows
**Happy path**
1. Service Advisor opens product detail from catalog list.
2. UI loads product data for selected location.
3. UI shows:
   - description + specs
   - pricing with status OK
   - availability with status OK
   - substitutions (if any)
   - lead time hints (if present)

**Alternate paths**
- User changes location selector → UI reloads pricing/availability for new location (same `productId`).
- Pricing unavailable → UI shows pricing as unavailable with safe “no price shown” behavior.
- Inventory unavailable → UI shows availability as UNKNOWN/unavailable (not “0”).
- Product not found (404) → UI shows “Product not found” and offers return to catalog list.

---

## 6. Functional Behavior

### Triggers
- Screen load for `Catalog/ProductDetail` with `productId` and `locationId`.
- Location change while on the product detail screen.
- User navigates to a substitution product from the substitutions list.

### UI actions
- On load and on location change:
  - Call backend endpoint and bind response to view state.
- Provide “Retry” action when degraded due to availability/pricing unavailable or transient errors.
- Provide an explicit visual indicator for:
  - `pricing.status`
  - `availability.status`
  - `generatedAt` and component `asOf` timestamps (at least available in an “Info” section or tooltip; exact placement up to implementation).

### State changes (frontend)
- Local view state transitions:
  - `idle` → `loading` → `loaded`
  - `loading` → `error` (hard error like 404, 400, 401/403, network failure)
  - `loaded` may include `degraded` flags based on component statuses

### Service interactions
- Single read interaction:
  - `GET /api/v1/products/{productId}?location_id={locationId}`
- No write operations.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- `productId` must be present and UUID-shaped; if not, block call and show “Invalid product id”.
- `locationId` must be present and UUID-shaped; if not, block call and show “Select a location to view pricing and availability” (wording may vary) and/or treat as error state.

### Enable/disable rules
- Disable actions that imply “price is known” when `pricing.status != OK` (even if MSRP/storePrice fields are null).  
  - In this story, actions are read-only, so minimum requirement: do not display computed totals or “price-confirmed” badges.
- Disable “in stock”/“available” claims when `availability.status != OK`. Must show UNKNOWN/unavailable state.

### Visibility rules
- Pricing display rules:
  - If `pricing.status = OK`: display `msrp`, `storePrice`, `currency`.
  - If `pricing.status != OK`: show “Pricing unavailable” and do not show numeric price values (since backend mandates nulls, UI should not format null as 0).
- Availability display rules:
  - If `availability.status = OK`: show `onHandQuantity`, `availableToPromiseQuantity`, and lead time if present.
  - If `availability.status != OK`: show “Availability unknown” (or similar) and do not show numeric quantities (null/omitted should not render as 0).
- Substitutions:
  - If `substitutions` empty: show empty state “No substitutions available”.
  - If non-empty: list substitution items with navigation to each substitute’s detail.

### Error messaging expectations
- 404: “Product not found” (no partial rendering).
- 400: “Invalid location selected” (and prompt to select a valid location).
- 401/403: “You don’t have access” (and do not render sensitive info).
- Network/5xx: “Unable to load product details right now” with Retry.

---

## 8. Data Requirements

### Entities involved (frontend view models)
- `ProductDetailView` (response DTO from backend)
  - Contains: `productId`, `description`, `specifications[]`, `generatedAt`, `pricing{...}`, `availability{...}`, `substitutions[]`
- `AvailabilityView` (nested)
- `SubstituteHint` (nested)

### Fields (type, required, defaults)
**Route/Input**
- `productId`: string (UUID), **required**
- `locationId`: string (UUID), **required** (sourced from app state or query param)

**Response: ProductDetailView**
- `productId`: string, required
- `description`: string, required (may be empty string; treat as present)
- `specifications`: array of `{ name: string, value: string }`, required (may be empty)
- `generatedAt`: ISO datetime string, required

**pricing**
- `pricing.status`: enum string, required (`OK|UNAVAILABLE|STALE` per backend)
- `pricing.asOf`: ISO datetime string, optional when status != OK (backend says “when applicable”)
- `pricing.msrp`: decimal number or null
- `pricing.storePrice`: decimal number or null
- `pricing.currency`: string (ISO 4217), nullable/optional if pricing unavailable (needs confirmation)

**availability**
- `availability.status`: enum string, required (`OK|UNAVAILABLE|STALE`)
- `availability.asOf`: ISO datetime string, optional when status != OK
- `availability.onHandQuantity`: integer or null/omitted
- `availability.availableToPromiseQuantity`: integer or null/omitted
- `availability.leadTime`: optional object:
  - `source`: string (e.g., `CATALOG`), optional
  - `minDays`, `maxDays`: integer, optional
  - `asOf`: ISO datetime string, optional
  - `confidence`: string, optional

**substitutions**
- array of `{ productId: string, reason: string }`, required (may be empty)

### Read-only vs editable by state/role
- All fields are read-only in UI for this story.

### Derived/calculated fields
- `isPricingDegraded = pricing.status != 'OK'`
- `isAvailabilityDegraded = availability.status != 'OK'`
- Display lead time “effective” label based on presence of `availability.leadTime` (dynamic overrides static per backend; UI just renders the returned object, does not compute precedence).

---

## 9. Service Contracts (Frontend Perspective)

### Load/view calls
- **HTTP**: `GET /api/v1/products/{productId}?location_id={locationId}`
- **Request headers**:
  - Auth headers per existing frontend convention (TBD by project)
  - Correlation/trace header propagation if frontend standard exists (TBD)

### Create/update calls
- None

### Submit/transition calls
- None

### Error handling expectations
- Map HTTP codes to UI states:
  - `200`: render view; handle degraded components by status fields.
  - `404`: terminal “not found” empty/error state (no retry unless user changes product).
  - `400`: “invalid location” error state; allow user to change location then retry.
  - `401/403`: unauthorized/forbidden state; do not show partial data.
  - `5xx` / timeout / network: transient error; show retry.

---

## 10. State Model & Transitions

### Allowed states (frontend screen state)
- `loading`
- `loaded_ok` (pricing.status=OK and availability.status=OK)
- `loaded_degraded_pricing` (pricing.status!=OK, availability.status=OK)
- `loaded_degraded_availability` (availability.status!=OK, pricing.status=OK)
- `loaded_degraded_both` (both != OK)
- `error_not_found` (404)
- `error_invalid_location` (400)
- `error_unauthorized` (401/403)
- `error_transient` (network/5xx)

### Role-based transitions
- Any authenticated user with product/location read permission can access (exact permission/role names TBD).

### UI behavior per state
- `loading`: show skeleton/loading indicator; disable location change only if it would cause race conditions (implementation choice).
- `loaded_*`: render all available sections; render status badges/messages for degraded parts.
- `error_*`: show error message + relevant next action (retry, change location, back to list).

---

## 11. Alternate / Error Flows

### Validation failures
- Missing/invalid `productId`:
  - Do not call backend.
  - Show invalid parameter error with navigation back.
- Missing/invalid `locationId`:
  - Do not call backend.
  - Prompt user to select location; once selected, call backend.

### Concurrency conflicts
- If user changes location quickly:
  - Ensure latest request wins (cancel/ignore older responses) to avoid showing mismatched location data.

### Unauthorized access
- If backend returns 401/403:
  - Redirect to login or show unauthorized screen according to app conventions (TBD).
  - Do not display cached/previous product sensitive data for a different user.

### Empty states
- No specifications: show “No specifications provided”.
- No substitutions: show “No substitutions available”.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Successful retrieval renders pricing and availability for selected location
**Given** I am an authenticated Service Advisor  
**And** I have selected a valid location with id `<locationId>`  
**And** I navigate to the product detail screen for product `<productId>`  
**When** the screen loads  
**Then** the UI requests `GET /api/v1/products/<productId>?location_id=<locationId>`  
**And** the UI displays product description and specifications  
**And** the UI displays `pricing.msrp`, `pricing.storePrice`, and `pricing.currency` when `pricing.status=OK`  
**And** the UI displays `availability.onHandQuantity` and `availability.availableToPromiseQuantity` when `availability.status=OK`.

### Scenario 2: Location change reloads price and availability
**Given** I am viewing product `<productId>` at location `<locationA>`  
**When** I change the selected location to `<locationB>`  
**Then** the UI requests `GET /api/v1/products/<productId>?location_id=<locationB>`  
**And** the UI updates pricing/availability to reflect `<locationB>` (not `<locationA>`).

### Scenario 3: Pricing unavailable shows explicit unavailable state and no numeric prices
**Given** the backend returns `200` for product `<productId>` and location `<locationId>`  
**And** the response contains `pricing.status=UNAVAILABLE`  
**When** the UI renders the product detail  
**Then** the UI displays a “Pricing unavailable” indicator  
**And** the UI does not display numeric MSRP/store price values (does not render null as 0)  
**And** availability (if `availability.status=OK`) is still displayed.

### Scenario 4: Inventory unavailable shows availability UNKNOWN (not in stock or out of stock)
**Given** the backend returns `200`  
**And** the response contains `availability.status=UNAVAILABLE`  
**When** the UI renders the product detail  
**Then** the UI displays an “Availability unknown/unavailable” indicator  
**And** the UI does not display on-hand or ATP numeric quantities  
**And** the UI does not label the product as “in stock” or “out of stock”.

### Scenario 5: Product not found
**Given** I navigate to product `<productId>` that does not exist  
**When** the backend responds `404 Not Found`  
**Then** the UI displays “Product not found”  
**And** the UI provides a way to return to the catalog list/search.

### Scenario 6: No substitutions configured
**Given** the backend returns `200`  
**And** the response contains `substitutions=[]`  
**When** the UI renders the substitutions section  
**Then** the UI shows an empty state indicating no substitutions are available.

### Scenario 7: Invalid location id
**Given** I navigate to product `<productId>` with an invalid `<locationId>`  
**When** the backend responds `400 Bad Request`  
**Then** the UI displays an “Invalid location” error  
**And** the UI allows me to select a different location and retry.

---

## 13. Audit & Observability

### User-visible audit data
- Display “Last updated/generated” timestamp using `generatedAt`.
- Optionally show component `asOf` timestamps for pricing/availability (can be in an info panel).

### Status history
- Not required (single read view).

### Traceability expectations
- Frontend should include correlation/trace id header if the project has a standard; otherwise defer (Open Question).
- Frontend logs (console/app logger) should include `productId` and `locationId` on load failures (no sensitive data).

---

## 14. Non-Functional UI Requirements
- **Performance:** initial load should render skeleton within 200ms; network response renders immediately when available (no artificial delays).
- **Accessibility:** status indicators must be conveyed via text (not color only); all interactive elements keyboard-accessible.
- **Responsiveness:** usable on tablet and desktop POS layouts.
- **i18n/timezone/currency:**
  - Currency formatting must respect `pricing.currency` (do not assume USD in UI).
  - Timestamps displayed in user’s locale/timezone per app convention (TBD).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATES: Provide standard empty states for specs/substitutions; qualifies as safe UI ergonomics; impacts UX Summary, Alternate/Empty states.
- SD-UX-RETRY: Provide a Retry button for transient/network errors; qualifies as safe error-handling ergonomics; impacts Alternate/Error flows, UX Summary.
- SD-UX-LATEST-WINS: On rapid location changes, latest request wins (ignore stale responses); qualifies as safe UI ergonomics preventing mismatched data; impacts Alternate/Concurrency.

---

## 16. Open Questions
1. **Location source of truth (blocking):** How does the frontend obtain the selected `locationId` (global app state, route query, user profile default, or a screen-level selector)? What is the existing convention in this repo?
2. **Moqui ↔ Vue integration pattern (blocking):** Should this be implemented as a Moqui screen that hosts a Vue route/component, or as a Vue SPA route calling backend directly (with Moqui providing shell/auth)? Repo convention needed.
3. **Auth & permission enforcement (blocking):** What permission/role gate should the screen enforce for product/location reads (and what is the existing permission naming pattern)?
4. **Status enum exact values:** Backend suggests `OK|UNAVAILABLE|STALE` but says exact enum TBD—should frontend treat status as opaque string and only special-case `OK` vs “not OK”?
5. **Currency formatting rules:** Are there existing utilities/components for money formatting and null-safe display in this project?
6. **Timestamp display:** Should the UI display `generatedAt`/`asOf` visibly, or only for debug/details? If visible, where is the standard placement?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Catalog: View Product Details with Price and Availability Signals  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/80  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Catalog: View Product Details with Price and Availability Signals

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want product detail views so that I can explain options and availability to customers.

## Details
- Show description, specs, MSRP, store price, substitutions hints.
- Show on-hand/ATP by location and external lead-time hints (optional).

## Acceptance Criteria
- Detail view loads reliably.
- Price and availability reflect selected location.
- Substitution suggestions available when configured.

## Integrations
- Product/pricing + inventory availability endpoints.

## Data / Entities
- ProductDetailView, AvailabilityView, SubstituteHint

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