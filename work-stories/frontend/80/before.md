Title: [FRONTEND] [STORY] Catalog: View Product Details with Price and Availability Signals
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/80
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Catalog: View Product Details with Price and Availability Signals

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want product detail views so that I can explain options and availability to customers.

## Details
- Show description, specs, MSRP, store price, substitutions hints.
- Show on-hand/ATP by location and external lead-time hints (optional).

## Acceptance Criteria
- Detail view loads reliably.
- Price and availability reflect selected location.
- Substitution suggestions available when configured.

## Integrations
- Product/pricing + inventory availability endpoints.

## Data / Entities
- ProductDetailView, AvailabilityView, SubstituteHint

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