Title: [FRONTEND] [STORY] Billing: Define Account Billing Rules
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/164
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Billing: Define Account Billing Rules

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Billing Clerk**, I want **to set billing rules for a commercial account** so that **invoicing is correct and consistent**.

## Details
- Rules: PO required, payment terms, invoice delivery (email/portal), invoice grouping (per vehicle/per workorder).
- Rule changes audited and permissioned.

## Acceptance Criteria
- Create/update rules.
- Rules returned on account lookup.
- Rule changes audited and access-controlled.

## Integration Points (Workorder Execution)
- Estimate approval enforces PO rules.
- Invoicing uses delivery + grouping defaults.

## Data / Entities
- BillingRule
- PaymentTerm
- DeliveryPreference

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