Title: [FRONTEND] [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/100
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Ledger: Compute On-hand and Available-to-Promise by Location/Storage

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want on-hand/ATP so that we can quote and schedule realistically.

## Details
- On-hand computed from ledger; ATP = on-hand - allocations + expected receipts (optional).
- Provide per location and optionally per storage location.

## Acceptance Criteria
- Availability query returns on-hand and ATP.
- Consistent UOM handling.
- SLA suitable for estimate UI.

## Integrations
- Product/workexec query availability; product may surface ATP to pricing.

## Data / Entities
- AvailabilityView, AllocationSummary, ExpectedReceiptSummary

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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