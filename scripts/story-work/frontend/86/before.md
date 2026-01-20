Title: [FRONTEND] [STORY] Security: Immutable Audit Trail for Movements and Workorder Links
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/86
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Immutable Audit Trail for Movements and Workorder Links

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Auditor**, I want immutable audit trails for movements and workorder links so that disputes can be resolved.

## Details
- Append-only logs for move details and references.
- Drilldown by workorder/product/location/user.

## Acceptance Criteria
- Audit append-only.
- Drilldown supported.
- Exportable.

## Integrations
- Workexec uses ledger references for traceability.

## Data / Entities
- AuditLog, MovementReferenceIndex, WorkorderRef

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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