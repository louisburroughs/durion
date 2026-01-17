Title: [FRONTEND] [STORY] Ledger: Record Stock Movements in Inventory Ledger
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/101
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Ledger: Record Stock Movements in Inventory Ledger

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want all stock movements recorded in a ledger so that on-hand is auditable and explainable.

## Details
- Movement types: Receive, PutAway, Pick, Issue/Consume, Return, Transfer, Adjust.
- Capture productId, qty, UOM, from/to storage, actor, timestamp, reason.

## Acceptance Criteria
- Every movement creates a ledger entry.
- Ledger is append-only.
- Can reconstruct on-hand by replay.
- Adjustments require reason and permission.

## Integrations
- Workexec can query movement history for a workorder line.

## Data / Entities
- InventoryLedgerEntry, MovementType, ReasonCode, AuditLog

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