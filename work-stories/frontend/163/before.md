Title: [FRONTEND] [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/163
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Billing: Expose CRM Snapshot (Account + Contacts + Vehicles + Rules)

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Workorder System**, I want **a single endpoint to fetch account/person, contacts, vehicles, rules, and preferences** so that **estimate creation can be fast and consistent**.

## Details
- Endpoint accepts partyId and/or vehicleId.
- Returns normalized snapshot with version/timestamp.
- Cache-friendly response model.

## Acceptance Criteria
- Returns expected fields.
- Includes timestamps/version.
- Not-found handled cleanly.

## Integration Points (Workorder Execution)
- Workorder Execution calls snapshot at estimate draft and on-demand refresh.

## Data / Entities
- CRM Snapshot DTO / API

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