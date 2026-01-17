Title: [FRONTEND] [STORY] Invoicing: Calculate Taxes, Fees, and Totals on Invoice
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/212
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

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