Title: [FRONTEND] [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/88
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want reallocations so that reservations reflect updated schedule and priorities.

## Details
- Reallocation by priority and due time.
- Rules prevent starvation (optional).

## Acceptance Criteria
- Allocations updated deterministically.
- Audit includes reason.
- ATP updated.

## Integrations
- Workexec triggers priority changes; shopmgr schedule updates due times.

## Data / Entities
- Allocation, PriorityPolicy, AuditLog

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