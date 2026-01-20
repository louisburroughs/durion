## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:draft

### Recommended
- agent:security-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** security-strict

STOP: Clarification required before finalization

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Security: Define Shop Roles and Permission Matrix (RBAC Admin UI)

## Primary Persona
System Administrator / Admin (authorized to manage RBAC)

## Business Value
Enable administrators to manage roles and assign permissions so sensitive shop operations (e.g., schedule overrides, time approval) are enforced consistently and changes are auditable.

---

# 2. Story Intent

## As a / I want / So that
**As an** Admin,  
**I want** to create/edit roles and assign predefined permissions (and optionally assign roles to users),  
**So that** only authorized staff can perform protected shop actions, and RBAC configuration changes are traceable.

## In-scope
- Moqui frontend screens to:
  - List roles
  - Create role
  - Edit role (name/description)
  - Assign/unassign permissions to a role
  - View role details including assigned permissions
- UI enforcement of access to RBAC admin screens (deny-by-default behavior when unauthorized).
- Display of audit information for RBAC changes **if backend exposes it** (see Open Questions).

## Out-of-scope
- Creating new Permission keys from the UI (permissions are predefined/registered elsewhere).
- Defining authentication/IdP flows.
- Implementing backend RBAC services/entities (assumed available via Moqui services or REST).
- Downstream feature-level enforcement across the whole POS UI (this story provides the admin configuration UI; individual feature screens enforcing specific permissions is separate unless already wired).

---

# 3. Actors & Stakeholders
- **Admin (primary)**: manages roles and permission assignments.
- **Shop Staff (indirect)**: experience allow/deny outcomes on protected actions.
- **Security/Audit stakeholders**: need traceability of changes.
- **Developers/Testers**: need deterministic UI + service interaction behavior.

---

# 4. Preconditions & Dependencies
- Moqui app is running with authentication already in place (principal available in session).
- Backend capabilities exist for:
  - Role CRUD
  - Permission list retrieval
  - Role↔Permission assignment mutation
  - (Optional) User↔Role assignment mutation (mentioned in inputs but not explicitly required for frontend scope—see Open Questions)
- Authorization checks exist to protect RBAC admin operations (backend returns 401/403 as appropriate).
- A canonical list of permissions exists (predefined; not created by admin in UI).

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Admin navigation: **Security → Roles & Permissions** (final menu placement depends on existing Moqui menu structure).

## Screens to create/modify (Moqui)
1. **`apps/pos/security/RoleList.xml`**
   - Lists roles, supports search/filter, and navigation to create/edit/detail.
2. **`apps/pos/security/RoleCreate.xml`**
   - Form to create a role (name, description).
3. **`apps/pos/security/RoleDetail.xml`**
   - View role metadata and permissions; entry to edit.
4. **`apps/pos/security/RoleEdit.xml`**
   - Edit role fields + permission assignment UI (multi-select or dual-list).
5. (Optional, if in-scope after clarification) **`apps/pos/security/UserRoleAssignments.xml`**
   - Assign roles to users.

## Navigation context
- Standard breadcrumb: Security → Roles → (Role Name)
- Role detail and edit accessible from list.

## User workflows
### Happy path: create role and assign permissions
1. Admin opens Role List.
2. Admin clicks “Create Role”.
3. Enters role name + description; saves.
4. Navigates to Role Edit/Detail.
5. Adds permissions (predefined list); saves assignments.

### Alternate path: remove permission
1. Admin opens Role Edit.
2. Removes a permission; saves.
3. UI confirms success; updated permission list displayed.

### Unauthorized path
- Non-admin attempts to access any RBAC screens → show access denied screen/message; no data leakage.

---

# 6. Functional Behavior

## Triggers
- Route entry to RBAC screens
- Form submit actions (create/update)
- Permission assignment save action

## UI actions (explicit)
- **Role List**
  - Search by role name (contains/starts-with; see safe defaults)
  - Click role row → Role Detail
  - Create button → Role Create
- **Role Create**
  - Inputs: roleName (required), description (optional)
  - Save → creates role, then transitions to Role Detail (or Edit)
  - Cancel → back to Role List
- **Role Edit**
  - Update role metadata (name/description) and save
  - Permission assignment widget:
    - Load all permissions
    - Load current role’s permissions
    - Add/remove permissions in UI
    - Save assignment changes (batch save preferred)
- **Role Detail**
  - Read-only display of role and assigned permissions
  - Edit button → Role Edit

## State changes
- Role created/updated/deleted (if delete is included; not explicitly requested for frontend—see Open Questions)
- RolePermission assignments created/removed

## Service interactions (Moqui)
- Use Moqui `service-call` (or REST via screen transitions) to:
  - Fetch roles list
  - Create/update role
  - Fetch permissions list
  - Fetch role permissions
  - Update role permissions (grant/revoke or replace)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- **Role name required** (client-side required + server-side enforcement)
- **Role name uniqueness**: if backend returns conflict (likely 409) for duplicate role names (case-insensitive), UI must:
  - Keep user on form
  - Highlight role name field
  - Show error: “Role name already exists.”
