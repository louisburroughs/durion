Title: [FRONTEND] [STORY] Accounting: Apply Payment to Invoice
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/178
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Apply Payment to Invoice

**Domain**: payment

### Story Description

/kiro
Apply payments to invoices with clear AR reconciliation.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
User action or automated rule to apply a recorded payment to one or more invoices

## Main Flow
1. Select payment and target open invoice(s)
2. Validate invoice status and remaining balance
3. Apply payment amount to invoice(s)
4. Reduce Accounts Receivable balance accordingly
5. Update invoice payment status (partial/paid)
6. Persist application records

## Alternate / Error Flows
- Overpayment → create credit balance
- Invoice closed or voided → block application
- Partial application across multiple invoices

## Business Rules
- AR reduction occurs only when payment is applied
- One payment may apply to multiple invoices
- One invoice may have multiple payments
- Application must be reversible with audit

## Data Requirements
- Entities: PaymentApplication, Invoice, AR
- Fields: appliedAmount, invoiceId, applicationTimestamp

## Acceptance Criteria
- [ ] AR balance reduces correctly
- [ ] Invoice status updates accurately
- [ ] Partial payments are supported
- [ ] Application is auditable and reversible

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