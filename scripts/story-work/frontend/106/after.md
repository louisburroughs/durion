STOP: Clarification required before finalization

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

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Security: Admin UI to Manage Roles & Permissions for Product/Pricing Access Control (RBAC)

### Primary Persona
System Administrator (Admin)

### Business Value
Provide a secure, auditable, least-privilege way to configure who can perform financially sensitive product/cost/pricing actions, enabling controlled operations and reducing risk of unauthorized changes.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Admin  
- **I want** a frontend interface to view and manage Roles, Permissions, and Role→Permission grants used for product/pricing controls  
- **So that** only authorized staff can change sensitive financial data, and changes are auditable.

### In-scope
- Moqui-based admin screens to:
  - List/view **Permissions** (filterable by domain such as `product`, `pricing`)
  - List/create/edit **Roles**
  - Grant/revoke **Permissions** to/from a **Role** (RolePermission management)
  - View **audit history** related to role/permission changes (read-only)
- Frontend enforcement of authorization **to access these admin screens/actions** (deny-by-default UX behavior when backend returns 401/403).
- Consistent error handling and validation messaging for RBAC mutations.

### Out-of-scope
- Defining the authoritative permission matrix content for product/pricing (exact permission keys and which roles get which permissions) **unless provided**.
- Assigning roles to users/principals (PrincipalRole/UserRole management) unless explicitly confirmed.
- Enforcing permissions in Product/Pricing domain screens themselves (those are separate domain stories).
- Authentication mechanism and IdP integration details (assumed external; handled elsewhere).

---

## 3. Actors & Stakeholders
- **Admin (Primary):** Configures roles and role-permission grants.
- **Security Service (System):** System of record for Role/Permission/RolePermission and audit emission.
- **Product/Pricing domain owners (Stakeholders):** Provide permission manifests and intended mapping guidance.
- **Compliance/Audit stakeholders:** Require visibility into who changed access configuration and when.

---

