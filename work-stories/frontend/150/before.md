Title: [FRONTEND] [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/150
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Manager**, I want **to assign employees to one or more locations with a primary designation** so that **workexec and shopmgr can enforce correct staffing eligibility**.

## Details
- Multiple active assignments allowed; exactly one primary.
- Effective-dated assignments.

## Acceptance Criteria
- Can assign/unassign person to locations.
- Primary location enforced.
- Assignment changes are audited and emitted.

## Integration Points (workexec/shopmgr)
- workexec eligible technician list constrained by assignment.
- shopmgr roster uses the same assignment source.

## Data / Entities
- PersonLocationAssignment

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