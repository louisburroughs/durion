Title: [FRONTEND] [STORY] Security: Define Roles and Permission Matrix for Product/Pricing
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/106
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Define Roles and Permission Matrix for Product/Pricing

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want roles/permissions for product/cost/pricing actions so that only authorized staff can change financially sensitive data.

## Details
- Roles: ProductAdmin, PricingAnalyst, StoreManager, ServiceAdvisor, IntegrationOperator.
- Permissions mapped to actions including location overrides.

## Acceptance Criteria
- Permissions enforced.
- Role changes audited.
- Least-privilege defaults.

## Integrations
- Integrates with durion-hr/security identity & role assignment.

## Data / Entities
- Role, Permission, RolePermission, UserRole, AuditLog

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