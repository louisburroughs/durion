Title: [FRONTEND] [STORY] Estimate: Calculate Taxes and Totals on Estimate
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/236
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

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