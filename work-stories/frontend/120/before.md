Title: [FRONTEND] [STORY] Master: Manage UOM and Pack/Case Conversions
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/120
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Master: Manage UOM and Pack/Case Conversions

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want to define UOM conversions so that packs/cases vs each transact correctly.

## Details
- Base UOM plus alternate UOM conversions.
- Validate non-zero and reversible conversions.

## Acceptance Criteria
- UOM conversions created and queryable.
- Conversion rules enforced.
- Audited changes.

## Integrations
- Inventory uses UOM on stock moves; Workexec uses UOM on lines.

## Data / Entities
- UnitOfMeasure, UomConversion, AuditLog

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