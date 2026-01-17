Title: [FRONTEND] [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/160
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotions: Define Eligibility Rules (Account/Vehicle)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Account Manager**, I want **to restrict offers to certain accounts or vehicle categories** so that **promotions are applied correctly**.

## Details
- Eligibility: specific accounts, simple tags (e.g., trailer/tractor), optional fleet-size threshold.
- Return eligibility decision with reason.

## Acceptance Criteria
- Configure eligibility.
- Evaluate eligibility with explanation.

## Integration Points (Workorder Execution)
- Workorder Execution calls eligibility evaluation during estimate pricing.

## Data / Entities
- PromotionEligibilityRule

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