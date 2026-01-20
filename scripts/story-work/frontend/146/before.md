Title: [FRONTEND] [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/146
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Integration: Start/Stop Timer Against Assigned Workorder Task

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Mechanic**, I want **to start and stop a job timer for a workorder task** so that **I can accurately capture job time without manual calculations**.

## Details
- Timer references workOrderId and optional workOrderItemId/laborCode.
- Enforce one active timer per mechanic (default).

## Acceptance Criteria
- Start/stop timer produces a job time entry.
- Prevent multiple active timers unless configured.
- Audited.

## Integration Points (workexec)
- Inbound: WorkOrderAssigned for context.

## Data / Entities
- TimeEntry (job)
- JobLink

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: People Management


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