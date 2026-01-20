Title: [FRONTEND] [STORY] Payment: Print/Email Receipt and Store Reference
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/71
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Payment: Print/Email Receipt and Store Reference

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Cashier**, I want receipts so that customers have proof of payment.

## Details
- Receipt includes invoice ref, auth code, timestamp, transaction refs.
- Email receipt optional.

## Acceptance Criteria
- Receipt produced on successful capture.
- Receipt ref stored.
- Reprint supported.

## Integrations
- Payment service returns receipt data; CRM provides email contact (optional).

## Data / Entities
- Receipt, ReceiptDelivery, ContactRef

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