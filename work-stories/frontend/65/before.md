Title: [FRONTEND] [STORY] Security: Audit Trail for Price Overrides, Refunds, and Cancellations
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/65
Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Audit Trail for Price Overrides, Refunds, and Cancellations

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As an **Auditor**, I want an audit trail so that financial exceptions are explainable.

## Details
- Record who/when/why for overrides/refunds/cancels.
- Exportable report.

## Acceptance Criteria
- Audit entries append-only.
- Drilldown by order/invoice.
- Payment refs included.

## Integrations
- Accounting and payment references included.

## Data / Entities
- AuditLog, ExceptionReport, ReferenceIndex

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