Title: [FRONTEND] [STORY] Access: Assign Roles and Scopes (Global vs Location)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/153
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Access: Assign Roles and Scopes (Global vs Location)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to assign roles with optional location scope** so that **users have appropriate access per shop and job function**.

## Details
- Roles include: OWNER, ADMIN, MANAGER, HR, ACCOUNTING, DISPATCHER, SERVICE_WRITER, MECHANIC, AUDITOR, READ_ONLY.
- Scope types: GLOBAL or LOCATION.
- Effective dates supported.

## Acceptance Criteria
- Role assignment supports scope and effective dates.
- Permission checks enforced consistently.
- Changes are audited and versioned.

## Integration Points (workexec/shopmgr)
- workexec technician eligibility uses MECHANIC + location assignment.
- shopmgr scheduling permissions depend on MANAGER/DISPATCHER scope.

## Data / Entities
- RoleAssignment
- RoleGrant

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