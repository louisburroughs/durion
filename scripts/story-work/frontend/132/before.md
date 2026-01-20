Title: [FRONTEND] [STORY] Timekeeping: Start/Stop Work Session for Assigned Work
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/132
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Timekeeping: Start/Stop Work Session for Assigned Work

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Mechanic**, I want to start/stop a work session tied to a workorder/task so that time is captured for payroll and costing.

## Details
- Work session includes mechanicId, workorderId, location/resource, start/end, breaks.
- Prevent overlap unless permitted.

## Acceptance Criteria
- Start/stop supported.
- Overlaps prevented.
- Lock after approval.

## Integrations
- Shopmgr→HR WorkSession events/API.
- Optional: Workexec consumes labor actuals.

## Data / Entities
- WorkSession, BreakSegment, ApprovalStatus

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