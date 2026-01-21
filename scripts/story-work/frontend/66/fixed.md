## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:draft

### Recommended
- agent:security-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

**Rewrite Variant:** security-strict

---

## 1. Story Header

### Title
[FRONTEND] Security: Roles & Permission Matrix Admin UI (Roles, Grants, Permission Registry, Security Audit)

### Primary Persona
Admin (authorized security administrator)

### Business Value
Provide an admin-facing UI to manage RBAC roles and role-permission grants with security-owned audit visibility so sensitive POS actions can be controlled with least privilege and changes are traceable.

---

## 2. Story Intent

### As a / I want / So that
- **As an Admin**, I want to create roles, update role metadata, and grant/revoke permissions on roles  
- **So that** sensitive operations are protected by explicit permission checks (deny-by-default) and RBAC changes are auditable.

### In-scope
- Moqui frontend screens to:
  - List/view/create roles
  - Update role metadata (description only; roleName immutable)
  - View permission registry (read-only; code-first)
  - Grant/revoke permissions on roles (idempotent mutations)
  - View security-owned audit log entries related to RBAC changes (read-only)
- Frontend enforcement behaviors:
  - Route/menu gating and in-screen gating based on authorization (deny-by-default)
  - Display canonical error envelope responses (code/message/correlationId/fieldErrors)
- Basic filtering/search/pagination for lists (safe UI defaults)

### Out-of-scope
- Principal-role assignment UI (explicitly deferred for v1; model exists but UI not included) (DECISION-INVENTORY-007)
- Creating/editing permissions in UI (permission registry is code-first; UI read-only) (DECISION-INVENTORY-006)
- Authentication mechanism (IdP/JWT) implementation details
- Location-scoped RBAC/ABAC (not in v1; tenant-scoped only) (DECISION-INVENTORY-008)
- Approval workflows/state machines (owned by workexec; security only gates access) (DECISION-INVENTORY-014)
- “Protected operations” wiring across the entire POS UI (this story focuses on RBAC admin UI)

---

## 3. Actors & Stakeholders
- **Admin**: performs RBAC configuration (role CRUD, grants)
- **Security Officer / Auditor**: reviews security audit trail (read-only)
- **Security backend service (`pos-security-service`)**: SoR for RBAC entities and security-owned audit emission/query
- **Developers/Operators**: rely on correlationId and audit entries for support/incident response

---

## 4. Preconditions & Dependencies
- Backend Security APIs exist and are reachable from the Moqui frontend under versioned base path:
  - `/api/v1/security/*` (DECISION-INVENTORY-015)
- Backend enforces deny-by-default authorization (DECISION-INVENTORY-001)
- Backend returns canonical REST error envelope for all non-2xx responses:
  - `code`, `message`, `correlationId`, optional `fieldErrors[]`, optional `details` (DECISION-INVENTORY-015)
- Permission keys exist and are registered code-first; UI can only list/search them (DECISION-INVENTORY-006, DECISION-INVENTORY-002)
- Tenant scoping is enforced server-side; tenant context is derived from trusted auth/request context and is not user-selectable in UI (DECISION-INVENTORY-008)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Administration → Security → Roles & Permissions**
- Deep links:
  - `/admin/security/roles`
  - `/admin/security/roles/{roleId}`
  - `/admin/security/permissions`
  - `/admin/security/audit`

### Screens to create/modify (Moqui)
Create screens under a security admin root (component path may be adjusted to repo conventions, but screen responsibilities are fixed):
- `component://pos-security/screen/admin/security/SecurityAdmin.xml` (root/menu)
- `.../RoleList.xml`
- `.../RoleDetail.xml` (includes permissions grant/revoke for the role)
- `.../PermissionRegistry.xml` (read-only list/search)
- `.../SecurityAuditLog.xml` (read-only; security-owned audit entries)

Each screen uses Moqui screen forms + transitions calling REST endpoints under `/api/v1/security/*` (DECISION-INVENTORY-015). All mutations must refresh from backend after success.

### Navigation context
- From Role List → Role Detail
- From Role Detail:
  - Role info (view roleName; edit description only)
  - Granted permissions (grant/revoke)
  - Link to audit filtered by roleId (subject)

### User workflows (happy + alternate paths)

