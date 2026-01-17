Title: [FRONTEND] [STORY] Security: Define Inventory Roles and Permission Matrix
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/87
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Define Inventory Roles and Permission Matrix

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want roles/permissions so that only authorized users can adjust stock or approve counts.

## Details
- Roles: InventoryManager, Receiver, StockClerk, MechanicPicker, Auditor.
- Least privilege defaults.

## Acceptance Criteria
- Permissions enforced.
- Role changes audited.

## Integrations
- Integrates with HR/security identity and role assignment.

## Data / Entities
- Role, Permission, RolePermission, UserRole, AuditLog

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