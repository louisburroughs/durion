Title: [FRONTEND] [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/194
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] AP: Create Vendor Bill from Purchasing/Receiving Event

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
AP: Create Vendor Bill from Purchasing/Receiving Event

## Acceptance Criteria
- [ ] VendorInvoiceReceived (or GoodsReceived) event creates an AP bill with PO/receipt refs
- [ ] GL postings: Dr Expense/Inventory, Cr AP (per rules)
- [ ] Traceability links bill ↔ event ↔ journal entry
- [ ] Idempotent by vendorInvoiceRef/eventId


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