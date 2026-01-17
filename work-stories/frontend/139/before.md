Title: [FRONTEND] [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/139
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Scheduling: Create Appointment with CRM Customer and Vehicle

**Domain**: payment

### Story Description

/kiro
# User Story
## Narrative
As a **Service Advisor**, I want to create an appointment selecting customer and vehicle from durion-crm so that the shop has accurate context for service and billing.

## Details
- Capture: crmCustomerId, crmVehicleId, requested services, notes, preferred time window, contact hints.

## Acceptance Criteria
- Appointment created with status Draft/Scheduled.
- Customer/vehicle references validated.
- Audited.

## Integrations
- CRM lookup/snapshot.
- Optional AppointmentCreated event.

## Data / Entities
- Appointment, AppointmentServiceRequest, CRM references

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