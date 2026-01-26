# [FRONTEND] [STORY] Vehicle: Create Vehicle Record with VIN and Description
## Purpose
Enable a Service Advisor to create a new Vehicle record in the CRM via a guided Moqui screen flow. The screen must collect VIN, unit number, and description (plus optional license plate and optional association fields), perform basic client-side validation/normalization, submit to the backend service/API, and handle success/error responses. After creation, the user is taken to a Vehicle Detail (or confirmation) view showing the persisted vehicle data.

## Components
- Page header: “Create Vehicle”
- Breadcrumbs/back link: “Vehicles” → “Create”
- Create Vehicle form
  - VIN input (required)
  - Unit number input (required)
  - Description input (required; free text make/model/year)
  - License plate input (optional)
  - Optional association selector (e.g., Party/Customer/Fleet) if backend requires at create (TBD)
- Inline field validation messages (per field)
- Global alert/banner area for submission errors
- Primary button: “Create Vehicle”
- Secondary button: “Cancel”
- Loading/disabled state on submit
- Post-create navigation target: Vehicle View / Confirmation view (read-only summary)

## Layout
- Top: Header + breadcrumbs/back link
- Main (center): Form in a single column with required fields first
  - VIN
  - Unit number
  - Description
  - License plate (optional)
  - Association selector (conditional, only if required)
  - Actions row: [Cancel] [Create Vehicle]
- Above form: global banner area for API errors
- Footer (optional): brief help text on VIN rules

## Interaction Flow
1. User navigates to Vehicle Create screen while authenticated and authorized to create vehicles.
2. User enters VIN, unit number, and description; optionally enters license plate (and optional association if shown).
3. Client-side validation runs:
   1. Required fields must be non-empty (VIN, unit number, description).
   2. VIN is trimmed and uppercased.
   3. VIN sanity check: exactly 17 characters and excludes I, O, Q.
   4. Optional fields are trimmed; if empty, send null/omit per contract.
4. User clicks “Create Vehicle”:
   1. Disable inputs and show loading state.
   2. Submit create request with normalized VIN and other fields.
5. On success response:
   1. Backend returns new vehicle UUID (required).
   2. Navigate to Vehicle View (or confirmation view) for that UUID.
   3. Vehicle View loads and displays persisted vehicle fields (prefer using returned summary if provided; otherwise load by id).
6. On failure response:
   1. Map service error payload to a global banner message.
   2. Map field-specific errors to inline field messages (e.g., VIN invalid, required missing, uniqueness conflict).
   3. Re-enable form and preserve user-entered values for correction.
7. Authorization edge case:
   1. If backend returns 403, show an access-denied banner and disable submission (optionally hide Create button).
8. Cancel flow:
   1. Clicking “Cancel” returns to previous page or Vehicles list without creating a record.

## Notes
- VIN normalization is mandatory: trim + uppercase before submit; validate 17 chars excluding I/O/Q.
- Required fields: VIN, unit number, description; license plate is optional; association-at-create is TBD and should be treated as conditional UI (only show if backend requires it, as it is blocking).
- Error handling must support both banner-level errors and field-level errors based on backend error payload mapping.
- Success criteria: after create, user lands on a detail/confirmation view for the new vehicle UUID and sees persisted fields.
- Backend permissions are enforced via JWT authorities; unauthorized operations return 403; client may also receive capability headers for feature/permission checks.
- Request DTO details are partially TBD (e.g., VIN uniqueness scope; license plate format single string vs plate+region); design should avoid over-constraining input until contract is finalized.
- Auditability concern: ensure created-by/created-at are available on the Vehicle View if the backend provides them (or plan for later enhancement).