**Happy path: create role + grant permissions**
1. Admin opens Role List → “Create Role”
2. Enters role name + description → Save
3. Navigates to Role Detail → “Permissions” → selects permissions → Grant
4. UI shows success and correlationId; audit entries visible in Security Audit Log

**Alternate path: update role description**
1. Admin opens Role Detail
2. Edits description → Save
3. UI refreshes role detail; roleName remains unchanged and non-editable

**Alternate path: revoke permission**
1. Admin opens Role Detail → Permissions list
2. Clicks revoke on a permission → confirm
3. UI refreshes grants list; audit entry appears

**Alternate path: view-only auditor**
1. Auditor opens Roles list and Permission Registry
2. Can view details and audit, but cannot see mutation controls; direct mutation attempts return 403 and UI shows not-authorized

---

## 6. Functional Behavior

### Triggers
- Admin navigates to security admin screens
- Admin submits create/update/grant/revoke actions
- User filters/searches/paginates lists
- User opens audit log and applies filters

### UI actions
- Role list:
  - Search by roleName (contains match)
  - Paginate
  - Open role detail
  - Create role (if authorized)
- Role detail:
  - View roleName (read-only)
  - Edit description (if authorized)
  - View granted permissions
  - Grant permissions (multi-select)
  - Revoke permission (per-row)
- Permission registry:
  - Search by permissionKey and/or description
  - Read-only view
- Security audit log:
  - Filter by date range, actor, subjectType/subjectId, eventType (as supported by API)
  - View curated details; no raw payload by default (DECISION-INVENTORY-013)

### State changes (frontend)
- Standard UI states per screen: `loading`, `loaded`, `empty`, `error`
- Mutation states: `submitting`, `success`, `error`
- No client-side caching assumptions; after any successful mutation, re-fetch affected resources from backend.

### Service interactions
- All reads/mutations use `/api/v1/security/*` endpoints (DECISION-INVENTORY-015)
- On 401: follow app’s session-expired/login flow
- On 403: show not-authorized state and remove/disable mutation controls (deny-by-default UX) (DECISION-INVENTORY-001)
- On 409: show conflict banner with backend `code` and `correlationId` and provide “Reload” action
- On network/timeouts: show retry affordance; do not assume mutation succeeded

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Role name required** on create:
  - Client-side: required + trim (do not collapse whitespace client-side; server is source of truth)
  - Server-side uniqueness uses normalized name: trim + collapse internal whitespace + lowercase (DECISION-INVENTORY-003)
- **Role name immutable**:
  - UI must not render roleName as editable on Role Detail
  - Any backend rejection must be surfaced (DECISION-INVENTORY-004)
- **Permission keys immutable and formatted**:
  - Display permissionKey exactly as returned
  - UI must not allow editing/creating permissions (DECISION-INVENTORY-002, DECISION-INVENTORY-006)
- **Grant/revoke selection required**:
  - Grant requires at least one permissionKey selected
  - Revoke requires explicit confirmation per permissionKey

### Enable/disable rules
- Deny-by-default UI posture (DECISION-INVENTORY-001):
  - If the user lacks required permission(s), hide entry points where feasible and also enforce in-screen disabling.
  - If authorization state cannot be determined (e.g., permissions endpoint fails), default to read-only UI and show an error banner.
- Disable mutation buttons while request is in-flight to prevent double-submit.
- Idempotent grant/revoke:
  - If backend treats duplicate grant/revoke as no-op success (preferred), UI should still show success and refresh (DECISION-INVENTORY-005)

### Visibility rules
- Permission registry is always read-only; show key + description (if provided) (DECISION-INVENTORY-006)
- Audit list shows curated fields by default; do not display raw payload fields unless a separate, explicitly gated endpoint/permission is introduced (not in scope here) (DECISION-INVENTORY-013)

### Error messaging expectations
- All errors must render:
  - `message` and `code` (if present)
  - `correlationId` (always expected on non-2xx) (DECISION-INVENTORY-015)
- Field validation:
  - Map `fieldErrors[]` to inline field messages when `field` matches form field names
- Common codes to handle explicitly (non-exhaustive):
  - `VALIDATION_FAILED` (400)
  - `FORBIDDEN` (403)
  - `NOT_FOUND` (404)
  - `ROLE_NAME_TAKEN` (409) (DECISION-INVENTORY-003)
  - `ROLE_NAME_IMMUTABLE` (400/409 per backend; treat as non-retryable) (DECISION-INVENTORY-004)

