Title: [FRONTEND] [STORY] Allocations: Handle Shortages with Backorder or Substitution Suggestion
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/89
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Allocations: Handle Shortages with Backorder or Substitution Suggestion

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want shortage handling so that work proceeds with backorders or approved substitutes.

## Details
- If ATP insufficient: propose external availability or substitute options.
- Link to product substitution rules.

## Acceptance Criteria
- Shortage flagged.
- Suggested actions returned.
- Decision captured and auditable.

## Integrations
- Product provides substitution/pricing; Positivity provides external availability; workexec updates estimate/WO.

## Data / Entities
- ShortageFlag, SubstituteSuggestion, ExternalAvailabilityRef

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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