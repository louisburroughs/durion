Title: [FRONTEND] [STORY] GL: Support Accrual vs Cash Basis Modes
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/199
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] GL: Support Accrual vs Cash Basis Modes

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Post Journal Entries to the General Ledger

## Story
GL: Support Accrual vs Cash Basis Modes

## Acceptance Criteria
- [ ] Business unit can be configured as accrual or cash basis
- [ ] Accrual: invoices post AR/AP and payments clear AR/AP
- [ ] Cash basis behavior is policy-defined and consistent across posting/reporting
- [ ] Basis changes are audited and permission-controlled


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