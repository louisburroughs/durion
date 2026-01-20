Title: [FRONTEND] [STORY] Putaway: Execute Put-away Move (Staging → Storage)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/95
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Putaway: Execute Put-away Move (Staging → Storage)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Stock Clerk**, I want to execute put-away moves so that inventory becomes available for picking.

## Details
- Scan from/to locations.
- Update ledger with movement PutAway.

## Acceptance Criteria
- Ledger entry created.
- On-hand updated per destination.
- Task marked complete.
- Audited.

## Integrations
- Workexec sees accurate pick locations.

## Data / Entities
- InventoryLedgerEntry(PutAway), PutawayTaskState, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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