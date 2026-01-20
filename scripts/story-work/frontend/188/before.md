Title: [FRONTEND] [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/188
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] Audit: Maintain Immutable Ledger Audit Trail and Explainability

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Reconciliation, Audit, and Controls

## Story
Audit: Maintain Immutable Ledger Audit Trail and Explainability

## Acceptance Criteria
- [ ] Ledger lines and JEs are immutable once posted (corrections via reversal)
- [ ] Store rule version and mapping version used for each posting
- [ ] Provide explainability view: event → mapping → rules → JE → ledger lines
- [ ] Full traceability from any GL line to source event/business document


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