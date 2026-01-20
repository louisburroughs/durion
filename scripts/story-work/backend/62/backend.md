Title: [BACKEND] [STORY] Security: Define Shop Roles and Permission Matrix
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/62
Labels: backend, story-implementation, user, type:story, domain:security, status:ready-for-dev

## 🏷️ Labels (Proposed)
### Required
- type:story
- domain:security
- status:draft

### Recommended
- agent:security
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** security-strict
## Story Intent
**As a** System Administrator,
**I want to** define a flexible Role-Based Access Control (RBAC) system with configurable roles and permissions,
**So that** I can enforce granular access control for sensitive operations within the shop, ensuring only authorized personnel can perform actions like overriding schedules or approving time.

This story establishes the core framework for RBAC, including the management of roles, the assignment of permissions to those roles, and the association of roles with users.

## Actors & Stakeholders
- **System Administrator**: The primary actor responsible for configuring and managing roles and permissions.
- **Application User**: Any user of the POS system (e.g., Service Advisor, Mechanic) whose actions are subject to permission checks.
- **System**: The application backend responsible for enforcing access control checks based on the configured RBAC policies.
- **Auditor**: A stakeholder who requires a verifiable log of all changes to security configurations (roles, permissions, assignments).

## Preconditions
1.  A source of truth for user identities (e.g., `durion-hr` or another identity provider) is established and accessible, providing a unique `user_id` for each person.
2.  The System Administrator is authenticated and possesses the necessary meta-permissions to manage the RBAC system itself.
3.  A canonical list of all securable actions (Permissions) within the application has been identified (e.g., `SCHEDULE_OVERRIDE`, `TIME_APPROVAL`, `INVOICE_DELETE`).

## Functional Behavior
1.  **Role Management (CRUD)**:
    -   The System Administrator can create a new Role by providing a unique name and description (e.g., "ShopManager", "ServiceAdvisor").
    -   The System Administrator can view a list of all existing Roles.
    -   The System Administrator can update the name and description of an existing Role.
    -   The System Administrator can delete a Role. Deleting a role also removes all associated user-role and role-permission assignments.

2.  **Permission Assignment**:
    -   When creating or editing a Role, the System Administrator can assign one or more predefined Permissions to it.
    -   The System Administrator can remove one or more Permissions from a Role.

3.  **User Role Assignment**:
    -   The System Administrator can assign one or more Roles to a specific User.
    -   The System Administrator can remove one or more Roles from a specific User.

4.  **Access Enforcement**:
    -   The System must provide a mechanism to check if a given user has a specific permission.
    -   This check will determine the user's assigned roles and see if any of those roles contain the required permission. The check returns `true` if the permission is granted and `false` otherwise.

## Alternate / Error Flows
- **Duplicate Role Creation**: If the Administrator attempts to create a role with a name that already exists, the system must reject the request with a clear error message.
- **Assigning Non-Existent Permission**: If an attempt is made to assign a permission key that does not exist in the system's canonical list, the operation must fail.
- **Access Denied**: When an Application User attempts an action for which they lack the required permission, the system must block the action and return an "Access Denied" or "Forbidden" status. This denial should be logged for security monitoring.
- **Unauthorized Management**: If a non-administrator user attempts to access the role management functions, the system must deny access.

## Business Rules
- Role names must be unique within the system and are case-insensitive for uniqueness checks (e.g., "shopmanager" is the same as "ShopManager").
- Permissions are a predefined, static list within the application code or a bootstrap configuration. Administrators can assign permissions, but not create new ones.
- A user's effective permissions are the union of all permissions granted by all roles assigned to them.
- All create, update, and delete operations on Roles, Role-Permission assignments, and User-Role assignments must be audited.

## Data Requirements
- **`Role`**
  - `role_id` (Primary Key, UUID)
  - `role_name` (String, Unique, Not Null)
  - `description` (String, Nullable)
  - `created_at`, `updated_at` (Timestamps)
- **`Permission`**
  - `permission_key` (Primary Key, String, e.g., `TIME_APPROVAL`)
  - `description` (String, Not Null)
- **`RolePermission`** (Join Table)
  - `role_id` (Foreign Key to `Role`)
  - `permission_key` (Foreign Key to `Permission`)
- **`UserRole`** (Join Table)
  - `user_id` (String or UUID, Foreign Key to external user identity system)
  - `role_id` (Foreign Key to `Role`)

## Acceptance Criteria
**AC 1: Admin can configure a new role with specific permissions**
- **Given** I am logged in as a System Administrator
- **And** the permissions `SCHEDULE_OVERRIDE` and `TIME_APPROVAL` exist in the system
- **When** I create a new role named "Shop Manager"
- **And** I assign the `SCHEDULE_OVERRIDE` and `TIME_APPROVAL` permissions to it
- **Then** the "Shop Manager" role is successfully created with exactly those two permissions.

**AC 2: A user with an assigned role can perform an allowed action**
- **Given** a role "Shop Manager" exists with the `TIME_APPROVAL` permission
- **And** a user "Jane Doe" is assigned the "Shop Manager" role
- **When** the system checks if "Jane Doe" has permission to perform `TIME_APPROVAL`
- **Then** the access check returns `true`.

**AC 3: A user without a required permission is denied access**
- **Given** a role "Service Advisor" exists and does *not* have the `TIME_APPROVAL` permission
- **And** a user "John Smith" is assigned only the "Service Advisor" role
- **When** the system checks if "John Smith" has permission to perform `TIME_APPROVAL`
- **Then** the access check returns `false`.

**AC 4: Role permission changes are reflected in access checks**
- **Given** the "Shop Manager" role has the `TIME_APPROVAL` permission
- **And** user "Jane Doe" is assigned the "Shop Manager" role
- **When** I, as a System Administrator, remove the `TIME_APPROVAL` permission from the "Shop Manager" role
- **And** the system subsequently checks if "Jane Doe" has permission for `TIME_APPROVAL`
- **Then** the access check now returns `false`.

**AC 5: All changes to a role's permissions are audited**
- **Given** I am logged in as a System Administrator
- **When** I add the `INVOICE_DELETE` permission to the "Shop Manager" role
- **Then** an audit event is created containing my user ID, the role modified ("Shop Manager"), the permission added (`INVOICE_DELETE`), and a timestamp.

## Audit & Observability
- **Audit Events**: The following events MUST be logged to a dedicated, immutable audit trail:
  - `ROLE_CREATED`
  - `ROLE_UPDATED`
  - `ROLE_DELETED`
  - `ROLE_PERMISSION_ASSIGNED`
  - `ROLE_PERMISSION_REMOVED`
  - `USER_ROLE_ASSIGNED`
  - `USER_ROLE_REMOVED`
- **Event Payload**: Each audit event must include the principal (who made the change), the target entity ID, the changes made (e.g., old/new values), and a timestamp.
- **Metrics**:
  - `access_check_latency_ms`: Latency for permission checks.
  - `access_denied_total`: Counter for permission check failures, tagged by permission key.

## Open Questions
- none

## Original Story (Unmodified – For Traceability)
# Issue #62 — [BACKEND] [STORY] Security: Define Shop Roles and Permission Matrix

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

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


### Backend Requirements

- Implement Spring Boot microservices
- Create REST API endpoints
- Implement business logic and data access
- Ensure proper security and validation

### Technical Stack

- Spring Boot 3.2.6
- Java 21
- Spring Data JPA
- PostgreSQL/MySQL

---
*This issue was automatically created by the Durion Workspace Agent*