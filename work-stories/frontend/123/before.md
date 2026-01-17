Title: [FRONTEND] [STORY] CrossDomain: Workexec Displays Operational Context in Workorder View
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/123
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] CrossDomain: Workexec Displays Operational Context in Workorder View

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mechanic**, I want workexec to display my assigned location/bay and team on the workorder so that I know where to work and who I’m working with.

## Details
- Workexec shows operational context fields.
- Updates allowed until work starts; after start require manager action.

## Acceptance Criteria
- Workorder shows location/resource/team.
- Update rules enforced.
- Audit visible.

## Integrations
- Shopmgr provides context; workexec renders it and emits statuses back.

## Data / Entities
- WorkorderOperationalContext (workexec domain)

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