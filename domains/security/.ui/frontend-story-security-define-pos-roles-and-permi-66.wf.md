# [FRONTEND] [STORY] Security: Define POS Roles and Permission Matrix
## Purpose
Provide a Security Admin UI to manage RBAC roles and role-permission grants in the Moqui frontend. Admins can list/create roles, view role details, edit role descriptions (roleName immutable), and grant/revoke permissions idempotently. The UI also exposes a read-only permission registry and a read-only, curated security audit log for RBAC changes, with deny-by-default gating and consistent error handling.

## Components
- Security Admin root navigation/menu entry (permission-gated)
- Roles List screen
  - Search input (roleName contains)
  - Results table (roleName, description, createdAt/updatedAt if available)
  - Pagination controls
  - “Create Role” button (authorized only)
  - Row action/link: “View” (Role Detail)
  - Empty state panel
- Create Role modal/page
  - roleName field (required, trim validation)
  - description field (optional)
  - Save/Create + Cancel buttons
  - Field error display (canonical envelope fieldErrors)
- Role Detail screen
  - Read-only roleName display
  - Editable description field (authorized only) + Save button
  - Granted Permissions table (permissionKey, description if available)
  - “Grant Permissions” action (authorized only)
  - Revoke action per permission row (authorized only) + confirmation modal
  - Link to Audit Log pre-filtered by subjectType/subjectId (role)
  - Empty state for no granted permissions
- Grant Permissions modal
  - Multi-select permission picker (from permission registry)
  - Search within picker (permissionKey/description)
  - Confirm/Grant + Cancel buttons
  - Validation message: must select at least one permissionKey
- Permission Registry screen (read-only)
  - Search inputs (permissionKey and/or description)
  - Results table (permissionKey, description)
  - Pagination controls
  - Empty state guidance
- Security Audit Log screen (read-only)
  - Filters: date range, actor, subjectType, subjectId, eventType
  - Results table (timestamp, eventType, actor, subjectType, subjectId, curated details)
  - Pagination controls
  - Empty state (“No matching events”)
- Global UI states
  - Loading spinners/skeletons for lists/detail
  - Banner/toast system for success/error with correlationId when present
  - Not-authorized (403) state view
  - Session-expired (401) redirect/flow hook
  - Conflict (409) banner with “Reload” action
  - Network/timeout banner with “Retry” action

## Layout
- Top: Page header “Security Admin” + tabs/links: Roles | Permission Registry | Audit Log
- Main (Roles List): Search row + [Create Role] right-aligned; below: table; bottom: pagination
- Main (Role Detail): Top summary (roleName read-only, description edit); below: Granted Permissions table + [Grant Permissions]; side/link: “View Audit for this Role”
- Main (Registry/Audit): Filter bar at top; results table center; pagination bottom

## Interaction Flow
1. Navigate to Security Admin
   1) If user lacks view permission for a screen, hide its menu entry; direct navigation shows a 403 not-authorized state.
   2) On any 401 response, follow the app’s session-expired/login flow.
2. Roles List: search, paginate, open detail
   1) Load paginated roles list; show loading state then table or empty state.
   2) User enters roleName search (contains) and submits; refresh list with safe defaults.
   3) User paginates; list refreshes while preserving search term.
   4) User clicks a role row to open Role Detail.
3. Create Role (authorized only)
   1) User clicks “Create Role”; open modal/page.
   2) Validate roleName required + trim (do not collapse internal whitespace client-side).
   3) Submit create; on success show success banner (include correlationId if provided) and navigate/refresh Role Detail from GET role endpoint.
   4) On 400 show fieldErrors; on 403 hide/disable create controls and show not-authorized banner; on network/timeout show retry.
4. Role Detail: view immutable roleName and edit description (authorized only)
   1) Load role detail; render roleName as read-only (never editable).
   2) If user has update permission, enable description edit + Save; otherwise render description read-only and hide Save.
   3) Submit description update; on success show banner and refresh detail; on backend rejection (including attempts to change roleName) surface canonical error envelope.
   4) On 409 show conflict banner with backend code/message and a “Reload” action to refetch.
5. Role Detail: grant permissions (idempotent)
   1) If authorized, user clicks “Grant Permissions”; open modal with permission registry-backed multi-select.
   2) Require at least one permissionKey selected; show validation if empty.
   3) Submit grant; on success refresh granted permissions list; repeated identical grant submissions must still result in a successful state with no duplicates.
   4) On 403 remove/disable grant controls and show not-authorized state; on network/timeout show retry and do not assume success.
6. Role Detail: revoke permission (idempotent)
   1) User clicks “Revoke” on a permission row; show confirmation modal naming the permissionKey.
   2) Confirm revoke; on success refresh granted permissions list; repeated revoke submissions must still result in a successful state with list remaining correct.
7. Permission Registry (read-only)
   1) Load paginated registry; allow search by permissionKey and/or description.
   2) Render permissionKey exactly as returned; no edit/create controls.
   3) If empty, show guidance: “No permissions registered; verify services registered permissions.”
8. Security Audit Log (read-only, curated)
   1) Load paginated audit entries; allow filtering by date range, actor, subjectType/subjectId, eventType (as supported).
   2) Display curated/redacted details only; no raw payload by default.
   3) From Role Detail, follow link to Audit Log pre-filtered to subjectType=ROLE and subjectId=roleId.

## Notes
- Deny-by-default UX: if user lacks mutation permissions, hide/disable create/update/grant/revoke controls; if user lacks view permission, hide menu and show 403 on direct access.
- Error handling must map 400/401/403/404/409/network to consistent banners and field errors using the canonical error envelope (code/message/correlationId/fieldErrors); always display correlationId on non-2xx errors.
- 409 conflicts must show a conflict banner including backend code/message and provide a “Reload” action.
- RoleName is immutable after create; Role Detail must never render roleName as editable.
- Permission registry is code-first and read-only; permissionKey immutable and displayed exactly as returned.
- Grant/revoke must be idempotent; UI should refresh lists after mutations and avoid duplicate display.
- Audit log must show curated fields only; raw payload access is out-of-scope and would require separate endpoint/permission gating.
- Out-of-scope: principal-role assignment UI, authentication implementation details, location-scoped RBAC/ABAC, approval workflows, and broad “protected operations” wiring across the entire POS UI.
- Known gap (backend/process): role deletion behavior/validation is unclear; if deletion is introduced later, ensure checks prevent orphan assignments (expected 409 or custom error).
