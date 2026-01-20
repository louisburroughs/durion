STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Inventory Security: Inventory Roles, Permission Matrix UI Gating, and Role Assignment Audit Visibility

### Primary Persona
System Administrator (Admin)

### Business Value
Ensure only authorized staff can perform sensitive inventory operations (stock adjustments, count approvals, receiving reversals) by reflecting RBAC permissions in the frontend, preventing unauthorized UI actions, and exposing auditable role assignment history for compliance and incident review.

---

## 2. Story Intent

### As a / I want / So that
As a System Administrator, I want the frontend to (1) display inventory roles and their permissions, (2) allow assigning/unassigning inventory roles to users where authorized, and (3) gate inventory UI actions based on permissions, so that inventory operations follow least-privilege and are auditable.

### In-scope
- Inventory security administration UI surfaces:
  - View inventory roles and the permission matrix (role → permission mapping)
  - Assign/unassign roles to users (if supported by backend contract)
  - View audit history of role assignment changes (who changed what, when)
- Frontend permission-aware UI behavior for Inventory screens:
  - Hide/disable protected actions based on effective permissions
  - Handle `403 Forbidden` consistently with user feedback
- Moqui screen(s), forms, transitions, and service calls required for the above.

### Out-of-scope
- Defining or changing backend RBAC framework behavior (server-side enforcement remains backend responsibility).
- Implementing identity provider (OIDC/HR) integration UI beyond selecting existing users.
- Designing new inventory operational flows (cycle count, receiving, adjust stock) beyond gating their existing entry points/actions.
- Creating/maintaining product master data.

---

## 3. Actors & Stakeholders
- **System Administrator (primary):** Manages role assignments and reviews audit.
- **Inventory Manager / Controller:** Stakeholder for separation-of-duties expectations.
- **Auditor / Compliance:** Needs audit visibility and traceability.
- **Inventory Users (Receiver, Stock Clerk, Mechanic Picker):** Impacted by UI gating (view vs action).
- **Security/RBAC backend service (system actor):** Source of truth for roles/permissions and audit events.

---

## 4. Preconditions & Dependencies
- Authenticated session exists and frontend can call Moqui backend services with user context.
- Backend provides:
  - A canonical inventory permission set (e.g., `inventory:stock:adjust`, `inventory:count:approve`, etc.).
  - APIs/services to fetch:
    - Current user’s effective permissions
    - Roles and their permissions
    - User role assignments
    - Audit log entries for role assignment changes
  - APIs/services to assign/unassign roles (if required for this story).
- Dependency reference (backend): `durion-positivity-backend` issue #23 (roles/permission matrix) and RBAC framework issue #42.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Inventory → Security** (admin-only visibility via permission gating).
- Secondary deep link route: `/inventory/security` (exact route finalization depends on existing screen tree).

### Screens to create/modify
1. **New Screen:** `InventorySecurity.xml` (or equivalent under `apps/durion/screen/inventory/`)
   - Subscreens:
     - `RoleMatrix` (roles list + permissions list read-only)
     - `UserRoleAssignments` (search user, view assigned roles, assign/unassign)
     - `RoleAudit` (audit log list + filters)
2. **Modify existing Inventory screens** (as needed):
   - Add permission gating to action buttons/menus:
     - Stock adjustment entry points
     - Cycle count initiate/submit/approve actions
     - Receiving receive/reverse actions
     - Location CRUD actions
     - Item CRUD actions
     - Reporting export actions

### Navigation context
- Inventory Security is an administrative area; should not appear to users lacking the required admin permission(s).
- From Role Matrix, Admin can navigate to User Role Assignments and Audit.

### User workflows
**Happy path: View role-permission matrix**
1. Admin opens Inventory → Security → Role Matrix
2. Sees list of roles and their granted permissions (read-only)
3. Can inspect a role to see permission keys and human-readable descriptions (if provided)

**Happy path: Assign a role to a user**
1. Admin opens User Role Assignments
2. Searches for a user (by name/email/username depending on backend)
3. Views current roles
4. Selects role(s) to add and submits
5. UI confirms success; audit entry becomes visible in Role Audit

