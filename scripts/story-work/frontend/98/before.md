Title: [FRONTEND] [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/98
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Receiving: Receive Items into Staging (Generate Ledger Entries)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Receiver**, I want to receive items into staging so that on-hand increases and put-away can follow.

## Details
- Default to staging location.
- Record qty and UOM; handle over/short.

## Acceptance Criteria
- Ledger entries created for Receive.
- Items visible as 'in staging'.
- Variances recorded.

## Integrations
- Availability updates; workexec may see expected receipts.

## Data / Entities
- InventoryLedgerEntry(Receive), StagingLocation, ReceivingLineState

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