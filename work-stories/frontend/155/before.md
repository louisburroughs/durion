Title: [FRONTEND] [STORY] Users: Provision User and Link to Person
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/155
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Users: Provision User and Link to Person

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want **to create a user in pos-security-service and link them to a person in pos-people** so that **the employee can log in and be correctly attributed across workexec and timekeeping**.

## Details
- Create user with username/email, status, and initial role assignments.
- Link userId ↔ personId using email as primary match.
- Idempotent behavior on retries.

## Acceptance Criteria
- User created (or resolved) in pos-security-service.
- Link created between user and person.
- Initial role assignment applied.
- Audit record captured with actor and timestamp.

## Integration Points (workexec/shopmgr)
- workexec resolves technician attribution using personId/userId link.
- shopmgr receives new staff visibility if assigned to a location.

## Data / Entities
- User (pos-security-service)
- Person (pos-people)
- UserPersonLink (pos-people)

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