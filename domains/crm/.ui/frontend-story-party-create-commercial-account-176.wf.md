# [FRONTEND] [STORY] Party: Create Commercial Account
## Purpose
Enable a Fleet Account Manager to create a new Commercial Account (Party) by entering required details and selecting default billing terms. Before creation, the UI must perform a duplicate check and, if potential duplicates are found, require explicit override confirmation and justification to proceed. The experience must provide clear validation, loading states, and a success confirmation with the newly created stable CRM ID.

## Components
- Page header: “Create Commercial Account”
- Access denied state (403): message + navigation option (e.g., back)
- Loading indicators (billing terms load, duplicate-check, create)
- Form fields:
  - Legal name (required text)
  - DBA (optional text)
  - Tax ID (optional text)
  - Default billing terms (required select)
  - External identifiers (optional section; conditionally rendered if contract supports)
- Inline field validation messages (required presence; backend field errors shown inline)
- Primary action button: “Create”
- Secondary action button: “Cancel”
- Duplicates panel (shown only when duplicate-check returns matches)
  - List of candidate duplicates (summary rows)
  - Per-candidate action: “Open candidate” (opens candidate Party view in same tab)
  - Override confirmation controls:
    - Checkbox: “I understand this may create a duplicate account”
    - Justification text input (required when overriding)
    - Button: “Create anyway” (gated by checkbox + justification)
- Success confirmation state
  - Stable CRM ID display (canonical ID)
  - Copy-to-clipboard control for CRM ID
  - Created timestamp / created by (only if provided)
  - Link/button to “View account” (or deterministic placeholder target if view not available)

## Layout
- Top: Page header + brief helper text (optional)
- Main (single column):
  - Form section: Legal name, DBA, Tax ID, Default billing terms, External identifiers (conditional)
  - Actions row: [Cancel] [Create]
  - Below form (conditional): Duplicates panel with candidate list + override controls + [Create anyway]
  - Post-submit (conditional): Success confirmation panel replacing or appearing above form
- Footer area (optional): inline error summary for submission-level errors

## Interaction Flow
1. Page load
   1) Load billing terms; show loading state within 200ms and disable submit until available (or show required error on submit if not selected).
   2) If backend indicates user is unauthorized (403) on access or subsequent calls, show Access Denied state and block creation actions.
2. Primary flow: create successfully with required fields
   1) User enters Legal name and selects Default billing terms.
   2) User clicks “Create”; UI runs client-side required checks (presence only).
   3) If required fields missing, block submission and show inline errors (e.g., “Default billing terms is required.”); do not call services.
   4) If valid, call duplicate-check service (preferred).
   5) If no duplicates returned, call create service with entered fields.
   6) On success, show confirmation with stable CRM ID and copy control; display created timestamp/created by if provided.
   7) Navigate to account view screen if it exists; otherwise remain on confirmation with a deterministic link target placeholder.
3. Duplicate handling flow: duplicates returned
   1) After duplicate-check returns matches, render Duplicates panel below the form.
   2) User may click “Open candidate” on any match to navigate to candidate Party view in the same tab; user can return via browser back with form inputs preserved.
   3) To proceed, user must:
      1) Check “I understand this may create a duplicate account”.
      2) Enter non-empty justification text.
   4) User clicks “Create anyway”; UI calls create service with override flag + justification (and audit fields if supported).
   5) If backend returns field errors (e.g., justification constraints), display inline on the relevant field; do not enforce format beyond non-empty unless backend errors specify.
4. Cancel flow
   1) User clicks “Cancel”; return to the form state with inputs preserved (no service calls).
5. Error and resilience behaviors
   1) Prevent double-submit during duplicate-check and create (disable buttons while loading).
   2) Show loading state within 200ms for billing terms load, duplicate-check, and create.
   3) If duplicate-check is not separate in implementation, proceed directly to create call while preserving the same UX expectations for duplicates/override if returned by backend.

## Notes
- Client-side validation: required presence only; no policy guessing (e.g., Tax ID format not enforced unless backend returns errors).
- Duplicate override is a distinct explicit state; “Create anyway” must be gated by both checkbox confirmation and non-empty justification.
- External identifiers section is rendered only if the backend/contract supports it; structure is TBD.
- Contact inputs (phone/email) are not included unless backend contract requires them.
- Success confirmation must display canonical stable CRM ID and allow copy; show created timestamp/created by only if provided by backend response or subsequent view load.
- Access control: if any relevant call returns 403, show Access Denied state and ensure no account is created.
- TODOs:
  - Confirm backend minimum length for justification; UI enforces non-empty only.
  - Confirm billing terms key type (string vs UUID) for payload.
  - Confirm whether create response includes created timestamp/created by and whether account view route exists.
