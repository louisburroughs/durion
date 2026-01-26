# [FRONTEND] [STORY] Location: Assign Person to Location with Primary Flag and Effective Dates
## Purpose
Enable managers to assign a Person to one or more Locations with effective dates and an optional Primary flag. Provide a clear list view with filtering for active vs historical assignments and authorized actions to add or end assignments. Ensure the UI reflects backend rules, including automatic demotion of a prior primary assignment when a new primary is created.

## Components
- Page header: Person name (fallback to People lookup if not provided), Person identifier/context
- Assignment list/table
  - Columns: Location, Role (optional), Primary indicator, Effective start, Effective end, Status (Active/Ended), Last updated (optional/display if provided)
  - Row actions: “End” (authorized; only for active rows)
- Toolbar actions
  - Primary CTA: “Add Assignment” (authorized)
  - Filter toggle: Active only (default) / Include history
- Empty state (when no assignments)
  - Message + primary CTA “Add Assignment” (if authorized)
- “Add Assignment” modal/drawer form
  - Location (required; selector)
  - Effective start (required; date/time)
  - Effective end (optional; date/time; exclusive semantics)
  - Primary (required boolean; checkbox; default false)
  - Assignment role (optional; shown only if backend provides allowed values or field is present)
  - Change reason code (optional; shown only if backend supports)
  - Actions: Save, Cancel
- “End Assignment” confirmation modal
  - Effective end input (required to end; exclusive semantics)
  - Change reason code (optional; only if supported)
  - Actions: End Assignment, Cancel
- Inline validation + error banner/toast for API failures

## Layout
- Top: Header (Person name + context) and toolbar (Add Assignment button, Active/History toggle)
- Main: Assignment list/table (rows with Primary badge and End action per active row)
- Main (empty): Empty-state panel with message + Add Assignment CTA
- Overlay: Add Assignment modal/drawer; End Assignment confirmation modal

## Interaction Flow
1. View assignments (default)
   1. Load page with Person context (personId required; display name from context or fetched via People lookup).
   2. Default filter is “Active only”; list shows active assignments (Effective end empty or in future per backend definition).
   3. User toggles “Include history” to show ended assignments; “End” action is hidden/disabled for non-active rows.
2. Create assignment (authorized)
   1. User clicks “Add Assignment”.
   2. Form opens with Primary unchecked by default; required fields indicated.
   3. User selects Location, sets Effective start, optionally sets Effective end (exclusive semantics), optionally selects Role and/or Change reason code if available.
   4. User clicks Save; UI validates required fields and date logic (end must be after start; end treated as exclusive).
   5. On success (201/200), close form and refresh list from server to reflect server-side adjustments.
3. Scenario: Create first primary assignment
   1. With no current assignments, user creates an assignment with Primary checked and valid dates.
   2. After refresh, list shows the new assignment marked as Primary.
4. Scenario: Create secondary (non-primary) assignment
   1. With an existing active primary assignment, user creates a second assignment with Primary unchecked.
   2. After refresh, list shows both assignments; original remains Primary.
5. Scenario: Create new primary triggers backend demotion
   1. With an existing active primary at Location A, user creates a new assignment at Location B with Primary checked and later Effective start.
   2. After refresh, list shows Location B as Primary and the previously primary assignment no longer primary (as returned by backend).
6. End assignment (authorized; active only)
   1. User clicks “End” on an active assignment row.
   2. Confirmation modal opens; user provides Effective end (exclusive semantics) and optional Change reason code if supported.
   3. User confirms; on success, refresh list and update status/end date; row no longer offers “End”.

## Notes
- Authorization gating: show “Add Assignment” and “End” only for users with manage permission; “End” only for active assignments.
- Data requirements/conditional fields:
  - Assignment role field is shown only if backend provides allowed values or the field is present; may be null.
  - Change reason code is optional and only shown if backend supports it (not required in v1).
- Person display name: if not provided in context, UI must perform an explicit People lookup rather than deriving.
- Effective end uses exclusive semantics; ensure UI copy/validation aligns (e.g., “Ends at” with exclusive behavior).
- Primary behavior: backend may demote prior primary when a new primary is created; UI must always refresh from backend after create to reflect final truth.
- Concurrency: if backend enforces optimistic concurrency, include lastUpdatedAt (read-only) in end/update requests as required.
- Empty state: use standard empty-state message and primary CTA when list is empty (safe default).
