Title: [FRONTEND] [STORY] Invoicing: Finalize and Issue Invoice
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/209
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Finalize and Issue Invoice

**Domain**: payment

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300023/Durion_Accounting_Event_Contract_v1.pdf)

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
Draft invoice is reviewed and ready to be issued.

## Main Flow
1. User reviews invoice totals and traceability links.
2. User selects 'Issue Invoice'.
3. System validates invoice completeness (customer details, taxes, totals, traceability).
4. System transitions invoice to Issued/Posted state per workflow.
5. System locks invoice lines and records issuance audit event; prepares delivery (email/print) per preference.

## Alternate / Error Flows
- Validation fails (missing billing info) → block issuance and show actionable errors.
- Invoice already issued → prevent duplicate issuance.

## Business Rules
- Issuance is a state transition with validations and locking.
- Issued invoice should be immutable except via credit/rebill (out of scope).

 ## Data Requirements
  - Entities: Invoice, Customer, AuditEvent, DeliveryPreference
  - Fields: status, issuedAt, issuedBy, deliveryMethod, emailAddress, billingAddress

## Acceptance Criteria
- [ ] Invoice can be issued only when validations pass.
- [ ] Issued invoice is locked against edits.
- [ ] Issuance is auditable and invoice is prepared for delivery.
- [ ] InvoiceIssued event is emitted exactly once per invoice version
- [ ] Event includes full line-item, tax, and total breakdown
- [ ] Accounts Receivable is created correctly
- [ ] Revenue and tax liabilities post accurately
- [ ] Duplicate or replayed events do not double-post

## Integrations

### Accounting
- Emits Event: InvoiceIssued
- Event Type: Posting
- Source Domain: workexec
- Source Entity: Invoice
- Trigger: Invoice finalized and issued
- Idempotency Key: invoiceId + invoiceVersion


## Notes for Agents
Issuance ends quote-to-cash; protect the integrity and lock the record.


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