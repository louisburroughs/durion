# [FRONTEND] [STORY] Security: Define Roles and Permission Matrix for Product/Pricing
## Purpose
Provide POS Admin Security screens to manage RBAC roles and their granted permissions, plus a read-only permission registry and a curated audit trail of RBAC mutations. Enable authorized admins to create roles, edit role descriptions, and grant/revoke permissions with idempotent behavior. Ensure users without mutation privileges can still view roles/permissions while controls are hidden/disabled, and unauthorized access shows explicit denied states.

## Components
- Global page header with breadcrumb: Admin → Security → (Roles | Permissions | Audit)
- Security sub-navigation tabs/links: Roles, Permissions, Audit (Audit hidden if not authorized)
- Roles List screen
  - Search/filter input (optional)
  - Roles table/list (roleName, description, roleId, createdAt/updatedAt if available)
  - “Create Role” button (authorized only)
  - Empty state panel (no roles)
- Create Role form (modal or dedicated page)
  - Fields: roleName (required), description (optional)
  - Actions: Create, Cancel
  - Field-level validation messages (roleName)
  - Banner/error summary area for non-field errors
- Role Detail screen
  - Read-only metadata panel: roleId, roleName (immutable), createdAt, updatedAt
  - Editable description field (authorized only) with Save/Cancel and dirty-state indicator
  - Granted Permissions section (paged list/table)
    - Columns: permissionKey, description, domain/provenance (if provided), enabled (if provided)
    - Grant control: single permissionKey input/select + Grant button (authorized only)
    - Optional bulk/multi-select grant (only if backend supports)
    - Revoke action per row (authorized only)
    - Per-action in-flight indicators and per-item error display
    - Empty state (no granted permissions)
- Permissions Registry List screen (read-only)
  - Filter by domain prefix input (e.g., “product.” / “pricing.”)
  - Permissions table/list (permissionKey, description, domain/provenance, enabled)
  - Empty state (“No permissions found” + suggestion to adjust search/filter)
  - Optional link/action: “View roles with this permission” (only if backend supports query)
- Audit screen (curated view; read-only)
  - Audit entries table/list: auditId, timestamp, principal, actionType, outcome, summary, message
  - Details drawer/panel for “context” object (roleId/permissionKey when applicable)
  - Empty state (no entries)
- Standard states
  - Loading skeleton/spinner per screen
  - Access Denied / Not Authorized state
  - Toast/banner notifications for success/failure
  - Disabled/hidden controls for partial authorization

## Layout
- Top: Page title “Security” + breadcrumb; below it horizontal tabs: Roles | Permissions | Audit
- Main content switches by tab:
  - Roles: [Search + Create Role] above [Roles table] with row click → Role Detail
  - Role Detail: left/top [Role metadata + description editor] then [Granted Permissions list + grant controls]
  - Permissions: top [Domain filter + search] then [Permissions table]
  - Audit: top [filters optional] then [Audit table] with row → details panel

## Interaction Flow
1. Navigate Admin → Security → Roles
   1. If initial roles API returns 403, show Access Denied and do not render list data.
   2. If no roles exist, show empty state; show “Create Role” only if user is authorized to create.
2. Create a role (authorized users)
   1. Click “Create Role” to open form.
   2. Submit with empty roleName → backend returns validation error; highlight roleName and show message.
   3. Submit with normalized duplicate roleName → backend returns 409; show roleName field error “Role name already exists” and display returned error message.
   4. On success, return to Roles list and show new role; allow click-through to detail.
3. View role detail
   1. From Roles list, click a role row → Role Detail loads metadata and granted permissions.
   2. If user can view roles but cannot mutate, render description and permissions read-only; hide/disable Save/Grant/Revoke controls.
4. Update role description (roleName immutable)
   1. Edit description field → mark form dirty; enable Save/Cancel.
   2. Save → show in-flight state; on success refresh metadata and clear dirty state.
   3. If a tampered request attempts roleName update and backend rejects as immutable, show error banner and refresh role data to server state.
5. Grant permission(s) to role (idempotent)
   1. In Granted Permissions section, enter/select permissionKey and click Grant.
   2. Show per-action in-flight state; on completion refresh granted list.
   3. If permission already granted, backend returns success no-op or deterministic conflict; UI treats as success and end state remains “granted” (no error shown).
   4. If bulk grant is supported, submit multi-select; show progress and per-item errors while continuing remaining grants.
6. Revoke permission from role (idempotent)
   1. Click Revoke on a permission row; confirm optional.
   2. Show per-action in-flight state; on completion refresh granted list.
   3. If grant does not exist, backend returns success no-op or deterministic not-found; UI treats as success and end state is “not granted”.
7. View Permissions registry (read-only)
   1. Navigate to Permissions tab; list loads.
   2. Apply domain prefix filter (e.g., “product.”) to narrow results.
   3. If no results, show “No permissions found” and suggest adjusting search/filter; note “Permissions are registered by deployed services.”
   4. If backend supports, optionally open “View roles with this permission”; otherwise omit the control entirely.
8. View Audit (read-only; security-owned)
   1. If user lacks audit permission, hide Audit tab; direct navigation shows Not Authorized.
   2. If authorized, show curated audit list; selecting an entry shows context (roleId/permissionKey) without raw payload.

## Notes
- Screens to implement under POS Admin Security area: Roles list; Role view/edit + grants; Permissions registry list (read-only); Audit entries (curated, security-owned).
- Breadcrumb and navigation must match: Admin → Security → (Roles | Permissions | Audit) with cross-links Roles list → Role detail and Role detail → granted permissions section.
- Idempotency requirements:
  - Re-granting an already granted permission must be treated as success (no-op or conflict handled as success).
  - Revoking a non-existent grant must be treated as success (no-op or not-found handled as success).
- No location scope fields are accepted or displayed for grants/revokes.
- Data fields:
  - Role: roleId (read-only), roleName (required on create; immutable), description (editable), createdAt/updatedAt (read-only if provided).
  - Permission: permissionKey (read-only), description/domain/provenance/enabled (read-only if provided).
  - AuditEntry curated: auditId, timestamp, principal, actionType, outcome, context object, summary, message; raw payload not displayed in v1 (redaction/gating).
- Authorization behavior:
  - 403 on initial API call → Access Denied state with no data rendered.
  - Partial authorization → lists visible but mutation controls hidden/disabled.
- Empty states:
  - No roles → empty state; Create Role only if authorized.
  - Role has no granted permissions → empty granted list; grant control only if authorized.
  - No permissions found → empty state with filter/search suggestion; do not instruct how to register beyond “registered by deployed services.”
- Ensure per-action in-flight states (grant/revoke/save) and clear error reporting (field-level for validation; banner/toast for general errors).
