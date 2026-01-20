Title: [FRONTEND] [STORY] Promotions: Apply Offer During Estimate Pricing
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/159
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

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