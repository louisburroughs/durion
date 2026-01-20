Title: [FRONTEND] [STORY] Order: Cancel Order with Controlled Void Logic
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/82
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Order: Cancel Order with Controlled Void Logic

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Store Manager**, I want to cancel an order so that mistaken orders can be voided safely.

## Details
- If payment authorized/captured, require appropriate void/refund flow.
- Prevent cancel when work already started unless controlled.

## Acceptance Criteria
- Cancellation enforces policy.
- Proper payment reversal initiated.
- Workexec link handled (cancel link or create adjustment).

## Integrations
- Payment service integration required; workexec notified if linked.

## Data / Entities
- CancellationRecord, PaymentReversalRef, WorkexecLink, AuditLog

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