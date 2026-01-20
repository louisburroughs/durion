Title: [FRONTEND] [STORY] Pricing: Define Base Company Price Book Rules
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/118
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Pricing: Define Base Company Price Book Rules

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Pricing Analyst**, I want base price books so that pricing is consistent across stores with controlled variation.

## Details
- Rule types: markup over cost, discount from MSRP, fixed price.
- Price books can be tiered by customer tier.
- Versioning + effective dating.

## Acceptance Criteria
- Deterministic rule evaluation.
- Version changes audited.

## Integrations
- Workexec calls PriceQuote with location + customer tier; store overrides layered after base.

## Data / Entities
- PriceBook, PriceRule, PriceBookVersion, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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