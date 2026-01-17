Title: [BACKEND] [STORY] Promotions: Apply Offer During Estimate Pricing
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/95
Labels: type:story, domain:pricing, status:ready-for-dev

## Story Intent
As a **Service Advisor**, I want **to apply a valid promotion code to a work estimate and see the calculated discount**, so that **customers receive accurate pricing before approving the work**.

## Actors & Stakeholders
- **Service Advisor** (Primary Actor): Initiates the promotion application on behalf of the customer.
- **Customer** (Beneficiary): Receives the promotional discount on their service estimate.
- **Pricing Service** (System): Validates promotion applicability and computes pricing adjustments.
- **Work Execution Service** (System Context): Owns the estimate entity and persists/reflects pricing results.
- **CRM Service** (Supporting System): System of record for promotion definitions, rules, and eligibility.

## Preconditions
- The Service Advisor is authenticated and authorized to create and modify estimates.
- An active work estimate exists in a `Draft` or `Pending Approval` state.
- The estimate has at least one priceable line item.
- Pricing, Work Execution, and CRM services are available and can communicate securely.

## Functional Behavior
- **Trigger**: The Service Advisor provides a `promotionCode` and requests its application to a specific `estimateId`.

1. **Request Initiation**: Work Execution receives the request and calls Pricing with `promotionCode` and estimate pricing context (customer + line items + totals).
2. **Promotion Validation**: Pricing calls CRM synchronously to validate the code and retrieve eligibility/rules/parameters.
3. **Eligibility Check**: CRM validates existence, active/expiry window, usage limits, and eligibility rules against the provided estimate context.
4. **Pricing Adjustment Generation**: Pricing computes a canonical `PricingAdjustment` (promotion discount) and returns it along with updated totals.
5. **Apply Results**: Work Execution persists the applied promotion reference and reflects the returned pricing adjustment(s) on the estimate (discount line + recalculated totals).

## Alternate / Error Flows
- **Invalid/Expired Code**: Pricing returns `PROMO_NOT_FOUND` (HTTP 400). UI displays a user-facing message (copy is provisional).
- **Ineligible Estimate**: Pricing returns `PROMO_NOT_APPLICABLE` (HTTP 400). UI displays a user-facing message (copy is provisional).
- **Multiple Promotions Attempted**: Pricing returns `PROMO_MULTIPLE_NOT_ALLOWED` (HTTP 400). No promotion applied.
- **Service Unavailability**: Pricing returns `SERVICE_UNAVAILABLE` (HTTP 503). Estimate remains unchanged.

## Business Rules
- **Domain Ownership (Resolved):** `domain:pricing` is the ultimate authority for `PricingAdjustment` and all promotion math. `domain:workexec` consumes pricing results and persists references for the estimate.
- **Promotion Count (Resolved):** Only **one** promotion may be applied to a single estimate for this story.
- Promotion discounts are calculated on the pre-tax subtotal of eligible items.
- The application of a promotion must be idempotent; applying the same valid code multiple times to the same estimate state results in the same final pricing.
- User-facing error message strings are **provisional**; backend must expose **stable error codes**.

## Data Requirements
- **Input (to Pricing Service)**:
  - `promotionCode`: `string`
  - `estimateContext`:
    - `estimateId`: `UUID`
    - `customerId`: `UUID`
    - `lineItems`: array of `{ sku, quantity, unitPrice }`
    - `subtotal`: `MonetaryAmount`

- **Output (from Pricing Service)**:
  - **Success**: pricing result containing `subtotal`, `total`, and `appliedAdjustments[]` including the promotion adjustment.
    - `PricingAdjustment` (promotion) fields:
      - `type`: `PROMOTION`
      - `sourceId`: `UUID` (promotion/offer id from CRM)
      - `label`: `string`
      - `amount`: `MonetaryAmount` (negative)
      - `metadata`: optional object for display/audit
  - **Failure**:
    - `errorCode`: `string` (stable)
    - `message`: `string` (provisional copy)
    - `details`: optional object

## Acceptance Criteria
- **AC1: Valid Promotion Applied Successfully**
  - **Given** a promotion "SAVE10" exists for a 10% discount
  - **And** a work estimate with a subtotal of $200.00 is eligible
  - **When** the Service Advisor applies the code "SAVE10"
  - **Then** Pricing returns a promotion `PricingAdjustment` amount of `-$20.00`
  - **And** Work Execution reflects a discount line and reduced estimate total

- **AC2: Invalid Promotion Code Rejected**
  - **Given** a work estimate exists
  - **When** the Service Advisor applies an invalid code "FAKECODE"
  - **Then** Pricing returns `PROMO_NOT_FOUND`
  - **And** the estimate pricing and totals remain unchanged

- **AC3: Valid Promotion for Ineligible Estimate**
  - **Given** a promotion "OILSPECIAL" exists but only applies to "Full Synthetic Oil Change"
  - **And** the estimate contains only "Tire Rotation"
  - **When** the Service Advisor applies "OILSPECIAL"
  - **Then** Pricing returns `PROMO_NOT_APPLICABLE`
  - **And** the estimate pricing and totals remain unchanged

## Audit & Observability
- Emit `INFO` log on successful application: `estimateId`, `promotionId`, `promotionCode`, `userId`, `discountAmount`.
- Emit `WARN` log on failed attempts: `estimateId`, `promotionCode`, `userId`, failure reason.
- Instrument Pricing→CRM call for latency, error rate, traffic.

## Open Questions
None.

---

## Original Story (Unmodified – For Traceability)
# Issue #95 — [BACKEND] [STORY] Promotions: Apply Offer During Estimate Pricing

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Promotions: Apply Offer During Estimate Pricing

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to apply a promotion code to an estimate and see the discount** so that **customers receive correct pricing before approval**.

## Details
- Validate code and eligibility.
- Record applied offer and discount parameters/lines.

## Acceptance Criteria
- Invalid code rejected.
- Discount line appears in estimate totals.
- Applied offer recorded for traceability.

## Integration Points (Workorder Execution)
- Workorder Execution calls CRM promotions API to validate/apply.
- CRM returns discount parameters or rule reference.

## Data / Entities
- AppliedPromotion reference
- PricingAdjustment (WO domain)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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