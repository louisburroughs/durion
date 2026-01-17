Title: [FRONTEND] [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/157
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Emit CRM Reference IDs in Workorder Artifacts

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **System**, I want **workorders/estimates to store CRM partyId, vehicleId, and contactIds** so that **traceability and reporting are possible**.

## Details
- Workorder domain stores foreign references to CRM.
- CRM merges must not break references (aliases/redirects).

## Acceptance Criteria
- Estimate/WO persist CRM references.
- References resolvable back to CRM after merges.

## Integration Points (Workorder Execution)
- Workorder Execution persists CRM IDs; CRM provides alias resolution endpoint if needed.

## Data / Entities
- WO/Estimate reference fields
- PartyAlias (optional)

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