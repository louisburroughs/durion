Title: [BACKEND] [STORY] Catalog: View Product Details with Price and Availability Signals
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/16
Labels: type:story, domain:positivity, status:ready-for-dev, agent:story-authoring

**Rewrite Variant:** integration-conservative

## Story Intent
**As a** Service Advisor (via client apps),
**I want to** view a product’s master details plus location-scoped pricing and availability signals in a single API response,
**so that** I can explain options and availability to customers without stitching multiple backend calls.

## Actors & Stakeholders
- **Service Advisor (User):** Consumes product details, prices, and availability signals.
- **POS Backend (This system):** Aggregates data from authoritative services.
- **Product Catalog Service:** System of record for product master data (description/specs/substitution hints) and static lead-time hints.
- **Pricing Service:** System of record for pricing.
- **Inventory/Supply Chain Service:** System of record for on-hand/ATP and best-effort dynamic lead time.

## Preconditions
1. Caller is authenticated and authorized to read product and location data.
2. Request provides a valid `productId` and `locationId`.
3. Downstream service interfaces are reachable under normal conditions.

## Functional Behavior
### Endpoint
- `GET /api/v1/products/{productId}?location_id={locationId}`

### Processing (Happy Path)
1. Validate `productId` and `location_id`.
2. Fetch in parallel (or optimized sequence):
   - Product Catalog: master details, specs, substitution hints, static lead-time hint.
   - Pricing: MSRP + location-scoped store price (and currency).
   - Inventory: on-hand + ATP + best-effort dynamic lead time.
3. Aggregate into a single response with **explicit per-component status metadata**.
4. Return `200 OK`.

## Alternate / Error Flows
- **Product Not Found:** If Product Catalog returns not-found for `productId`, return `404 Not Found` and do not attempt to synthesize product data.
- **Invalid Location:** If `location_id` is invalid, return `400 Bad Request`.
- **Partial Failure (Graceful Degradation for browse/quote flows):**
  - This endpoint is a **browse/quote-building** read endpoint and MUST use **graceful degradation**.
  - If Pricing or Inventory is unavailable, return `200 OK` with:
    - the fields that could be retrieved
    - **status fields** indicating `OK | UNAVAILABLE | STALE`
    - `generatedAt` and `asOf` timestamps so callers can gate downstream actions.
  - Do not silently return `null` without a corresponding status.

## Business Rules
1. **Authority**
   - Product Catalog is authoritative for product master data and substitution hints.
   - Pricing is authoritative for price fields.
   - Inventory/Supply Chain is authoritative for availability fields and dynamic lead-time when available.

2. **Partial Failure Defaults**
   - If Inventory is unavailable: availability MUST be represented as **UNKNOWN** (not “in stock”), using status metadata.
   - If Pricing is unavailable: pricing values MUST be `null` with `pricing.status=UNAVAILABLE`; consumers MUST prevent actions requiring price finalization.

3. **Lead Time Hints (Two-tier model)**
   - Always return catalog static lead-time hints when present.
   - Prefer best-effort dynamic lead time from Inventory/Supply Chain when available; it overrides catalog hint.

4. **Staleness Signaling**
   - Include `pricing.asOf` and `availability.asOf` (when applicable).
   - Include `pricing.status` and `availability.status`.

5. **Caching**
   - Cache read-only responses with short TTLs and staleness metadata.
   - Default aggregated TTL: **15 seconds** (min of dynamic components).
   - Component-level defaults:
     - Pricing TTL: **60 seconds**
     - Inventory availability TTL: **15 seconds**
     - Dynamic lead time TTL: **5 minutes**
     - Catalog static fields TTL: **24 hours** (or version-keyed)
   - If future pricing becomes account-specific, cache keys MUST include the pricing context (e.g., `customerAccountId` / price list identifiers).

## Data Requirements
### Request
- **Path Parameter:** `productId` (UUID)
- **Query Parameter:** `location_id` (UUID)

### Response (200 OK)
`ProductDetailView` JSON object with explicit per-component status metadata:
```json
{
  "productId": "string",
  "description": "string",
  "specifications": [
    { "name": "string", "value": "string" }
  ],
  "generatedAt": "2026-01-12T15:03:11Z",
  "pricing": {
    "status": "OK",
    "asOf": "2026-01-12T15:03:11Z",
    "msrp": "decimal",
    "storePrice": "decimal",
    "currency": "USD"
  },
  "availability": {
    "status": "OK",
    "asOf": "2026-01-12T15:03:11Z",
    "onHandQuantity": 10,
    "availableToPromiseQuantity": 8,
    "leadTime": {
      "source": "CATALOG",
      "minDays": 2,
      "maxDays": 5,
      "asOf": "2026-01-12T15:03:11Z",
      "confidence": "MEDIUM"
    }
  },
  "substitutions": [
    {
      "productId": "string",
      "reason": "string"
    }
  ]
}
```

## Acceptance Criteria
**Scenario 1: Successful Retrieval**
- **Given** `productId` and `locationId` exist
- **And** Product, Pricing, and Inventory are available
- **When** `GET /api/v1/products/{productId}?location_id={locationId}` is called
- **Then** return `200 OK`
- **And** `pricing.status=OK` and `availability.status=OK`
- **And** location-scoped price and availability fields are populated.

**Scenario 2: No Substitutions Configured**
- **Given** product exists and has no substitutions configured
- **When** the endpoint is called
- **Then** return `200 OK`
- **And** `substitutions` is an empty array.

**Scenario 3: Product Does Not Exist**
- **Given** `productId` does not exist in Product Catalog
- **When** the endpoint is called
- **Then** return `404 Not Found`.

**Scenario 4: Inventory Unavailable (Graceful Degradation)**
- **Given** product exists
- **And** Product Catalog and Pricing are available
- **And** Inventory is unavailable
- **When** the endpoint is called
- **Then** return `200 OK`
- **And** master data and pricing are populated
- **And** `availability.status=UNAVAILABLE` (or equivalent) and availability quantities are omitted or null
- **And** availability is represented as UNKNOWN (not in-stock).

**Scenario 5: Pricing Unavailable (Graceful Degradation)**
- **Given** product exists
- **And** Product Catalog and Inventory are available
- **And** Pricing is unavailable
- **When** the endpoint is called
- **Then** return `200 OK`
- **And** `pricing.status=UNAVAILABLE`
- **And** `pricing.msrp`/`pricing.storePrice` are null
- **And** availability signals remain populated.

## Audit & Observability
- Log request start/end including `productId` and `locationId`.
- Emit per-dependency latency metrics (Product, Pricing, Inventory).
- Emit counters for degraded responses (pricing unavailable, inventory unavailable).
- Include `generatedAt` and component `asOf` timestamps in the response.

## Resolved Questions (Decision Record)
Decisions applied from clarification issue #218 (comment: https://github.com/louisburroughs/durion-positivity-backend/issues/218#issuecomment-3738963265):
- Partial failure policy: graceful degradation for browse/quote endpoints with explicit status metadata; fail fast reserved for commit/checkout endpoints.
- Lead time: two-tier (Catalog static baseline + dynamic best-effort override) returned as structured `leadTime` with `source/minDays/maxDays/asOf/confidence`.
- Caching: cache read-only responses with short TTLs (pricing 60s, inventory 15s, lead time 5m, catalog 24h) and staleness metadata; aggregated TTL defaults to 15s.

## Original Story (Unmodified – For Traceability)
# Issue #16 — [BACKEND] [STORY] Catalog: View Product Details with Price and Availability Signals

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

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