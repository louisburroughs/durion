## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:security
- status:ready

### Recommended
- agent:security-domain-agent
- agent:story-authoring

### Blocking / Risk
- none

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
  - Assign/revoke roles for users (principal-role assignments deferred per DECISION-INVENTORY-007; implement role CRUD and grants only in v1)
  - View audit log entries related to RBAC changes (read-only; security-owned audit per DECISION-INVENTORY-012)
- Frontend enforcement behaviors:
  - Show/hide/disable protected admin actions based on authorization responses (deny-by-default per DECISION-INVENTORY-001)
  - Display clear error states for 401/403/validation/conflicts using canonical envelope (DECISION-INVENTORY-015)
- Basic filtering/search/pagination for lists (safe UI defaults)

### Out-of-scope
- Defining the canonical permission list itself (code-first registry per DECISION-INVENTORY-006; owned by security backend/manifest)
- Authentication mechanism (IdP/JWT) implementation details
- Location-scoped RBAC (deferred per DECISION-INVENTORY-008; tenant-scoped only in v1)
- Role inheritance / hierarchy rules (not specified)
- Principal-role assignment UI screens (data model supports it per DECISION-INVENTORY-007, but UI deferred unless explicitly added to scope)
- "Protected operations" enforcement across the entire POS UI (this story focuses on admin RBAC management UI; wiring other features belongs to their stories)

---

## 3. Actors & Stakeholders
- **Admin**: performs RBAC configuration (requires security:role:* and security:role_permission:* permissions)
- **Security Officer / Auditor**: reviews audit trail (requires security:audit_entry:view permission; read-only)
- **POS Operator**: indirectly impacted by permissions (not a UI actor here)
- **Security backend service (`pos-security-service`)**: system of record for RBAC entities and audit emission
- **People domain / Identity**: provides user/principal lookup (integration detail; not owned here)

---

## 4. Preconditions & Dependencies
- Moqui app can call backend services at `/api/v1/security/*` for:
  - Role CRUD
  - Permission registry read
  - Grant/revoke permissions to roles
  - Audit log query
- Backend enforces deny-by-default authorization (DECISION-INVENTORY-001) and returns standard HTTP status codes (401/403/400/404/409) using canonical error envelope (DECISION-INVENTORY-015).
- The permission registry exists (code-first per DECISION-INVENTORY-006; registered on service startup).
- Tenant context is available from auth claims or request context (tenant-scoped RBAC per DECISION-INVENTORY-008).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main nav: **Administration → Security → Roles & Permissions** (gated by security:role:view)
- Deep links:
  - `/admin/security/roles`
  - `/admin/security/roles/{roleId}`
  - `/admin/security/audit` (RBAC audit events)

### Screens to create/modify (Moqui)
Create new screens under a security admin root:
- `component://pos-security/screen/admin/security/SecurityAdmin.xml` (root/menu)
- `.../RoleList.xml`
- `.../RoleDetail.xml`
- `.../AuditLog.xml` (filtered to RBAC-related events)

Each screen uses Moqui screen forms + transitions that call services (or REST via Moqui service facade, depending on project conventions).

### Navigation context
- From Role List → Role Detail
- From Role Detail tabs/sections:
  - Role info (name immutable per DECISION-INVENTORY-004; description editable)
  - Permissions granted to role
  - Audit events for this role

### User workflows
**Happy path: create role + grant permissions**
1. Admin opens Role List → "Create Role" (requires security:role:create)
2. Enters role name + description → Save
3. Backend validates uniqueness using normalized name (trim + collapse whitespace + lowercase per DECISION-INVENTORY-003)
4. On Role Detail, opens "Permissions" section → selects permissions → Grant (requires security:role_permission:grant)
5. System confirms; audit events visible in Audit Log screen

**Alternate path: revoke permission**
1. Admin views Role Detail → "Permissions" tab
2. Selects permission(s) → Revoke (requires security:role_permission:revoke)
3. Backend removes grants idempotently (DECISION-INVENTORY-005)
4. UI refreshes; audit entry created

**Alternate path: edit role description**
1. Admin opens Role Detail → Edit
2. Updates description only (roleName is immutable per DECISION-INVENTORY-004)
3. Backend rejects any roleName change with 400 ROLE_NAME_IMMUTABLE
4. UI shows success on description update

---

## 6. Functional Behavior

