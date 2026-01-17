Title: [FRONTEND] [STORY] StorePrice: Set Location Store Price Override within Guardrails
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/116
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] StorePrice: Set Location Store Price Override within Guardrails

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Store Manager**, I want to set store price overrides so that I can compete locally within policy.

## Details
- Override layered over base price.
- Guardrails: min margin %, max discount %, approval thresholds.

## Acceptance Criteria
- Override created/updated.
- Guardrails enforced with approvals.
- Audited.

## Integrations
- Workexec receives store price for that location; reporting tracks override usage.

## Data / Entities
- LocationPriceOverride, GuardrailPolicy, ApprovalRecord, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Product / Parts Management


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