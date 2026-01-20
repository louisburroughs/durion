Title: [BACKEND] [STORY] Permission Management: Define POS Roles and Permission Matrix
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/2
Labels: type:story, domain:security, status:needs-review, agent:story-authoring, agent:security, layer:domain

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:needs-review

### Recommended
- agent:security
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** security-strict

---

## Story Intent

As an **Administrator**, I need to define POS roles and map them to granular permissions so that sensitive operations (price overrides, refunds, order cancellations, payment voids, inventory adjustments) are protected by role-based access control (RBAC), minimizing the risk of unauthorized actions and ensuring least-privilege enforcement.

---

## Actors & Stakeholders

- **Administrator** (primary actor) — creates, assigns, and revokes roles; maintains the permission matrix
- **Security Officer** — audits role assignments and permission changes; reviews access control violations
- **POS Operators** (various roles: Cashier, Service Advisor, Mechanic, Manager) — execute operations within their assigned permissions
- **HR System / Identity Provider** — provides user identity, organizational structure, and initial role suggestions (read-only integration)
- **System** — enforces permission checks at operation invocation and logs authorization events

---

## Preconditions

- User accounts exist in the system and are authenticated
- Sensitive operations (e.g., refunds, overrides, voids, adjustments) are identifiable and protected by permission checks
- Audit logging infrastructure is available and operational
- HR integration (if used) provides user identity and organizational role data

---

## Functional Behavior

### Define Roles
**When** an Administrator defines a new role (e.g., "Cashier", "Service Advisor", "Manager", "Finance Manager"),
**Then** the system SHALL:
1. Create a role entity with a unique role name and optional description
2. Initialize the role with no permissions (least privilege default)
3. Record the creation timestamp and creator identity
4. Log the `RoleCreated` audit event

### Map Permissions to Roles
**When** an Administrator assigns permissions to a role,
**Then** the system SHALL:
1. Validate that the permissions exist in the permission registry (e.g., `APPROVE_REFUND`, `OVERRIDE_PRICE`, `CANCEL_ORDER`, `VOID_PAYMENT`, `ADJUST_INVENTORY`)
2. Create role-permission mappings (many-to-many relationship)
3. Persist the mappings atomically
4. Log the `PermissionAssignedToRole` audit event with the role, permission, and administrator identity

### Assign Roles to Users
**When** an Administrator assigns a role to a user,
**Then** the system SHALL:
1. Validate that the role exists
2. Validate that the user exists and is authenticated
3. Create a user-role association (many-to-many relationship)
4. Record the assignment timestamp and administrator identity
5. Log the `RoleAssignedToUser` audit event

### Enforce Permissions at Operation Invocation
**When** a user attempts a protected operation (e.g., issuing a refund),
**Then** the system SHALL:
1. Identify the required permission for the operation (e.g., `APPROVE_REFUND`)
2. Query the user's assigned roles and their associated permissions
3. If the user has the required permission, allow the operation to proceed
4. If the user lacks the required permission, reject the operation and log the `PermissionDenied` audit event (with user identity, operation, and required permission)

### Revoke Roles from Users
**When** an Administrator revokes a role from a user,
**Then** the system SHALL:
1. Remove the user-role association
2. Record the revocation timestamp and administrator identity
3. Log the `RoleRevokedFromUser` audit event
4. Immediately enforce the updated permissions (no grace period)

---

## Alternate / Error Flows

### Error: Unauthorized Access Attempt
**When** a user without the required permission attempts a protected operation,
**Then** the system SHALL:
1. Reject the operation with a clear error message ("Insufficient permissions: requires APPROVE_REFUND")
2. Log the `PermissionDenied` event with full context (user, operation, required permission, timestamp)
3. Optionally alert the Security Officer if repeated violations occur from the same user

### Error: Role Conflict or Circular Dependency
**When** an Administrator attempts to assign a role that creates a circular dependency or violates role hierarchy rules,
**Then** the system SHALL reject the assignment and provide a descriptive error message.

