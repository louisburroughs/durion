Title: [FRONTEND] [STORY] Estimate: Add Parts to Estimate
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/238
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Add Parts to Estimate

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor

## Trigger
A Draft estimate exists and parts need to be quoted.

## Main Flow
1. User searches parts catalog by part number, description, or category.
2. User selects a part and specifies quantity.
3. System defaults unit price from configured price list and applies discounts/markups as configured.
4. User optionally overrides price if permitted and provides a reason code if required.
5. System adds the part line item and recalculates totals.

## Alternate / Error Flows
- Part not found → allow controlled non-catalog part entry (if enabled) with mandatory description.
- Quantity invalid (<=0) → block and prompt correction.
- Price override not permitted → block and show policy message.

## Business Rules
- Each part line item references a part number (or controlled non-catalog identifier).
- Totals must be recalculated on line change.
- Price overrides require permission and may require reason codes.

## Data Requirements
- Entities: Estimate, EstimateItem, Product, PriceList, DiscountRule, AuditEvent
- Fields: itemSeqId, productId, partNumber, quantity, unitPrice, discountAmount, taxCode, isNonCatalog, overrideReason

## Acceptance Criteria
- [ ] User can add a catalog part line item to a Draft estimate.
- [ ] Totals update immediately after adding or editing part items.
- [ ] Audit records who changed quantity/price.

## Notes for Agents
Model part items so later promotion preserves stable identifiers and tax snapshots.


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