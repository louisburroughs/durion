Title: [FRONTEND] [STORY] Execution: Issue and Consume Parts
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/222
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Issue and Consume Parts

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299981/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Technician / Parts Counter

## Trigger
Parts are picked/issued for installation on a workorder.

## Main Flow
1. User selects a workorder part item.
2. User records parts issued (picked) and parts consumed (installed).
3. System validates quantities and updates on-hand commitments (if integrated).
4. System records consumption event with timestamp and user.
5. System updates item completion indicators where applicable.

## Alternate / Error Flows
- Insufficient inventory → flag and move workorder to waiting parts status.
- Consumption exceeds authorized quantity → block or require approval per policy.

## Business Rules
- Parts usage must be recorded as events (issue/consume/return).
- Consumption should not silently change authorized scope without approval.
- Traceability must be preserved.

## Data Requirements
- Entities: WorkorderItem, PartUsageEvent, InventoryReservation
- Fields: productId, quantityIssued, quantityConsumed, eventType, eventAt, performedBy, originEstimateItemId

## Acceptance Criteria
- [ ] Parts issued/consumed can be recorded and audited.
- [ ] System enforces quantity integrity and policy limits.
- [ ] Workorder status reflects parts availability issues.
- [ ] Each issued part emits exactly one InventoryIssued event
- [ ] Inventory on-hand quantity is reduced correctly
- [ ] COGS or WIP impact follows configured accounting model
- [ ] Issued quantities are traceable to workorder and technician
- [ ] Replayed events do not double-reduce inventory

## Integrations

### Accounting
- Emits Event: InventoryIssued
- Event Type: Non-posting or Posting (configurable: WIP vs immediate COGS)
- Source Domain: workexec
- Source Entity: WorkorderPartUsage
- Trigger: Part is issued/consumed for a workorder item
- Idempotency Key: workorderId + partId + usageSequence


## Notes for Agents
Keep parts usage consistent with promotion snapshot; changes route through approvals.


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