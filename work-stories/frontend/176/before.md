Title: [FRONTEND] [STORY] Party: Create Commercial Account
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/176
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Create Commercial Account

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Fleet Account Manager**, I want **to create a commercial customer account with legal name, billing profile, and identifiers** so that **workorders and invoices can be consistently tied to the correct business entity**.

## Details
- Capture: legal name, DBA, tax ID (optional), account status, default billing terms.
- Support external identifiers (ERP/customer number) as optional fields.
- Basic duplicate warning on create (name + phone/email match).

## Acceptance Criteria
- Can create account with required fields.
- Account has stable CRM ID.
- Duplicate warning presented when close matches exist.

## Integration Points (Workorder Execution)
- Workorder Execution can search/select the account by name/ID.
- Selected account CRM ID is stored on Estimate/Workorder.

## Data / Entities
- Party/Account
- ExternalId (optional)
- Audit fields

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