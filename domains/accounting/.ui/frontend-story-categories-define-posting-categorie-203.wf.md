# [FRONTEND] [STORY] Categories: Define Posting Categories and Mapping Keys

## Purpose
Provide Moqui frontend screens to manage Posting Categories, Mapping Keys, and effective-dated GL mappings tied deterministically to a category. Users need to create, view, update (if allowed), and deactivate categories/keys, and create new GL mapping versions without overlapping effective ranges. The UI must surface backend validation/conflict/access errors clearly while preserving user input on failures.

## Components
- Global
  - Page header with title + breadcrumb navigation
  - Permission-gated primary actions (Create, Edit, Deactivate, Add/New GL Mapping)
  - Toast/banner area for success/error messages
- Posting Categories List
  - Filter/search inputs (e.g., name/code/status)
  - Server-side paginated table (columns: Name/Code, Status, Updated, Actions/View)
  - “Create Posting Category” button (permission-gated)
- Posting Category Detail
  - Read-only summary fields (e.g., name/code/description, status)
  - “Edit” button (if supported/allowed)
  - “Deactivate” button (permission-gated; disabled if already INACTIVE)
  - Linked Mapping Keys panel
    - Table/list of keys (key, status if modeled, updated, view link)
    - “Create Mapping Key” action (permission-gated; pre-linked to category)
  - GL Mapping History panel
    - Server-side paginated table (GL Account, effective start, effective end, audit metadata)
    - “Add/New GL Mapping” button (permission-gated; disabled/hidden if category INACTIVE)
- Create/Edit Posting Category
  - Form fields (allowed fields only; others read-only if immutable)
  - Save/Cancel buttons
  - Inline field validation messages
- Mapping Keys List
  - Filter/search inputs (e.g., key, category, status)
  - Server-side paginated table (key, linked category, status, updated, actions/view)
  - “Create Mapping Key” button (permission-gated)
- Mapping Key Detail / Create/Edit
  - Fields for key + linked Posting Category selector (or fixed when created from category detail)
  - Save/Cancel buttons; optional deactivate action if modeled
  - Inline field validation messages
- Create New GL Mapping (modal or dedicated subpage from category detail)
  - Read-only Posting Category context
  - GL Account lookup control (paged/search CoA selector)
  - Effective start date (required) and effective end date (optional)
  - Optional dimensions section (only if backend supports)
  - Submit/Cancel buttons
  - Conflict details area (shown on 409 with overlapping ranges/mapping IDs if provided)

## Layout
- Top: Breadcrumbs + Page Title + primary action buttons (Create/Edit/Deactivate as applicable)
- Main (List pages): Filters row above a paginated results table; right-aligned “Create” button
- Main (Category detail): Summary card at top; below two stacked panels: [Linked Mapping Keys] then [GL Mapping History] with “Add/New GL Mapping” in panel header
- Main (Create/Edit forms): Single-column form with Save/Cancel at bottom; inline errors under fields

## Interaction Flow
1. View Posting Categories list
   1. User opens Posting Categories list; system loads paged results with current filters.
   2. User filters/searches; system re-queries server-side pagination.
   3. User selects a row to open Category detail.
   4. If authorized, user clicks “Create Posting Category” to open create form and submits; on success, navigate to new Category detail.
2. View Posting Category detail (including linked keys and mapping history)
   1. System loads category detail by ID; displays fields and status.
   2. System loads linked Mapping Keys list for the category.
   3. System loads GL Mapping history (paged) showing effective ranges and audit metadata.
   4. If 404, show “Not found” and provide navigation back to list.
3. Create Mapping Key linked to a category (Scenario 3)
   1. From Category detail, user clicks “Create Mapping Key” (if authorized).
   2. Form opens with Posting Category pre-selected/locked to current category.
   3. User enters key fields and submits.
   4. On success, return to Category detail and show the new key in the linked keys panel.
4. Deactivate Posting Category
   1. On Category detail, “Deactivate” is visible only with manage permission; disabled if already INACTIVE.
   2. User confirms deactivation; system calls deactivate service.
   3. On success, status updates to INACTIVE and actions that require ACTIVE state update accordingly.
5. Create an effective-dated GL mapping (Scenario 5)
   1. On ACTIVE Category detail, user clicks “Add/New GL Mapping” (permission-gated).
   2. User searches/selects a GL Account via CoA lookup (paged/search).
   3. User sets effective start date (required) and optional end date; fills optional dimensions if present.
   4. User submits; system creates a new GL Mapping record/version.
   5. On success, GL Mapping History refreshes and shows the new mapping with its effective range and audit metadata.
6. Block new mappings for inactive category (Scenario 7)
   1. When category status is INACTIVE, “Add/New GL Mapping” is disabled or hidden.
   2. If user attempts submission via direct navigation or stale UI, backend error is shown indicating mappings cannot be created for inactive categories; user inputs remain visible for correction/navigation.
7. Error handling and edge cases
   1. 400/422: Map field-level errors to inline messages when keys match field names; show a general banner for non-field errors.
   2. 409 conflict (overlapping effective ranges): Show conflict banner; keep user inputs; if response includes conflicting mapping IDs/date ranges, display them in the conflict details area.
   3. 401/403: Show access denied message; do not reveal whether the entity exists; provide navigation back to list.
   4. Missing GL account / invalid CoA selection: show inline error on GL account field and prevent submit.

## Notes
- No delete actions in UI; deactivation is the supported state change for Posting Categories (and Mapping Keys only if modeled by backend).
- GL Mapping is effective-dated; UI must prevent/handle overlapping ranges (client-side hints optional, but backend is source of truth; 409 must be handled).
- If backend enforces immutability-by-version, do not offer editing existing GL mappings; only allow “Create new mapping” and show full history.
- Audit fields for GL mappings are read-only and displayed in history (e.g., created/updated timestamps and user if available).
- Permission gating is TBD; implement visibility/disabled states per manage permission and category ACTIVE/INACTIVE status.
- Required backend contracts are currently blocking: exact Moqui service names/REST endpoints for list/detail/create/update/deactivate for Posting Category and Mapping Key; list/history/create for GL Mapping; and GL Account lookup (paged/search).