**Alternate path: Unauthorized access**
- User without permission visits `/inventory/security` directly → shown “Access denied” with no data leakage.

**Alternate path: Backend denies action**
- Admin UI attempts assign/unassign but backend returns 403 (misconfigured permissions) → UI shows error and refreshes assignments from server.

---

## 6. Functional Behavior

### Triggers
- Route entry to `/inventory/security/*`
- Click actions on:
  - “Assign Role”, “Remove Role”
  - Filtering/sorting audit log
  - Navigating to protected inventory functions elsewhere in the app

### UI actions
- Load current user’s effective permissions on app startup or on entering Inventory module (see Open Questions for source).
- Conditional rendering:
  - Show/enable actions only if user has required permission.
- Forms:
  - User search form
  - Assign role form (select role(s), confirm)
  - Remove role action (with confirm)
  - Audit filter form (date range, actor, target user, role, outcome)

### State changes
- Client-side state: cached permissions and role list; refreshed on successful assignment changes.
- No inventory operational state machine changes are introduced by this story.

### Service interactions
- Fetch permissions for current user
- Fetch role-permission matrix
- Fetch user role assignments
- Submit role assignment changes
- Fetch audit logs

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Role assignment:
  - Must select a target user (required)
  - Must select at least one role to add (required)
  - For removal, must select an existing assigned role
- Inputs validated client-side for emptiness; authoritative validation comes from backend.

### Enable/disable rules
- Inventory Security nav entry visible only if user has the security-admin permission(s) defined for this module (Open Question: exact key).
- In inventory operational screens:
  - Disable/hide actions requiring permission keys per the canonical list:
    - `inventory:stock:adjust` gates “Adjust Stock”
    - `inventory:count:approve` gates “Approve Count”
    - `inventory:receiving:reverse` gates “Reverse Receipt”
    - etc. (see Data Requirements permission catalog below)

### Visibility rules
- Do not show role/permission details to unauthorized users (no partial rendering).
- Audit screen visible only to authorized users (Open Question: which permission).

### Error messaging expectations
- 403: “You don’t have permission to perform this action.”
- 400 validation: show backend message inline, mapped to fields when possible.
- 5xx/network: show generic failure and allow retry.

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- **Role**
- **Permission**
- **RolePermission** (role → permission mapping)
- **UserRole** (user → role mapping)
- **AuditLog** (role assignment audit events)

> Note: Exact entity names in Moqui may differ; frontend must bind to services/view-entities provided by backend/Moqui components.

### Fields
**Permission**
- `permissionId` / `permissionKey` (string, required) e.g., `inventory:stock:adjust`
- `description` (string, optional)

**Role**
- `roleId` (string, required)
- `roleName` (string, required)
- `description` (string, optional)

**RolePermission**
- `roleId` (string, required)
- `permissionKey` (string, required)

**UserRole**
- `userId` (string, required)
- `roleId` (string, required)
- `fromDate` (datetime, optional)
- `thruDate` (datetime, optional)

**AuditLog** (role assignment change)
- `auditId` (string, required)
- `eventType` (string, required) e.g., `security.userRole.assigned`, `security.userRole.removed`
- `actorUserId` / `actorPrincipal` (string, required)
- `targetUserId` (string, required)
- `roleId` (string, required)
- `timestamp` (datetime, required)
- `outcome` (enum/string, required: `ALLOW`/`DENY`/`SUCCESS`/`FAILURE` — depends on backend)
- `reason` / `message` (string, optional)
- `correlationId` (string, optional)

### Read-only vs editable
- Role-permission matrix: read-only (this story does not change role definitions).
- User role assignments: editable only if user has role-admin permission (Open Question: exact key).
- Audit log: read-only.

### Derived/calculated fields
- Effective permissions display: derived from assigned roles (server-calculated; frontend just displays).
- “Has permission” booleans for gating: derived from loaded effective permissions list.

---

## 9. Service Contracts (Frontend Perspective)

> Blocking: backend/Moqui service names and payloads are not provided. Below are required contracts to implement the frontend; map to actual Moqui services once confirmed.

