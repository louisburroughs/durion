Title: [FRONTEND] [STORY] Receiving: Direct-to-Workorder Receiving (Cross-dock) from Distributor
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/97
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Receiving: Direct-to-Workorder Receiving (Cross-dock) from Distributor

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Inventory Manager**, I want to receive items directly against a workorder so that urgent jobs can be fulfilled without normal put-away.

## Details
- Receiving lines can be linked to workorderId and workorderLineId.
- Items received can be immediately allocated/issued to that workorder.
- Optionally bypass storage and go straight to issue.

## Acceptance Criteria
- Receipt linked to workorder.
- Allocation/issue auto or confirm.
- Workexec notified.
- Audit includes supplier shipment ref.

## Integrations
- Workexec sends demand; Positivity provides shipment status; inventory posts receive+issue.

## Data / Entities
- WorkorderReceiptLink, ReceivingLine, InventoryLedgerEntry(Receive/Issue)

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