---

## 8. Data Requirements

### Entities involved (frontend perspective)
Security domain (SoR backend):
- `Role`
- `Permission` (registry; read-only)
- `RolePermission` (grant mapping)
- `AuditEntry` (security-owned audit entries for RBAC mutations)

### Fields (type, required, defaults)

**Role**
- `roleId` (uuid/string, read-only; immutable identifier used in APIs) (DECISION-INVENTORY-004)
- `roleName` (string, required on create; immutable after create) (DECISION-INVENTORY-004)
- `roleNameNormalized` (string, read-only if exposed; not required for UI)
- `description` (string, optional; editable)
- `createdAt` (datetime, read-only)
- `createdBy` (string, read-only)
- `updatedAt` (datetime, read-only, optional)
- `updatedBy` (string, read-only, optional)

**Permission (registry)**
- `permissionKey` (string, read-only; immutable; format `domain:resource:action` snake_case) (DECISION-INVENTORY-002)
- `description` (string, read-only, optional)
- `serviceName` / `source` (string, read-only, optional if provided by backend; helps trace code-first origin) (DECISION-INVENTORY-006)

**RolePermission**
- `roleId` (uuid/string, read-only)
- `permissionKey` (string, read-only)
- `assignedAt` (datetime, read-only, optional)
- `assignedBy` (string, read-only, optional)

**AuditEntry (security-owned)**
- `auditId` (uuid/string, read-only)
- `eventType` (string, read-only; e.g., ROLE_CREATED, ROLE_UPDATED, ROLE_PERMISSION_GRANTED, ROLE_PERMISSION_REVOKED)
- `actorId` (string, read-only)
- `occurredAt` (datetime, read-only)
- `correlationId` (string, read-only)
- `subjectType` (string, read-only; e.g., ROLE)
- `subjectId` (string, read-only; roleId)
- `detailsSummary` (string/object, read-only; curated/redacted) (DECISION-INVENTORY-013)

### Read-only vs editable by state/role
- Editable (authorized Admin only):
  - Role create: `roleName`, `description`
  - Role update: `description` only
  - Grants: grant/revoke role permissions
- Read-only:
  - Permission registry
  - Audit entries
  - All IDs/timestamps/actor fields

### Derived/calculated fields
- Display-only counts (if backend provides or can be derived from list sizes):
  - `permissionCount` for a role (derived from grants list length)

---

## 9. Service Contracts (Frontend Perspective)

All endpoints are normative per Security domain guide; frontend must not invent alternate paths.

### Load/view calls
- List roles (paginated):
  - `GET /api/v1/security/roles?pageIndex={n}&pageSize={n}&search={optional}`
  - Response: `{ items: Role[], pageIndex, pageSize, totalCount }` (DECISION-INVENTORY-015)
- Get role detail:
  - `GET /api/v1/security/roles/{roleId}`
- List permission registry (paginated):
  - `GET /api/v1/security/permissions?pageIndex={n}&pageSize={n}&search={optional}`
- List role permissions (if not included in role detail):
  - `GET /api/v1/security/roles/{roleId}/permissions?pageIndex={n}&pageSize={n}`
- List security audit entries (paginated):
  - `GET /api/v1/security/audit-entries?pageIndex={n}&pageSize={n}&eventType={optional}&subjectType={optional}&subjectId={optional}&actorId={optional}&from={optional}&to={optional}`
  - Returns curated fields only by default (DECISION-INVENTORY-013)

### Create/update calls
- Create role:
  - `POST /api/v1/security/roles`
  - Body: `{ roleName: string, description?: string }`
  - Errors:
    - 400 `VALIDATION_FAILED` with `fieldErrors` (DECISION-INVENTORY-015)
    - 409 `ROLE_NAME_TAKEN` (DECISION-INVENTORY-003)
- Update role (description only):
  - `PUT /api/v1/security/roles/{roleId}`
  - Body: `{ description?: string }`
  - Backend must reject roleName updates (DECISION-INVENTORY-004)

