Title: [FRONTEND] [STORY] Customer: Load Customer + Vehicle Context and Billing Rules
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/68
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Customer: Load Customer + Vehicle Context and Billing Rules

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want a customer snapshot so that order creation respects billing rules and contacts.

## Details
- Show account, individuals, contacts, preferred contact method.
- Show vehicles and VIN/description.
- Show billing rules (PO required, tax exemption, terms).

## Acceptance Criteria
- Snapshot loads for selected customer.
- Billing rule enforcement hooks present.
- Cached/updated appropriately.

## Integrations
- CRM provides snapshot API; workexec can use same refs.

## Data / Entities
- CustomerSnapshot, VehicleRef, BillingRuleRef

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