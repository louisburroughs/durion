Title: [FRONTEND] [STORY] Dispatch: Import Mechanic Roster and Skills from HR
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/136
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Import Mechanic Roster and Skills from HR

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want shopmgr to sync mechanic roster, roles, skills, and home location from durion-hr so that dispatch can assign the right mechanic.

## Details
- Ingest mechanic identities, roles, skill tags, location affiliations.
- Track active/assignable status.

## Acceptance Criteria
- Mechanics present with hrPersonId.
- Deactivated mechanics not assignable.
- Sync idempotent.

## Integrations
- HR→Shopmgr roster API or MechanicUpserted events.

## Data / Entities
- MechanicProfile, SkillTag, LocationAffiliation

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