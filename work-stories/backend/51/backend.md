Title: [BACKEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/51
Labels: type:story, domain:pricing, status:ready-for-dev

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:ready-for-dev

### Recommended
- agent:pricing
- agent:story-authoring

---
**Rewrite Variant:** pricing-strict
---

## Story Intent
As a Service Advisor using the POS system, I need to retrieve the accurate, context-sensitive price for a product when adding it to a customer's estimate, so that I can provide a reliable and transparent quote that reflects all applicable rules and promotions.

## Actors & Stakeholders
- **Primary Actor:** Service Advisor (initiates the action via the POS or Estimate Builder UI).
- **System Actor:** Work Execution Service (the client system that makes the API call to the Pricing Service on behalf of the user).
- **System of Record:** Pricing Service (the authoritative system that owns, calculates, and serves the price).
- **Stakeholders:** Customer (receives the quote), Dealership Management (relies on correct pricing for profitability and reporting), Finance Department (relies on auditable pricing).

## Preconditions
- The calling service (Work Execution Service) is authenticated and authorized to access the Pricing API.
- The `productId`, `locationId`, and `customerTier` provided in the request correspond to valid, existing entities in the system.
- The Pricing Service is running, healthy, and accessible from the Work Execution Service.

## Functional Behavior
The system shall expose a synchronous API endpoint for retrieving a price quote for a given product and context.

1.  **Trigger:** The Work Execution Service sends an HTTP `POST` request to a new `/v1/price-quotes` endpoint.
2.  **Request Payload:** The request body will be a JSON object containing:
    - `productId` (UUID)
    - `quantity` (Integer)
    - `locationId` (UUID)
    - `customerTierId` (UUID, or similar identifier)
    - `effectiveTimestamp` (ISO 8601 UTC, optional, defaults to `now()`)
3.  **Core Logic:**
    - The Pricing Service receives the request and validates the payload.
    - It fetches the base price for the `productId`.
    - It applies a deterministic sequence of pricing rules based on the provided context (`locationId`, `customerTierId`, `effectiveTimestamp`). The required evaluation order is:
        1.  Base Product Price (MSRP)
        2.  Location-Specific Price Overrides
        3.  Customer Tier Adjustments (e.g., discounts, markups)
        4.  Final Rounding
    - The service generates a trace of all rules that were considered and applied.
4.  **Success Response:** On successful calculation, the service returns an HTTP `200 OK` with a JSON payload containing:
    - The final calculated `unitPrice` and `extendedPrice` (unitPrice * quantity).
    - The original `msrp` for comparison.
    - A `pricingBreakdown` array, detailing each rule applied in order.
    - A `warnings` array for non-blocking issues (e.g., "Customer tier discount not applicable to this product category").

## Alternate / Error Flows
- **Invalid Product:** If the `productId` does not exist, the service returns an HTTP `404 Not Found`.
- **Invalid Context:** If `locationId` or `customerTierId` are invalid or not found, the service returns an HTTP `400 Bad Request` with a descriptive error message.
- **Malformed Request:** If any required fields are missing or have incorrect data types, the service returns an HTTP `400 Bad Request`.
- **No Price Found:** If the product is valid but has no base price defined, the service returns an HTTP `404 Not Found` with a message indicating a data configuration issue.
- **Internal Error:** If any unexpected server-side error occurs during processing, the service returns an HTTP `500 Internal Server Error`.

## Business Rules
- **BR1: Deterministic Evaluation:** The pricing evaluation order (Base → Location Override → Customer Tier → Rounding) is fixed and non-negotiable to ensure consistent and predictable pricing.
- **BR2: Auditability:** Every component of the price calculation (base price, each adjustment) must be explicitly listed in the `pricingBreakdown` field of the response.
- **BR3: Monetary Precision:** All internal monetary calculations will be performed using a precision of at least 4 decimal places. The final `unitPrice` and `extendedPrice` returned in the API response will be rounded to 2 decimal places according to the system's authoritative financial rounding policy.
- **BR4: Time-Sensitivity:** Pricing rules are time-sensitive. The `effectiveTimestamp` in the request is used to determine which rules are active. If omitted, the request is processed using the current system time (`now()`).

## Data Requirements
**`PriceQuoteRequest`**
```json
{
  "productId": "uuid",         // Non-nullable
  "quantity": "integer",       // Non-nullable, > 0
  "locationId": "uuid",        // Non-nullable
  "customerTierId": "uuid",    // Non-nullable
  "effectiveTimestamp": "date-time" // Optional, UTC
}
```

**`PriceQuoteResponse`**
```json
{
  "productId": "uuid",
  "quantity": "integer",
  "msrp": { "amount": "decimal", "currency": "string" },
  "unitPrice": { "amount": "decimal", "currency": "string" },
  "extendedPrice": { "amount": "decimal", "currency": "string" },
  "pricingBreakdown": [
    {
      "ruleName": "string",
      "ruleType": "string", // e.g., 'BASE_PRICE', 'LOCATION_OVERRIDE'
      "adjustment": { "amount": "decimal", "currency": "string" },
      "resultingValue": { "amount": "decimal", "currency": "string" }
    }
  ],
  "warnings": ["string"]
}
```

## Acceptance Criteria
**AC1: Happy Path - Correct Price Calculation**
- **Given** a product with a base MSRP of $100.00
- **And** a location override rule that sets the price to $95.00
- **And** a customer tier rule that applies a 10% discount
- **When** a price quote is requested for a quantity of 2 with that product, location, and customer tier
- **Then** the system returns a `200 OK` response
- **And** the `unitPrice` is $85.50 (95.00 * 0.9)
- **And** the `extendedPrice` is $171.00
- **And** the `pricingBreakdown` contains entries for the base price, the location override, and the customer tier discount in that order.

**AC2: No Applicable Rules**
- **Given** a product with a base MSRP of $50.00
- **And** no specific pricing rules for the requested location or customer tier
- **When** a price quote is requested
- **Then** the system returns a `200 OK` response
- **And** the `unitPrice` is $50.00
- **And** the `pricingBreakdown` shows only the 'BASE_PRICE' rule was applied.

**AC3: Invalid Product ID**
- **Given** the requesting service is authenticated
- **When** a price quote is requested with a non-existent `productId`
- **Then** the system returns an HTTP `404 Not Found` error response.

**AC4: Invalid Request Payload**
- **Given** the requesting service is authenticated
- **When** a price quote is requested with a `quantity` of 0
- **Then** the system returns an HTTP `400 Bad Request` error response with a clear validation message.

**AC5: Performance SLA**
- **Given** the Pricing Service is operating under normal load conditions
- **When** 1000 valid price quote requests are processed
- **Then** the P95 response time for the endpoint is less than 150ms.

## Audit & Observability
- **Logging:** All price quote requests (with sanitized PII) and their full responses shall be logged at the INFO level for traceability and support. All errors (4xx, 5xx) shall be logged at the ERROR level with a full stack trace where applicable.
- **Metrics:** The service must expose metrics for:
    - Request latency (P50, P90, P95, P99).
    - Total request count, broken down by response code (2xx, 4xx, 5xx).
- **Events:** On every successful price quote generation, a `PriceQuoteGenerated` event should be published to the event stream. The event payload should contain the full request and response data for consumption by downstream systems like analytics or audit logging.

## Resolved Questions

### RQ1 (Rounding Policy)
**Question:** What is the authoritative financial rounding policy for the system (e.g., half-up, banker's rounding)?

**Resolution:** The system uses **Banker's Rounding (Round Half to Even)** applied at currency precision (2 decimal places). This rounding must be applied:
- Per line item (after all pricing rules applied)
- At invoice total calculation

**Example:** 
- 2.345 → 2.34
- 2.355 → 2.36

**Rationale:** This method minimizes cumulative bias in financial calculations, particularly important for high-volume transactions. This approach is the financial industry standard (IEEE 754) and provides better statistical properties than simple half-up rounding.

---

### RQ2 (Performance SLA)
**Question:** What is the specific P95 latency target in milliseconds for this endpoint?

**Resolution:** The performance SLA targets are:
- **P95 latency:** ≤ 150ms
- **P50 latency:** ≤ 60ms  
- **P99 latency:** ≤ 300ms

**Measurement Scope:** These SLAs are measured at the Pricing service boundary (excluding network transit time from the client and excluding any upstream availability lookups from Inventory).

**Rationale:** These targets support a responsive UI experience while allowing for reasonable database query time and pricing rule evaluation. The P95 target of 150ms allows the UI to render pricing feedback within 200ms end-to-end (including network), which meets user responsiveness expectations.

---

### RQ3 (Coupled Availability Check)
**Question:** Should pricing synchronously check inventory availability in the same API call? What are the timeout and error handling requirements?

**Resolution:** Coupled availability check is **NOT a hard requirement** and is **strongly discouraged for v1**. 

**Preferred Architecture (v1):**
- Pricing service must NOT synchronously call Inventory service
- Client (Workexec) should make a separate, parallel API call to Inventory for availability
- This maintains proper domain boundaries and prevents cascading failures

**Fallback Option (if business requirement forces coupling):**
- Availability check must be **best-effort only**
- Must use a strict **75ms timeout** for the Inventory service call
- On timeout or Inventory service failure:
  - Return pricing successfully (200 OK)
  - Include availability field: `"availabilityStatus": "UNKNOWN"`
  - Log warning for monitoring
- Pricing calculation must never fail due to Inventory unavailability

**Rationale:** Coupling domains introduces tight dependencies and performance risks. Separate calls allow parallel execution, independent scaling, and better fault isolation. If coupling is required, the timeout and graceful degradation ensure pricing (the primary responsibility) is never blocked by availability lookups.

---

### RQ4 (No Price Found Behavior)
**Question:** If a product has no pricing rules defined for the given context, what should the system do?

**Resolution:** Fallback to the base MSRP is **correct and approved**.

**Required Behavior:**
1. If no location-specific or customer-tier pricing rules are found, use the base MSRP
2. Mark the response with explicit source indicator:
   - Include field: `"priceSource": "MSRP_FALLBACK"`
3. Allow downstream business policy to:
   - Warn the user (e.g., "Using MSRP - no special pricing available")
   - Require manager override approval if needed
4. **Do not** fail the pricing request, return null, or substitute a different customer tier's price

**Rationale:** Providing MSRP as a fallback ensures pricing is always available, preventing workflow blockage. The explicit `priceSource` flag allows the UI and business logic to handle this scenario appropriately (e.g., showing a notice to the Service Advisor or requiring approval for quotes using fallback pricing). This balances system availability with business control.

## Original Story (Unmodified – For Traceability)
# Issue #51 — [BACKEND] [STORY] Workexec: Price Product for Estimate Line (Location + Customer Tier)

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

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


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*