Title: [FRONTEND] [STORY] Vehicle: Create Vehicle Record with VIN and Description
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/169
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Create Vehicle Record with VIN and Description

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to create a vehicle record with VIN, unit number, and description** so that **future service and billing can be linked to the correct vehicle**.

## Details
- Capture VIN, make/model/year (free text initially), unit number, license plate (optional).
- Minimal VIN format validation; external decode optional stub.

## Acceptance Criteria
- Can create vehicle with VIN.
- Vehicle has stable Vehicle ID.
- VIN uniqueness rule defined (global or per account).

## Integration Points (Workorder Execution)
- Workorder Execution selects vehicle for estimate/workorder; Vehicle ID stored on workorder.

## Data / Entities
- Vehicle
- VehicleIdentifier (VIN, unit, plate)

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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