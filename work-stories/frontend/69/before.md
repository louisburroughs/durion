Title: [FRONTEND] [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/69
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Reconcile POS Status with Accounting Authoritative Status

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Cashier**, I want POS to reflect accounting’s authoritative status so that disputes are minimized.

## Details
- Show “pending posting” vs “posted.”
- Provide drilldown refs.

## Acceptance Criteria
- Accounting status overrides local state.
- Pending/posted states clear.
- Audit of reconciliation.

## Integrations
- Accounting emits InvoiceStatusChanged/PostingConfirmed events.

## Data / Entities
- PostingConfirmation, InvoiceStatusChangedEvent, AuditLog

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