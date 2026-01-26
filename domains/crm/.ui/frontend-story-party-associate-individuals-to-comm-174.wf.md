# [FRONTEND] [STORY] Party: Associate Individuals to Commercial Account
## Purpose
Enable a Fleet Account Manager to view and manage associations between an existing commercial account (Organization) and existing individuals (Persons) via relationship roles and effective dates. Provide a “Contacts & Roles” screen that lists active relationships by default and supports creating new relationships and managing the account’s primary billing contact. Ensure downstream systems can reliably retrieve approvers and billing contacts from CRM-owned relationship data.

## Components
- Page header: “Contacts & Roles” + commercial account identifier/name (from route)
- Filter controls
  - Status filter (default: Active)
  - Role filter (multi-select or dropdown; populated from backend-provided roles if available)
  - Search input (text)
- Relationships list/table
  - Columns: Person (name), Role, Effective From, Effective Thru (if present), Primary Billing indicator (only meaningful for BILLING), Actions
  - Row actions (Active only): “Set as Primary Billing Contact” (for BILLING), “Deactivate”
  - Read-only display for inactive rows (no actions)
- Pagination controls (page size default 25; page index)
- Loading state (skeleton/spinner) and empty state messaging
- “Add Contact” / “Associate Individual” button
- Add Relationship modal/drawer
  - Person selector (existing individual lookup)
  - Role selector (required; at minimum APPROVER, BILLING)
  - Effective From date (required)
  - “Set as primary billing contact” checkbox (enabled only when role = BILLING)
  - Save / Cancel buttons
- Toast/inline alerts for success/error

## Layout
- Top: Page title + account context; primary action button “Add Contact”
- Below header: Filters row (Status | Role | Search) aligned left; pagination summary aligned right
- Main: Relationships table/list with row-level actions on the right
- Bottom: Pagination controls + results count
- Modal/Drawer: Add Relationship form (stacked fields; Save/Cancel at bottom)

## Interaction Flow
1. Load screen (default view)
   1. User navigates to commercial account “Contacts & Roles” screen (orgPartyId from route).
   2. UI calls list service with orgPartyId and defaults (status = Active, pageIndex=0, pageSize=25, sort default).
   3. Show loading state; render table when results return.
   4. Display each relationship’s role, effective dates, and primary billing indicator for BILLING relationships.
2. Filter/search/paginate
   1. User changes Status (Active/Inactive), Role filter, or Search text.
   2. UI re-calls list service with updated parameters; keep UI responsive and show loading state.
   3. User paginates; UI calls list service with new pageIndex/pageSize.
3. Create relationship (including primary billing option)
   1. User clicks “Add Contact”.
   2. Modal opens; user selects an existing Person, selects Role, and sets Effective From date.
   3. If Role = BILLING, user may check “Set as primary billing contact”; otherwise checkbox is disabled/hidden.
   4. User clicks Save; UI calls create relationship service with orgPartyId, personPartyId, role, fromDate, and optional isPrimaryBilling (only when BILLING).
   5. On success, close modal and refresh list; ensure exactly one BILLING relationship is shown as primary after refresh (backend enforces atomic demotion).
4. Change primary billing contact (atomic demotion)
   1. In Active view, user finds a non-primary active BILLING relationship and clicks “Set as Primary Billing Contact”.
   2. UI calls set-primary service with orgPartyId and selected relationshipId.
   3. On success, refresh list; show selected relationship as primary and previously primary as not primary; no duplicate primary indicators.
5. Deactivate relationship (active only)
   1. User clicks “Deactivate” on an active relationship.
   2. UI confirms intent (optional lightweight confirm) and calls deactivate action (sets thruDate).
   3. Refresh list; relationship disappears from Active view and appears in Inactive view with all fields read-only.
6. Edge/empty/error states
   1. If no results, show empty state with prompt to “Add Contact”.
   2. If service errors, show error alert/toast and keep current list state if possible.

## Notes
- Read-only vs editable:
  - Active relationships: editable only via actions “Set primary billing” (BILLING only) and “Deactivate” (sets thruDate).
  - Not editable in this story: role, fromDate, person/org identifiers, relationship type after create.
  - Inactive relationships: all fields read-only; no reactivation in scope.
- Primary billing semantics:
  - Only meaningful for role = BILLING; backend enforces exactly one primary BILLING relationship per org and atomically demotes any existing primary.
  - UI must avoid showing multiple primary indicators; always refresh after set-primary/create with primary.
- Data contract considerations:
  - Relationship DTO includes IDs (read-only), role enum (read-only after create), isPrimaryBilling (read-only after create), fromDate (read-only after create), thruDate (nullable), optional audit fields (read-only if present).
  - List service may return allowedRoles list for safe role dropdown population.
- Performance/UX:
  - Typical accounts <200 relationships; list should load within 2 seconds.
  - Provide clear loading state; keep filters usable and avoid blocking the entire page longer than necessary.
- Sorting:
  - Use backend sort parameter; default sort should be stable (e.g., role then person name or fromDate) to reduce perceived flicker on refresh.
- Accessibility:
  - Ensure table actions are keyboard accessible; primary indicator should be text + iconography (not color-only).
