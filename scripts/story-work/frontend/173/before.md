Title: [FRONTEND] [STORY] Party: Search and Merge Duplicate Parties (Basic)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/173
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Party: Search and Merge Duplicate Parties (Basic)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Admin**, I want **to identify and merge obvious duplicate parties** so that **workorder selection remains clean and accurate**.

## Details
- Search by name/email/phone.
- Merge workflow: choose survivor, move relationships/vehicles/contacts, record merge audit.
- Optional alias/redirect record for merged IDs.

## Acceptance Criteria
- Can list possible duplicates.
- Can merge with an audit record.
- References remain resolvable after merge.

## Integration Points (Workorder Execution)
- Workorder Execution references must remain resolvable after merge (alias/redirect lookup).

## Data / Entities
- Party
- MergeAudit
- PartyAlias (optional)

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