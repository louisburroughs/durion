Title: [FRONTEND] [STORY] Accounting: Update Invoice Payment Status from Payment Outcomes
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/70
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Update Invoice Payment Status from Payment Outcomes

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As the **POS system**, I want to update invoice payment status so that customer balances are accurate.

## Details
- Map payment outcomes to invoice statuses: Paid/PartiallyPaid/Unpaid/Failed.
- Include transaction refs.
- Idempotent updates.

## Acceptance Criteria
- Status updates emitted for accounting.
- Retries and idempotency supported.
- UI reflects latest status.

## Integrations
- POS emits PaymentApplied events; accounting responds with posting confirmation events.

## Data / Entities
- PaymentAppliedEvent, InvoiceStatusView, IdempotencyKey

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

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