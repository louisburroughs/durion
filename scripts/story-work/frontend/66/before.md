Title: [FRONTEND] [STORY] Security: Define POS Roles and Permission Matrix
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/66
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Define POS Roles and Permission Matrix

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As an **Admin**, I want roles and permissions so that sensitive actions (refunds, overrides) are controlled.

## Details
- Roles mapped to permissions.
- Least privilege defaults.

## Acceptance Criteria
- Protected operations enforce permissions.
- Role changes audited.

## Integrations
- Integrates with HR/security identity/roles.

## Data / Entities
- Role, Permission, RolePermission, UserRole, AuditLog

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