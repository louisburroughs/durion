# [FRONTEND] [STORY] Party: Create Individual Person Record
## Purpose
Enable an authenticated CSR to create a new Individual Person (CRM Party subtype Person) through a guided Moqui/Quasar/Vue screen flow. The screen collects required person identity fields and preferred contact method, plus optional zero-to-many emails and phone numbers with primary selection rules. The UI enforces client-side validations aligned to CRM domain rules and handles submit success/error states while preserving user input.

## Components
- Page header: “Create Person”
- Breadcrumb/back link (optional)
- Person details form section
  - Text input: First name (required)
  - Text input: Last name (required)
  - Select/radio group: Preferred contact method (required; enum)
- Contact points section: Emails
  - Repeating list/table of email rows
  - Per-row: Email address input (required if row exists), Primary checkbox/radio
  - Buttons: Add email, Remove row
  - Inline validation messages per row/field
- Contact points section: Phones
  - Repeating list/table of phone rows
  - Per-row: Phone number input (required if row exists), Extension input (optional), Primary checkbox/radio
  - Buttons: Add phone, Remove row
  - Inline validation messages per row/field
- Form actions
  - Primary button: Create
  - Secondary button: Cancel/Back
- Submission state UI
  - Loading indicator on Create button and/or page-level spinner
  - Disabled state for inputs/actions during in-flight request
- Feedback
  - Success confirmation panel/banner including created Person/Party ID
  - Error alert/banner for backend errors (e.g., 400) plus inline field errors when available

## Layout
- Top: Page header + optional breadcrumb/back
- Main (single column): Person Details section → Emails section (repeatable rows) → Phones section (repeatable rows) → Actions (Create/Cancel) → Feedback area
- Inline within each contact section: rows listed vertically with per-row remove action aligned right

## Interaction Flow
1. Initial load
   1. Display empty required fields (First name, Last name, Preferred contact method) and empty Emails/Phones lists.
   2. Create button is enabled only when required person fields are valid; contact rows are optional unless added.
2. Minimal create succeeds (Scenario 1)
   1. User enters First name and Last name.
   2. User selects Preferred contact method (explicit selection required).
   3. User clicks Create.
   4. UI validates required fields; if valid, disables inputs and Create button and shows submitting state.
   5. UI submits create request payload (person fields; no contact points if none added).
   6. On 200/201 success, UI shows success confirmation including the new Person/Party identifier and transitions to success state (no further edits on this screen unless explicitly allowed).
3. Create with multiple emails/phones and primary enforcement (Scenario 2)
   1. User adds two email rows; enters valid email addresses.
   2. User marks the second email as Primary.
   3. UI ensures only one primary email is set (selecting one primary unsets any other primary in EMAIL rows).
   4. User adds two phone rows; enters phone numbers that normalize to 10–15 digits; optionally enters extensions.
   5. User marks the first phone as Primary; UI ensures only one primary phone is set (PHONE rows).
   6. User clicks Create; UI validates all present rows and derived normalization constraints.
   7. UI submits payload including 4 contact points with correct kinds and primary flags; phone rows include derived normalizedDigits.
   8. On success, show confirmation and success state.
4. Invalid email rejected; nothing created (Scenario 4)
   1. User enters valid First name, Last name, and Preferred contact method.
   2. User adds an email row and enters an invalid email value.
   3. User clicks Create.
   4. UI blocks submit and shows inline email format error (or, if submitted and backend returns 400, maps error to field/banner).
   5. UI remains in editing/error state with all user input preserved; no success confirmation is shown.
5. General validation and error handling
   1. Missing required person fields: block submit; show inline errors on the missing fields.
   2. Phone normalization: on blur or on submit, derive digits-only normalizedDigits from phoneNumber; if derived length is not 10–15, show inline error and block submit.
   3. Extension: if present, must be digits-only; otherwise show inline error and block submit.
   4. Backend error (non-validation): show error banner; re-enable inputs; preserve user input for correction/retry.

## Notes
- Required fields: firstName (trimmed), lastName (trimmed), preferredContactMethod (explicit enum selection; allowed values per domain).
- Contact points are optional (0..N emails, 0..N phones). If a row exists, its required fields must be valid before submit.
- Primary rules: at most one primary EMAIL and at most one primary PHONE; enforce client-side by auto-unsetting other primaries within the same kind.
- Phone payload requirements: normalizedDigits is required and derived from phoneNumber; digits-only length 10–15. phoneNumber remains the user-entered display value.
- UX state requirement: disable submit and inputs during in-flight request to prevent duplicate submits; show a clear submitting indicator.
- Success response: handle 200 or 201; display confirmation including the created Person/Party ID.
- Out of scope: role tags, dedup/merge workflows, associating person to accounts/vehicles/work orders, editing existing person records.
- Ensure error handling preserves user-entered data and supports retry without losing added rows.
