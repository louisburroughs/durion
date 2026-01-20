Title: [FRONTEND] [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/167
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Vehicle Lookup by VIN/Unit/Plate

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to search vehicles by VIN, unit number, or plate** so that **I can quickly start an estimate for the correct asset**.

## Details
- Partial VIN and unit searches.
- Return matches including owner account context.

## Acceptance Criteria
- Search returns ranked matches.
- Selecting a match returns full vehicle + owner snapshot.

## Integration Points (Workorder Execution)
- Estimate creation uses vehicle search/selection.

## Data / Entities
- Vehicle search endpoint/index

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