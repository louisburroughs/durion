## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** pricing-strict

---

1. Story Header

- Title: [FRONTEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)
- Primary Persona: Service Advisor (POS user)
- Business Value: Provide accurate, auditable, and context-sensitive product pricing to Service Advisors when building estimates so customers receive reliable quotes and dealership profitability is preserved.

2. Story Intent

As a Service Advisor using the POS/Estimate Builder UI, I want the UI to request and display the authoritative, context-aware price for a selected product (considering location and customer tier) so that I can present an accurate quote to the customer and continue estimate workflows without ambiguity.

In-scope:
- UI flows to request price for a selected product and quantity within the Estimate Builder or POS line-add workflow.
- Display of `unitPrice`, `extendedPrice`, `msrp`, `pricingBreakdown`, and any `warnings` returned by the Pricing Service.
- Client-side validation of request inputs (product selection, quantity > 0, location context present, customer tier present when required).

Out-of-scope:
- Implementing backend pricing rules or persistence (handled by Pricing Service).
- Synchronous inventory availability checks (client may call Inventory in parallel per backend guidance).

3. Actors & Stakeholders

- Primary Actor: Service Advisor (user interacting with POS/Estimate Builder)
- System Actor: POS frontend (Moqui screens + client services)
- External System: Pricing Service (authoritative price calculation)
- Stakeholders: Dealership Management, Finance, Customers, QA

4. Preconditions & Dependencies

