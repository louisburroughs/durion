Title: [FRONTEND] [STORY] Promotions: Record Promotion Redemption from Invoicing
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/158
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotions: Record Promotion Redemption from Invoicing

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **System**, I want **to record when a promotion is redeemed against a finalized invoice/workorder** so that **we can track usage and prevent abuse**.

## Details
- On invoice finalization, Workorder Execution emits PromotionRedeemed.
- CRM records redemption once (idempotent) and updates counters.

## Acceptance Criteria
- Redemption recorded once.
- Usage limits enforced when configured.
- Redemption links to Workorder/Invoice reference.

## Integration Points (Workorder Execution)
- Workorder Execution emits PromotionRedeemed; CRM consumes and updates usage.

## Data / Entities
- PromotionUsage
- RedemptionEvent
- ProcessingLog

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