Title: [FRONTEND] [STORY] Order: Apply Price Override with Permission and Reason
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/84
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Order: Apply Price Override with Permission and Reason

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want to override a line price with reason and permission so that I can resolve exceptions while staying compliant.

## Details
- Override requires role/permission.
- Capture reason code and optional manager approval.

## Acceptance Criteria
- Override blocked without permission.
- Override recorded with who/why.
- Reporting includes override usage.

## Integrations
- Pricing service returns baseline; override stored as adjustment.

## Data / Entities
- PriceOverride, ApprovalRecord, AuditLog

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