- User is authenticated and has permission to create/edit estimates and view prices.
- The POS context includes an active `locationId` (store/branch) and an optionally selected `customerTier` for the current customer.
- Product catalog entry exists and `productId` is available from product selection components.
- Pricing Service endpoint `/v1/price-quotes` (backend contract) is available and reachable.
- Currency/rounding policy is provided by backend (Banker's rounding) per BR3.

5. UX Summary (Moqui-Oriented)

- Entry points:
  - Estimate Builder -> Add Line -> Product search/selector
  - POS quick-add product flow

- Screens / Components to create/modify:
  - `EstimateLineAdd` screen: product selector, quantity input, location context indicator, customer tier selector (if not global), `Get Price` action.
  - `PricePreview` component: shows `msrp`, `unitPrice`, `extendedPrice`, `pricingBreakdown` (collapsible), and `warnings` area.
  - `EstimateLine` inline editor: updates when price is fetched or edited by override flow.

- Navigation context:
  - From product selection, `Get Price` triggers an inline modal or side panel (`PricePreview`) without leaving Estimate Builder.

- User workflows:
  - Happy path: Select product → enter qty → `Get Price` → display PricePreview → `Add to Estimate` uses returned `unitPrice`.
  - Alternate: If `pricingBreakdown` contains warnings, show a caution panel and allow Service Advisor to proceed or request manager approval (manager approval flow out-of-scope; surface as Open Question if needed).

6. Functional Behavior

- Triggers:
  - `Get Price` explicit button or auto-fetch on product+quantity change (configurable UI setting; default: auto-fetch after 500ms debounce).

- UI actions & sequence:
  1. Validate `productId` and `quantity` on client (quantity > 0).
  2. Build `PriceQuoteRequest` payload:
     - `productId` (UUID)
     - `quantity` (Integer)
     - `locationId` (UUID) — from current POS context
     - `customerTierId` (UUID) — from current customer profile or null if not provided (backend requires non-null; frontend must ensure tier present or prompt to select)
     - `effectiveTimestamp` (ISO 8601 UTC) — optional, default now()
  3. Call Pricing Service via backend bridge: POST `/v1/price-quotes` with JSON body.
  4. Show loading state on `PricePreview` component with spinner and correlated audit/tracing id.
  5. On HTTP 200: parse and render `unitPrice`, `extendedPrice`, `msrp`, `pricingBreakdown`, and `warnings`.
  6. On HTTP 4xx/5xx: show friendly error message; map to standard UI patterns (toast + inline error area). For validation errors (400) show field-level messages.

- Service interactions:
  - POST `/v1/price-quotes` — see backend contract for request/response shape (PriceQuoteRequest / PriceQuoteResponse).
  - Observability: include `X-Correlation-Id` and propagate W3C trace headers.

7. Business Rules (Translated to UI Behavior)

- BR1 (Deterministic Evaluation): UI must present `pricingBreakdown` in the exact order returned (Base → Location Override → Customer Tier → Rounding) and not reorder it.
- BR2 (Auditability): Provide a collapsed `pricingBreakdown` exposing each rule name, type, adjustment amount, and resulting value; allow QA to expand for traceability.
- BR3 (Monetary Precision): Display `unitPrice` and `extendedPrice` rounded to 2 decimal places; where intermediate values are shown (breakdown), show at least 4 decimal places in developer/debug mode.
- BR4 (Time-Sensitivity): If UI allows editing `effectiveTimestamp`, default to now(); display the used timestamp near the prices.

Validation/UI rules:
- Disable `Add to Estimate` until a successful price has been retrieved OR a manager override is explicitly applied (override flow out-of-scope; surface as Open Question if needed).
- If `priceSource` === `MSRP_FALLBACK`, show an inline warning: "Using MSRP fallback — no special pricing found for this location or tier." and require explicit acknowledge checkbox if dealership policy requires.

8. Data Requirements

- Entities involved (frontend view):
  - `EstimateLineDraft` (productId, quantity, selectedUnitPrice, priceSource, pricingBreakdown[], warnings[])
  - `PriceQuoteRequest` (per backend contract)
  - `PriceQuoteResponse` (per backend contract)

- Fields (type, required):
  - `productId`: UUID (required)
  - `quantity`: integer > 0 (required)
  - `locationId`: UUID (required)
  - `customerTierId`: UUID (required by backend) — if missing, UI must prompt for tier selection
  - `unitPrice`: { amount: decimal, currency: string } (read-only after fetch)
  - `extendedPrice`: { amount: decimal, currency: string } (read-only)
  - `msrp`: { amount: decimal, currency: string } (read-only)
  - `pricingBreakdown`: array of rule objects (read-only)

Read-only vs editable:
- Price fields returned from Pricing Service are read-only in the default flow. Any UI-level price override requires manager permission and explicit audit note (override flow: out-of-scope).

9. Service Contracts (Frontend Perspective)

- Load/View Calls:
  - No special pre-load required; product selection provides `productId`.

- Create/Update Calls:
  - POST `/v1/price-quotes` — request example (see backend spec). Client must send `X-Correlation-Id` and accept JSON.

- Error handling expectations:
  - 200 OK: render response
  - 400 Bad Request: show field-level validation errors; allow correction
  - 404 Not Found (product or price missing): show user-friendly message: "Price unavailable — check product or contact pricing admin." If `priceSource` is MSRP fallback, display as a warning not an error.
  - 500 Internal Server Error: show generic error with retry option

10. State Model & Transitions

- Local state machine for `EstimateLineDraft`:
  - `idle` (product selected, qty entered) -> `pricing:loading` (request sent)
  - `pricing:loading` -> `pricing:success` (200) OR `pricing:error` (4xx/5xx)
  - `pricing:success` -> `line:ready` (Add to Estimate enabled)
  - `pricing:error` -> `idle` (allow correction) OR `retry`

Role-based transitions:
- Only users with `override:price` permission may bypass `line:ready` requirement; UI must hide manager override controls otherwise (permission controls via existing frontend auth layer).

11. Alternate / Error Flows

- Validation failure: quantity <= 0 → inline message and prevent request.
- Concurrency: If the product price changes between fetch and add-to-estimate, show a warning "Price changed since retrieval" and offer Refresh/Proceed options; ensure PriceQuoteResponse includes an id/timestamp to compare.
- Unauthorized: If backend returns 403, show "Permission denied to fetch pricing" and surface an escalation action.
- Empty states: If `pricingBreakdown` is empty but `unitPrice` present, treat as valid (display "No adjustments applied").

12. Acceptance Criteria (Gherkin)

AC1: Happy Path - Correct Price Display
```
Given the Service Advisor selects product P with quantity 2
And the current location L has an override to $95.00 for P
And the customer tier applies a 10% discount
When the advisor fetches price
Then the UI displays unitPrice $85.50 and extendedPrice $171.00
And the pricingBreakdown shows base price, location override, and customer tier discount in that order
```

AC2: MSRP Fallback
```
Given product P has no special pricing for location L or tier T
When the advisor fetches price
Then the UI displays unitPrice equal to MSRP
And the UI shows a visible warning: "Using MSRP fallback — no special pricing found for this location or tier."
```

AC3: Validation Error
```
Given the advisor enters quantity 0
When they attempt to fetch price
Then the UI shows a validation error: "Quantity must be greater than 0"
```

AC4: Backend Validation Error
```
Given the frontend sends an invalid customerTierId
When the Pricing Service returns 400 with details
Then the UI shows the field-level message and allows correction
```

AC5: Performance
```
Given normal system load
When 1000 price quote requests are made
Then UI-observed P95 latency for the fetch action is below 200ms end-to-end (including network)
```

13. Audit & Observability

- Log price fetch attempts (sanitized) with `X-Correlation-Id`, request payload (productId, qty, locationId, customerTierId), response summary, and timing.
- Expose metrics: `price_fetch_latency` (histogram), `price_fetch_success_total`, `price_fetch_failure_total` (broken down by 4xx/5xx).
- UI must render the correlation id in debug mode for traceability.

14. Non-Functional UI Requirements

- Performance: Price fetch should not block UI interactions; show non-blocking spinner and disable `Add to Estimate` until success.
- Accessibility: `PricePreview` content must be readable by screen readers; warnings must use `role="alert"` and be focusable.
- Responsiveness: `PricePreview` collapsible breakdown should be usable on mobile with touch-friendly controls.
- i18n/timezone/currency: Currency formatting must use the dealership's configured currency; display should respect user locale.

15. Applied Safe Defaults

- Default ID: `ui.autoFetchDebounce=500ms` — assumed to improve UX by reducing noise; impacts UX and Service Contracts (reduces unnecessary calls).
- Default ID: `ui.showPriceBreakdownCollapsed=true` — breakdown collapsed by default to reduce visual noise; impacts UX.
- Default ID: `display.decimalPlaces.debug=4` — show 4 decimal places in developer/debug mode for breakdowns; impacts UX and Audit.

16. Open Questions

- OQ1: Manager override workflow: Should the frontend include an inline manager-override flow (permission, audit note) to accept non-pricing-service prices, or is that strictly backend-driven and out-of-scope? If required, add `blocked:clarification`.
- OQ2: Customer tier sourcing: Should the UI default to a `customerTier` inferred from the customer profile if present, or require explicit selection on estimate line? Backend requires non-null `customerTierId` — confirm preferred UX.
- OQ3: Price refresh behavior: When an estimate is edited later, should the frontend automatically refresh prices for existing lines and notify users of changes, or require manual refresh per line?

STOP: none

---

This story is authored for Moqui frontend implementers and testers. It maps closely to the backend Pricing Service contract (PriceQuoteRequest / PriceQuoteResponse) documented in the backend story (#51). Implementers should reference the backend issue for precise JSON schemas and the rounding and availability decisions.
