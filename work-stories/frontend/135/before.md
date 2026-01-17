Title: [FRONTEND] [STORY] Dispatch: Determine Mechanic Availability for a Time Window
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/135
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Determine Mechanic Availability for a Time Window

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to see which mechanics are available for a time window so that I can assign work without double-booking.

## Details
- Availability includes shifts/PTO (HR), existing assignments, mobile travel blocks.
- Explainability via reason codes.

## Acceptance Criteria
- API returns availability + reasons.
- Conflicts detected.

## Integrations
- Shopmgr queries HR availability endpoint or consumes AvailabilityChanged events.

## Data / Entities
- AvailabilityQuery, AvailabilityResult

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