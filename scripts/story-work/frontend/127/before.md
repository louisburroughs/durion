Title: [FRONTEND] [STORY] Workexec: Update Appointment Status from Workexec Events
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/127
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Update Appointment Status from Workexec Events

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **System**, I want appointments to reflect workexec status so that dispatch has real-time visibility.

## Details
- Map workexec states to appointment states.
- Handle reopen as exception.

## Acceptance Criteria
- Status updates idempotent.
- Reopen flagged.
- Timeline stored.

## Integrations
- Workexec→Shopmgr WorkorderStatusChanged/InvoiceIssued events.

## Data / Entities
- AppointmentStatus, StatusTimeline, ExceptionFlag

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