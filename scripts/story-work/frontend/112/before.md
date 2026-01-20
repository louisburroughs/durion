Title: [FRONTEND] [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/112
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Availability: Expose On-hand and Available-to-Promise by Location (from Inventory)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want on-hand/ATP by location so that I can quote with realistic fulfillment expectations.

## Details
- Query inventory for on-hand and ATP.
- Optional reservations and expected replenishment.

## Acceptance Criteria
- Availability API returns quantities by location.
- Consistent productId mapping.
- SLA for estimate UI.

## Integrations
- Product → Inventory availability query; Inventory → Product responses/events.

## Data / Entities
- AvailabilityView, LocationQty, ReservationSummary

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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