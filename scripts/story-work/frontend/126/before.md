Title: [FRONTEND] [STORY] Security: Define Shop Roles and Permission Matrix
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/126
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Security: Define Shop Roles and Permission Matrix

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As an **Admin**, I want to define shop roles and permissions so that only authorized staff can override schedules or approve time.

## Details
- Roles: Dispatcher, ServiceAdvisor, ShopManager, MobileLead, Mechanic.
- Permissions stored and enforced.

## Acceptance Criteria
- Configurable roles/permissions.
- Access checks enforced.
- Changes audited.

## Integrations
- Integrates with durion-hr / security identities and role assignments.

## Data / Entities
- Role, Permission, RolePermission, UserRole

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: Shop Management


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