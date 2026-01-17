Title: [FRONTEND] [STORY] Contacts: Maintain Contact Roles and Primary Flags
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/172
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Contacts: Maintain Contact Roles and Primary Flags

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **CSR**, I want **to set contact roles (billing, approver, driver) and primary flags** so that **the correct person is used in approvals and invoicing**.

## Details
- Allow multiple roles per person/account.
- Optionally enforce at least one billing contact when invoice delivery is email.

## Acceptance Criteria
- Can assign roles.
- Primary billing contact can be designated.
- Validation enforced when configured.

## Integration Points (Workorder Execution)
- Estimate approval uses approver role.
- Invoice delivery uses billing contact.

## Data / Entities
- ContactRole
- PartyRelationship

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