- **Permission assignment validity**:
  - UI must only allow selecting from permissions returned by backend (no free-text permission keys).
  - If backend rejects assignment due to invalid permission key / missing role, display returned message and reload current state.

## Enable/disable rules
- Save buttons disabled while request in-flight (prevent double-submit).
- Permission assignment save disabled if no changes compared to loaded state (diff-based) OR allow idempotent save (see safe defaults).

## Visibility rules
- RBAC admin menu and screens should be hidden or blocked for unauthorized users.
  - If permission/role gating mechanism exists in frontend, use it for hiding.
  - Regardless, backend 403 must be handled correctly (cannot rely on hiding alone).

## Error messaging expectations
- 401: “Your session has expired. Please sign in again.”
- 403: “You do not have access to manage roles and permissions.”
- 404: “Role not found.” (when navigating to unknown roleId)
- 409: “Conflict: role name already exists.” or backend-provided message
- Generic 5xx/network: “Something went wrong. Try again.”

---

# 8. Data Requirements

## Entities involved (conceptual; backend-owned)
- `Role`
- `Permission`
- `RolePermission`
- `UserRole` (only if role assignment UI included)

## Fields
### Role
- `roleId` (string/UUID, read-only)
- `roleName` (string, required, unique case-insensitive)
- `description` (string, optional)
- `createdAt` / `updatedAt` (read-only; display optional)

### Permission
- `permissionKey` (string; stable identifier)
- `description` (string; display label)

### RolePermission (derived for UI)
- `roleId` (read-only)
- `permissionKey` (selected from list)

## Read-only vs editable
- Editable: roleName, description, role’s permission assignments
- Read-only: IDs, timestamps, permission keys (select-only, not editable)

## Derived/calculated fields
- Effective permission count per role (derived; show in list if available)
- “Changed” state for permissions assignment (diff between loaded and selected)

---

# 9. Service Contracts (Frontend Perspective)

> Concrete service names/endpoints are not provided in inputs; Moqui implementation must bind to whatever the backend exposes. This section defines **required capabilities** and expected request/response semantics; exact service names are an Open Question.

## Load/view calls
- **List Roles**
  - Input: paging (pageIndex/pageSize), optional roleName filter
  - Output: array of roles with roleId, roleName, description
- **Get Role**
  - Input: roleId
  - Output: role object
- **List Permissions**
  - Input: optional domain filter (if supported)
  - Output: list of permissions (permissionKey, description)
- **Get Role Permissions**
  - Input: roleId
  - Output: list of permissionKeys (or permission objects)

## Create/update calls
- **Create Role**
  - Input: roleName, description
  - Output: created role (including roleId)
  - Errors: 400 validation, 409 duplicate
- **Update Role**
  - Input: roleId, roleName, description
  - Output: updated role
  - Errors: 400/404/409

## Submit/transition calls
- **Update Role Permissions**
  - Preferred: replace set (idempotent)
    - Input: roleId, permissionKeys[]
  - Alternate: incremental grant/revoke
    - Input: roleId, permissionKey, action=(grant|revoke)
  - Output: success + updated permissions
  - Errors: 400 invalid key, 404 role not found

## Error handling expectations
- Moqui screens must map backend errors into:
  - Field errors (roleName duplicates/invalid)
  - Top-level banner for authorization/network
- After failed permission save, UI must reload role permissions from server to avoid drift if partial updates occurred.

---

# 10. State Model & Transitions

## Allowed states
- No explicit lifecycle states provided for Role/Permission in inputs.
- Treat role as active if it exists.

## Role-based transitions
- Transition into any RBAC screen requires an admin authorization check (server-enforced).
- Mutation actions require authorization.

## UI behavior per state
- **Unauthorized**: show access denied; do not render data tables/forms.
- **Loading**: show spinner/skeleton; disable actions.
- **Saving**: disable save; show progress indicator.
- **Error**: show error banner; preserve user input when safe.

---

# 11. Alternate / Error Flows

## Validation failures
- Missing roleName → inline validation; prevent submit.
- Backend 400 → display field-level or form-level message from response.

## Concurrency conflicts
- Two admins edit same role:
  - If backend supports optimistic locking (not specified), handle 409 with message: “Role was updated by someone else. Reload and try again.”
  - If not supported, last write wins; UI should reload after save to show canonical state. (Needs clarification.)

## Unauthorized access
- Direct URL navigation to RBAC screens without permission:
  - Backend returns 403
  - UI shows access denied and does not leak role/permission lists.

## Empty states
- No roles exist:
  - Show empty table message and a “Create Role” CTA (if authorized).
- No permissions returned:
  - Permission assignment section shows “No permissions available” and disables save.

---

# 12. Acceptance Criteria (Gherkin)

## Scenario 1: Authorized admin can view roles list
**Given** I am authenticated as a user authorized to manage roles and permissions  
**When** I navigate to Security → Roles & Permissions  
**Then** I see a list of roles loaded from the backend  
**And** each role shows at least its name  
**And** I can open a role’s detail screen

