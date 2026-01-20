Title: [FRONTEND] [STORY] Vehicle: Associate Vehicles to Account and/or Individual
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/168
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Associate Vehicles to Account and/or Individual

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to link a vehicle to a commercial account and optionally a primary driver** so that **workorders default to the right billing entity and contacts**.

## Details
- Vehicle associated to an account (owner) and optionally an individual (driver).
- Support reassignment with history.

## Acceptance Criteria
- Create/update associations.
- History preserved on reassignment.

## Integration Points (Workorder Execution)
- Workorder Execution fetches owner/driver for selected vehicle.

## Data / Entities
- VehiclePartyAssociation (owner/driver, dates)

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