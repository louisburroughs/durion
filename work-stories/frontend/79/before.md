Title: [FRONTEND] [STORY] Workexec: Retrieve and Display Estimates for Customer/Vehicle
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/79
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Retrieve and Display Estimates for Customer/Vehicle

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want to view estimates for a customer/vehicle so that I can move from quote to appointment or checkout.

## Details
- List open/draft/approved estimates.
- Show totals, status, and last updated.

## Acceptance Criteria
- Estimates list shown with statuses.
- Drilldown shows lines and notes.
- Links preserved.

## Integrations
- Workexec provides estimate read APIs/events.

## Data / Entities
- EstimateSummary, EstimateDetail, WorkexecRef

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

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