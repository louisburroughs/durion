# [FRONTEND] [STORY] Security: Define Shop Roles and Permission Matrix
## Purpose
Provide Moqui frontend screens to manage shop roles and their permissions within the authenticated tenant context. Enable admins to list, create, and view roles; edit role descriptions; and grant/revoke permissions using idempotent operations that support retry. Ensure deny-by-default UI gating and robust handling of unauthorized responses (401/403), while exposing a read-only permission registry for discovery and selection.

## Components
- Global navigation/menu with route gating (hide unauthorized items where possible)
- Roles List page
  - Page title + breadcrumb
  - Search input (basic filter)
  - Paged table/list (default page size 25)
  - Columns: roleName, description, (optional) permissionCount, (optional) createdAt/updatedAt
  - Row action: View Details
  - Primary CTA: Create Role (permission-gated)
  - Empty state panel with Create Role CTA (permission-gated)
- Create Role page/modal (implementation choice)
  - Form fields: roleName (required), description (optional)
  - Buttons: Save, Cancel
  - Inline validation + error banner/toast
- Role Detail page
  - Read-only metadata: roleId, roleName, (optional) roleType, (optional) timestamps
  - Granted permissions list (paged optional; at minimum scrollable list)
  - Actions: Edit Description, Manage Permissions (permission-gated)
- Edit Role Description panel/page
  - roleName displayed read-only/disabled
  - description editable
  - Buttons: Save, Cancel
- Manage Role Permissions page
  - Permission registry list/search (read-only registry)
  - Current grants indicator (selected state)
  - Selection controls (checkboxes/toggles)
  - Derived “pending changes” summary (added/removed counts)
  - Buttons: Save Changes, Cancel/Back, Reset to Server State
  - Error handling UI for partial failures/retry guidance
- Permission Registry page (read-only)
  - Search input
  - Paged list/table (default page size 25)
  - Columns: permissionKey, description/label (if available)
- Authorization/Errors
  - 401/403 handling: inline “Not authorized” state + safe redirect option
  - Loading spinners/skeletons for list/detail pages

## Layout
- Top: App header + tenant context implied from auth (no tenant/location inputs) + user menu
- Left: Security/Admin navigation (Roles, Permissions) with permission-gated visibility
- Main (varies by route):
  - Roles List: [Title + Search + Create CTA] above [Paged Roles Table] above [Pagination]
  - Role Detail: [Header: roleName + actions] then [Metadata card] then [Granted Permissions list]
  - Manage Permissions: [Header + Save/Cancel] then [Search] then [Registry list with checkboxes] then [Pending changes summary]
- Footer: optional status/help links

## Interaction Flow
1. Enter Roles List route
   1) If user lacks view permission, deny-by-default: hide menu item; if route accessed directly, show 403 state and/or redirect to a safe page.
   2) Load roles (page size 25) and render table; allow basic search filter and pagination.
   3) If no roles returned, show empty state; show “Create Role” CTA only if user has create permission.
2. Create Role
   1) User clicks “Create Role” (requires create permission) to open Create Role form.
   2) User enters roleName (required) and optional description.
   3) Save: call create role endpoint; on success navigate to Role Detail for returned roleId.
   4) Cancel: return to Roles List without changes.
   5) Edge case (Scenario 4): backend returns 409 with duplicate/normalized roleName conflict; highlight roleName field and show “Role name already exists.” Keep entered description visible.
3. View Role Detail
   1) From Roles List, user opens a role; load role metadata and granted permissions from server.
   2) Display roleId and roleName as read-only; show granted permissions list.
   3) If unauthorized (401/403), show deny state and prevent data leakage; do not render protected actions.
4. Edit Role Description (Scenario 5)
   1) From Role Detail, user selects “Edit Description” (requires update permission).
   2) Show roleName as read-only/disabled; allow editing description only.
   3) Save: call update role endpoint without sending roleName; on success reload role detail and show updated description.
   4) Cancel: return to Role Detail without changes.
5. View Permission Registry
   1) User navigates to Permission Registry (requires registry view permission).
   2) Load registry list (page size 25) with search; registry is read-only.
6. Manage Role Permissions (Scenario 6)
   1) From Role Detail, user selects “Manage Permissions” (requires view roles + view registry).
   2) Load permission registry and current role grants; render registry with selected state for granted permissions.
   3) User selects/deselects permissions; compute diff (added/removed) client-side and show pending changes summary.
   4) Save Changes:
      - Preferred: call grant endpoint per added permissionKey and revoke endpoint per removed permissionKey (idempotent; safe under retry).
      - After success: reload role grants from server and update UI; Role Detail reflects changes after reload.
   5) Authorization gating on save:
      - Additions require grant permission; removals require revoke permission; disable Save or show inline error if missing.
   6) Error handling: if any grant/revoke call fails, show actionable error state and allow retry; do not assume local state is authoritative until reload succeeds.

## Notes
- Deny-by-default: hide unauthorized routes/menu items where possible, but always handle direct navigation with server-enforced 401/403 responses.
- Tenant-scoped: UI must not accept tenantId/locationId inputs; derive tenant context from auth/request context only.
- Role fields:
  - roleId: required, read-only.
  - roleName: required on create; immutable after create; display read-only on detail/edit.
  - description: optional; editable only with update permission.
  - roleType (if present): backend-derived; display-only.
  - createdAt/updatedAt: read-only; display optional.
- Read-only vs editable:
  - Create: roleName + description editable (permission-gated).
  - Update: description only.
  - Grants: add/remove permissions (grant/revoke permissions separately).
- Pagination UX: Roles List and Permission Registry default page size 25 with basic search; interactions should be safe and reversible.
- Manage Permissions save strategy:
  - Preferred diff-based grant/revoke calls per change (idempotent; supports retry).
  - Optional future: replace-set endpoint if backend supports it (TODO: confirm availability and required permissions).
- After any mutation (create/update/grants), reload from server to ensure authoritative state and consistent display.
