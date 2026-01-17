Title: [FRONTEND] [STORY] Topology: Define Default Staging and Quarantine Locations for Receiving
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/102
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Topology: Define Default Staging and Quarantine Locations for Receiving

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want default receiving staging and quarantine locations so that receiving workflows are consistent.

## Details
- Each site can define staging and quarantine locations.
- Quarantine requires approval to move into available stock.

## Acceptance Criteria
- Staging/quarantine configured per location.
- Receiving uses staging by default.
- Quarantine moves require permission.

## Integrations
- Distributor receiving may land in staging; quality hold uses quarantine.

## Data / Entities
- ReceivingPolicy, StorageLocationRef, PermissionCheck

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