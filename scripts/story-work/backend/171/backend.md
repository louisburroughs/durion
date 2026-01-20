Title: [BACKEND] [STORY] Estimate: Calculate Taxes and Totals on Estimate
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/171
Labels: general, type:story, domain:pricing, status:needs-review

STOP: Clarification required before finalization
## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing
- agent:story-authoring

### Blocking / Risk
- blocked:clarification

---
**Rewrite Variant:** pricing-strict
---
## Story Intent
**As a** System (Pricing Service),
**I want to** reliably and accurately calculate all financial totals (subtotal, taxes, fees, grand total) for a service Estimate,
**so that** Service Advisors can present correct pricing to customers and the business can maintain an auditable record of how totals were derived.

## Actors & Stakeholders
- **Primary Actor:**
  - **System (Pricing Service):** The automated service responsible for executing pricing and tax calculations.
- **Stakeholders:**
  - **Service Advisor:** Relies on the accuracy of the calculated totals to communicate with customers.
  - **`workexec` Domain:** The consumer of the calculation results. It owns the `Estimate` entity and its lifecycle state.
  - **`accounting` Domain:** Will use the calculation snapshot for future invoicing and variance analysis.
  - **`audit` Domain:** Requires a complete and reproducible calculation record for compliance.

## Preconditions
- An `Estimate` entity exists in a `Draft` or similar mutable state.
- The `Estimate` is associated with a specific business location/jurisdiction for which tax rules are configured.
- The `Estimate` has at least one line item (Part, Labor, Fee, or Discount).
- All required pricing and tax configurations (e.g., tax rates, fee rules) for the relevant jurisdiction are available and active.

## Functional Behavior
### Trigger
An `Estimate` is modified through the creation, update, or deletion of any of its constituent line items (e.g., parts, labor, fees, discounts). The `workexec` domain service will initiate a totals calculation request.

### Main Flow
1.  **Request Reception:** The Pricing Service receives a synchronous request from the `workexec` service to calculate totals for a specified `EstimateID`.
2.  **Data Aggregation:** The service retrieves the current set of all line items associated with the `EstimateID`.
3.  **Subtotal Calculation:** The service calculates the pre-tax, pre-fee subtotal by summing the extended price of all Part and Labor line items.
4.  **Tax Calculation:**
    a. For each line item, the service determines its taxability based on its type (Part, Labor, Fee) and assigned tax code.
    b. It applies the appropriate tax rate(s) based on the Estimate's jurisdiction.
    c. It calculates the total tax amount by summing the tax calculated for each individual line item.
5.  **Fee & Discount Application:** The service calculates the total value of all applicable fees (e.g., shop supplies, environmental fees) and discounts.
6.  **Grand Total Calculation:** The service calculates the grand total by summing the subtotal, total tax, and total fees, then subtracting total discounts. It applies a defined rounding policy to the final value.
7.  **Snapshot Generation:** The service creates an immutable `CalculationSnapshot` record. This record contains all inputs (line items, quantities, prices), rules applied (tax rates, jurisdiction, fee rules), and all calculated output values.
8.  **Response:** The Pricing Service returns the calculated totals (Subtotal, TaxTotal, FeeTotal, DiscountTotal, GrandTotal) and the unique identifier for the `CalculationSnapshot` to the calling `workexec` service.

## Alternate / Error Flows
- **Flow 1: Item with Missing Tax Configuration**
  - **Trigger:** A line item that should be taxable is missing a required tax code.
  - **Outcome:** The system's behavior is determined by business policy (see Open Question OQ1). The service will either apply a configured default or reject the calculation with a specific error code (`ERR_MISSING_TAX_CODE`).
- **Flow 2: Unconfigured Tax Jurisdiction**
  - **Trigger:** The `Estimate` is associated with a location/jurisdiction for which no tax rules are defined.
  - **Outcome:** The calculation request is rejected with a `ERR_CONFIG_JURISDICTION_MISSING` error. The `workexec` domain must block the `Estimate` from proceeding to an `Approved` state.
- **Flow 3: Invalid Input Data**
  - **Trigger:** The calculation request contains invalid data (e.g., negative quantity on a part line).
  - **Outcome:** The request is rejected with a `400 Bad Request` status and a descriptive error message.

## Business Rules
- **Rule-PRC-1 (Line-Level Taxability):** Taxability is determined at the individual line item level. Different item types (e.g., Parts, Labor, certain Fees) may have different tax rules.
- **Rule-PRC-2 (Calculation Determinism):** All calculations must be deterministic. Given the exact same set of inputs and configuration, the service must always produce the exact same outputs.
- **Rule-PRC-3 (Auditability):** Every calculation that results in a change to the Estimate's totals must generate a persistent, immutable `CalculationSnapshot`. This snapshot must contain sufficient context to reproduce the calculation.
- **Rule-PRC-4 (Rounding Policy):** A system-wide, authoritative rounding policy shall be applied to the final Grand Total. (See Open Question OQ2).

## Data Requirements
- **Entity: `CalculationSnapshot`**
  - An immutable record capturing the state of a single calculation event.
  - **Fields:**
    - `snapshotId` (Primary Key)
    - `estimateId` (Foreign Key)
    - `calculationTimestamp`
    - `inputLineItems` (JSON/structured data: item ID, description, quantity, unit price, tax code)
    - `appliedRules` (JSON/structured data: jurisdiction, tax rates, fee rules, discount IDs)
    - `outputSubtotal`
    - `outputTaxTotal`
    - `outputFeeTotal`
    - `outputDiscountTotal`
    - `outputGrandTotal`
    - `outputRoundingAdjustment`

