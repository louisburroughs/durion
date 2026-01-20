## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** pricing-strict

STOP: Clarification required before finalization

---

# 1. Story Header

### Title
[FRONTEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)

### Primary Persona
Service Advisor

### Business Value
Ensure estimate line pricing shown in the POS/Workexec estimate builder is accurate, deterministic, and transparent (breakdown + warnings), so customers receive reliable quotes and advisors can proceed without manual recalculation.

---

# 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** the UI to retrieve and display an accurate price quote for a product line using location + customer tier context  
- **So that** estimate line pricing reflects applicable rules (base MSRP → location override → customer tier → rounding) with a clear breakdown and any warnings.

### In-scope
- Frontend (Moqui screens) support to request a **Price Quote** for an estimate line item using:
  - `productId`, `quantity`, `locationId`, `customerTierId`, optional `effectiveTimestamp`
- Display returned:
  - MSRP, unit price, extended price
  - breakdown trace (ordered)
  - warnings
  - price source indicator (e.g., MSRP fallback)
- Error-handling UI for 400/404/500 from pricing quote.
- Performance-friendly UX patterns (debounce/retry messaging) without changing domain logic.

### Out-of-scope
- Creating/editing pricing rules, overrides, promotions, rounding policy.
- Inventory availability checks in the same call (explicitly discouraged in backend reference).
- Persisting pricing snapshots to an estimate/work order (that is a Workexec lifecycle concern; this story is quote retrieval/display only unless clarified).
- Customer tier management.

---

# 3. Actors & Stakeholders

- **Primary actor:** Service Advisor (POS user building an estimate)
- **System actors:**
  - Moqui frontend (this repo) as the UI layer
  - Pricing service/API (authoritative pricing calculation; system of record)
  - Workexec context provider (estimate builder flow provides estimateId, customer/location context) — exact SoR unclear in frontend story input
- **Stakeholders:** Customer, dealership management, finance/audit teams (need traceable pricing)

---

# 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the POS UI.
- The estimate builder context provides (or can derive) the following before quoting:
  - `productId` selected for the line
  - `quantity` entered (>0)
  - `locationId`
  - `customerTierId`
  - optional `effectiveTimestamp` (if supported by UI flow)

