Title: [FRONTEND] [STORY] CrossDomain: HR Ingests Work Sessions from Shopmgr
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/122
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] CrossDomain: HR Ingests Work Sessions from Shopmgr

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Payroll Clerk**, I want HR to receive work sessions from shopmgr so that payroll and compliance reporting can be produced.

## Details
- HR stores sessions with approval status.
- Reject/adjust supported.

## Acceptance Criteria
- Ingest idempotent.
- Visible for payroll.
- Approval tracked.

## Integrations
- Shopmgr→HR WorkSession events/API; optional HR→Shopmgr approval updates.

## Data / Entities
- TimekeepingEntry (hr domain)

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