### Error: Orphaned Permissions
**When** a permission is assigned to a role but the operation no longer exists (e.g., deprecated feature),
**Then** the system SHALL flag the orphaned permission in an administrative report and allow cleanup.

---

## Business Rules

1. **Least Privilege by Default:** Newly created roles have NO permissions assigned; permissions must be explicitly granted.
2. **Atomic Role-Permission Mapping:** Permission assignments to roles must succeed or fail atomically to avoid partial states.
3. **Immediate Enforcement:** Role and permission changes take effect immediately; no caching delay is acceptable for security-critical operations.
4. **Immutability of Audit Events:** All role and permission changes must be logged as immutable audit events (append-only).
5. **Role Uniqueness:** Role names must be unique within the system (case-insensitive).
6. **Permission Registry:** Permissions must be defined in a centralized registry; ad-hoc permissions are forbidden.

---

## Data Requirements

**Core Entities:**

**Role:**
- `role_id` (unique identifier)
- `role_name` (unique, e.g., "Cashier", "Manager")
- `description` (optional)
- `created_at` (timestamp)
- `created_by` (administrator identity)

**Permission:**
- `permission_id` (unique identifier)
- `permission_name` (unique, e.g., `APPROVE_REFUND`, `OVERRIDE_PRICE`, `CANCEL_ORDER`)
- `operation_category` (e.g., `PAYMENT`, `PRICING`, `INVENTORY`)
- `description` (optional)

**RolePermission (many-to-many mapping):**
- `role_id` (foreign key)
- `permission_id` (foreign key)
- `assigned_at` (timestamp)
- `assigned_by` (administrator identity)

**UserRole (many-to-many mapping):**
- `user_id` (foreign key)
- `role_id` (foreign key)
- `assigned_at` (timestamp)
- `assigned_by` (administrator identity)
- `revoked_at` (nullable, timestamp)
- `revoked_by` (nullable, administrator identity)

**AuditLog (for role/permission events):**
- `audit_id` (unique identifier)
- `event_type` (e.g., `RoleCreated`, `RoleAssignedToUser`, `PermissionDenied`)
- `actor_id` (user performing the action)
- `subject_id` (user or role affected)
- `timestamp` (immutable)
- `details` (JSON or structured log of the event)

---

## Acceptance Criteria

### Permission Enforcement
- **Given** a user with the "Cashier" role (which does NOT have the `APPROVE_REFUND` permission),
- **When** the user attempts to approve a refund,
- **Then** the system rejects the operation, returns an error message, and logs a `PermissionDenied` event.

### Successful Authorization
- **Given** a user with the "Manager" role (which HAS the `APPROVE_REFUND` permission),
- **When** the user attempts to approve a refund,
- **Then** the system allows the operation to proceed and logs the refund approval action.

### Role Assignment and Immediate Effect
- **Given** an Administrator assigns the "Service Advisor" role to a user,
- **When** the role is assigned,
- **Then** the system records the user-role mapping, logs the `RoleAssignedToUser` event, and the user can immediately perform operations permitted by that role.

### Role Revocation and Immediate Effect
- **Given** an Administrator revokes the "Manager" role from a user,
- **When** the role is revoked,
- **Then** the system removes the user-role mapping, logs the `RoleRevokedFromUser` event, and the user can NO LONGER perform operations requiring that role's permissions.

### Audit Trail for Role Changes
- **Given** an Administrator assigns a role to a user,
- **When** the Security Officer queries the audit trail,
- **Then** the `RoleAssignedToUser` event is present with the timestamp, administrator identity, user identity, and role name.

---

## Audit & Observability

**Audit Events to Log:**
- `RoleCreated` — fired when a new role is defined (include role name, creator, timestamp)
- `RoleDeleted` — fired when a role is deleted (include role name, deleter, timestamp)
- `PermissionAssignedToRole` — fired when a permission is added to a role (include role, permission, administrator, timestamp)
- `PermissionRevokedFromRole` — fired when a permission is removed from a role
- `RoleAssignedToUser` — fired when a role is assigned to a user (include user, role, administrator, timestamp)
- `RoleRevokedFromUser` — fired when a role is revoked from a user
- `PermissionDenied` — fired when a user attempts an operation without the required permission (include user, operation, required permission, timestamp)

