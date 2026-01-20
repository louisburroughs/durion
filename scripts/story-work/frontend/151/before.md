Title: [FRONTEND] [STORY] Location: Create/Update Location (pos-location) Including Timezone
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/151
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Location: Create/Update Location (pos-location) Including Timezone

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to create and update locations** so that **staffing, scheduling, and timekeeping are anchored to the correct site and timezone**.

## Details
- Location fields: code, name, address, timezone, status, optional parent.

## Acceptance Criteria
- Location can be created/updated.
- Inactive locations prevent new staffing assignments.

## Integration Points (workexec/shopmgr)
- shopmgr schedules are tied to locationId.
- workexec workorders reference a service location.

## Data / Entities
- Location

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