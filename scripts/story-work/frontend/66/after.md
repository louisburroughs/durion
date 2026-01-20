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
[FRONTEND] Security: Roles & Permission Matrix Admin UI (Roles, Grants, Assignments, Audit)

### Primary Persona
Admin (authorized security administrator)

### Business Value
Provide an admin-facing UI to manage RBAC (roles, permissions, role grants, user role assignments) with audit visibility so sensitive POS actions can be controlled with least privilege and changes are traceable.

---

## 2. Story Intent

### As a / I want / So that
- **As an Admin**, I want to create/edit roles, grant permissions to roles, and assign roles to users  
- **So that** sensitive operations (refunds, overrides, voids, adjustments, cancellations) are protected by explicit permission checks and all changes are auditable.

### In-scope
- Moqui frontend screens to:
  - List/view/create roles
  - View permissions registry and grant/revoke permissions on roles
  - Assign/revoke roles for users (principal)
  - View audit log entries related to RBAC changes (read-only)
- Frontend enforcement behaviors:
  - Show/hide/disable protected admin actions based on authorization responses (deny-by-default)
  - Display clear error states for 401/403/validation/conflicts
- Basic filtering/search/pagination for lists (safe UI defaults)

### Out-of-scope
- Defining the canonical permission list itself (owned by security backend/manifest)
- Authentication mechanism (IdP/JWT) implementation details
- Location/tenant scoping policy (unless explicitly provided)
- Role inheritance / hierarchy rules (not specified)
- “Protected operations” enforcement across the entire POS UI (this story focuses on admin RBAC management UI; wiring other features belongs to their stories)

---

## 3. Actors & Stakeholders
- **Admin**: performs RBAC configuration
- **Security Officer / Auditor**: reviews audit trail (read-only)
- **POS Operator**: indirectly impacted by permissions (not a UI actor here)
- **Security backend service (`pos-security-service`)**: system of record for RBAC entities and audit emission
- **People domain / Identity**: provides user/principal lookup and person linkage (integration detail; not owned here)

---

## 4. Preconditions & Dependencies
- Moqui app can call backend services for:
  - Role CRUD
  - Permission registry read
  - Grant/revoke permissions to roles
  - Assign/revoke roles to principals/users
  - Audit log query
- Backend enforces deny-by-default authorization and returns standard HTTP status codes (401/403/400/404/409).
- The permission registry exists (reference mentions `permissions_v1.yml` in backend repo), and an API exists to list permissions (exact endpoint TBD).
- A way to search/select a user/principal exists (endpoint + shape TBD).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Administration → Security → Roles & Permissions**
- Deep links:
  - `/admin/security/roles`
  - `/admin/security/roles/{roleId}`
  - `/admin/security/users` (or `/admin/security/principals`) (TBD)

### Screens to create/modify (Moqui)
Create new screens under a security admin root:
- `component://pos-security/screen/admin/security/SecurityAdmin.xml` (root/menu)
- `.../RoleList.xml`
- `.../RoleDetail.xml`
- `.../UserRoleAssignments.xml`
- `.../AuditLog.xml` (filtered to RBAC-related events)

Each screen uses Moqui screen forms + transitions that call services (or REST via Moqui service facade, depending on project conventions).

### Navigation context
- From Role List → Role Detail
- From Role Detail tabs/sections:
  - Role info
  - Permissions granted to role
  - Users assigned to role (optional; depends on API)
  - Audit events for this role

### User workflows
**Happy path: create role + grant permissions**
1. Admin opens Role List → “Create Role”
2. Enters role name + description → Save
3. On Role Detail, opens “Permissions” section → selects permissions → Grant
4. System confirms; audit events visible in Audit Log screen

**Alternate path: assign role to user**
1. Admin opens User Role Assignments screen
2. Searches user/principal
3. Assigns one or more roles
4. Role takes effect immediately (frontend reflects success; no caching assumptions)

**Alternate path: revoke**
- Revoke permission from role / revoke role from user with confirmation and resulting UI refresh

---

## 6. Functional Behavior

### Triggers
- Admin navigates to security admin screens
- Admin submits create/update/grant/revoke/assign actions
- Admin filters/searches lists

### UI actions
- Role list: search by name, open detail
- Create role: submit form
- Edit role: update description/name (if allowed; TBD)
- Grant permission(s): multi-select grant action
- Revoke permission: per-row revoke action
- Assign role(s) to user: add assignment
- Revoke role from user: remove assignment
- Audit log: filter by event type/date/actor/role (as supported)

### State changes (frontend)
- Local UI state: loading/error/success banners
- No domain state machine is defined; treat entities as active records unless backend indicates statuses.

### Service interactions
- All mutations call backend; on success, refresh the affected lists/details from backend.
- On 401 → route to login/session-expired flow (project convention TBD).
- On 403 → show “Not authorized” and disable mutation controls.
- On 409 → show conflict message (e.g., duplicate role name).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Role name required; enforce basic client-side validation (non-empty, trim). Server remains source of truth.
- Permission grant/revoke requires selected permission(s).
- User selection required before assigning roles.

