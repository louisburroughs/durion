Title: [FRONTEND] [STORY] Users: Disable User (Offboarding) Without Losing History
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/154
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Users: Disable User (Offboarding) Without Losing History

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to disable a user account** so that **access is removed while historical labor and timekeeping records remain intact**.

## Details
- Disable login in pos-security-service.
- Optionally end active location assignments.
- Force-stop any active job timers.

## Acceptance Criteria
- Disabled users cannot authenticate.
- Person record retained and marked inactive (policy-driven).
- All forced stops and changes are audited.

## Integration Points (workexec/shopmgr)
- workexec excludes disabled users from assignment.
- shopmgr excludes disabled users from future schedules.

## Data / Entities
- User
- Person
- Assignment
- TimeEntry

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


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