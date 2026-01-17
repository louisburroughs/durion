Title: [FRONTEND] [STORY] Timekeeping: Capture Mobile Travel Time Separately
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/131
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Capture Mobile Travel Time Separately

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mobile Lead**, I want to record travel time for a mobile appointment so that availability and payroll are accurate.

## Details
- Travel segments depart/arrive/return.
- Policies may auto-apply buffers.

## Acceptance Criteria
- Segments recorded.
- Sent to HR.
- Availability blocked during travel.

## Integrations
- Shopmgr→HR TravelTime events/API.

## Data / Entities
- TravelSegment, MobileAssignmentRef

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