### Load/view calls
1. **Get current user permissions**
   - Service: `security.getUserPermissions` (placeholder)
   - Request: none (uses auth context)
   - Response: `{ permissions: string[] }`

2. **Get inventory roles + permissions**
   - Service: `security.getRolesWithPermissions` (placeholder)
   - Request: `{ domain: "inventory" }` or filter prefix `inventory:`
   - Response: `{ roles: Role[], permissions: Permission[], rolePermissions: RolePermission[] }`

3. **Search users**
   - Service: `security.searchUsers` (placeholder)
   - Request: `{ query: string, limit, offset }`
   - Response: `{ users: { userId, displayName, email? }[] }`

4. **Get roles for user**
   - Service: `security.getUserRoles` (placeholder)
   - Request: `{ userId }`
   - Response: `{ roles: Role[] }`

5. **Get audit logs**
   - Service: `security.getAuditLogs` (placeholder)
   - Request: filters `{ fromTs?, toTs?, targetUserId?, actorUserId?, roleId?, eventType?, limit, offset }`
   - Response: `{ audits: AuditLog[], total }`

### Create/update calls
1. **Assign roles to user**
   - Service: `security.assignUserRoles` (placeholder)
   - Request: `{ userId, roleIds: string[] }`
   - Response: success acknowledgement + updated assignments (preferred) or 200 OK.

2. **Remove role from user**
   - Service: `security.removeUserRole` (placeholder)
   - Request: `{ userId, roleId }`
   - Response: success acknowledgement.

### Submit/transition calls
- None (no inventory workflow transitions in this story).

### Error handling expectations
- `403 Forbidden`: treat as authorization failure; do not retry automatically; show denial message.
- `400 Bad Request`: show validation errors (field-level if backend provides structured errors).
- `409 Conflict`: if backend detects concurrent role changes, show “Roles changed since you loaded; refresh” and reload assignments.
- Network/5xx: show retry.

---

## 10. State Model & Transitions

### Allowed states
- Not applicable for inventory entities; this is RBAC/UI gating.

### Role-based transitions
- Not applicable.

### UI behavior per state
- **Loading:** show skeleton/loader for matrix and audit lists.
- **Empty:** show “No roles found” / “No audit entries match filters”.
- **Error:** show inline error with retry.

---

## 11. Alternate / Error Flows

1. **Unauthorized route access**
   - If user lacks required permission, screen returns Access Denied state; no service calls that would leak data.

2. **Backend denies assign/unassign (403)**
   - Show denial toast/banner.
   - Refresh user roles from server to reflect actual state.

3. **User search returns empty**
   - Show empty state and tips to refine search.

4. **Concurrency conflict (409)**
   - Inform user, reload current roles, require re-apply changes.

5. **Audit service unavailable**
   - Show non-blocking error on Audit tab only; other tabs still usable if their calls succeed.

---

## 12. Acceptance Criteria

```gherkin
Scenario: Inventory Security menu is hidden when user lacks admin permission
  Given a logged-in user without the required Inventory Security admin permission
  When the user views the Inventory module navigation
  Then the "Security" entry is not shown
  And direct navigation to "/inventory/security" shows "Access denied"

Scenario: Role-permission matrix loads for authorized admin
  Given a logged-in Admin with permission to view roles and permissions
  When the Admin opens "/inventory/security/role-matrix"
  Then the UI displays Inventory roles
  And for each role the UI displays its granted permission keys
  And the UI does not allow editing role-permission mappings

Scenario: Assign role to user succeeds and is reflected in UI
  Given a logged-in Admin authorized to assign roles
  And a target user exists
  When the Admin assigns the role "Inventory Controller" to the target user
  Then the UI shows the role as assigned after the save completes
  And the UI shows a success confirmation

Scenario: Assign role to user is denied without permission
  Given a logged-in user who can view role matrix but cannot assign roles
  When the user attempts to assign a role to a target user
  Then the backend returns 403
  And the UI displays an authorization error
  And the UI does not show the role as assigned

Scenario: Protected inventory action is disabled/hidden without required permission
  Given a logged-in user without permission "inventory:stock:adjust"
  When the user opens an Inventory screen that offers "Adjust Stock"
  Then the "Adjust Stock" action is not available (hidden or disabled)
  And if the user attempts the action via direct URL/action invocation
  Then the UI handles a 403 response by showing an authorization error

Scenario: Role assignment changes are visible in audit log
  Given an Admin authorized to view audit logs
  And a role assignment change occurred for a target user
  When the Admin opens "/inventory/security/role-audit" and filters by the target user
  Then the UI lists an audit entry including actor, target, role, timestamp, and outcome
```

