Title: [FRONTEND] [STORY] Locations: Create Mobile Units and Coverage Rules
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/140
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Locations: Create Mobile Units and Coverage Rules

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to define mobile units with service capabilities and coverage so that mobile appointments can be scheduled with travel buffers.

## Details
- Mobile unit attributes: capabilities, base location, service area tags, travel buffer policy, max daily jobs.

## Acceptance Criteria
- Mobile unit created and assignable.
- Coverage/buffers configurable.
- Out-of-service blocks scheduling.

## Integrations
- HR receives travel time.
- Workexec stores mobileUnitId context for mobile workorders.

## Data / Entities
- Resource(MobileUnit), CoverageRule, TravelBufferPolicy

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