Title: [FRONTEND] [STORY] Party: Create Individual Person Record
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/175
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Create Individual Person Record

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to create an individual person record with contact information** so that **I can associate them to a commercial account and/or vehicles**.

## Details
- Capture: name, phone(s), email(s), preferred contact method.
- Support role tags later (billing contact, driver, approver).

## Acceptance Criteria
- Can create person.
- Can store multiple phones/emails.
- Preferred contact method stored.

## Integration Points (Workorder Execution)
- Workorder Execution can select a person for notifications/approvals.

## Data / Entities
- Party/Person
- ContactPoint
- Preferences

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