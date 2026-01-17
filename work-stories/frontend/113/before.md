Title: [FRONTEND] [STORY] Workexec: Handle Substitution Pricing for Part Substitutions
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/113
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Handle Substitution Pricing for Part Substitutions

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want substitutes priced when originals are unavailable so that work continues without manual math.

## Details
- Return substitute candidates with availability + prices.
- Enforce allowed substitution types.

## Acceptance Criteria
- Candidates returned with policy flags.
- Selection captured on estimate/WO line.

## Integrations
- Workexec integrates with substitution + availability queries.

## Data / Entities
- SubstituteLink, SubstitutePolicy, PriceQuoteResponse

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