**Observability Metrics:**
- Count of permission denial events per user, per day/week/month (to detect anomalies or training gaps)
- Count of role assignments and revocations per administrator (to detect unusual administrative activity)
- Count of active users per role (to understand role distribution)
- Average time-to-revoke after user separation (to measure offboarding efficiency)

---

## Open Questions

**Question 1: Permission Registry and Granularity**
- What is the complete list of protected operations and their corresponding permissions?
- Are permissions operation-specific (e.g., `APPROVE_REFUND_UNDER_100`, `APPROVE_REFUND_OVER_100`) or role-scoped?
- Who maintains the permission registry, and how are new permissions added when new features are released?

**Why it matters:** The permission model must match the granularity of operational risk. If refund thresholds exist, permissions may need to be parameterized. The registry must be extensible without requiring code changes.

**Impact:** Without a clear permission registry, the system may have under-protected operations or overly complex permission logic.

---

**Question 2: Role Hierarchies and Inheritance**
- Are roles flat (independent), or is there a hierarchy (e.g., "Manager" inherits permissions from "Cashier")?
- If hierarchies exist, how are conflicts resolved (e.g., a higher role revokes a permission inherited from a lower role)?

**Why it matters:** Role hierarchies simplify permission assignment but introduce complexity in conflict resolution and auditing. Flat roles are simpler but may require more maintenance.

**Impact:** Unclear role inheritance rules may lead to permission leakage or excessive administrative burden.

---

**Question 3: HR Integration and Identity Sync**
- Is the HR system the authoritative source for user identity, or is it just a reference for organizational roles?
- How are role assignments synchronized: one-time import, real-time sync, or manual mapping by an Administrator?
- If a user changes departments in HR, does their POS role change automatically, or is that a separate decision?

**Why it matters:** The integration model determines whether the POS system is authoritative for access control or if HR drives role assignments. Real-time sync introduces complexity and potential availability dependencies.

**Impact:** Misaligned identity sources may lead to authorization drift (HR says one thing, POS enforces another).

---

**Question 4: Permission Scope and Multi-Tenant Considerations**
- Are permissions scoped globally (across all POS locations), per-location, or per-shop?
- If a user has the "Manager" role at Shop A, does that grant permissions at Shop B?
- How are cross-location authorizations handled (e.g., district managers)?

**Why it matters:** Multi-location businesses require location-scoped permissions to prevent unauthorized cross-location access while allowing legitimate district-level operations.

**Impact:** Unclear scoping may lead to security gaps or over-restriction of legitimate use cases.

---

**Question 5: Temporary Role Assignments and Expiration**
- Can roles be assigned with an expiration date (e.g., temporary manager authority)?
- Are there "break-glass" emergency roles that grant elevated permissions with audit scrutiny?

**Why it matters:** Temporary roles and emergency access are common in operational environments. The system must support time-bound permissions and elevated audit requirements for high-risk temporary access.

**Impact:** Without temporary role support, administrators may create permanent over-privileged accounts or fail to revoke access promptly.

---

## Amendment: permissions_v1.yml

I added the canonical permission registry file to the repository at: https://github.com/louisburroughs/durion-positivity-backend/blob/main/.github/permissions/permissions_v1.yml

This file is the machine-readable, versioned registry (v1) referenced in the resolution comments. It can be used as the authoritative `permissions.yml` for enforcement and code-backed policy.

---

## Original Story (Unmodified – For Traceability)

# Issue #2 — [BACKEND] [STORY] Permission Management: Define POS Roles and Permission Matrix

## Current Labels
- backend
- story-implementation
- user

## Current Body
## Backend Implementation for Story

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