### Dependencies
- **Pricing API** endpoint available: `POST /v1/price-quotes` (from backend story reference #51)
- Known error semantics:
  - `404 Not Found` for invalid product or no base price
  - `400 Bad Request` for invalid context/payload
  - `500` for server error
- Moqui frontend must have an HTTP client mechanism consistent with repo conventions (not provided in inputs).
- Workexec estimate UI location/route where line items are added/edited (not provided in inputs).

---

# 5. UX Summary (Moqui-Oriented)

### Entry points
- From Estimate Builder / Estimate Detail UI when:
  - adding a new estimate line item
  - editing an existing estimate line item’s product and/or quantity

### Screens to create/modify (Moqui)
- **Modify** the Estimate line edit/add screen(s) to:
  - Trigger a price quote fetch when product/qty/context changes
  - Render quote results, breakdown, and warnings
- **Potential new sub-screen/widget** (recommended for reuse):
  - `component://.../screen/Quote/PriceQuotePanel.xml` (name TBD by repo conventions)
  - Used inside line add/edit forms to show pricing results and diagnostics

> Open question: actual screen names/locations and component paths depend on existing repo structure and routing conventions (not included in provided inputs).

### Navigation context
- User remains on estimate line editor; quoting should not navigate away.
- Quote panel updates in-place.

### User workflows
**Happy path**
1. Service Advisor selects a product and enters quantity.
2. UI calls pricing quote API with productId/qty/locationId/customerTierId/effectiveTimestamp.
3. UI displays:
   - MSRP, unit price, extended price
   - priceSource (if present)
   - ordered breakdown entries
   - warnings (non-blocking)
4. Advisor saves/continues estimate editing (save is outside this story unless clarified).

**Alternate paths**
- Change quantity after quote → UI refreshes quote.
- Missing/invalid context (e.g., no customer tier selected) → UI blocks quote call and prompts user to fix input (exact behavior depends on whether customer tier is always available).
- API returns MSRP fallback indicator → UI shows non-blocking notice.

---

# 6. Functional Behavior

### Triggers
- On line item form input changes:
  - `productId` change
  - `quantity` change
  - `locationId` change (if selectable in UI)
  - `customerTierId` change (if selectable/derived)
  - `effectiveTimestamp` change (if exposed)

### UI actions
- Validate required inputs locally before calling API:
  - productId present
  - quantity integer > 0
  - locationId present
  - customerTierId present
- Call price quote endpoint.
- Show loading state while awaiting response.
- On success:
  - Update displayed unit/ext price fields (read-only display)
  - Display breakdown list in returned order
  - Display warnings list (if any)
- On failure:
  - Show inline error banner with actionable message
  - Keep previous quote visible but marked “stale” OR clear it (must be clarified; see Open Questions)

### State changes (frontend)
- `quoteStatus`: idle | loading | success | error
- `quoteRequestHash`: derived from request fields to avoid racing responses (recommended)
- `quoteData`: last successful response
- `quoteError`: last error payload

### Service interactions
- Synchronous HTTP POST to pricing service:
  - `POST /v1/price-quotes`
- No other domain calls in this story.

---

# 7. Business Rules (Translated to UI Behavior)

> Pricing logic is authoritative in Pricing domain; UI must reflect it and not re-implement formulas.

### Validation
- Quantity must be an integer > 0:
  - Disable “Get price” (if a manual button exists) and/or skip auto-quote until valid.
- If required context missing (locationId/customerTierId):
  - UI must prevent calling API and display a clear prompt indicating what is missing.

### Enable/disable rules
- Quote fetch is enabled only when all required request fields are present and valid.
- While `loading`, disable repeated triggers (debounce) to avoid flooding API.

### Visibility rules
- Breakdown panel visible only after at least one successful quote OR when in error state with an explanatory message.
- Warnings visible only when provided and non-empty.

### Error messaging expectations
Map backend response classes to user-visible text:
- **400**: “Cannot price this line: <validation message>” (display server-provided message if safe)
- **404**: “No price found for this product in the current context.” Distinguish:
  - invalid product vs no base price if backend message indicates (do not guess otherwise)
- **500/Network**: “Pricing service is currently unavailable. Try again.”

---

# 8. Data Requirements

### Entities involved (frontend view models)
- `PriceQuoteRequest` (request DTO)
- `PriceQuoteResponse` (response DTO)
- Optional: `PricingRuleTrace` (if represented as breakdown items)

### Fields (type, required, defaults)

**PriceQuoteRequest (UI → API)**
- `productId: UUID` (required)
- `quantity: integer` (required, >0)
- `locationId: UUID` (required)
- `customerTierId: UUID` (required)
- `effectiveTimestamp: ISO-8601 datetime UTC` (optional; default omitted to let backend use now)

**PriceQuoteResponse (API → UI)**
- `productId: UUID` (required)
- `quantity: integer` (required)
- `msrp.amount: decimal` (required)
- `msrp.currency: string (ISO 4217)` (required)
- `unitPrice.amount: decimal` (required)
- `unitPrice.currency: string` (required)
- `extendedPrice.amount: decimal` (required)
- `extendedPrice.currency: string` (required)
- `pricingBreakdown: array` (required, may be empty but backend implies at least BASE_PRICE)
  - `ruleName: string`
  - `ruleType: string` (e.g., BASE_PRICE, LOCATION_OVERRIDE)
  - `adjustment.amount: decimal`
  - `adjustment.currency: string`
  - `resultingValue.amount: decimal`
  - `resultingValue.currency: string`
- `warnings: string[]` (optional/empty)
- `priceSource: string` (required if backend includes per RQ4; UI must render if present)

### Read-only vs editable
- Request fields come from editable form inputs (productId, quantity) and context selectors (location/customer).
- Response fields are **read-only display**.

### Derived/calculated fields (frontend)
- `extendedPriceDisplay` computed from response (do not recompute for correctness; display response)
- `isStale` derived if current form inputs do not match last response request hash

---

# 9. Service Contracts (Frontend Perspective)

> Contract is based on backend story reference #51; frontend must align with actual implementation once available.

### Load/view calls
- None required beyond pricing quote call (estimate/product search is out-of-scope here).

### Create/update calls
- None (quote retrieval only).

### Submit/transition calls
- **POST** `/v1/price-quotes`
  - Request JSON matches `PriceQuoteRequest`
  - Success: `200 OK` with `PriceQuoteResponse`

### Error handling expectations
- **400**: parse JSON error response; display field-level error if structure supports it, else show banner
- **404**: show “not found/no price” message; do not crash line editor
- **500**: show retry message
- Timeouts: treat as unavailable; allow user retry
- Correlation ID: if response headers include it, log it to console/logger for support (do not display unless instructed)

---

# 10. State Model & Transitions

### Allowed states (frontend quote state)
- `IDLE`: no quote attempted yet
- `LOADING`: quote request in-flight
- `SUCCESS`: quote displayed, consistent with current inputs
- `STALE`: quote displayed but inputs changed since last quote (optional state; see Open Questions)
- `ERROR`: last attempt failed

### Role-based transitions
- No special role logic specified for UI quoting. (Authorization happens server-side.)

### UI behavior per state
- IDLE: show “Enter product and quantity to see price”
- LOADING: show spinner and disable repeated calls
- SUCCESS: show price, breakdown, warnings
- STALE (if implemented): show prior price dimmed with “Recalculate pricing” prompt
- ERROR: show error banner + retry action

---

# 11. Alternate / Error Flows

### Validation failures (client-side)
- Quantity missing/0/negative/non-integer:
  - No API call; show inline validation on quantity field
- Missing locationId/customerTierId:
  - No API call; show inline message indicating missing context

### Server-side validation failures (400)
- Display server message; if server returns field errors, map to the corresponding inputs.

### Not found (404)
- If invalid productId: prompt user to reselect product
- If no price found: show message and prevent saving priced totals (save rules out-of-scope; at minimum do not populate price)

### Concurrency/race conditions
- If user changes quantity quickly:
  - Debounce requests and ignore out-of-order responses using request hash or incrementing requestId.

### Unauthorized access (401/403)
- 401: redirect to login (standard app behavior)
- 403: show “You don’t have permission to view pricing.” and block quote panel

### Empty states
- Breakdown empty or missing: show “No breakdown available” (should not happen per backend rule BR2; treat as warning and log)

---

# 12. Acceptance Criteria

### Scenario 1: Quote pricing for valid line (happy path)
**Given** I am a Service Advisor editing an estimate line  
**And** I have selected a productId  
**And** I have entered quantity `2`  
**And** the estimate context includes locationId and customerTierId  
**When** the UI requests a price quote  
**Then** the UI sends `POST /v1/price-quotes` with productId, quantity, locationId, customerTierId  
**And** the UI displays msrp, unitPrice, and extendedPrice from the response  
**And** the UI displays pricingBreakdown entries in the order returned by the API  
**And** the UI displays any warnings returned.

### Scenario 2: Do not call API when quantity is invalid
**Given** I am editing an estimate line  
**And** I have selected a productId  
**When** I enter quantity `0` (or blank)  
**Then** the UI shows a validation error “Quantity must be greater than 0”  
**And** the UI does not call `POST /v1/price-quotes`.

### Scenario 3: Handle 404 (product not found / no price)
**Given** I am editing an estimate line with a productId that pricing cannot price  
**When** the UI requests a price quote  
**And** the API returns `404 Not Found`  
**Then** the UI shows an inline error indicating no price was found for the product/context  
**And** the UI does not display a misleading price.

### Scenario 4: Handle 400 (invalid context)
**Given** I am editing an estimate line  
**When** the UI requests a price quote with an invalid customerTierId  
**And** the API returns `400 Bad Request` with a descriptive message  
**Then** the UI displays the message to the user  
**And** allows retry after the context is corrected.

### Scenario 5: Race condition protection on rapid quantity changes
**Given** I rapidly change quantity from 1 to 2 to 3  
**When** multiple quote requests are triggered  
**Then** only the latest response is shown in the UI  
**And** earlier responses are ignored if they return after the latest request.

### Scenario 6: Render MSRP fallback indicator (if provided)
**Given** the API response includes `priceSource = "MSRP_FALLBACK"`  
**When** the UI displays the quote  
**Then** the UI shows a non-blocking notice that MSRP fallback pricing is being used.

---

# 13. Audit & Observability

### User-visible audit data
- Show “Pricing as of <effectiveTimestamp or now>” if effective timestamp is provided/known.
- Show breakdown entries (ruleName/type, adjustment, resulting value) to support explainability.

### Status history
- Not required to persist history, but UI should keep last successful quote in-memory until line editor is closed.

### Traceability expectations
- Frontend should log (app logger) quote request metadata:
  - productId, quantity, locationId, customerTierId
  - requestId/hash
  - response status code
  - correlationId header if present
- Do not log customer PII.

---

# 14. Non-Functional UI Requirements

- **Performance:** UI should feel responsive; debounce quote calls (e.g., 250–400ms) on quantity typing to avoid excessive API traffic.
- **Accessibility:** All error messages must be screen-reader accessible; use ARIA-live region for async error banners.
- **Responsiveness:** Quote panel must render appropriately on tablet-sized screens used at POS counters.
- **i18n/timezone/currency:**
  - Currency: display using ISO currency from response; do not assume USD.
  - Timezone: effectiveTimestamp uses UTC; if displayed, convert to user locale with clear timezone indicator (needs clarification on desired display).

---

# 15. Applied Safe Defaults

- SD-UX-01 (Debounced input-triggered calls): Assumed debounce on quantity/product changes to reduce API load and improve UX; qualifies as UI ergonomics and does not change business logic; impacts UX Summary, Functional Behavior, Acceptance Criteria, Error Flows.
- SD-ERR-01 (Standard HTTP error mapping): Assumed conventional mapping of 400/404/500 to inline banner + field validation where possible; qualifies as standard error-handling mapping; impacts Business Rules, Service Contracts, Alternate/Error Flows.
- SD-OBS-01 (Client-side requestId/hash for race protection): Assumed request sequencing to avoid stale updates; qualifies as frontend reliability/ergonomics; impacts Functional Behavior, Alternate/Error Flows, Acceptance Criteria.

---

# 16. Open Questions

1. **Where in the Moqui screen hierarchy does estimate line add/edit live (screen path, parameters, routing)?** Provide the target screen(s) to modify and the route params available (estimateId, locationId, customerId, etc.).  
2. **Source of `customerTierId`:** Is customer tier always present on the estimate/customer context, or must the Service Advisor select it? If selectable, where is it stored and how is it validated?  
3. **What should the UI do with quote results—display-only or also populate editable line fields that will be saved?** If it populates fields, which fields on the estimate line entity are authoritative (unitPrice, extendedPrice, msrp, priceSource, snapshotId, etc.)?  
4. **Stale quote behavior:** When inputs change after a successful quote, should the UI (a) clear the quote, (b) keep it but mark “stale”, or (c) automatically re-quote on every change? (Auto re-quote is preferred but confirm.)  
5. **Exact API base URL / auth mechanism in Moqui frontend:** Is `/v1/price-quotes` proxied through Moqui, or called directly to the pricing service? Provide the expected Moqui service name or HTTP client configuration pattern used in this repo.  
6. **Error response schema:** For 400/404/500, what JSON structure is returned (message field name, fieldErrors array, error codes)? Needed to implement consistent UI parsing.  
7. **Should `effectiveTimestamp` be passed from the UI?** If yes, what value should it use (estimate created time, quote time, scheduled service time), and should the user be able to change it?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/115

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/115
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want correct pricing on estimate lines so that customers receive accurate quotes.

## Details
- Request: productId, qty, locationId, customer tier, effective time.
- Response: unit/ext price, MSRP, breakdown, policy flags.

## Acceptance Criteria
- Deterministic evaluation order (base→store override→rounding).
- Returns breakdown + warnings.
- SLA suitable for UI.

## Integrations
- Workexec → Product PriceQuote API; optional availability in same response.

## Data / Entities
- PriceQuoteRequest, PriceQuoteResponse, PricingRuleTrace

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