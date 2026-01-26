# [FRONTEND] [STORY] Users: Create/Update Employee Profile (pos-people)
## Purpose
Provide Moqui/Vue/Quasar screens to create and edit an Employee Profile using People REST endpoints. Ensure the UI enforces People domain validation rules, handles backend success/errors/warnings consistently, and displays audit metadata. Support optimistic concurrency on update when a backend token is provided, and enforce permission-based access and read-only behavior for terminated employees by default.

## Components
- Page header with title: “Create Employee” or “Edit Employee”
- Breadcrumbs / navigation context: People / Employees
- Permission gate / access denied panel (403)
- Loading state (skeleton/spinner) for edit load
- Employee Profile form
  - Read-only fields: employeeProfileId (edit), createdAt, updatedAt, updatedBy/createdBy (if provided), concurrency token display (hidden by default)
  - Editable fields (see Notes for full list and rules)
- Field-level validation messages (required, format, date rules, enum constraints)
- Status selector (enum; default ACTIVE on create)
- Save button (primary) and Cancel/Back button (secondary)
- Non-blocking warnings callout/panel (post-save) showing warning code(s) and message(s)
- Duplicate/conflict error display
  - Blocking duplicate/conflict banner (409) with backend-provided details
  - Inline field errors for 400 validation responses
- Read-only explanation banner for terminated employees (when edits not allowed)
- Not found (404) empty state for edit route

## Layout
- Top: Page title + breadcrumbs; right-aligned actions (Save, Cancel/Back)
- Main: Form in sections (e.g., Identity/Employment, Contact, Address/Other); inline validation under fields
- Below form: Audit metadata panel (read-only) and warnings panel (shown only when present)
- Footer area: Secondary actions and read-only/permission messaging as needed

## Interaction Flow
1. Navigate to People / Employees and choose “Create Employee” (or direct create route).
2. Create screen loads with empty form; status defaults to ACTIVE.
3. User enters required fields (firstName, lastName, employeeNumber, hireDate, status) and optional fields as needed.
4. On Save:
   1. Frontend validates required fields, email formats, and date rule (terminationDate >= hireDate when provided).
   2. Send create request to backend People REST endpoint.
   3. If 201 success: show success confirmation; populate returned employeeProfileId and audit timestamps/actors if provided; keep form in saved state.
   4. If 201/200 success with warnings payload: show success confirmation and display non-blocking warnings panel with warning code(s) and message(s); retain saved state.
   5. If 400 validation: show field-level errors and/or summary banner; do not navigate away.
   6. If 403 forbidden: show access denied panel; disable form.
   7. If 409 conflict/duplicate: show blocking conflict banner with backend-provided duplicate/conflict details; do not save; keep user inputs.
5. Navigate to edit via “Edit” action (from detail context) or direct URL.
6. Edit screen load:
   1. Require update permission for access; if unauthorized return 403 view.
   2. Request load endpoint; show loading state.
   3. If 404: show not found state with link back to Employees.
   4. If success: populate form, show employeeProfileId and audit metadata; store concurrency token if provided.
7. Edit and Save:
   1. Apply same client-side validation rules.
   2. Submit update request including concurrency token when provided/required by backend.
   3. If 200 success: show success confirmation; refresh audit metadata; show warnings panel if warnings returned.
   4. If 409 due to concurrency mismatch: show conflict message indicating record changed; prompt user to reload latest data (and prevent overwriting).
8. Terminated employee read-only behavior:
   1. If loaded profile has status TERMINATED and response does not include capability allowing terminated edits: render all form fields read-only, disable Save, and show explanation banner (“Terminated employees are read-only by default”).

## Notes
- Permissions:
  - Create route and submit require create permission.
  - Edit route load and update submit require update permission (default to update permission for edit access since no separate view permission is specified).
- Fields (EmployeeProfile):
  - Read-only: employeeProfileId (edit), createdAt, updatedAt, createdBy/updatedBy (if provided), concurrency token (read-only on load; required on update submit if provided).
  - Required: firstName, lastName, employeeNumber (unique), status (enum; default ACTIVE on create), hireDate.
  - Optional with rules: terminationDate (must be >= hireDate), emails (normalize lowercase), remaining optional strings per backend schema.
- Backend response handling:
  - Success: 201 (create) / 200 (update); display confirmation and returned identifiers/audit fields.
  - 400: render field-level validation aligned to People domain rules.
  - 403/404: show dedicated states; prevent editing.
  - 409: handle both duplicate/conflict errors and optimistic concurrency mismatch; present backend message/details.
  - Warnings payload: display as non-blocking callout after save; do not block success or alter saved state.
- Sensitive data:
  - Capabilities object may include sensitive flags (e.g., allowTerminatedEdits); do not display capabilities by default; only use to control UI behavior when explicitly enabled/needed.
- UI consistency:
  - Menu placement must follow existing app conventions for People / Employees.
  - Employees list screen is out of scope; only entry points/actions are required.
