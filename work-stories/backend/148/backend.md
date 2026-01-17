Title: [BACKEND] [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/148
Labels: general, type:story, domain:accounting, status:ready-for-dev

## 🏷️ Labels (Current)
### Required
- type:story
- domain:accounting
- status:ready-for-dev

### Recommended
- agent:accounting
- agent:story-authoring

---
**Rewrite Variant:** accounting-strict
**Clarifications:** Resolved via #299
---

## Story Intent
As the System, I need to accurately calculate all financial totals (subtotal, taxes, fees, and grand total) for a draft invoice based on authoritative tax and fee rules. This ensures that a financially correct, auditable, and compliant invoice is prepared for issuance to the customer.

## Actors & Stakeholders
- **Actors**:
  - `System`: The automated process responsible for performing the calculations.
- **Stakeholders**:
  - `Accountant`: Relies on the accuracy and auditability of financial calculations for reporting and compliance.
  - `Service Advisor`: Needs to review the final, calculated invoice totals with the customer before issuance.
  - `Customer`: Receives the invoice and expects it to be accurate and transparent.
  - `Finance Department`: Uses the aggregated invoice data for financial forecasting and revenue recognition.

## Preconditions
- A draft `Invoice` entity exists in the system.
- The `Invoice` is associated with one or more `InvoiceItem` line entries, each with a defined price and quantity.
- Each `InvoiceItem` is associated with a product or service that has properties (e.g., `taxCode`) determining its taxability.
- An authoritative source for tax and fee rules (e.g., Tax Configuration Service) is available and accessible by the system.
- The original `Estimate` snapshot, associated with the work that generated the invoice, is available for variance comparison.

## Functional Behavior
### Trigger
An `Invoice` is transitioned to the `TotalsCalculationRequired` state. This event is triggered by:
1.  The creation of a new draft `Invoice` with line items.
2.  The addition, modification, or removal of an `InvoiceItem` on an existing draft `Invoice`.

### Process
1.  The System initiates the calculation process for the `Invoice`.
2.  For each `InvoiceItem`, the System resolves the applicable `TaxRule` and `FeeRule` from the authoritative configuration source based on the item's properties (e.g., `taxCode`, `productType`).
3.  The System calculates the `taxAmount` for each line item and any applicable invoice-level taxes.
4.  The System calculates any applicable line-item or invoice-level `feeAmount`.
5.  The System calculates the `InvoiceSubtotal` by summing the `lineTotal` of all `InvoiceItem`s.
6.  The System aggregates all calculated taxes into `TotalTax` and all fees into `TotalFees`.
7.  The System calculates the `GrandTotal` as the sum of `InvoiceSubtotal`, `TotalTax`, `TotalFees`, and any `RoundingAdjustment` required by policy.
8.  The System compares the calculated totals with the corresponding totals in the `EstimateSnapshot`. If a variance exists, a `Variance` record is created, detailing the amount and a system-generated `varianceReasonCode`.
9.  The System persists all calculated values (`Subtotal`, `TotalTax`, `TotalFees`, `GrandTotal`, etc.) on the `Invoice` entity.
10. A `CalculationSnapshot` is created and stored, containing an immutable record of the rules, rates, and values used for this specific calculation, ensuring auditability.
11. Upon successful completion, the `Invoice` state is transitioned to `TotalsCalculated`.

## Alternate / Error Flows
- **Flow: Incomplete Tax Basis**
  - **Trigger**: An `InvoiceItem` is missing required tax basis fields (taxCode, jurisdiction, pointOfSaleLocation, etc.).
  - **Outcome**: The calculation process fails. The `Invoice` state is transitioned to `CalculationFailed`. An error event is logged specifying the invoice and the line item with the missing data. The invoice is blocked from being issued until the data is corrected.

- **Flow: Unavailable Tax Service**
  - **Trigger**: The authoritative tax configuration source is unavailable or returns an error.
  - **Outcome**: The calculation process fails. The `Invoice` state is transitioned to `CalculationFailed`. A system alert is generated. The operation should be designed for graceful retries.

- **Flow: Variance Detected**
  - **Trigger**: The calculated `GrandTotal` differs from the `EstimateSnapshot.grandTotal`.
  - **Outcome**: This is considered a normal flow. The system automatically creates a `Variance` record with a reason code (e.g., `TAX_RULE_CHANGE`, `QUANTITY_CHANGE`). This variance is surfaced to the user for review before the invoice is issued.

## Business Rules
- **Rule-B1 (Authority)**: Tax and fee calculations must exclusively use the system's central Tax Configuration Service as the single source of truth for all rules and rates.
- **Rule-B2 (Immutability)**: Once an `Invoice` is transitioned to an `Issued` state, its calculated totals and the associated `CalculationSnapshot` are immutable and cannot be changed.
- **Rule-B3 (Auditability)**: Every component of the invoice total must be traceable. The `CalculationSnapshot` must provide a clear and complete audit trail from the `GrandTotal` back to the individual line items, tax rules, and rates applied.
- **Rule-B4 (Rounding)**: All monetary calculations use `RoundingMode.HALF_UP` with currency-scale precision (e.g., USD/EUR = 2 decimals). Round per-line, then sum. Persist explicit rounding deltas for audit.
- **Rule-B5 (Issuance Block)**: An invoice cannot be transitioned to the `Issued` state if its status is `CalculationFailed` or if it has unresolved blocking conditions (e.g., missing tax basis).
- **Rule-B6 (Variance Codes)**: System automatically detects and applies canonical variance reason codes. Variances exceeding thresholds require approval with `accounting:invoice:approve-variance` permission.
- **Rule-B7 (Tax Basis Validation)**: Mandatory fields include: `taxCode`, `jurisdiction`, `pointOfSaleLocation`, `productType`. Use fail-fast validation; override only with explicit permission and audit.

## Data Requirements
- **Entity: `Invoice`**
  - `invoiceId` (PK)
  - `status` (Enum: `Draft`, `TotalsCalculationRequired`, `TotalsCalculated`, `CalculationFailed`, `Issued`)
  - `subtotal` (Decimal)
  - `totalTax` (Decimal)
  - `totalFees` (Decimal)
  - `roundingAdjustment` (Decimal)
  - `grandTotal` (Decimal)
  - `estimateSnapshotId` (FK)
  - `invoiceDate` (Timestamp)
  - `jurisdiction` (String)
  - `shipAddressId` (FK)
  - `pointOfSaleLocationId` (FK)
  - `overriddenByUser` (String, nullable)
  - `overrideReason` (String, nullable)
  - `hasOverride` (Boolean, default: false)

- **Entity: `InvoiceItem`**
  - `invoiceItemId` (PK)
  - `taxCode` (String, Not Null)
  - `lineTotal` (Decimal)
  - `taxAmount` (Decimal)
  - `productId` (FK, Not Null)
  - `isExempt` (Boolean, default: false)

- **Entity: `CalculationSnapshot`**
  - `calculationSnapshotId` (PK)
  - `invoiceId` (FK, Unique)
  - `calculatedAt` (Timestamp)
  - `calculationDetails` (JSONB/Text): Immutable record of rules, rates, versions, rounding method, and deltas used in the calculation.

- **Entity: `Variance`**
  - `varianceId` (PK)
  - `invoiceId` (FK)
  - `varianceAmount` (Decimal)
  - `varianceReasonCode` (Enum: `TAX_RULE_CHANGE`, `QUANTITY_CHANGE`, `PRICE_CHANGE`, `DISCOUNT_APPLIED`, `FEE_ADDED`, `ROUNDING_VARIANCE`, `MANUAL_ADJUSTMENT`, `ESTIMATE_OUTDATED`, `CONFIGURATION_ERROR`)
  - `notes` (String)
  - `detectedAt` (Timestamp)
  - `approvedBy` (String, nullable)
  - `approvedAt` (Timestamp, nullable)

## Acceptance Criteria
- **AC1: Correct Totals for Standard Invoice**
  - **Given** a draft invoice with a line item priced at $100 and a quantity of 2.
  - **And** the applicable tax rule is a flat 10%.
  - **When** the system is triggered to calculate totals.
  - **Then** the invoice `subtotal` is $200.00.
  - **And** the `totalTax` is $20.00.
  - **And** the `grandTotal` is $220.00.
  - **And** the invoice status is updated to `TotalsCalculated`.
  - **And** rounding adjustment is $0.00 (exactly 2 decimal places).

- **AC2: Correct Totals for Mixed-Tax Invoice**
  - **Given** a draft invoice with a taxable item at $100 and a non-taxable item at $50.
  - **And** the applicable tax rate for the taxable item is 8%.
  - **When** the system calculates totals.
  - **Then** the invoice `subtotal` is $150.00.
  - **And** the `totalTax` is $8.00.
  - **And** the `grandTotal` is $158.00.
  - **And** no rounding variance exists between line sum and invoice subtotal.

- **AC3: Variance Detection due to Tax Rate Change**
  - **Given** an invoice whose `EstimateSnapshot` was created with a total of $105 based on a 5% tax rate.
  - **And** the current authoritative tax configuration specifies a 7% rate for the invoice items.
  - **When** the system calculates totals for the invoice.
  - **Then** the new `grandTotal` is $107.
  - **And** a `Variance` record is created with `varianceAmount` of $2.00 and `varianceReasonCode` of `TAX_RULE_CHANGE`.
  - **And** variance is audited with detection timestamp and reason.

- **AC4: Block on Incomplete Tax Data**
  - **Given** a draft invoice where a line item is missing its required `taxCode`.
  - **When** the system attempts to calculate totals.
  - **Then** the process fails.
  - **And** the invoice status is updated to `CalculationFailed`.
  - **And** the invoice cannot be transitioned to the `Issued` state.
  - **And** error response includes specific missing fields (taxCode, jurisdiction, pointOfSaleLocation, etc.).

- **AC5: Tax Basis Validation**
  - **Given** a draft invoice missing mandatory tax basis field (jurisdiction or pointOfSaleLocation).
  - **When** validation is performed.
  - **Then** calculation fails with RFC 7807 error listing specific missing fields.
  - **And** invoice remains in `CalculationFailed` state until corrected.

## Audit & Observability
- **Audit Trail**: Every state transition of an `Invoice` (e.g., `Draft` -> `TotalsCalculated`) must be logged as an audit event, including the actor (System), timestamp, and reason. The creation of a `CalculationSnapshot` is a primary audit event.
- **Logging**:
  - INFO: Log the start and successful completion of invoice total calculations, including the `invoiceId`.
  - ERROR: Log failures in detail, including `invoiceId`, error message, and stack trace, especially for failed dependencies like the tax service.
- **Metrics**:
  - `invoice_calculation_success_count`: Counter for successful calculations.
  - `invoice_calculation_failure_count`: Counter for failed calculations, tagged by error type (e.g., `data_missing`, `service_unavailable`).
  - `invoice_calculation_duration_ms`: Histogram of calculation latency.
  - `invoice_variance_detected_count`: Counter for invoices where a variance from the estimate was found, tagged by `varianceReasonCode`.

## Resolved Questions (from #299)
All clarification questions have been resolved:

1. **Rounding Policy**: Currency-scale rounding with HALF_UP; round per-line, then sum; persist rounding deltas.
2. **Tax Authority Source**: Internal Tax Configuration Service with optional third-party integration and fallback policies.
3. **Variance Reason Codes**: Nine canonical codes defined (TAX_RULE_CHANGE, QUANTITY_CHANGE, PRICE_CHANGE, DISCOUNT_APPLIED, FEE_ADDED, ROUNDING_VARIANCE, MANUAL_ADJUSTMENT, ESTIMATE_OUTDATED, CONFIGURATION_ERROR).
4. **Incomplete Tax Basis**: Mandatory fields: taxCode, jurisdiction, pointOfSaleLocation, productType; fail-fast validation; override with permission.

See comment thread for detailed implementation guidance.

---
## Original Story (Unmodified – For Traceability)
# Issue #148 — [BACKEND] [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice

## Current Labels
- backend
- story-implementation
- general

## Current Body
## Backend Implementation for Story

**Original Story**: [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice

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
Invoice draft is created or invoice lines are adjusted.

## Main Flow
1. System applies tax and fee rules to invoice lines based on snapshot/tax config.
2. System calculates subtotal, taxes, fees, rounding adjustments, and grand total.
3. System compares invoice totals to estimate snapshot totals and records variance reasons where applicable.
4. System updates invoice totals and stores calculation snapshot.
5. System prevents issuing invoice if tax basis is incomplete (policy).

## Alternate / Error Flows
- Tax configuration changed since estimate → flag variance and require review.
- Missing tax codes → block issuance and show actionable errors.

## Business Rules
- Tax calculation must be explainable and auditable.
- Mixed-tax scenarios must be supported.

## Data Requirements
- Entities: Invoice, InvoiceItem, TaxRule, CalculationSnapshot, EstimateSnapshot
- Fields: taxCode, taxRate, taxAmount, feeTotal, roundingAdjustment, varianceAmount, varianceReason

## Acceptance Criteria
- [ ] Invoice totals compute correctly for mixed-tax scenarios.
- [ ] System records variance vs estimate snapshot when applicable.
- [ ] Invoice cannot be issued when required tax basis is missing.

## Notes for Agents
Variance explanations reduce disputes—capture them automatically when possible.


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