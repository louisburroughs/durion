Title: [FRONTEND] [STORY] Billing: Enforce PO Requirement During Estimate Approval
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/162
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Billing: Enforce PO Requirement During Estimate Approval

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want **the system to require a PO when account rules mandate it** so that **billing exceptions are reduced**.

## Details
- PO required triggers validation in Estimate approval step.
- Capture PO number and optional attachment reference.

## Acceptance Criteria
- Approval blocked without PO when required.
- PO stored and visible on invoice.

## Integration Points (Workorder Execution)
- Workorder Execution checks CRM billing rules and enforces PO before approval.

## Data / Entities
- PurchaseOrderRef (WO domain)
- CRM BillingRule reference

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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