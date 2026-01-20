Title: [FRONTEND] [STORY] Vehicle: Store Vehicle Care Preferences
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/166
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Vehicle: Store Vehicle Care Preferences

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **to record vehicle-specific care preferences and service notes** so that **the shop can deliver service aligned with customer expectations**.

## Details
- Preferences: preferred tire brand/line, rotation interval, alignment preference, torque spec notes.
- Structured key/values + free-form notes.

## Acceptance Criteria
- Add/update preferences.
- Preferences visible on estimate/workorder.
- Changes audited.

## Integration Points (Workorder Execution)
- Workorder Execution displays preferences at estimate and during execution.

## Data / Entities
- VehiclePreference
- VehicleNote

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