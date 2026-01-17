Title: [FRONTEND] [STORY] Locations: Create and Maintain Shop Locations
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/142
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Locations: Create and Maintain Shop Locations

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to create/edit shop locations with address, hours, and timezone so that appointments and scheduling rules are correct per site.

## Details
- Store location name/address/timezone/operating hours/holiday closures.
- Defaults: check-in and cleanup buffers.

## Acceptance Criteria
- Create/update/deactivate location.
- Hours/timezone validated.
- Changes audited.

## Integrations
- Workexec stores locationId on Estimate/WO/Invoice context.
- HR availability can be filtered by location affiliation.

## Data / Entities
- Location, OperatingHours, AuditLog

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