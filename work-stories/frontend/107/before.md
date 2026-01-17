Title: [FRONTEND] [STORY] Rules: Enforce Location Restrictions and Service Rules for Products
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/107
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Rules: Enforce Location Restrictions and Service Rules for Products

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want restriction rules so that unsafe or incompatible items are not sold/installed.

## Details
- Block based on location tags or service type.
- Override requires permission + rationale.

## Acceptance Criteria
- Restrictions enforced in pricing/quote APIs.
- Override permission required.
- Decision recorded in trace.

## Integrations
- Workexec receives warnings/errors; shopmgr provides service context tags (optional).

## Data / Entities
- RestrictionRule, OverrideRecord, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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