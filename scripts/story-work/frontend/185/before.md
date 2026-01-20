Title: [FRONTEND] [STORY] Accounting: Ingest InventoryIssued Event
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/185
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InventoryIssued Event

**Domain**: user

### Story Description

/kiro
Focus on inventory valuation, COGS timing, and idempotent posting.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InventoryIssued` event from Workorder Execution

## Main Flow
1. Receive inventory issue event with part, quantity, and workorder reference
2. Validate event schema and idempotency key
3. Determine valuation method (configured, e.g., FIFO/average)
4. Reduce on-hand inventory quantity
5. Record corresponding COGS or WIP entry based on configuration
6. Persist posting references and source links

## Alternate / Error Flows
- Duplicate event → ignore (idempotent)
- Invalid inventory reference → reject and flag
- Posting failure → retry or dead-letter

## Business Rules
- Inventory may only be reduced once per issued quantity
- Valuation method is configuration-driven
- Posting must be traceable to source workorder and part issue

## Data Requirements
- Entities: InventoryItem, InventoryTransaction, WorkorderRef
- Fields: quantity, valuationAmount, issueTimestamp

## Acceptance Criteria
- [ ] Inventory quantity is reduced correctly
- [ ] COGS/WIP is recorded per configuration
- [ ] Event is idempotent
- [ ] Posting references original workorder

## Classification (confirm labels)
- Type: Story
- Layer: Functional
- Domain: Accounting

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)

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