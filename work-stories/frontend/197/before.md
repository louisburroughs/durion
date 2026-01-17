Title: [FRONTEND] [STORY] AR: Create Customer Invoice from Invoice-Issued Event
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/197
Labels: frontend, story-implementation, customer

## Frontend Implementation for Story

**Original Story**: [STORY] AR: Create Customer Invoice from Invoice-Issued Event

**Domain**: customer

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
AR: Create Customer Invoice from Invoice-Issued Event

## Acceptance Criteria
- [ ] InvoiceIssued event creates an AR invoice record with terms/due date
- [ ] GL postings: Dr AR, Cr Revenue, Cr Tax Payable (per rules)
- [ ] Traceability links invoice ↔ event ↔ journal entry
- [ ] Idempotent by invoiceId/eventId


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