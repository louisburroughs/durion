Title: [FRONTEND] [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/108
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Product Admin**, I want fitment hints so that advisors can pick correct parts/tires for a vehicle.

## Details
- Basic tags: make/model/year ranges, tire size specs, axle position.
- Hints only (not full fitment engine).

## Acceptance Criteria
- Fitment tags stored and retrievable.
- Search/filter by tag.
- Audited.

## Integrations
- Workexec can pass vehicle attributes from CRM to filter candidates.

## Data / Entities
- FitmentTag, VehicleApplicabilityHint, AuditLog

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