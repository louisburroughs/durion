Title: [FRONTEND] [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/134
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Assign Mechanic and Resource (Bay/Mobile) to Appointment

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to assign mechanics and a resource to an appointment so that work can be executed efficiently.

## Details
- Validate mechanic/resource availability + required skills.
- Support team assignment (lead/helper).

## Acceptance Criteria
- Assignment created only if checks pass (or override).
- Changes audited.
- Schedule updated.

## Integrations
- Emit AssignmentCreated/Updated to workexec when linked.

## Data / Entities
- Assignment, AssignmentRole, AuditLog

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