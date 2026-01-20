Title: [FRONTEND] [STORY] Completion: Finalize Billable Scope Snapshot
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/216
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Completion: Finalize Billable Scope Snapshot

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
Workorder is ready to be completed and prepared for invoicing.

## Main Flow
1. System compiles all authorized and completed items into a billable scope snapshot.
2. System validates quantities, pricing, taxes, and fees basis for invoicing.
3. System marks items as invoice-ready and stores snapshot version.
4. System records who initiated snapshot and when.
5. System exposes snapshot totals for back office review.

## Alternate / Error Flows
- Items completed but not authorized → block or flag for approval per policy.
- Tax configuration changes since estimate → store variance and require review.

## Business Rules
- Invoice derives from billable scope snapshot, not from live mutable items.
- Snapshot must be versioned and auditable.

## Data Requirements
- Entities: BillableScopeSnapshot, WorkorderItem, TaxSnapshot
- Fields: snapshotVersion, invoiceReadyFlag, taxAmount, feeTotal, grandTotal, varianceReason

## Acceptance Criteria
- [ ] System creates a billable scope snapshot that matches completed authorized work.
- [ ] Snapshot is versioned and retrievable.
- [ ] Items are marked invoice-ready.

## Notes for Agents
Snapshot is your source of truth for invoicing; do not compute invoices off mutable live items.


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