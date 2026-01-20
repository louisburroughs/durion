Title: [FRONTEND] [STORY] Promotions: Create Promotion Offer (Basic)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/161
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotions: Create Promotion Offer (Basic)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Account Manager**, I want **to create a simple promotion (discount amount/percent) with start/end dates** so that **I can apply it to estimates for eligible customers**.

## Details
- Offer types: % off labor, % off parts, fixed amount off invoice.
- Store code, description, active dates, optional usage limit.

## Acceptance Criteria
- Create/activate/deactivate offer.
- Validate date range.
- Unique code enforced.

## Integration Points (Workorder Execution)
- Workorder Execution can query active offers for a customer.

## Data / Entities
- PromotionOffer

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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