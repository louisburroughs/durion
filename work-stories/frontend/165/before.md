Title: [FRONTEND] [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/165
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Ingest Vehicle Updates from Workorder Execution

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **System**, I want **to update vehicle details captured during service (VIN correction, mileage, notes)** so that **CRM remains accurate over time**.

## Details
- Accept updates via event envelope from Workorder Execution.
- Apply idempotency and audit against workorder reference.

## Acceptance Criteria
- Vehicle updates are processed once.
- Audit includes source Workorder/Estimate ID.
- Conflicts handled (last-write or review queue; define policy).

## Integration Points (Workorder Execution)
- Workorder Execution emits VehicleUpdated events.
- CRM persists updates and exposes updated snapshot.

## Data / Entities
- Vehicle
- EventEnvelope
- ProcessingLog

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