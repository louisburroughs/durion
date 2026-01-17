Title: [FRONTEND] [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/117
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] StorePrice: Sync Locations from durion-hr for Pricing Scope

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want to sync location identifiers from durion-hr so that store pricing can be scoped to valid locations.

## Details
- Import locationId, name, status.
- Optional region/tags.

## Acceptance Criteria
- Locations present in product domain.
- Deactivated locations cannot receive new overrides.
- Sync idempotent.

## Integrations
- HR → Product location roster API/events.

## Data / Entities
- LocationRef, SyncLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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