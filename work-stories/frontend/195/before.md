Title: [FRONTEND] [STORY] AR: Issue Credit Memo / Refund with Traceability
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/195
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AR: Issue Credit Memo / Refund with Traceability

**Domain**: payment

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Accounts Receivable (Invoice → Cash Application)

## Story
AR: Issue Credit Memo / Refund with Traceability

## Acceptance Criteria
- [ ] Credit memo references original invoice and offsets balances
- [ ] GL postings reverse revenue/tax and reduce AR (or drive refund payment)
- [ ] Reason code required and actions audited
- [ ] Period-close policies handled (adjusting entries if needed)


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