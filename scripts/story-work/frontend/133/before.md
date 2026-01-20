Title: [FRONTEND] [STORY] Dispatch: Override Conflict with Manager Permission
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/133
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Dispatch: Override Conflict with Manager Permission

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want to override a scheduling conflict with a reason so that urgent jobs can proceed with auditability.

## Details
- Requires shopmgr.appointment.override.
- Records rationale and impacts.

## Acceptance Criteria
- Permission required.
- Reason required.
- Conflict flagged.

## Integrations
- Workexec receives override/expedite flag via context.

## Data / Entities
- OverrideRecord, PermissionCheck

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