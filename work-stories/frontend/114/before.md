Title: [FRONTEND] [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/114
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Persist Immutable Pricing Snapshot for Estimate/WO Line

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want an immutable pricing snapshot per document line so that later price changes don’t alter history.

## Details
- Snapshot includes price, cost-at-time, MSRP-at-time, applied rules, timestamp, policy decisions.

## Acceptance Criteria
- Snapshot written on quote and/or booking.
- Immutable.
- Drilldown supported.

## Integrations
- Workexec stores snapshotId on lines; Accounting may consume for margin reporting (optional).

## Data / Entities
- PricingSnapshot, DocumentLineRef, PricingRuleTrace

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