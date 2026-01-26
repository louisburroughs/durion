# [FRONTEND] [STORY] Access: Assign Roles and Scopes (Global vs Location)
## Purpose
Provide an “Access / Roles” screen to view a user’s role assignments with active-only filtering by default and an option to include historical (ended) assignments. Enable admins to create new role assignments with correct scope rules (GLOBAL vs LOCATION) and effective dating, and to end assignments (no in-place edits). Ensure client-side validation prevents invalid submissions (e.g., LOCATION without location) while keeping backend as source of truth.

## Components
- Page header: “Access / Roles” + user identifier (name/ID)
- Filter controls:
  - Toggle/checkbox: “Include history” (default off)
  - Optional: “Refresh” action
- Role assignments table/list:
  - Columns: Role, Scope Type, Location, Effective Start, Effective End, Status (derived)
  - Row actions: “End” (enabled per rules)
- Create Role Assignment panel/form:
  - Role picker (required)
  - Scope Type select (required; GLOBAL / LOCATION)
  - Location picker (required iff scopeType=LOCATION; lazy-loaded)
  - Effective Start datetime (required; default now)
  - Effective End datetime (optional; exclusive end)
  - Optional fields (only if supported by backend): Reason, CreatedBy/metadata (display-only)
  - Buttons: “Assign Role” (primary), “Clear/Reset” (secondary)
  - Inline validation messages
- End Role Assignment modal/dialog:
  - Read-only summary of selected assignment (role, scope, location, start)
  - Effective End datetime (required; default now)
  - Hidden/implicit concurrency token field (effectiveFromDate) when present on row
  - Optional reason (only if backend supports)
  - Buttons: “Confirm End” (danger/primary), “Cancel”
- Loading/empty/error states:
  - Skeleton/spinner for assignments list
  - Empty state text when no assignments match filter
  - Inline/API error banner for failed create/end/load

## Layout
- Top: Page title “Access / Roles” + user context; right side filter toggle “Include history”
- Main (two-column):
  - Left/main (wider): Role Assignments table with row “End” actions
  - Right (narrow): “Assign Role” form card
- Modal overlay: “End Role Assignment” confirmation dialog

## Interaction Flow
1. Navigate to user “Access / Roles” screen.
2. System loads role assignments for the user with active-only filter applied by default; render table with derived Status using half-open interval semantics (active if now ∈ [start, end)).
3. User toggles “Include history” on:
   1) UI reloads assignments including ended records; table updates to show historical rows with end timestamps and non-active status.
4. Admin creates a GLOBAL role assignment:
   1) Select Role (required).
   2) Select Scope Type = GLOBAL (required).
   3) UI ensures Location is hidden/cleared and will not be sent (must be omitted/null).
   4) Set Effective Start (required; default now) and optional Effective End (must be > start if provided).
   5) Click “Assign Role”; UI calls create role assignment service.
   6) On success: remain on screen, refresh assignments list, show new row with scopeType GLOBAL and blank location.
5. Admin creates a LOCATION role assignment (lazy-load locations):
   1) Select Role (required).
   2) Select Scope Type = LOCATION; UI triggers loading locations list only now.
   3) Select Location (required); if missing, show inline error and block submit.
   4) Set Effective Start (required; default now) and optional Effective End (> start if provided).
   5) Click “Assign Role”; UI submits create including locationId.
   6) On success: refresh assignments list; show new row with scopeType LOCATION and selected location.
6. End an existing role assignment (no in-place edits):
   1) Click “End” on a row; open End modal with assignment summary.
   2) Default Effective End = now; user may adjust.
   3) Validate Effective End > Effective Start (even if start is in the future; end must still be after start).
   4) If the loaded row includes concurrency token (effectiveFromDate), include it in end request.
   5) Confirm End; on success close modal and refresh list (row becomes ended; status updates).
7. Error/edge handling:
   1) If API load/create/end fails: show error banner/message; keep user inputs where possible.
   2) If scopeType changes from LOCATION to GLOBAL: clear location selection and do not send locationId.

## Notes
- Scope/location constraints (DECISION-PEOPLE-003):
  - If scopeType=LOCATION → locationId required.
  - If scopeType=GLOBAL → locationId must be omitted/null; UI must not send it.
  - scopeType must be one of GLOBAL or LOCATION.
- Effective dating (DECISION-PEOPLE-014):
  - effectiveEndAt (if provided) must be strictly greater than effectiveStartAt.
  - “Active” status uses half-open interval: active if now >= start and (end is null or now < end).
- Mutability preference (DECISION-PEOPLE-026): do not support editing roleId/scopeType/location/start; changes require end + create.
- Optimistic concurrency (DECISION-PEOPLE-017): when effectiveFromDate is present on loaded assignment, include it in end request (and any backend-required concurrency checks).
- SD-UX-01: lazy-load locations only when scopeType=LOCATION is selected.
- Validation: block submit and show inline error for LOCATION scope without location; also validate end > start and end > start for end action.
- Service interactions: implement as Moqui screens with transitions calling REST (or direct services) per repo convention for create, end, and list loading (roles, assignments, locations).
- Risk note: requirements flagged incomplete; avoid inventing reason codes/fields—only render optional “reason” inputs if backend supports them.