### Triggers
- Admin navigates to security admin screens (requires security:role:view)
- Admin submits create/update/grant/revoke actions (requires corresponding permissions)
- Admin filters/searches lists

### UI actions
- Role list: search by name (normalized match), open detail
- Create role: submit form (requires security:role:create)
- Edit role: update description only (requires security:role:update; roleName immutable)
- Grant permission(s): multi-select grant action (requires security:role_permission:grant)
- Revoke permission: per-row or multi-select revoke action (requires security:role_permission:revoke)
- Audit log: filter by event type/date/actor/roleId (requires security:audit_entry:view)

### State changes (frontend)
- Local UI state: loading/error/success banners
- No domain state machine is defined; treat entities as active records.
- Roles are active by default (no status field).

### Service interactions
- All mutations call backend; on success, refresh the affected lists/details from backend.
- On 401 → route to login/session-expired flow (project convention).
- On 403 → show "Not authorized" and disable mutation controls.
- On 409 ROLE_NAME_TAKEN → show conflict message (normalized name collision per DECISION-INVENTORY-003).
- All responses include correlationId per DECISION-INVENTORY-015.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Role name required; enforce basic client-side validation (non-empty, trim). Server remains source of truth for uniqueness (normalized per DECISION-INVENTORY-003).
- Permission grant/revoke requires selected permission(s).
- Role name is immutable after create (DECISION-INVENTORY-004); UI must disable roleName field on edit.

### Enable/disable rules
- If user lacks security:role:view:
  - Entire Security Admin menu hidden OR screen shows 403 state.
- If user lacks security:role:create, hide "Create Role" button.
- If user lacks security:role_permission:grant, disable grant controls.
- If user lacks security:role_permission:revoke, disable revoke controls.
- Mutation buttons disabled while request in-flight to avoid double-submit.
- Deny-by-default (DECISION-INVENTORY-001): if permission check endpoint is unavailable, UI must default to hiding/disabling protected actions and show an error.

### Visibility rules
- Permissions list display includes permissionKey (immutable per DECISION-INVENTORY-002) and description.
- Permission keys follow format: `domain:resource:action` (snake_case).
- Audit screen shows only RBAC-relevant event types by default (role created/updated, permission granted/revoked).
- Audit entries return curated fields by default (DECISION-INVENTORY-013); raw payload is not exposed in v1.

### Error messaging expectations
- Validation errors: show field-level messages where possible.
- Authorization errors: "Insufficient permissions to perform this action." (403)
- Conflicts: "Role name already exists" (409 ROLE_NAME_TAKEN; normalized collision per DECISION-INVENTORY-003).
- Immutability: "Role name cannot be changed; only description can be updated." (400 ROLE_NAME_IMMUTABLE per DECISION-INVENTORY-004).
- Not found: "Role not found or you no longer have access." (404)

---

## 8. Data Requirements

### Entities involved (frontend perspective)
Security domain (SoR backend):
- Role
- Permission (code-first registry per DECISION-INVENTORY-006)
- RolePermission (grant mapping)
- AuditEntry (RBAC events; append-only per DECISION-INVENTORY-012)

### Fields (type, required, defaults)
**Role**
- `roleId` (UUID, read-only; immutable identifier per DECISION-INVENTORY-004)
- `roleName` (string, required, immutable; normalized for uniqueness per DECISION-INVENTORY-003)
- `description` (string, optional, editable)
- `tenantId` (UUID, read-only; tenant-scoped per DECISION-INVENTORY-008)
- `createdAt` (datetime, read-only)
- `createdBy` (string, read-only)

**Permission**
- `permissionKey` (string, read-only, immutable; format `domain:resource:action` per DECISION-INVENTORY-002)
- `description` (string, read-only, optional)
- `domain` (string, read-only; extracted from key if needed)

**RolePermission**
- `roleId` (UUID, read-only)
- `permissionKey` (string, read-only)
- `assignedAt` (datetime, read-only)
- `assignedBy` (string, read-only)

**AuditEntry**
- `auditId` (UUID)
- `eventType` (string; enum: ROLE_CREATED, ROLE_UPDATED, PERMISSION_GRANTED, PERMISSION_REVOKED)
- `actorId` (string)
- `subjectType` (string; e.g., "ROLE")
- `subjectId` (UUID; roleId)
- `occurredAt` (datetime)
- `correlationId` (UUID, present in all responses per DECISION-INVENTORY-015)
- `detailsSummary` (string; curated view per DECISION-INVENTORY-013)