## 4. Preconditions & Dependencies
- Backend security RBAC APIs exist (from backend story reference #42) and are reachable by Moqui frontend:
  - `GET /permissions` and `GET /permissions?domain=...`
  - Role CRUD endpoints (exact paths TBD)
  - RolePermission grant/revoke endpoints (exact paths TBD)
  - Audit log query endpoint (exact path/filtering TBD)
- Admin authentication is already established (principal available to backend; frontend receives 401/403 appropriately).
- Permission naming convention is enforced server-side: `domain:resource:action`.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Admin navigation item: **Security → Roles & Permissions** (exact menu placement/path TBD).

### Screens to create/modify (Moqui)
Create new screens under an admin/security area, for example:
- `apps/pos/screens/admin/security/Roles.xml`
- `apps/pos/screens/admin/security/RoleDetail.xml`
- `apps/pos/screens/admin/security/Permissions.xml`
- `apps/pos/screens/admin/security/Audit.xml` (scoped to RBAC events)

> If repository conventions differ, align names/paths to `durion-moqui-frontend` README conventions (needs confirmation).

### Navigation context
- Breadcrumb: Admin → Security → (Roles | Permissions | Audit)
- Cross-links:
  - From Role detail → “View granted permissions”
  - From Permissions list → “View roles with this permission” (optional; only if backend supports query)

### User workflows
**Happy path: Create role + grant permissions**
1. Admin opens Roles list → selects “Create Role”
2. Enters role name/description → saves
3. On Role detail, selects permissions to grant (filtered by domain `product`/`pricing`)
4. Saves grants; sees confirmation and updated list of grants
5. Audit view shows corresponding events

**Alternate paths**
- Admin edits role description only
- Admin revokes a permission from a role
- Admin filters permissions by domain and searches by key substring

---

## 6. Functional Behavior

### Triggers
- Screen load triggers list-fetch services (roles, permissions, audit).
- Form submit triggers create/update/grant/revoke calls.

### UI actions
- Roles list:
  - Search by role name
  - Open Role detail
  - Create new role
- Role detail:
  - Edit role description (role name immutability needs confirmation)
  - Grant permission(s) (single and/or bulk grant; bulk behavior needs confirmation)
  - Revoke permission(s)
- Permissions list:
  - Filter by domain (e.g., `product`, `pricing`)
  - Search by permission key
- Audit view:
  - Filter by event types related to RBAC (exact event types from security guide)
  - Filter by date range
  - Filter by actorId/roleName (if supported)

### State changes
- Role created/updated in backend
- RolePermission created/deleted in backend
- No frontend-local state machine beyond optimistic UI state; backend is authoritative.

### Service interactions
- All mutations call backend services and must handle:
  - `400` validation errors
  - `401` unauthenticated
  - `403` unauthorized (deny-by-default)
  - `404` missing role/permission
  - `409` conflicts (e.g., duplicate role name, duplicate grant, concurrency)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Role name:
  - Required
  - Uniqueness handled by backend; frontend shows `409` as “Role name already exists.”
  - Format/length constraints are unknown → must be returned/validated server-side; frontend displays field-level error if provided.
- Permission key:
  - Not editable in UI (permissions are registered code-first); UI is read-only list.
- Grant/revoke:
  - Must reference existing role and permission key; show `404` clearly if backend indicates missing.

### Enable/disable rules
- Disable all mutation controls (create/update/grant/revoke) while request in-flight.
- Disable grant button if no permission selected.
- If backend returns `403`, hide or disable mutation controls for the remainder of the session on that screen (but do not invent permissions; simply reflect denial).

### Visibility rules
- Screens themselves should be protected: if user lacks authorization, route to an “Access Denied” screen or show inline denied state (implementation pattern TBD).
- Audit tab visible only if audit endpoint is authorized; otherwise show “Not authorized to view audit history.”

### Error messaging expectations
- Show top-level error banner for non-field errors.
- Show field-level messages when backend provides structured validation errors (shape TBD → open question).

---

## 8. Data Requirements

### Entities involved (conceptual)
- `Role`
- `Permission`
- `RolePermission`
- `AuditLog` (or equivalent audit event read model)

### Fields (type, required, defaults)
**Role**
- `roleId` (string/UUID; read-only)
- `roleName` (string; required; likely immutable after create—needs confirmation)
- `description` (string; optional)

**Permission**
- `permissionKey` (string; read-only)
- `domain` (string; read-only; derived from key or stored)
- `description` (string; read-only; optional)
- `deprecated` (boolean; read-only; optional)

**RolePermission**
- `roleId` / `roleName` (reference; read-only on display)
- `permissionKey` (reference; read-only on display)
- `grantedAt` (datetime; read-only; if available)

**AuditLog**
- `timestamp`
- `actorId` (admin principal)
- `eventType` (e.g., `role.permission.grant`)
- `details` (structured; display-friendly summary needs confirmation)
- `outcome` (success/failure/deny)
- `correlationId`

### Read-only vs editable by state/role
- Only authorized admins can edit roles and grants; everyone else gets denied/hidden based on backend authorization.
- Permissions are always read-only.

### Derived/calculated fields
- Permission domain can be derived from `permissionKey` prefix before first `:` for display/filtering when backend doesn’t provide `domain`.

---

## 9. Service Contracts (Frontend Perspective)

> Exact endpoint paths and payload shapes are **not defined in provided inputs**; below is a frontend contract **template** that must be mapped to actual Moqui services/endpoints once confirmed.

### Load/view calls
- List roles
  - `GET /roles?search=...&page=...`
- Get role detail (including granted permissions)
  - `GET /roles/{roleId}`
  - `GET /roles/{roleId}/permissions`
- List permissions
  - `GET /permissions?domain=pricing`
- Query audit logs (RBAC-related)
  - `GET /audit?eventType=role.permission.grant&from=...&to=...`

### Create/update calls
- Create role
  - `POST /roles` with `{ roleName, description }`
- Update role
  - `PUT /roles/{roleId}` with `{ description }` (and roleName if editable—TBD)

### Submit/transition calls (grant/revoke)
- Grant permission to role
  - `POST /roles/{roleId}/permissions` with `{ permissionKey }` (or bulk `{ permissionKeys: [] }`—TBD)
- Revoke permission from role
  - `DELETE /roles/{roleId}/permissions/{permissionKey}`

### Error handling expectations
- `400`: show validation messages; map field errors to inputs if provided
- `401`: redirect to login (mechanism TBD) or show session expired
- `403`: show access denied; disable/hide mutation actions
- `404`: show “Not found” with safe details (role/permission missing)
- `409`: show conflict (duplicate role, duplicate grant, concurrent update)

---

## 10. State Model & Transitions

### Allowed states
- Not a domain state machine; UI has view/edit modes:
  - `viewing`
  - `editing` (role description)
  - `granting` (in-flight mutation)
  - `revoking` (in-flight mutation)

### Role-based transitions
- Transition into screens/actions depends on backend authorization:
  - If backend denies, UI must not provide a path to mutate data (still may allow read-only lists if permitted).

### UI behavior per state
- `viewing`: show data and available actions
- `editing`: enable role fields and save/cancel
- `granting/revoking`: disable controls, show spinner/progress, then refresh role permissions list on success

---

## 11. Alternate / Error Flows

### Validation failures
- Creating role with missing name → show required error
- Backend rejects roleName format/length → show backend message
- Grant with invalid permission key (should not happen if selected from list) → show error and refresh permissions list

### Concurrency conflicts
- If two admins edit same role:
  - On `409`/ETag mismatch (if implemented), show “Role changed; reload required” and provide reload action (ETag support TBD).

### Unauthorized access
- Direct navigation to Security admin screens:
  - If backend returns `403` on initial load, show Access Denied screen/state.
- Partial authorization:
  - If roles list loads but audit query returns `403`, show audit as unavailable.

### Empty states
- No roles exist → show empty state with “Create Role” if authorized.
- No permissions for selected domain → show “No permissions registered for domain” and link to guidance (no guessing of registration steps).

---

## 12. Acceptance Criteria

### Scenario 1: View permissions filtered by domain
**Given** I am an authenticated Admin authorized to view permissions  
**When** I open the Permissions screen and select domain filter `pricing`  
**Then** I see a list of permissions whose keys begin with `pricing:` (or whose domain is `pricing`)  
**And** each permission is read-only (cannot edit key).

### Scenario 2: Create a new role
**Given** I am authorized to create roles  
**When** I submit a new role with a unique role name and optional description  
**Then** the role is created successfully  
**And** I am routed to the Role Detail screen for the new role.

### Scenario 3: Prevent duplicate role names
**Given** a role named `PricingAnalyst` already exists  
**And** I am authorized to create roles  
**When** I try to create another role named `PricingAnalyst`  
**Then** I receive a conflict error  
**And** the UI shows “Role name already exists” without creating a duplicate.

### Scenario 4: Grant a permission to a role
**Given** I am authorized to modify role permissions  
**And** a role exists  
**And** the permission `pricing:price_book:edit` exists in the permissions registry  
**When** I grant `pricing:price_book:edit` to the role  
**Then** the granted permissions list for that role includes `pricing:price_book:edit` after refresh  
**And** the UI shows a success confirmation.

### Scenario 5: Revoke a permission from a role
**Given** a role has permission `pricing:price_book:edit` granted  
**And** I am authorized to modify role permissions  
**When** I revoke `pricing:price_book:edit` from the role  
**Then** the role no longer shows that permission in its granted list  
**And** the UI shows a success confirmation.

### Scenario 6: Access denied for unauthorized user
**Given** I am authenticated but not authorized to manage roles/permissions  
**When** I open the Roles & Permissions area  
**Then** the UI shows an Access Denied state  
**And** no role/permission mutation controls are available.

### Scenario 7: Audit visibility for RBAC changes
**Given** I am authorized to view audit logs  
**When** I open the Audit view filtered to RBAC events  
**Then** I see entries for role/permission changes including timestamp, actorId, eventType, and outcome.

---

## 13. Audit & Observability

### User-visible audit data
- Provide an Audit tab/view showing RBAC change events (read-only) with:
  - timestamp
  - actor/principal identifier
  - event type (e.g., `role.permission.grant`, `role.permission.revoke`, `role.created`, `role.updated`, `permission.registered`, `principal.role.assign` if present)
  - target identifiers (roleName/roleId, permissionKey where applicable)
  - correlationId (display optional; useful for support)

### Status history
- Not applicable beyond audit entries for changes.

### Traceability expectations
- Frontend should pass/propagate `correlationId` if the platform has a standard header (TBD); otherwise rely on backend-generated correlation IDs and display them when returned.

---

## 14. Non-Functional UI Requirements
- **Performance:** Lists should support pagination for large permission registries (page size configurable).
- **Accessibility:** All form controls keyboard-navigable; proper labels; error messages associated to fields.
- **Responsiveness:** Usable on tablet widths; admin pages may be desktop-first but must not break on smaller screens.
- **i18n/timezone:** Display timestamps in user’s locale/timezone (source timezone from backend; formatting handled in UI).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide explicit empty states for no roles/permissions results; safe because it does not change domain behavior and only affects presentation. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-PAGINATION: Add pagination controls to roles/permissions lists; safe because it’s a UI ergonomics concern and backend already implies list querying. (Impacted: UX Summary, Service Contracts)
- SD-ERR-HTTP-MAP: Standard mapping of HTTP 400/401/403/404/409 to user-facing messages; safe because it follows backend contract patterns without inventing policies. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions
1. **Backend endpoints & schemas:** What are the exact REST paths, request/response schemas, and error shapes for Role CRUD, RolePermission grant/revoke, and AuditLog query in `pos-security-service`?
2. **Authorization scope:** Which permission(s) gate access to:
   - viewing roles
   - creating/updating roles
   - granting/revoking permissions
   - viewing audit logs  
   (Per security agent contract, we cannot invent permission names.)
3. **Role name mutability:** Is `roleName` editable after creation, or immutable with only `description` editable?
4. **Role assignment UI:** Is PrincipalRole/UserRole assignment **in scope** for this frontend story (assign roles to users/groups), or explicitly deferred?
5. **Location overrides:** The synopsis mentions “permissions mapped to actions including location overrides.” Is there any UI requirement now for location-scoped grants/overrides (ABAC-like), or is this strictly global RBAC for this story?
6. **Audit API & retention:** What audit event types are available via the query API, and does the backend return structured `details` suitable for display, or only unstructured text?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Define Roles and Permission Matrix for Product/Pricing — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/106

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

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

====================================================================================================

END BACKEND REFERENCES