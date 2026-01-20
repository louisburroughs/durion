Title: [FRONTEND] [STORY] Locations: Create Bays with Constraints and Capacity
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/141
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Locations: Create Bays with Constraints and Capacity

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to define bays with constraints (lift/equipment) so that scheduler assigns the right work to the right bay.

## Details
- Bay attributes: type (lift/alignment), supported services, capacity, required skills, status (active/out-of-service).

## Acceptance Criteria
- Bay created under a location.
- Out-of-service blocks assignments.
- Constraints queryable.

## Integrations
- Dispatch validates assignments against bay constraints.
- Workexec displays bay context during execution.

## Data / Entities
- Resource(Bay), ResourceConstraint, ResourceStatus

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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