### Read-only vs editable by state/role
- Editable: role description (with security:role:update)
- Immutable: roleId, roleName (DECISION-INVENTORY-004), permissionKey (DECISION-INVENTORY-002)
- Read-only: permission registry, audit logs, generated IDs/timestamps

### Derived/calculated fields
- Display-only counts:
  - number of permissions in role (derived from RolePermission count)

---

## 9. Service Contracts (Frontend Perspective)

> All endpoints are under `/api/v1/security/*` per AGENT_GUIDE.md. Pagination uses `pageIndex/pageSize/totalCount`. All errors use canonical envelope per DECISION-INVENTORY-015.

### Load/view calls
- `GET /api/v1/security/roles?pageIndex={n}&pageSize={m}` → list roles (tenant-scoped per DECISION-INVENTORY-008)
  - Response: `{ items: [{ roleId, roleName, description, tenantId, createdAt, createdBy }], pageIndex, pageSize, totalCount }`
- `GET /api/v1/security/roles/{roleId}` → role detail
  - Response: `{ roleId, roleName, description, tenantId, createdAt, createdBy }`
- `GET /api/v1/security/permissions?pageIndex={n}&pageSize={m}` → permission registry list (code-first per DECISION-INVENTORY-006)
  - Response: `{ items: [{ permissionKey, description, domain }], pageIndex, pageSize, totalCount }`
- `GET /api/v1/security/roles/{roleId}/permissions` → current grants
  - Response: `{ items: [{ permissionKey, assignedAt, assignedBy }] }`
- `GET /api/v1/security/audit?eventType=ROLE_CREATED,ROLE_UPDATED,PERMISSION_GRANTED,PERMISSION_REVOKED&subjectId={roleId}&pageIndex={n}&pageSize={m}` → audit query (RBAC events per DECISION-INVENTORY-012)
  - Response: `{ items: [{ auditId, eventType, actorId, subjectType, subjectId, occurredAt, correlationId, detailsSummary }], pageIndex, pageSize, totalCount }`

### Create/update calls
- `POST /api/v1/security/roles` → create role (requires security:role:create)
  - Request: `{ roleName, description }`
  - Response (201): `{ roleId, roleName, description, tenantId, createdAt, createdBy, correlationId }`
  - Error (409 ROLE_NAME_TAKEN): normalized name collision per DECISION-INVENTORY-003
- `PATCH /api/v1/security/roles/{roleId}` → update role (requires security:role:update)
  - Request: `{ description }` (roleName updates rejected per DECISION-INVENTORY-004)
  - Response (200): `{ roleId, roleName, description, tenantId, updatedAt, updatedBy, correlationId }`
  - Error (400 ROLE_NAME_IMMUTABLE): if roleName is included in request

### Submit/transition calls (mutations)
- `POST /api/v1/security/roles/{roleId}/permissions:grant` (requires security:role_permission:grant)
  - Request: `{ permissionKeys: ["domain:resource:action"] }`
  - Response (200): `{ grantedCount, correlationId }`
  - Idempotent per DECISION-INVENTORY-005 (duplicate grants are no-op)
- `POST /api/v1/security/roles/{roleId}/permissions:revoke` (requires security:role_permission:revoke)
  - Request: `{ permissionKeys: ["domain:resource:action"] }`
  - Response (200): `{ revokedCount, correlationId }`
  - Idempotent per DECISION-INVENTORY-005 (missing grants are no-op)

### Error handling expectations (DECISION-INVENTORY-015)
All error responses return canonical envelope:
```json
{
  "code": "ROLE_NAME_TAKEN | ROLE_NAME_IMMUTABLE | VALIDATION_FAILED | FORBIDDEN | NOT_FOUND | ...",
  "message": "Human-readable error message",
  "correlationId": "UUID",
  "fieldErrors": [{ "field": "roleName", "message": "required" }] // optional
}
```

Status codes:
- `400` validation → surface messages; map to field errors when keys provided
- `401` unauthenticated → route to login/session recovery
- `403` unauthorized → show not-authorized state; disable controls
- `404` missing resource → show not-found
- `409` conflict (ROLE_NAME_TAKEN / concurrent changes) → show conflict banner with refresh option
- Network/timeouts → retry affordance; do not assume mutation succeeded

---

## 10. State Model & Transitions

### Allowed states
- No explicit state machine defined for Role/Permission. Treat as active records.
- Roles are active by default (no status field in v1).

