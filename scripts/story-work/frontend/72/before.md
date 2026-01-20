Title: [FRONTEND] [STORY] Payment: Void Authorization or Refund Captured Payment
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/72
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Payment: Void Authorization or Refund Captured Payment

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Store Manager**, I want to void/refund payments so that corrections are handled safely.

## Details
- Voids before settlement; refunds after settlement.
- Requires permission and reason.
- Partial refunds supported (basic).

## Acceptance Criteria
- Void/refund returns success or actionable error.
- Invoice status updated.
- Audit includes reason/approver.

## Integrations
- Payment service API; accounting status updated accordingly.

## Data / Entities
- RefundRecord, VoidRecord, PaymentTransactionRef, AuditLog

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