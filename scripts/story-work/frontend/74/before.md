Title: [FRONTEND] [STORY] Appointment: Show Assignment (Location/Bay/Mobile + Mechanic)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/74
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Appointment: Show Assignment (Location/Bay/Mobile + Mechanic)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want to see assigned resources so that I can set expectations.

## Details
- Display assigned bay/mobile unit and mechanic.
- Show notes (optional).

## Acceptance Criteria
- Assignment displayed.
- Updates reflect changes.
- Read-only for most roles.

## Integrations
- Shopmgr provides assignment data; HR provides mechanic identity.

## Data / Entities
- AssignmentView, LocationRef, MechanicRef

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