Title: [FRONTEND] [STORY] Mapping: Configure EventType → Posting Rule Set
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/202
Labels: frontend, story-implementation, inventory

## Frontend Implementation for Story

**Original Story**: [STORY] Mapping: Configure EventType → Posting Rule Set

**Domain**: inventory

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
Mapping: Configure EventType → Posting Rule Set

## Acceptance Criteria
- [ ] Posting rules are versioned and referenced on every journal entry
- [ ] Rules produce balanced debit/credit outputs for representative test fixtures
- [ ] Rules support conditional logic (taxable/non-taxable, inventory/non-inventory)
- [ ] Publishing rules that don’t balance is blocked


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