### Submit/transition calls (mutations)
RBAC mutation semantics must support idempotent grant/revoke; replace-set is optional (DECISION-INVENTORY-005). UI will implement grant/revoke; replace-set is not required.

- Grant permissions to role:
  - `POST /api/v1/security/roles/{roleId}/permissions/grant`
  - Body: `{ permissionKeys: string[] }`
- Revoke permissions from role:
  - `POST /api/v1/security/roles/{roleId}/permissions/revoke`
  - Body: `{ permissionKeys: string[] }`

### Error handling expectations
- All non-2xx responses include canonical envelope with `correlationId` (DECISION-INVENTORY-015)
- UI mapping:
  - 400: show inline field errors + banner
  - 401: session expired/login flow
  - 403: not authorized; hide/disable mutation controls
  - 404: show not found
  - 409: show conflict banner; provide reload action
  - Network/timeout: show retry; do not assume success

### Permission gating (route + action)
Security defines permission keys used to gate screens and actions (DECISION-INVENTORY-002). Minimum keys to implement:

- RBAC view:
  - `security:role:view`
  - `security:permission:view`
- Role CRUD:
  - `security:role:create`
  - `security:role:update`
- Grants:
  - `security:role_permission:grant`
  - `security:role_permission:revoke`
- Security audit:
  - `security:audit_entry:view`

UI must gate:
- Menu/route visibility: require corresponding `*:view` permission for entry
- Action buttons: require specific mutation permission; also handle backend 403 defensively

---

## 10. State Model & Transitions

### Allowed states
- No explicit state machine for Role/Permission in v1; treat as active records.
- Audit entries are append-only and immutable.

### Role-based transitions
- Admin transitions (permission-gated):
  - Create role (`security:role:create`)
  - Update role description (`security:role:update`)
  - Grant permission to role (`security:role_permission:grant`)
  - Revoke permission from role (`security:role_permission:revoke`)
- Read-only transitions:
  - View roles (`security:role:view`)
  - View permission registry (`security:permission:view`)
  - View security audit entries (`security:audit_entry:view`)

### UI behavior per state
- If user has view permission but lacks mutation permission:
  - Render read-only screens; hide/disable create/update/grant/revoke controls
- If user lacks view permission:
  - Hide menu entry; direct navigation shows 403 state

---

## 11. Alternate / Error Flows

### Validation failures
- Create role with empty roleName:
  - Client blocks submit and shows inline error
- Backend validation failure:
  - Show banner with `message` and `correlationId`
  - Map `fieldErrors` to inputs; preserve user input

### Concurrency conflicts
- If backend returns 409 on update/grant/revoke:
  - Show conflict banner with `code` and `correlationId`
  - Provide “Reload” action that re-fetches role detail and grants list
  - Do not attempt auto-merge

### Unauthorized access
- User without `security:role:view` deep-links to `/admin/security/roles`:
  - Screen renders 403 state
  - No data is displayed
- User with view but without mutation permission attempts action:
  - UI prevents via disabled controls
  - If attempted (e.g., stale UI), backend returns 403; UI shows not-authorized banner

### Empty states
- No roles exist:
  - Show empty state; show “Create Role” only if `security:role:create`
- Role has no permissions:
  - Show empty state; show “Grant Permissions” only if `security:role_permission:grant`
- Permission registry empty (unexpected):
  - Show empty state with guidance: “No permissions registered; verify services registered permissions.”
- No audit entries match filters:
  - Show “No matching events”

---

## 12. Acceptance Criteria

### Scenario 1: Authorized admin can create a role; roleName becomes immutable
**Given** the user is authenticated and has `security:role:create` and `security:role:view`  
**When** the user creates a role with `roleName="Price Manager"` and `description="Manages price overrides"`  
**Then** the role is created and returned with a `roleId`  
**And** the Role Detail screen shows `roleName` as read-only (not editable)  
**And** the role initially has zero granted permissions  
**And** the UI refreshes role detail from `GET /api/v1/security/roles/{roleId}`

### Scenario 2: Role name uniqueness uses normalization and returns deterministic conflict
**Given** a role exists with `roleName="manager"` in the current tenant  
**When** the user attempts to create a role with `roleName="  MANAGER  "`  
**Then** the backend responds with HTTP 409 and error `code="ROLE_NAME_TAKEN"` and a `correlationId`  
**And** the UI shows a conflict banner including the `correlationId`  
**And** no duplicate role appears in the role list after refresh

