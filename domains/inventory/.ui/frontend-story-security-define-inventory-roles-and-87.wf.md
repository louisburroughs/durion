# [FRONTEND] [STORY] Security: Define Inventory Roles and Permission Matrix

## Purpose
Provide an Inventory Security administration area that lets authorized users view inventory roles and the role→permission matrix, manage user role assignments, and review an audit trail of changes. Ensure Inventory UI actions are permission-aware by hiding/disabling protected actions based on effective permissions and handling unauthorized responses consistently. All data loading and mutations must go through Moqui screens/forms/transitions/services (no direct frontend calls to an external security backend).

## Components
- Inventory navigation item: “Security” (permission-gated visibility)
- Page header: “Inventory Security”
- Tabs or segmented navigation:
  - Roles & Permission Matrix (read-only)
  - User Role Assignments
  - Audit Log
- Roles & Permission Matrix view:
  - Roles list/table (name, key, description)
  - Permission matrix table (roles as rows, permissions as columns; read-only indicators)
  - Empty state: “No roles found”
  - Loading and error/unauthorized banner
- User Role Assignments view:
  - User search form: query string input + Search button + Clear
  - Results list/table of users (id, username, display name/email as available)
  - Empty state: “No users match your search”
  - User detail panel/section: selected user summary + current roles list
  - Role assignment form: multi-select roles + Assign/Save button
  - Role removal action per role: Remove button
  - Confirmation modal: confirm assign changes; confirm role removal
  - Success/error toast/banner
- Audit Log view:
  - Audit filter form: date range, actor, target user, role, outcome + Apply + Reset
  - Audit list/table (event id, action, actor, target, role, timestamp, outcome, correlation id if present)
  - Cursor pagination controls: Next/Previous (or Load more)
  - Empty state: “No audit entries match filters”
- Global permission-aware UI behavior:
  - Protected action buttons rendered disabled/hidden based on effective permissions
  - Standard unauthorized handling (e.g., inline banner + no action execution)

## Layout
- Top: Inventory header + breadcrumb (Inventory > Security) + page title
- Below header: Tabs [Roles & Permission Matrix | User Role Assignments | Audit Log]
- Main (per tab):
  - Matrix tab: left Roles list; right Permission matrix table
  - Assignments tab: top User search; left Results; right Selected user + Roles + Assign form
  - Audit tab: top Filters; below Audit table + pagination

## Interaction Flow
1. Navigation visibility:
   1. If user lacks the Security-domain permission to view inventory security admin, hide “Security” navigation entry.
   2. If user navigates directly without permission, show unauthorized state per platform convention and prevent data display.
2. View Roles & Permission Matrix:
   1. Open “Roles & Permission Matrix” tab.
   2. Load role list and role-permission matrix via Moqui.
   3. Render roles and matrix read-only; show correlation between roles and permissions.
   4. If no roles returned, show “No roles found”.
   5. If service returns unauthorized/forbidden, show consistent unauthorized handling and do not render protected data.
3. Search users and view role assignments:
   1. Open “User Role Assignments” tab.
   2. Enter query string and submit Search.
   3. Load matching users via Moqui; display results list.
   4. If no matches, show “No users match your search”.
   5. Select a user to load current role assignments and effective permissions (as needed for UI gating).
4. Assign roles to a user:
   1. In selected user panel, open multi-select roles and choose one or more roles.
   2. Click Assign/Save; show confirmation modal summarizing changes.
   3. On confirm, call Moqui transition/service to assign roles.
   4. Refresh user role assignments and (if applicable) effective permissions display/state.
   5. Show success message; on error, show error banner/toast.
5. Unassign/remove a role:
   1. Click Remove next to a role in the user’s assigned roles list.
   2. Show confirmation modal; on confirm, call Moqui transition/service to unassign.
   3. Refresh assignments and show success/error feedback.
6. View audit history:
   1. Open “Audit Log” tab.
   2. Set filters (date range, actor, target user, role, outcome) and Apply.
   3. Load audit events via Moqui with cursor pagination preferred.
   4. Display correlation ID when available.
   5. If no entries, show “No audit entries match filters”.
7. Permission-aware Inventory UI behavior (outside admin screens):
   1. On Inventory screens, load effective permissions via Moqui as needed.
   2. Hide or disable protected actions based on effective permissions.
   3. If an action is attempted and backend returns unauthorized, handle consistently (disable future attempts, show unauthorized message).

## Notes
- Read-only requirement: role list and role→permission matrix are view-only in this scope.
- Role assignment UI is conditional: only enable assign/unassign if supported by Moqui/security backend contract; otherwise render read-only with disabled controls.
- All integration must be via Moqui screens/forms/transitions/services; no direct Vue calls to an external security backend.
- Required forms: user search, role assignment (multi-select + confirm), role removal (confirm), audit filters (date range/actor/target/role/outcome).
- Required loads/services: effective permissions, role-permission matrix, user search, user role assignments, assign/unassign roles, audit logs (cursor pagination preferred).
- Empty states must match: “No roles found”, “No users match your search”, “No audit entries match filters”.
- Unauthorized handling must be consistent with platform conventions; ensure protected navigation and actions are permission-gated in UI and validated by backend.
- Data model fields are partially TBD in the issue (role/user/audit field names and permission key); implement with placeholders and align to backend contract when finalized.
