Title: [FRONTEND] [STORY] Users: Create/Update Employee Profile (pos-people)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/152
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Users: Create/Update Employee Profile (pos-people)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **HR user**, I want **to maintain employee profile details** so that **the organization has a reliable directory for operations and compliance**.

## Details
- Fields: legal name, preferred name, employee number, status, hire/term dates, contact info.
- Basic duplicate warning based on employee number and email.

## Acceptance Criteria
- Create/update person with required fields.
- Duplicate warning presented for likely collisions.
- All changes audited.

## Integration Points (workexec/shopmgr)
- workexec displays technician identity consistently.
- shopmgr shows staff name and status for rosters.

## Data / Entities
- Person

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