### Scenario 3: Admin can update role description but cannot update roleName
**Given** the user has `security:role:update` and `security:role:view`  
**And** a role exists with `roleName="Cashier"` and `description="Old"`  
**When** the user updates the role description to `"Front counter cashier"`  
**Then** the backend returns success and the UI shows the updated description after refresh  
**And** the UI does not provide any control to edit `roleName`

### Scenario 4: Admin can grant permissions to a role (idempotent)
**Given** the user has `security:role_permission:grant` and `security:role:view` and `security:permission:view`  
**And** a role exists with `roleId=R1`  
**When** the user grants permission keys `["security:role:view","security:permission:view"]` to role `R1`  
**Then** the UI refreshes the role permissions list and shows both permission keys as granted  
**And** if the same grant request is submitted again, the UI still results in a successful state and the grants list remains correct (no duplicates)

### Scenario 5: Admin can revoke a permission from a role (idempotent)
**Given** the user has `security:role_permission:revoke` and a role `R1` currently has `permissionKey="security:permission:view"` granted  
**When** the user revokes `["security:permission:view"]` from role `R1`  
**Then** after refresh the permission no longer appears in the granted list  
**And** if the revoke is submitted again, the UI results in a successful state and the granted list remains correct

### Scenario 6: Permission registry is read-only and uses immutable keys
**Given** the user has `security:permission:view`  
**When** the user opens the Permission Registry screen  
**Then** the UI lists permissions including `permissionKey` and optional `description`  
**And** there is no UI action to create, edit, or delete permissions  
**And** permission keys are displayed exactly as returned (no client-side rewriting)

### Scenario 7: Unauthorized users are denied by default
**Given** the user is authenticated but lacks `security:role:view`  
**When** the user navigates directly to `/admin/security/roles`  
**Then** the UI shows a 403 not-authorized state  
**And** no role data is displayed  
**And** no mutation controls are available

### Scenario 8: Security audit log is viewable with curated fields and correlationId
**Given** the user has `security:audit_entry:view`  
**When** the user opens the Security Audit Log screen and filters by `subjectType="ROLE"` and `subjectId=R1`  
**Then** the UI displays audit entries with `eventType`, `actorId`, `occurredAt`, and `correlationId`  
**And** the UI does not display raw/unredacted payload fields by default

---

## 13. Audit & Observability

### User-visible audit data
- Provide Security Audit Log screen showing security-owned RBAC mutation events (append-only):
  - Role created
  - Role updated (description changes)
  - Permission granted to role
  - Permission revoked from role

### Status history
- Role Detail provides a “Recent changes” view by linking to audit log filtered by `subjectType=ROLE` and `subjectId=roleId`.

### Traceability expectations
- UI must display `correlationId` on:
  - Error banners for any failed request (expected on all non-2xx) (DECISION-INVENTORY-015)
  - Success banners for mutations if the backend returns a correlationId in success payload/headers (if not available, omit on success but still show on errors)
- Audit API responses must be curated/redacted by default; any raw payload access is out-of-scope and would require explicit permission gating (DECISION-INVENTORY-013)

---

## 14. Non-Functional UI Requirements

- **Performance:** role list, permission registry, and audit list must be paginated; initial page load should complete within 2s on typical datasets.
- **Accessibility:** all forms keyboard navigable; inputs have labels; validation errors are programmatically associated with fields.
- **Responsiveness:** admin screens must remain usable at tablet widths; tables may scroll horizontally if needed.
- **i18n/timezone:** timestamps displayed in user locale/timezone per app convention; do not hardcode formats.

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATES: Provide explicit empty states with authorized primary actions; safe because it is presentational and does not change domain behavior. (Impacted: UX Summary, Alternate / Error Flows)
- SD-UX-PAGINATION: Paginate role/permission/audit lists with a standard page size; safe because it only affects presentation and load behavior. (Impacted: UX Summary, Service Contracts)
- SD-ERR-HTTP-MAP: Map 400/401/403/404/409/network to consistent banners and field errors using the canonical envelope; safe because it follows documented backend semantics. (Impacted: Service Contracts, Business Rules, Alternate / Error Flows)

---

## 16. Open Questions
- none