Title: [FRONTEND] [STORY] Party: Associate Individuals to Commercial Account
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/174
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Associate Individuals to Commercial Account

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to link individuals to a commercial account with relationship types/roles** so that **workorder approval and billing contacts are unambiguous**.

## Details
- Relationship includes role(s) and effective dates.
- Allow one or more primary billing contacts per account.

## Acceptance Criteria
- Can create relationship with role.
- Primary billing contact can be designated.
- Relationship can be deactivated.

## Integration Points (Workorder Execution)
- Workorder Execution retrieves approvers/billing contacts for an account.

## Data / Entities
- PartyRelationship (roles, flags, effective dates)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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