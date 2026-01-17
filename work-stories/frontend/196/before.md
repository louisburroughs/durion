Title: [FRONTEND] [STORY] AR: Apply Payment to Open Invoice(s)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/196
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AR: Apply Payment to Open Invoice(s)

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
AR: Apply Payment to Open Invoice(s)

## Acceptance Criteria
- [ ] Payment can be applied to one or more invoices (full/partial)
- [ ] GL postings: Dr Cash/Bank, Cr AR (per rules)
- [ ] Unapplied and overpayment scenarios are supported per policy
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