### Enable/disable rules
- If user lacks authorization to manage RBAC:
  - Entire Security Admin menu hidden OR screen shows 403 state (implementation depends on how authz is exposed to UI; TBD).
- Mutation buttons disabled while request in-flight to avoid double-submit.
- Deny-by-default: if permission check endpoint is unavailable, UI must default to hiding/disabling protected actions and show an error.

### Visibility rules
- Permissions list display includes permission key and description (if provided).
- Audit screen shows only RBAC-relevant event types by default (filter), with ability to broaden if API supports.

### Error messaging expectations
- Validation errors: show field-level messages where possible.
- Authorization errors: “Insufficient permissions to perform this action.”
- Conflicts: “Role name already exists” (if 409 indicates unique constraint).
- Not found: “Role not found or you no longer have access.”

---

## 8. Data Requirements

### Entities involved (frontend perspective)
Security domain (SoR backend):
- Role
- Permission
- RolePermission (grant mapping)
- PrincipalRole / UserRole (assignment mapping)
- AuditLog (RBAC events)

### Fields (type, required, defaults)
**Role**
- `roleId` (string/uuid, read-only)
- `roleName` (string, required, unique; casing rules TBD)
- `description` (string, optional)
- `createdAt` (datetime, read-only)
- `createdBy` (string, read-only)

**Permission**
- `permissionKey` (string, read-only; format `domain:resource:action`)
- `description` (string, read-only, optional)
- `domain` (string, read-only, optional if derived)
- `resource` / `action` (string, read-only, optional if derived)

**RolePermission**
- `roleId` (read-only)
- `permissionKey` (read-only)
- `assignedAt` (datetime, read-only)
- `assignedBy` (string, read-only)

**PrincipalRole/UserRole**
- `principalId` or `userId` (string, read-only once selected)
- `roleId` (string)
- `assignedAt`, `assignedBy` (read-only)
- `revokedAt`, `revokedBy` (read-only; if soft revoke exists; TBD)

**AuditLog**
- `auditId` (string)
- `eventType` (string)
- `actorId` (string)
- `subjectType` + `subjectId` (strings, if provided)
- `occurredAt` (datetime)
- `correlationId` (string, optional)
- `details` (json/string, read-only)

### Read-only vs editable by state/role
- Editable: roleName/description (if backend allows), grant/revoke mappings, assignments
- Read-only: permission registry, audit logs, generated IDs/timestamps

### Derived/calculated fields
- Display-only counts:
  - number of permissions in role
  - number of users assigned to role (only if API provides)

---

## 9. Service Contracts (Frontend Perspective)

> Endpoints are **TBD** unless confirmed by repo conventions; frontend must be implemented behind a service abstraction so URLs can be swapped without UI rewrites.

### Load/view calls
- `GET roles` → list roles (pagination)
- `GET roles/{roleId}` → role detail
- `GET permissions` → permission registry list (filter by domain optional)
- `GET roles/{roleId}/permissions` → current grants (or included in role detail)
- `GET principals/search?q=` → search users/principals (TBD)
- `GET principals/{principalId}/roles` → assignments (TBD)
- `GET audit?eventType in (...)&subjectId=...` → audit query (TBD)

### Create/update calls
- `POST roles` → create role
- `PUT/PATCH roles/{roleId}` → update role (TBD)

### Submit/transition calls (mutations)
- `POST roles/{roleId}/permissions:grant` with `{ permissionKeys: [] }` (TBD)
- `POST roles/{roleId}/permissions:revoke` with `{ permissionKeys: [] }` (TBD)
- `POST principals/{principalId}/roles:assign` with `{ roleIds: [] }` (TBD)
- `POST principals/{principalId}/roles:revoke` with `{ roleIds: [] }` (TBD)

### Error handling expectations
- `400` validation → surface messages; map to field errors when keys provided
- `401` unauthenticated → route to login/session recovery
- `403` unauthorized → show not-authorized state; disable controls
- `404` missing resource → show not-found
- `409` conflict (duplicate name / concurrent changes) → show conflict banner with refresh option
- Network/timeouts → retry affordance; do not assume mutation succeeded

---

## 10. State Model & Transitions

### Allowed states
- No explicit state machine defined for Role/Permission. Treat as active records.
- Assignments may be active/revoked if backend models revocation vs delete (TBD).

### Role-based transitions
- Admin-only transitions:
  - Create role
  - Update role
  - Grant/revoke permissions
  - Assign/revoke roles
- Auditor-only (or read-only admin) transitions:
  - View roles/permissions/audit

### UI behavior per state
- If assignment supports “revoked” history:
  - Show active roles by default; allow “include revoked” toggle.

---

## 11. Alternate / Error Flows

### Validation failures
- Empty role name → inline error; do not submit
- Invalid role name rejected by backend → show backend message; keep user input

### Concurrency conflicts
- If role was modified elsewhere and backend returns 409:
  - UI shows conflict message and provides “Reload role” action.
  - Do not auto-merge.

