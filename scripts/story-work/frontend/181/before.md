Title: [FRONTEND] [STORY] Accounting: Ingest InvoiceIssued Event
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/181
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Accounting: Ingest InvoiceIssued Event

**Domain**: user

### Story Description

/kiro
Post AR, revenue, and tax liabilities from issued invoices.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Actor
Accounting System

## Trigger
Receipt of `InvoiceIssued` event from Workorder Execution

## Main Flow
1. Validate invoice payload and idempotency
2. Create Accounts Receivable entry
3. Post revenue by classification
4. Post tax liabilities by jurisdiction
5. Persist posting references

## Business Rules
- Invoice is the legal revenue trigger
- Taxes must be posted separately from revenue
- Posting must be idempotent per invoice version

## Data Requirements
- Entities: Invoice, AR, RevenueAccount, TaxLiability
- Fields: invoiceId, totals, taxBreakdown

## Acceptance Criteria
- [ ] AR balance increases correctly
- [ ] Revenue posted to correct accounts
- [ ] Tax liabilities recorded accurately
- [ ] Duplicate events do not double-post

## References
- Durion Accounting Event Contract v1

[Durion_Accounting_Event_Contract_v1.pdf](https://github.com/user-attachments/files/24299815/Durion_Accounting_Event_Contract_v1.pdf)

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