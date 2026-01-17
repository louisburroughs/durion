Title: [FRONTEND] [STORY] Security: Audit Trail for Schedule and Assignment Changes
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/125
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Audit Trail for Schedule and Assignment Changes

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want an audit history of schedule and assignment changes so that we can explain conflicts and resolve disputes.

## Details
- Record diff summary, actor, timestamp, reason codes.
- Immutable audit log.

## Acceptance Criteria
- Every change audited.
- Search by appointment/workorder/mechanic.

## Integrations
- Include workexec refs for cross-domain traceability.

## Data / Entities
- AuditLog, AuditDiffSummary

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