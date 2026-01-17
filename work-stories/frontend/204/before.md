Title: [FRONTEND] [STORY] CoA: Create and Maintain Chart of Accounts
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/204
Labels: frontend, story-implementation, general

## Frontend Implementation for Story

**Original Story**: [STORY] CoA: Create and Maintain Chart of Accounts

**Domain**: general

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Maintain Chart of Accounts and Posting Categories

## Story
CoA: Create and Maintain Chart of Accounts

## Acceptance Criteria
- [ ] Accounts support types: Asset/Liability/Equity/Revenue/Expense
- [ ] Accounts are effective-dated (activeFrom/activeThru) and audit-logged
- [ ] Duplicate account codes are blocked
- [ ] Deactivation rules are enforced per policy (e.g., balances/usage)


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