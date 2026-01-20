Title: [FRONTEND] [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/93
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Fulfillment: Reserve/Allocate Stock to Workorder Lines

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want to reserve stock for workorder lines so that parts are held for the job.

## Details
- Soft allocation vs hard reservation.
- Handle partial reservations and backorders.

## Acceptance Criteria
- Reservation created/updated.
- ATP reflects allocations.
- Idempotent updates.
- Audited.

## Integrations
- Workexec requests reservation; inventory responds with allocations and pick tasks.

## Data / Entities
- Reservation, Allocation, WorkorderLineRef

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