### Role-based transitions
- Admin-only transitions (requires corresponding permissions):
  - Create role (security:role:create)
  - Update role description (security:role:update)
  - Grant permissions (security:role_permission:grant)
  - Revoke permissions (security:role_permission:revoke)
- Auditor-only transitions:
  - View roles/permissions (security:role:view, security:permission:view)
  - View audit (security:audit_entry:view)

### UI behavior per state
- Roles are always active (no enable/disable toggle in v1).

---

## 11. Alternate / Error Flows

### Validation failures
- Empty role name → inline error; do not submit
- Invalid role name rejected by backend → show backend message; keep user input
- Attempt to update roleName → backend returns 400 ROLE_NAME_IMMUTABLE; UI shows "Role name cannot be changed; only description can be updated."

### Concurrency conflicts
- If role was modified elsewhere and backend returns 409:
  - UI shows conflict message and provides "Reload role" action.
  - Do not auto-merge.
- If role name collision (normalized per DECISION-INVENTORY-003):
  - Backend returns 409 ROLE_NAME_TAKEN.
  - UI shows "Role name already exists (case-insensitive match)."

### Unauthorized access
- Direct navigation to admin screens without security:role:view:
  - Screen returns 403 state with link back to home.
  - No mutation controls rendered.
- Missing specific permission (e.g., security:role:create):
  - "Create Role" button hidden.
  - Attempt via direct API call returns 403 with correlationId.

### Empty states
- No roles exist → show empty state with "Create Role" if authorized (security:role:create)
- No permissions granted to role → show empty state with "Grant Permissions" if authorized (security:role_permission:grant)
- No audit events found → "No matching events"

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Admin can create a role with least privilege defaults (deny-by-default per DECISION-INVENTORY-001)
**Given** the user is authenticated as an Admin with security:role:create permission  
**When** the user creates a role with a unique role name and optional description  
**Then** the role is created successfully with HTTP 201  
**And** the response includes roleId and correlationId  
**And** the role shows zero permissions granted by default  
**And** the UI refreshes the role detail from the backend

### Scenario 2: Duplicate role name is rejected (normalized uniqueness per DECISION-INVENTORY-003)
**Given** a role named "Cashier" already exists  
**When** the Admin attempts to create another role named " CASHIER " (different casing and whitespace)  
**Then** the backend returns 409 with code ROLE_NAME_TAKEN and correlationId  
**And** the UI shows an error indicating the role name already exists (case-insensitive match)  
**And** no duplicate role appears in the list

### Scenario 3: Admin grants permissions to a role (idempotent per DECISION-INVENTORY-005)
**Given** an Admin with security:role_permission:grant permission is viewing an existing role detail  
**And** the permission registry is available (code-first per DECISION-INVENTORY-006)  
**When** the Admin grants one or more permissions to the role  
**Then** the role's granted permissions list updates to include the new permissions  
**And** the UI shows a success confirmation with correlationId  
**And** the UI can navigate to audit log view and see a PERMISSION_GRANTED event

### Scenario 4: Admin revokes a permission from a role (idempotent per DECISION-INVENTORY-005)
**Given** a role currently has a permission granted  
**And** the Admin has security:role_permission:revoke permission  
**When** the Admin revokes that permission  
**Then** the permission no longer appears in the role's granted permissions list after refresh  
**And** the backend returns 200 with revokedCount and correlationId  
**And** a PERMISSION_REVOKED audit entry is created

### Scenario 5: Role name is immutable (DECISION-INVENTORY-004)
**Given** an Admin with security:role:update permission is editing an existing role  
**When** the Admin attempts to update the roleName field  
**Then** the backend returns 400 with code ROLE_NAME_IMMUTABLE and correlationId  
**And** the UI shows "Role name cannot be changed; only description can be updated."  
**And** the roleName field is disabled in the UI to prevent attempts

### Scenario 6: Unauthorized user cannot access RBAC admin mutations (deny-by-default per DECISION-INVENTORY-001)
**Given** the user is authenticated but lacks security:role:view permission  
**When** the user navigates to the Roles & Permissions screens  
**Then** the UI shows a not-authorized state (403) or hides the module entrypoint  
**And** no create/edit/grant/revoke actions are available  
**And** direct API calls return 403 with correlationId

