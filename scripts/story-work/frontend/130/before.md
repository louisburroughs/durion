Title: [FRONTEND] [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/130
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Approve Submitted Time for a Day/Workorder

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want to approve time submissions so that time becomes locked for payroll.

## Details
- Approve/reject with reason.
- Adjustments via delta entry.

## Acceptance Criteria
- Approved locked.
- Adjustments tracked.
- Exceptions list supported.

## Integrations
- HR receives approval state and totals.

## Data / Entities
- TimeApproval, AdjustmentEntry, ExceptionFlag

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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