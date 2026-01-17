Title: [FRONTEND] [STORY] Workexec: Display Invoice and Request Finalization (Controlled)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/77
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Workexec: Display Invoice and Request Finalization (Controlled)

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want to display invoice details and request finalization so that we can proceed to payment.

## Details
- Show invoice items, taxes/fees, totals.
- If invoice not finalized, request finalization workflow (controlled).

## Acceptance Criteria
- Invoice view consistent with workexec.
- Finalize request requires permission.
- Resulting status shown.

## Integrations
- Workexec invoice APIs; accounting status follows after posting.

## Data / Entities
- InvoiceView, FinalizeRequest, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

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