### Scenario 7: Audit trail is queryable and redacted (DECISION-INVENTORY-012, DECISION-INVENTORY-013)
**Given** an Admin with security:audit_entry:view permission  
**When** the Admin views the Audit Log filtered by RBAC event types  
**Then** the UI displays curated audit entries with: auditId, eventType, actorId, subjectType, subjectId, occurredAt, correlationId, detailsSummary  
**And** raw payload fields are not exposed in v1 (redacted per DECISION-INVENTORY-013)  
**And** pagination is supported with pageIndex/pageSize/totalCount

---

## 13. Audit & Observability

### User-visible audit data
- Provide an Audit Log screen (read-only; requires security:audit_entry:view) showing RBAC-related events:
  - ROLE_CREATED
  - ROLE_UPDATED
  - PERMISSION_GRANTED
  - PERMISSION_REVOKED
- Audit entries are append-only (no edit/delete per DECISION-INVENTORY-012).
- Default response returns curated fields (detailsSummary) per DECISION-INVENTORY-013; raw payload not exposed in v1.

### Status history
- Role detail includes "Recent changes" section sourced from audit query filtered by subjectType=ROLE and subjectId={roleId}.

### Traceability expectations
- All mutation requests include correlationId in response per DECISION-INVENTORY-015.
- UI surfaces correlationId on success/error banners for support traceability.

---

## 14. Non-Functional UI Requirements

- **Performance:** lists must support pagination; initial load < 2s on typical datasets (exact SLA TBD).
- **Accessibility:** keyboard navigable forms; proper labels; error messages associated with inputs.
- **Responsiveness:** usable on tablet widths; admin screens may be desktop-first but must not break on smaller screens.
- **i18n/timezone:** display timestamps in user's locale/timezone per project convention; do not hardcode formats.

---

## 15. Applied Safe Defaults

- **SD-UX-EMPTY-STATES**: Provide explicit empty states with authorized primary actions; safe because it's purely presentational and reversible. Qualifies as safe per allowlist (UI ergonomics). (Impacted: UX Summary, Alternate/Empty states)
- **SD-UX-PAGINATION**: Paginate role/permission/audit lists with standard page size (pageIndex/pageSize/totalCount); safe because it doesn't change domain meaning and aligns with backend contract (DECISION-INVENTORY-015). Qualifies as safe per allowlist (UI ergonomics). (Impacted: UX Summary, Service Contracts)
- **SD-ERR-HTTP-MAP**: Standard mapping of 400/401/403/404/409/network to banners/field errors; safe because it follows canonical error envelope (DECISION-INVENTORY-015) and is UI-only. Qualifies as safe per allowlist (standard error-handling mapping when backend contract implies it). (Impacted: Service Contracts, Error Flows)
- **SD-OBS-CORRELATION-ID**: Surface correlationId in UI banners for support traceability; safe because it aligns with observability boilerplate (DECISION-INVENTORY-015). Qualifies as safe per allowlist (observability boilerplate consistent with workspace defaults). (Impacted: Audit & Observability, Service Contracts, Error Flows)

---

## 16. Open Questions

- none (all questions resolved using AGENT_GUIDE.md decisions)

---

## Resolution Summary

All blocking questions have been resolved using the normative decisions from AGENT_GUIDE.md:

1. **Permission/role management API contract** → Resolved via DECISION-INVENTORY-015 (canonical error envelope and status codes) and documented REST base path `/api/v1/security/*` with pagination using `pageIndex/pageSize/totalCount`.

2. **Authorization model for admin UI** → Resolved via DECISION-INVENTORY-002 (permission keys defined and owned by Security):
   - View: `security:role:view`, `security:permission:view`
   - Create: `security:role:create`
   - Update: `security:role:update`
   - Grant: `security:role_permission:grant`
   - Revoke: `security:role_permission:revoke`
   - Audit: `security:audit_entry:view`

3. **Principal identity & scoping** → Resolved via DECISION-INVENTORY-008 (tenant-scoped RBAC; no location-scoped RBAC in v1) and DECISION-INVENTORY-007 (principal-role assignment data model exists but UI deferred for v1).

4. **Role uniqueness & casing rules** → Resolved via DECISION-INVENTORY-003 (normalized uniqueness: trim + collapse whitespace + lowercase) and DECISION-INVENTORY-004 (roleName immutable; rename requires recreate/migrate).

5. **Audit visibility** → Resolved via DECISION-INVENTORY-012 (RBAC audit owned by security; financial exception audit owned by audit domain) and DECISION-INVENTORY-013 (curated fields by default; raw payload redacted).

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
