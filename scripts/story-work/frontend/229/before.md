Title: [FRONTEND] [STORY] Promotion: Generate Workorder Items from Approved Scope
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/229
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotion: Generate Workorder Items from Approved Scope

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
System

## Trigger
A workorder header is created from an approved estimate.

## Main Flow
1. System iterates through approved scope line items.
2. System creates Workorder Items for parts and labor with stable identifiers.
3. System copies pricing/tax snapshot fields needed for downstream invoicing and variance explanations.
4. System marks items as 'Authorized' and sets execution flags (e.g., required vs optional).
5. System validates totals and quantity integrity.

## Alternate / Error Flows
- Approved scope contains an item no longer valid in catalog → allow as snapshot item and flag for review.
- Tax configuration missing → block promotion and require correction.

## Business Rules
- Only approved items are created on the workorder.
- Snapshot pricing/tax fields are preserved.
- Workorder items maintain traceability to estimate items.

## Data Requirements
- Entities: WorkorderItem, ApprovedScope, EstimateItem, TaxSnapshot
- Fields: itemSeqId, originEstimateItemId, authorizedFlag, quantity, unitPrice, taxCode, taxAmount, snapshotVersion

## Acceptance Criteria
- [ ] Workorder items match approved scope in quantity and pricing.
- [ ] Workorder items carry traceability back to estimate items.
- [ ] Promotion fails if required tax basis is missing (policy).

## Notes for Agents
Design for variance explanations later: preserve the numbers, not just totals.


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