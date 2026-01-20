Title: [FRONTEND] [STORY] Payment: Initiate Card Authorization and Capture
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/73
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Payment: Initiate Card Authorization and Capture

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Cashier**, I want to accept card payments so that invoices can be paid at checkout.

## Details
- Support auth then capture (or sale/capture).
- Store only tokens + transaction IDs.
- Produce receipt.

## Acceptance Criteria
- Auth/capture outcomes handled.
- Receipt produced.
- No card data stored.
- Audit recorded.

## Integrations
- Payment service API integration; accounting notified after success.

## Data / Entities
- PaymentIntent, PaymentTransactionRef, Receipt, AuditLog

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