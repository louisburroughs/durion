Title: [FRONTEND] [STORY] Events: Define Canonical Accounting Event Envelope
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/208
Labels: frontend, story-implementation, order

## Frontend Implementation for Story

**Original Story**: [STORY] Events: Define Canonical Accounting Event Envelope

**Domain**: order

### Story Description

/kiro
# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: accounting

## Capability
[CAP] Ingest Accounting Events (Cross-Module)

## Story
Events: Define Canonical Accounting Event Envelope

## Acceptance Criteria
- [ ] Event envelope includes eventId, eventType, sourceModule, sourceEntityRef, occurredAt, businessUnitId, currencyUomId, lines[], tax[], metadata, schemaVersion
- [ ] Supports multi-line totals (parts/labor/fees/discount/tax)
- [ ] Schema is versioned and published as a contract
- [ ] Traceability fields exist (workorderId, invoiceId, poId, receiptId, etc. where applicable)


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