- **Data Contract (Response to `workexec`):**
  - `subtotal`
  - `taxTotal`
  - `feeTotal`
  - `discountTotal`
  - `grandTotal`
  - `calculationSnapshotId`

## Acceptance Criteria
- **AC1: Standard Calculation with Taxable Items**
  - **Given** an Estimate with one Part line item at $100 and one Labor line item at $50.
  - **And** both items are subject to a 10% tax rate.
  - **When** the system calculates the totals.
  - **Then** the Subtotal is $150.00, the Tax Total is $15.00, and the Grand Total is $165.00.
  - **And** a `CalculationSnapshot` is created reflecting these inputs and outputs.

- **AC2: Calculation with Mixed Taxable and Non-Taxable Items**
  - **Given** an Estimate with a taxable Part at $100 (10% tax) and a non-taxable Labor line at $50.
  - **When** the system calculates the totals.
  - **Then** the Subtotal is $150.00, the Tax Total is $10.00, and the Grand Total is $160.00.
  - **And** a `CalculationSnapshot` is created.

- **AC3: Calculation Fails for Unconfigured Jurisdiction**
  - **Given** an Estimate is created for a location with no configured tax rules.
  - **When** a line item is added, triggering a calculation.
  - **Then** the Pricing Service returns a `ERR_CONFIG_JURISDICTION_MISSING` error.
  - **And** the `workexec` service prevents the Estimate from being approved.

- **AC4: Calculation with Shop Fee and Discount**
  - **Given** an Estimate with a subtotal of $200.
  - **And** a 10% shop supply fee (calculated on subtotal) is applied.
  - **And** a $15 discount is applied.
  - **And** the total taxable amount is subject to a 5% tax rate.
  - **When** the system calculates the totals.
  - **Then** the Fee Total is $20.00, the Discount Total is $15.00, the Tax Total is $10.00 (on the $200 subtotal), and the Grand Total is $215.00 ($200 + $20 Fee + $10 Tax - $15 Discount).
  - **And** a `CalculationSnapshot` is created.

## Audit & Observability
- **Audit Trail:** The `CalculationSnapshot` entity serves as the primary audit artifact for all financial totals on the Estimate. Its `snapshotId` must be stored on the `Estimate` entity in the `workexec` domain.
- **Events:** Upon successful calculation, the Pricing Service shall emit a `EstimateTotalsCalculated` event containing the `estimateId` and the `calculationSnapshotId`.
- **Logging:** Key stages of the calculation process (e.g., request received, rules applied, response sent) should be logged with correlation IDs. Errors during calculation must be logged with a high severity level.

## Open Questions
- **OQ1: Missing Tax Code Policy:** What is the specific business policy for handling a taxable item that is missing a tax code?
  - **Option A:** Fail the calculation and return an error.
  - **Option B:** Apply a configured "default" tax code and proceed.
  - **Decision Needed:** Which behavior is required? If Option B, what is the default code and should this case be flagged for review?
- **OQ2: Rounding Policy:** What is the authoritative rounding policy for final totals? (e.g., standard round half up to nearest cent, banker's rounding). This must be explicitly defined to ensure consistency.
- **OQ3: Tax-Inclusive Pricing:** Does the system need to support tax-inclusive pricing models for any jurisdiction now, or is a tax-exclusive model (where tax is added to the base price) sufficient for the initial implementation?

## Original Story (Unmodified – For Traceability)
# Issue #171 — [BACKEND] [STORY] Estimate: Calculate Taxes and Totals on Estimate

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Estimate: Calculate Taxes and Totals on Estimate

**Domain**: general

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
System

## Trigger
Estimate line items are created or modified (parts/labor/fees/discounts).

## Main Flow
1. System identifies taxable basis per line item using tax codes and jurisdiction.
2. System calculates line-level and/or header-level taxes per configuration.
3. System applies discounts, fees (shop supplies, environmental), and rounding rules.
4. System updates estimate subtotal, tax total, and grand total.
5. System records calculation snapshot (inputs and outputs) for audit/reproducibility.

## Alternate / Error Flows
- Missing tax code on an item → apply default tax code or block based on policy.
- Tax region not configured → block submission for approval and surface configuration error.

## Business Rules
- Tax rules may vary by item type (parts vs labor vs fees).
- Support tax-inclusive and tax-exclusive modes.
- Persist enough calculation context to explain totals later (disputes).

## Data Requirements
- Entities: Estimate, EstimateItem, TaxRule, FeeRule, CalculationSnapshot
- Fields: taxCode, taxRate, taxAmount, subtotal, discountTotal, feeTotal, grandTotal, roundingAdjustment

## Acceptance Criteria
- [ ] Totals and taxes update correctly for mixed taxable/non-taxable items.
- [ ] System stores a calculation snapshot that can be reviewed.
- [ ] Estimate cannot proceed to approval if required tax configuration is missing (per policy).

## Notes for Agents
Ensure calculation snapshots can be reused during promotion/invoice variance explanations.


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