## Scenario 2: Unauthorized user is blocked from RBAC screens
**Given** I am authenticated as a user not authorized to manage roles and permissions  
**When** I navigate directly to the Roles & Permissions URL  
**Then** the system shows an “Access denied” message  
**And** no role or permission data is displayed

## Scenario 3: Admin can create a role
**Given** I am authorized to manage roles and permissions  
**When** I create a role with role name “ShopManager” and a description  
**Then** the role is created successfully  
**And** I am taken to the role detail (or edit) view for the new role  
**And** the role appears in the roles list

## Scenario 4: Duplicate role name is rejected
**Given** I am authorized to manage roles and permissions  
**And** a role named “ShopManager” already exists (case-insensitive)  
**When** I attempt to create a role named “shopmanager”  
**Then** the system prevents creation  
**And** I see an error indicating the role name already exists  
**And** my entered form values remain visible for correction

## Scenario 5: Admin can assign permissions to a role
**Given** I am authorized to manage roles and permissions  
**And** a role “ShopManager” exists  
**And** the permissions list includes “SCHEDULE_OVERRIDE” and “TIME_APPROVAL”  
**When** I open the role edit screen for “ShopManager”  
**And** I select “SCHEDULE_OVERRIDE” and “TIME_APPROVAL”  
**And** I save permission assignments  
**Then** the role detail shows exactly those permissions assigned  
**And** reloading the page still shows those permissions assigned

## Scenario 6: Admin can remove a permission from a role
**Given** I am authorized to manage roles and permissions  
**And** a role “ShopManager” has permission “TIME_APPROVAL” assigned  
**When** I remove “TIME_APPROVAL” from the role and save  
**Then** the role no longer shows “TIME_APPROVAL” assigned after reload

## Scenario 7: Backend rejects invalid permission assignment
**Given** I am authorized to manage roles and permissions  
**And** the backend returns an error when saving role permissions  
**When** I attempt to save permission assignments  
**Then** I see an error message returned by the backend (or a generic error)  
**And** the UI reloads the role’s assigned permissions from the backend to reflect the canonical state

---

# 13. Audit & Observability

## User-visible audit data
- If backend provides audit events for RBAC changes:
  - Role detail screen should include an “Audit” section showing recent changes (event type, actor, timestamp, summary).
- If not available:
  - UI does not fabricate audit history; this remains backend-only.

## Status history
- Not applicable unless backend provides change history.

## Traceability expectations
- All mutation requests should include/propagate a correlation/request ID if the frontend framework supports it (Moqui requestId).
- UI should log (client console only in dev) minimal debug info; do not log sensitive identity tokens.

---

# 14. Non-Functional UI Requirements
- **Performance**: Role list loads within 2 seconds for up to 200 roles (paged).
- **Accessibility**: Forms have labels, validation messages are announced (aria-live), keyboard navigation supported for permission selection.
- **Responsiveness**: Works on tablet widths; tables wrap or stack columns gracefully.
- **i18n**: All user-facing strings routed through existing i18n mechanism (if present in repo).
- **Security**: Do not expose internal IDs in URLs beyond roleId already required; never log tokens/PII.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for “no roles” and “no permissions” to avoid ambiguous blank screens; qualifies as safe UI ergonomics. (UX Summary, Error Flows)
- SD-UX-PAGINATION: Paginate Role List with default page size (e.g., 25) and basic name filter; safe and reversible UI ergonomics. (UX Summary, Service Contracts)
- SD-ERR-MAP-HTTP: Standard mapping of HTTP 400/401/403/404/409/5xx into inline vs banner errors; safe because it does not change domain policy, only presentation. (Business Rules, Error Flows)

---

# 16. Open Questions
1. **Backend service contract for Moqui:** What are the exact Moqui service names (or REST endpoints) for:
   - listRoles, getRole, createRole, updateRole
   - listPermissions
   - getRolePermissions, updateRolePermissions (replace vs grant/revoke)?
2. **Authorization gating:** What permission(s) control access to RBAC admin screens and mutation actions? (Story must not invent permission keys or role hierarchy.)
3. **Role deletion:** Is deleting roles in-scope for the frontend? If yes, what are constraints (cannot delete if assigned to users? soft delete vs hard delete)?
4. **User↔Role assignment UI:** The input mentions `UserRole`. Is role assignment to users required in this frontend story, or handled elsewhere?
5. **Audit visibility:** Does the backend expose an audit query API for RBAC events (ROLE_CREATED/UPDATED/etc.)? If yes, what is the endpoint and schema?
6. **Multi-tenant/location scoping:** Are roles/permissions global to tenant, or scoped per location/shop? How is tenant/location derived in UI requests?
7. **Optimistic locking/concurrency:** Does Role update use versioning/ETag for conflict detection? If so, what field/header should the UI send and how should 409 be handled?

---

## Original Story (Unmodified – For Traceability)

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