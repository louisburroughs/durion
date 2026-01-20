Title: [FRONTEND] [STORY] Order: Create Sales Order Cart and Add Items
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/85
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Order: Create Sales Order Cart and Add Items

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **POS Clerk**, I want to create a cart and add products/services so that I can quote and sell efficiently at the counter.

## Details
- Create cart for a customer/vehicle context.
- Add items by SKU/service code; set quantities.
- Support linking to an existing estimate/workorder as the source of items (optional).

## Acceptance Criteria
- Cart created with unique orderId.
- Items can be added/updated/removed.
- Totals recalc deterministically.
- Audit changes.

## Integrations
- Pull product pricing from product/pricing service; optionally check availability from inventory.

## Data / Entities
- PosOrder, PosOrderLine, PriceQuote, TaxQuote (hook), AuditLog

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