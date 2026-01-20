Title: [FRONTEND] [STORY] Invoicing: Support Authorized Invoice Adjustments
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/210
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Invoicing: Support Authorized Invoice Adjustments

**Domain**: user

### Story Description

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24300028/Durion_Accounting_Event_Contract_v1.pdf)

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Back Office Manager

## Trigger
A Draft invoice requires an adjustment (goodwill discount, correction).

## Main Flow
1. Authorized user edits invoice line items or applies a discount.
2. System requires a reason code and free-text justification if configured.
3. System recalculates taxes and totals.
4. System records adjustment audit event including before/after values.
5. System flags invoice as adjusted for reporting.

## Alternate / Error Flows
- Unauthorized user attempts adjustment → block.
- Adjustment would cause negative totals → block or require special permission.

## Business Rules
- Adjustments require permissions and audit trail.
- Adjustments must not break traceability; they must be explainable.

## Data Requirements
- Entities: Invoice, InvoiceItem, AuditEvent, ReasonCode
- Fields: adjustmentType, reasonCode, justification, beforeTotal, afterTotal, adjustedBy, adjustedAt

## Acceptance Criteria
- [ ] Only authorized roles can adjust invoices.
- [ ] Adjustments require reason codes and are auditable.
- [ ] Totals are recalculated correctly after adjustments.
- [ ] Invoice adjustments emit a corresponding accounting event
- [ ] Revenue, tax, and AR are adjusted correctly
- [ ] Adjustments reference the original invoice
- [ ] Authorization and reason code are required
- [ ] Multiple adjustments do not corrupt invoice totals

## Integrations

### Accounting
- Emits Event: InvoiceAdjusted or CreditMemoIssued
- Event Type: Posting (reversal / amendment)
- Source Domain: workexec
- Source Entity: Invoice
- Trigger: Authorized adjustment or credit issuance
- Idempotency Key: invoiceId + adjustmentVersion


## Notes for Agents
Keep adjustments rare and transparent; otherwise you erode trust in the system.


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