---

## 13. Audit & Observability

### User-visible audit data
- Audit tab shows role assignment events with:
  - Actor identity
  - Target user
  - Role
  - Timestamp (displayed in user locale; stored UTC)
  - Outcome/status
  - CorrelationId if provided (for support escalation)

### Status history
- For a selected user, optionally show “Role assignment history” (if backend supports query by user).

### Traceability expectations
- All assign/unassign actions from UI must include enough request context for backend auditing (actor from auth; optionally include client correlation ID header if used by project conventions—Open Question).

---

## 14. Non-Functional UI Requirements
- **Performance:** Role matrix and permissions load within 2s for up to 200 roles and 500 permissions (paginate if larger).
- **Accessibility:** Keyboard navigable tables/forms; proper labels for inputs; focus management on dialogs.
- **Responsiveness:** Usable on tablet widths; tables support horizontal scroll.
- **i18n/timezone:** Audit timestamps rendered in local timezone; backend timestamps assumed UTC. No currency concerns.

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide standard empty-state messaging for no search results/audit rows; safe because it doesn’t alter domain logic. (Impacted: UX Summary, Alternate Flows)
- SD-UI-PAGINATION: Paginate user search and audit log lists with limit/offset; safe because it’s UI ergonomics and non-domain. (Impacted: UX Summary, Service Contracts)
- SD-ERR-403-MAP: Map HTTP 403 to a consistent “not authorized” UX pattern; safe because it reflects backend enforcement, not policy. (Impacted: Business Rules, Error Flows, Acceptance Criteria)

---

## 16. Open Questions

1. What are the exact permission keys that authorize:
   - Viewing Inventory Security screens (role matrix, assignments, audit)?
   - Assigning/unassigning roles?
   - Viewing audit logs?
   (Backend story defines inventory operation permissions, but not “RBAC admin” permissions.)

2. What are the actual Moqui service names, request/response shapes, and error payload formats for:
   - current user effective permissions
   - role-permission matrix
   - user search
   - user role assignments
   - role assignment audit logs
   - assign/unassign role actions

3. Are inventory roles the canonical set from backend story (“Inventory Viewer/Clerk/Manager/Controller/Admin”), or must the UI support the roles named in the original frontend issue description (InventoryManager, Receiver, StockClerk, MechanicPicker, Auditor)? If both exist, which are displayed/assignable?

4. Is role assignment managed within this system (Moqui) or via external IdP groups? If external, does the frontend need to be read-only with a link-out?

5. Which existing Inventory screens/actions must be gated in this story (exact screen IDs/routes), or should gating be limited strictly to Inventory Security pages only?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Define Inventory Roles and Permission Matrix — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/87

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

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

====================================================================================================

BACKEND STORY REFERENCES (FOR REFERENCE ONLY)

----------------------------------------------------------------------------------------------------

Backend matches (extracted from story-work):


[1] backend/23/backend.md

    Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory, agent:security

----------------------------------------------------------------------------------------------------

Backend Story Full Content:

### BACKEND STORY #1: backend/23/backend.md

------------------------------------------------------------

Title: [BACKEND] [STORY] Security: Define Inventory Roles and Permission Matrix
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/23
Labels: type:story, domain:inventory, status:ready-for-dev, agent:story-authoring, agent:inventory, agent:security

(remaining backend reference omitted here for brevity in traceability section)

------------------------------------------------------------

====================================================================================================

END BACKEND REFERENCES