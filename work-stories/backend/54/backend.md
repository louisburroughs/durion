Title: [BACKEND] [STORY] Pricing: Define Base Company Price Book Rules
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/54
Labels: type:story, domain:pricing, status:ready-for-dev, agent:story-authoring, agent:pricing

---
**Rewrite Variant:** pricing-strict
---

## Story Intent
**As a** Pricing Administrator or System Architect,
**I want to** define and manage the base company price book rules that determine product pricing,
**so that** pricing is consistent, auditable, and reflects company-wide policies across all locations and customer segments.

## Actors & Stakeholders
- **Pricing Administrator (Primary Actor)**: Defines/updates company-wide pricing rules (markups, margins, fixed prices, tier/location conditions).
- **Pricing System (System Actor)**: Evaluates pricing rules, calculates effective prices, and publishes rule lifecycle events.
- **CRM System (Integration / Data Source)**: Authoritative source for customer tier.
- **Product System (Integration / Data Source)**: Authoritative source for MSRP.
- **Inventory System (Integration / Data Source)**: Authoritative source for cost (location-aware).
- **Store Manager (Indirect Stakeholder)**: May apply store/location overrides that layer on top of the base price book.
- **Reporting / Analytics (Downstream Consumer)**: Consumes pricing data for margin and compliance reporting.

## Preconditions
1. Pricing Administrator is authenticated and authorized to manage base price books and rules.
2. Product and Inventory data sources are reachable (MSRP and cost may be missing for some products).
3. Customer tier source (CRM) is reachable (tier may be missing for some accounts).
4. A company default base price book exists (or can be created).

## Functional Behavior
1. Pricing Administrator accesses base price book management.
2. Administrator creates or updates a rule within a base price book. A rule supports:
   - **Target granularity**: product (`SKU`/`productId`), category (`categoryId`), or global.
   - **Pricing logic**: e.g., markup over MSRP, markup over cost, fixed price, tier discount from base.
   - **Conditions**: optional conditions such as customer tier and/or location.
   - **Effective date range**: `effectiveStartAt` and optional `effectiveEndAt`.
   - **Priority**: deterministic tie-breaker when multiple candidate rules apply.
3. System validates the rule:
   - Required fields present.
   - Conflicts are detected (same target/conditions with overlapping effective windows).
   - Pricing logic is evaluatable given available base data.
4. If validation passes, system persists the rule.
5. System recalculates affected prices (or defers to price-request time; implementation choice) and publishes the appropriate rule lifecycle event.

## Alternate / Error Flows
- **Error Flow 1: Missing Base Data (Graceful Degradation)**
  - If a rule requires base data (cost or MSRP) and that base data is missing, the system marks the rule evaluation outcome as `NOT_APPLICABLE_MISSING_BASE` and continues to evaluate the next applicable rule in precedence order.
  - If *no* rule can produce a price and MSRP is missing, return error `PRICE_BASE_DATA_MISSING`.
- **Error Flow 2: Conflicting Rules**
  - If a new/updated rule conflicts with an existing active rule for the same target/conditions/effective window, reject the change with a validation error describing the conflicting rule(s).
- **Error Flow 3: Unauthorized Action**
  - If the user is not authorized to create/update base price book rules, return `403 Forbidden`.
- **Alternate Flow 1: Rule Deactivation**
  - Administrator can deactivate a rule by setting `effectiveEndAt` to a past date or marking `INACTIVE`.
  - Deactivated rules do not influence pricing but remain queryable for audit.

## Business Rules
These decisions were resolved via Clarification #255 (see issue comments for decision record).

- **BR1: Rule Target Granularity & Precedence (Most-Specific-Wins)**
  - Supported targets: `SKU`/product (`productId`), `CATEGORY` (`categoryId`, with hierarchical taxonomy), `GLOBAL`.
  - Precedence (highest → lowest):
    1. SKU/product rule (exact product match)
    2. Category rule (nearest category in the taxonomy; lowest depth distance wins)
    3. Global rule
    4. Fallback to MSRP (if present)
