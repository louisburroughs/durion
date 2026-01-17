Title: [FRONTEND] [STORY] Topology: Sync Locations from durion-hr
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/104
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Topology: Sync Locations from durion-hr

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want to sync location identifiers and metadata from durion-hr so that inventory is scoped to valid shops and mobile sites.

## Details
- Import locationId, name, status, timezone, and tags.
- Keep a local reference table for FK integrity.

## Acceptance Criteria
- Location refs created/updated idempotently.
- Deactivated locations cannot receive new stock movements.
- Audit sync runs.

## Integrations
- HR → Inventory location roster API/events.

## Data / Entities
- LocationRef, SyncLog

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