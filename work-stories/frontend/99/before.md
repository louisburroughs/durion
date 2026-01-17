Title: [FRONTEND] [STORY] Receiving: Create Receiving Session from PO/ASN
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/99
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Receiving: Create Receiving Session from PO/ASN

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Receiver**, I want to create a receiving session from a PO/ASN so that inbound items can be checked in.

## Details
- Session includes supplier/distributor, shipment ref, expected lines.
- Support scanning barcodes and capturing lot/serial (optional v1).

## Acceptance Criteria
- Receiving session created.
- Lines can be matched and variances captured.
- Session auditable.

## Integrations
- Positivity may provide ASN; product master maps items.

## Data / Entities
- ReceivingSession, ReceivingLine, SupplierRef, VarianceRecord

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Inventory Management


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