### Unauthorized access
- Direct navigation to admin screens without permission:
  - Screen returns 403 state with link back to home.
  - No mutation controls rendered.

### Empty states
- No roles exist → show empty state with “Create Role” if authorized
- No permissions granted to role → show empty state with “Grant Permissions”
- No audit events found → “No matching events”

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Admin can create a role with least privilege defaults
**Given** the user is authenticated as an Admin authorized to manage roles  
**When** the user creates a role with a unique role name and optional description  
**Then** the role is created successfully  
**And** the role shows zero permissions granted by default  
**And** the UI refreshes the role detail from the backend

### Scenario 2: Duplicate role name is rejected
**Given** a role named "Cashier" already exists  
**When** the Admin attempts to create another role named "Cashier"  
**Then** the backend returns a conflict error (e.g., 409)  
**And** the UI shows an error indicating the role name already exists  
**And** no duplicate role appears in the list

### Scenario 3: Admin grants permissions to a role
**Given** an Admin is viewing an existing role detail  
**And** the permission registry is available  
**When** the Admin grants one or more permissions to the role  
**Then** the role’s granted permissions list updates to include the new permissions  
**And** the UI shows a success confirmation  
**And** the UI can navigate to audit log view and see an RBAC change event for the grant (if audit query API supports it)

### Scenario 4: Admin revokes a permission from a role
**Given** a role currently has a permission granted  
**When** the Admin revokes that permission  
**Then** the permission no longer appears in the role’s granted permissions list after refresh  
**And** any backend error is surfaced to the user with no silent failure

### Scenario 5: Admin assigns a role to a user/principal
**Given** an Admin can search and select a user/principal  
**When** the Admin assigns a role to that principal  
**Then** the assignment appears in the principal’s role list after refresh  
**And** a success confirmation is shown  
**And** the UI displays any audit reference/correlation id returned (if provided)

### Scenario 6: Unauthorized user cannot access RBAC admin mutations
**Given** the user is authenticated but lacks authorization to manage RBAC  
**When** the user navigates to the Roles & Permissions screens  
**Then** the UI shows a not-authorized state (403) or hides the module entrypoint per convention  
**And** no create/edit/grant/assign actions are available

---

## 13. Audit & Observability

### User-visible audit data
- Provide an Audit Log screen (read-only) showing RBAC-related events:
  - role created/updated
  - permission granted/revoked
  - role assigned/revoked for principal
  - (optionally) access denied events if exposed

### Status history
- Role detail includes “Recent changes” section sourced from audit query filtered by subject roleId (if supported).

### Traceability expectations
- All mutation requests include a `correlationId` header if the frontend has one (or uses backend-generated id returned in response; TBD).
- UI surfaces correlationId on success/error banners when available for support.

---

## 14. Non-Functional UI Requirements

- **Performance:** lists must support pagination; initial load < 2s on typical datasets (exact SLA TBD).
- **Accessibility:** keyboard navigable forms; proper labels; error messages associated with inputs.
- **Responsiveness:** usable on tablet widths; admin screens may be desktop-first but must not break on smaller screens.
- **i18n/timezone:** display timestamps in user’s locale/timezone per project convention; do not hardcode formats.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATES: Provide explicit empty states with authorized primary actions; safe because it’s purely presentational and reversible. (Impacted: UX Summary, Alternate/Empty states)
- SD-UX-PAGINATION: Paginate role/permission/audit lists with standard page size; safe because it doesn’t change domain meaning. (Impacted: UX Summary, Service Contracts)
- SD-ERR-HTTP-MAP: Standard mapping of 400/401/403/404/409/network to banners/field errors; safe because it follows implied backend semantics and is UI-only. (Impacted: Service Contracts, Error Flows)

---

## 16. Open Questions

1. **Permission/role management API contract (blocking):** What are the exact backend endpoints, request/response schemas, and identifiers for:
   - Role CRUD
   - Permission registry list (does it come from `permissions_v1.yml` via an endpoint?)
   - Grant/revoke permissions to role
   - Assign/revoke roles to principal/user
   - Audit log query and filter fields

2. **Authorization model for admin UI (blocking):** Which permissions (keys) govern:
   - Viewing RBAC screens
   - Creating/updating roles
   - Granting/revoking permissions
   - Assigning/revoking user roles  
   (Per security agent contract: do not invent permission names.)

3. **Principal identity & scoping (blocking):**
   - Is assignment performed against `userId`, `principalId`, or something else?
   - Is RBAC scoped by `tenantId` and/or `locationId`? If yes, how is scope selected in UI and enforced?

4. **Role uniqueness & casing rules (blocking):**
   - Is role name uniqueness case-insensitive?
   - Are updates to role name allowed after creation?

5. **Audit visibility (blocking):**
   - Is audit log queryable by the frontend? If yes, what fields are safe to display and what retention applies?
   - Should access denied events be visible in UI or only in logs?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Security: Define POS Roles and Permission Matrix — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/66

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