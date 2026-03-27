# CAP-253 Run Artifact — Security RBAC Admin UI

## Delivery Summary

- **Wave**: E
- **Branch**: cap/security-wave-e
- **Status**: Story #66 complete; Story #65 deferred (audit-domain API TBD)

## Implemented (Story #66 — RBAC Admin UI)

### Models

- `src/app/features/security/models/security.models.ts`
  - `SecurityRole` (includes `grantedPermissions?: SecurityPermission[]`, `status?`)
  - `SecurityPermission`, `CreateRoleRequest`, `UpdateRolePermissionsRequest`
  - `RoleAssignment`, `SecurityApiError`, `PagedResponse<T>`

### Service

- `src/app/features/security/services/security.service.ts`
  - `getAllRoles(page, size)` → GET /v1/roles
  - `createRole(req)` → POST /v1/roles
  - `getRoleByName(name)` → GET /v1/roles/{name}
  - `getAllPermissions(page, size)` → GET /v1/permissions
  - `updateRolePermissions(req)` → PUT /v1/roles/permissions
  - `revokeRoleAssignment(assignmentId)` → DELETE /v1/roles/assignments/{assignmentId}
  - `getUserRoleAssignments(userId)` → GET /v1/roles/assignments/user/{userId}

### Pages

- **Roles List** (`/app/security`): paginated roles table, search, Create Role modal
- **Role Detail** (`/app/security/roles/:name`): role info, granted permissions, Edit Permissions modal, inline revoke confirmation
- **Permissions Registry** (`/app/security/permissions`): read-only paginated permissions list
- **Security Audit Log** (`/app/security/audit`): placeholder "coming soon" panel

### Shell

- `security.component.ts/html/css` — tabbed shell with Roles | Permissions | Audit Log tabs and `<router-outlet>`

## Deferred (Story #65)

- Financial Exception Audit Trail — depends on audit domain API (`/v1/audit/exceptions/*`) which is out of scope for pos-security-service. Placeholder page included.

## Tests

- `security.service.spec.ts`: 11 tests
- `roles-list-page.component.spec.ts`: 18 tests
- `role-detail-page.component.spec.ts`: 22 tests
- `permissions-list-page.component.spec.ts`: 12 tests
- `security-audit-list-page.component.spec.ts`: 3 tests
