Title: [FRONTEND] [STORY] Scheduling: Reschedule or Cancel Appointment with Audit
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/137
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Scheduling: Reschedule or Cancel Appointment with Audit

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Dispatcher**, I want to reschedule or cancel an appointment so that the plan stays accurate.

## Details
- Reschedule: change window/resource; Cancel: reason.
- Notify workexec if linked to estimate/WO.

## Acceptance Criteria
- Reschedule updates schedule.
- Cancel sets status+reason.
- Changes audited.

## Integrations
- Emit AppointmentUpdated/Cancelled to workexec when linked.

## Data / Entities
- Appointment, AppointmentAudit, WorkexecLinkRef

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