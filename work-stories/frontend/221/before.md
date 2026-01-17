Title: [FRONTEND] [STORY] Execution: Handle Part Substitutions and Returns
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/221
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Execution: Handle Part Substitutions and Returns

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Parts Counter / Technician

## Trigger
A different part is used or unused parts are returned.

## Main Flow
1. User selects a part item and chooses 'Substitute' or 'Return'.
2. System records substitution linking original and substituted part references.
3. System records quantity returned and updates usage totals.
4. If substitution impacts price/tax, system flags for approval if required.
5. System records all events for audit and inventory reconciliation.

## Alternate / Error Flows
- Substitution not allowed by policy → block.
- Return would create negative consumed quantity → block.

## Business Rules
- Substitutions must preserve traceability to original authorized scope.
- Returns must be reconciled against issued/consumed quantities.
- Price/tax impacts may require customer approval.

## Data Requirements
- Entities: PartUsageEvent, WorkorderItem, SubstitutionLink, ChangeRequest
- Fields: originalProductId, substituteProductId, quantityReturned, eventType, requiresApprovalFlag

## Acceptance Criteria
- [ ] System records substitutions with traceability.
- [ ] Returns reconcile correctly without negative totals.
- [ ] Approval is triggered when substitution changes customer-visible totals (policy).
- [ ] Substituted or returned parts emit a single InventoryAdjusted event
- [ ] Adjustment references the original issued part record
- [ ] Inventory quantities reconcile correctly after adjustment
- [ ] COGS impact (if any) is reversible and auditable
- [ ] Duplicate adjustment events do not double-adjust inventory

## Integrations

### Accounting
- Emits Event: InventoryAdjusted
- Event Type: Non-posting (inventory / COGS correction)
- Source Domain: workexec
- Source Entity: WorkorderPartUsage
- Trigger: Part substitution or return after initial issue
- Idempotency Key: workorderId + originalPartId + adjustedPartId + adjustmentVersion

## Notes for Agents
Substitution is a classic variance driver—capture it cleanly for invoice explanations.


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