Title: [FRONTEND] [STORY] Appointment: Create Appointment from Estimate or Order
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/76
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Appointment: Create Appointment from Estimate or Order

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Scheduler**, I want to create an appointment from an estimate/order so that the work is scheduled and resources assigned.

## Details
- Appointment includes preferred times, service duration, skill tags, bay/mobile requirements.
- Linked to estimate/workorder if applicable.

## Acceptance Criteria
- Appointment created with confirmation.
- Linked refs stored.
- Conflicts surfaced.

## Integrations
- Shopmgr appointment create API; workexec duration hints (optional).

## Data / Entities
- AppointmentRequest, AppointmentRef, ConflictResult

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