- **BR2: Deterministic Tie-Breakers**
  - If multiple rules apply at the same granularity, resolve deterministically by:
    1. Highest `priority`
    2. Most recent `effectiveStartAt`
    3. Lowest `ruleId`
- **BR3: Authoritative Data Sources**
  - CRM is authoritative for customer tier.
  - Product is authoritative for MSRP.
  - Inventory is authoritative for cost (location-aware).
- **BR4: Missing Base Data Handling**
  - If a rule requires cost/MSRP and that base is missing, mark that rule evaluation as `NOT_APPLICABLE_MISSING_BASE` and continue evaluating lower-precedence rules.
  - If no rule can price and MSRP is also missing, return `PRICE_BASE_DATA_MISSING`.
- **BR5: Price Book Scope & Selection**
  - Multiple base price books are allowed, but there must be exactly one active default per scope.
  - Deterministic selection order (highest precedence first):
    1. (locationId + customerTier)
    2. (locationId)
    3. (customerTier)
    4. company default
  - Store overrides (tracked in story #52) layer on top of the resolved base book result.
- **BR6: Immutability of Historical Rules**
  - Once a rule’s effective period has ended, it becomes immutable for audit; future changes require a new rule.

## Data Requirements
### Entities
- **PriceBookRule**
  - `ruleId` (PK)
  - `priceBookId` (FK)
  - `targetType` (Enum: `SKU`, `CATEGORY`, `GLOBAL`)
  - `targetId` (productId or categoryId; null for GLOBAL)
  - `pricingLogic` (structured JSON; supports markup/fixed/discount-from-base patterns)
  - `conditionType` (Enum: `CUSTOMER_TIER`, `LOCATION`, `NONE`)
  - `conditionValue` (e.g., tierCode or locationId)
  - `priority` (Integer)
  - `effectiveStartAt` (Timestamp)
  - `effectiveEndAt` (Timestamp, Nullable)
  - `status` (Enum: `ACTIVE`, `INACTIVE`, `NOT_APPLICABLE_MISSING_BASE`)
  - `createdByUserId`
  - `createdAt` (Timestamp)
  - `updatedAt` (Timestamp)

- **PriceBook**
  - `priceBookId` (PK)
  - `name` (String)
  - `scope` (Enum: `COMPANY_DEFAULT`, `LOCATION`, `CUSTOMER_TIER`)
  - `scopeId` (FK to location or tier; null for COMPANY_DEFAULT)
  - `isDefault` (Boolean; only one default per scope)
  - `status` (Enum: `ACTIVE`, `INACTIVE`)

### Integration Contracts (Authoritative)
- **CRM Tier**
  - `GET /crm/v1/accounts/{accountId}/tier`

    ```json
    {
      "accountId": "UUIDv7",
      "tierId": "UUIDv7",
      "tierCode": "FLEET_GOLD",
      "effectiveAt": "2026-01-12T00:00:00Z"
    }
    ```

  - Batch (recommended): `POST /crm/v1/accounts:tier-resolve`

    ```json
    { "accountIds": ["UUIDv7","UUIDv7"] }
    ```

- **MSRP (Product)**
  - `GET /product/v1/products/{productId}/msrp?currency=USD`

    ```json
    {
      "productId": "UUIDv7",
      "msrp": { "amount": 199.99, "currency": "USD" },
      "asOf": "2026-01-12T00:00:00Z"
    }
    ```

- **Cost (Inventory, location-aware)**
  - `GET /inventory/v1/cost/{productId}?locationId=UUIDv7`

    ```json
    {
      "productId": "UUIDv7",
      "locationId": "UUIDv7",
      "unitCost": { "amount": 120.50, "currency": "USD" },
      "costMethod": "AVERAGE | FIFO | STANDARD",
      "asOf": "2026-01-12T00:00:00Z"
    }
    ```

- **Performance note**: batch forms for tier/MSRP/cost are strongly recommended to avoid N+1 during price calculation.

## Acceptance Criteria
- **AC1: Create Global Markup Rule**
  - **Given** the Pricing Administrator creates a new rule in the company base price book,
  - **And** the rule is global with `pricingLogic` equivalent to “MSRP markup +20%”,
  - **When** the rule is saved and activated,
  - **Then** products without a more specific rule must price as `MSRP * 1.20`,
  - **And** a `PriceBookRuleCreated` event is published.

- **AC2: Create Product (SKU) Fixed Price Rule**
  - **Given** a product `productId=P1` has an MSRP,
  - **When** the Pricing Administrator creates a rule targeting `SKU/productId = P1` with fixed price $89.99,
  - **Then** the effective price for `P1` is $89.99 and overrides category/global rules.

- **AC3: Rule Precedence - Most Specific Wins**
  - **Given** a global rule “MSRP * 1.20”,
  - **And** a category rule for Category `C_Tires` “MSRP * 1.15”,
  - **And** a product rule for `productId=P_Tire123` “fixed $99.99”,
  - **When** the system prices `P_Tire123`,
  - **Then** it uses the product rule ($99.99).

- **AC4: Missing Base Data Handling**
  - **Given** a rule requires `unitCost` and `unitCost` is missing for `(productId, locationId)`,
  - **When** the system evaluates pricing,
  - **Then** that rule evaluation is treated as `NOT_APPLICABLE_MISSING_BASE` and a lower-precedence rule is evaluated,
  - **And** if no rule applies, the system falls back to MSRP if present,
  - **And** if MSRP is also missing, the system returns `PRICE_BASE_DATA_MISSING`.

  **Response contract (minimum required fields)**:

  ```json
  {
    "productId": "UUIDv7",
    "price": { "amount": 100.00, "currency": "USD" },
    "priceSource": "RULE | MSRP_FALLBACK",
    "appliedRuleId": "UUIDv7 | null",
    "missingCost": true,
    "missingMsrp": false
  }
  ```

- **AC5: Customer Tier-Specific Rule**
  - **Given** a rule applies a discount for tierCode `FLEET_GOLD`,
  - **When** the system prices a product for an account whose CRM tier resolves to `FLEET_GOLD`,
  - **Then** the system applies the tier rule,
  - **And** for an account with a different tierCode, the rule does not apply.

- **AC6: Multiple Base Price Books - Deterministic Selection**
  - **Given** a company default base price book and a location-specific base price book for `locationId=L1`,
  - **When** the system prices at `locationId=L1`,
  - **Then** it evaluates the location-specific base price book first,
  - **And** falls back to the company default base price book if no location-specific rule applies.

## Audit & Observability
- **Audit Trail:** Every create/update/deactivate operation for a price book rule is logged in an immutable audit log, including user, timestamps, and before/after values for updates.
- **Events:** Publish domain events:
  - `PriceBookRuleCreated`
  - `PriceBookRuleUpdated`
  - `PriceBookRuleDeactivated`
- **Metrics:**
  - Active rule count (by price book and target type)
  - Rules marked `NOT_APPLICABLE_MISSING_BASE`
  - Price calculation request rate
  - Cache hit/miss rate (if caching is used)

## Open Questions (if any)
- None.

## Original Story (Unmodified – For Traceability)
# Issue #54 — [BACKEND] [STORY] Pricing: Define Base Company Price Book Rules

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Pricing: Define Base Company Price Book Rules

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Pricing Administrator**, I want to define base price book rules (markup %, margin requirements, discount thresholds), so all locations start from the same pricing foundation.

## Details
- Rules at SKU or category level.
- Conditions: customer tier, location, effective dates.
- Stored overrides layer on top.

## Acceptance Criteria
- Rule created/updated.
- Price calculation respects base rules before overrides.
- Audited.

## Integrations
- Product service for MSRP/cost; CRM for customer tiers.

## Data / Entities
- PriceBookRule, PriceBook, AuditLog

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
