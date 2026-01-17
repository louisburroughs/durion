Title: [FRONTEND] [STORY] Invoicing: Generate Invoice Draft from Completed Workorder
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/213
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Generate Invoice Draft from Completed Workorder

**Domain**: payment

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office / Accounts Receivable Clerk

## Trigger
A workorder is Completed and invoice-ready.

## Main Flow
1. User selects 'Create Invoice' on the completed workorder.
2. System creates a Draft invoice using the billable scope snapshot.
3. System carries over customer billing details and references (PO number, terms).
4. System populates invoice line items and initial totals.
5. System links invoice to workorder, estimate version, and approval trail.

## Alternate / Error Flows
- Workorder not invoice-ready → block and show missing prerequisites.

## Business Rules
- Invoices are created from the billable scope snapshot.
- Traceability links are required.

## Data Requirements
- Entities: Invoice, InvoiceItem, BillableScopeSnapshot, Workorder, ApprovalRecord
- Fields: invoiceId, status, snapshotVersion, workorderId, estimateId, approvalId, termsId, poNumber

## Acceptance Criteria
- [ ] System creates a Draft invoice with all billable items present.
- [ ] Invoice references workorder and upstream approval trail.
- [ ] Invoice totals are populated.

## Notes for Agents
Keep invoice generation deterministic; the snapshot is the single source of truth.


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