Title: [FRONTEND] [STORY] AP: Execute Payment and Post to GL
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/192
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AP: Execute Payment and Post to GL

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Payable (Bill → Payment)

## Story
AP: Execute Payment and Post to GL

## Acceptance Criteria
- [ ] Payment allocates across one or more bills (full/partial)
- [ ] GL postings: Dr AP, Cr Cash/Bank (per rules)
- [ ] Fees/unallocated amounts handled per policy
- [ ] Idempotent by paymentRef/eventId


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