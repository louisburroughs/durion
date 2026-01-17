Title: [FRONTEND] [STORY] Workexec: Propagate Assignment Context to Workorder
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/128
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Propagate Assignment Context to Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want workorders to carry location/resource/mechanic context from shopmgr so that execution and reporting are accurate.

## Details
- Attach operational context at WO creation.
- Updates allowed until work starts.

## Acceptance Criteria
- Workorder has locationId/resourceId/mechanicIds.
- Updates applied pre-start.
- Audit maintained.

## Integrations
- Shopmgr emits AssignmentUpdated; workexec applies update rules; workexec emits StatusChanged.

## Data / Entities
- OperationalContext, AssignmentSyncLog

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