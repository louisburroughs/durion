Title: [FRONTEND] [STORY] Accounting: Ingest PaymentReceived Event
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/179
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest PaymentReceived Event

**Domain**: payment

### Story Description

/kiro
Focus on cash recognition, AR reduction, and idempotency.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `PaymentReceived` event from an external payment source
(e.g., POS terminal, bank feed, payment processor, manual entry)

## Main Flow
1. Receive payment event with amount, currency, method, and reference(s)
2. Validate event schema and idempotency key
3. Identify target customer and candidate open invoices
4. Record cash receipt in appropriate cash/bank account
5. Create unapplied payment record or proceed to invoice application
6. Persist payment with full source metadata

## Alternate / Error Flows
- Duplicate event → ignore (idempotent)
- Unknown customer or reference → create unapplied payment
- Currency mismatch → reject or flag for review
- Posting failure → retry or dead-letter

## Business Rules
- Payment receipt reduces cash suspense or increases cash immediately
- Payment does not reduce AR until applied to invoice(s)
- Idempotency is enforced per external transaction reference

## Data Requirements
- Entities: Payment, CashAccount, Customer
- Fields: amount, currency, method, receivedTimestamp, externalTxnId

## Acceptance Criteria
- [ ] Cash/bank balance increases correctly
- [ ] Payment is recorded exactly once
- [ ] Unapplied payments are visible and traceable
- [ ] Payment references external source transaction

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