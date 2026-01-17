Title: [FRONTEND] [STORY] Master: Set Product Lifecycle State (Active/Discontinued) with Effective Dates
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/119
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Master: Set Product Lifecycle State (Active/Discontinued) with Effective Dates

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want lifecycle states with effective dates so that quoting avoids unsourceable items.

## Details
- States: Active, Inactive, Discontinued, Replaced.
- Optional replacement product link.

## Acceptance Criteria
- Discontinued items blocked from new quotes unless override permission.
- Replacement suggested.
- Audited.

## Integrations
- Workexec receives lifecycle status in pricing/availability responses.

## Data / Entities
- ProductLifecycle, ReplacementLink, AuditLog

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