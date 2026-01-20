Title: [FRONTEND] [STORY] Adjustments: Create Manual Journal Entry with Controls
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/190
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Adjustments: Create Manual Journal Entry with Controls

**Domain**: user

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Period Close, Adjustments, and Reporting

## Story
Adjustments: Create Manual Journal Entry with Controls

## Acceptance Criteria
- [ ] Authorized users can create manual JEs with reason code
- [ ] System blocks unbalanced manual JEs
- [ ] Posted manual JEs are immutable (corrections via reversal)
- [ ] Posting respects period controls and audit requirements


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