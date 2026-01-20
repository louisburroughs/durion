Title: [FRONTEND] [STORY] Workexec: Create Draft Estimate from Appointment
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/129
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Create Draft Estimate from Appointment

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want to create a draft estimate in workexec from an appointment so that quote-to-cash starts with operational context.

## Details
- Appointment provides customerId, vehicleId, location/resource, requested services.
- Workexec returns estimateId and shopmgr stores linkage.

## Acceptance Criteria
- Estimate created and linked.
- Idempotent retry safe.

## Integrations
- Shopmgr→Workexec CreateEstimateFromAppointment; Workexec→Shopmgr EstimateCreated.

## Data / Entities
- WorkexecLink(appointmentId→estimateId), CommandEnvelope

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