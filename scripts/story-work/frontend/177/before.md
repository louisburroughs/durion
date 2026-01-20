Title: [FRONTEND] [STORY] Accounting: Handle Refund Issued
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/177
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Handle Refund Issued

**Domain**: payment

### Story Description

/kiro
Reverse cash and revenue effects with full traceability.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `RefundIssued` event or authorized refund action

## Main Flow
1. Validate refund authorization and reference
2. Identify original payment and/or invoice
3. Reduce cash/bank balance
4. Adjust AR and/or revenue as appropriate
5. Record refund transaction with reason code
6. Persist linkage to original payment/invoice

## Alternate / Error Flows
- Refund exceeds original payment → block
- Partial refund → supported
- Refund against already credited invoice

## Business Rules
- Refunds must reference an original transaction
- Revenue impact depends on refund reason (pricing error vs goodwill)
- Refunds require explicit authorization

## Data Requirements
- Entities: Refund, Payment, Invoice
- Fields: refundAmount, reasonCode, originalTxnRef

## Acceptance Criteria
- [ ] Cash/bank balance reduces correctly
- [ ] AR and/or revenue adjust appropriately
- [ ] Refund is traceable to original transaction
- [ ] Audit trail captures reason and authorizer

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