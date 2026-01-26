# [FRONTEND] [STORY] Users: Provision User and Link to Person
## Purpose
Enable an Admin to provision a new user in the Security service and link that user to an existing Person from the People service. The flow supports searching and selecting a Person, capturing required identity fields, optionally assigning initial roles, and submitting an idempotent provisioning request. The UI must show immediate success while Person-linking completes asynchronously, and allow manual status refresh with clear, actionable error messaging.

## Components
- Page header: “Provision User and Link to Person”
- Person search section
  - Search input (email/name fragment)
  - Search button / enter-to-search
  - Results list (selectable rows)
  - Empty-state message (no matches)
  - Selected Person summary (read-only: personId, name, email)
- Provisioning form section
  - personId (read-only, populated on selection)
  - Email (prefilled from selected Person, locked/non-editable)
  - Username input (required)
  - Display name input (optional)
  - Identity provider select (required; default value preselected)
  - Roles multi-select (0..n) populated from roles list (by role name)
  - Submit button (disabled until valid; disabled while submitting)
- Status & feedback section
  - Success confirmation panel (shows userId, personId, username, email, provider)
  - Link status indicator (linked / pending / failed) with explanatory text
  - Manual “Refresh link status” button
  - Error alert panel rendering canonical error envelope (field + message)
  - Inline error for email mismatch with action button: “Reset from selected Person”
- Loading indicators
  - Person search loading state
  - Roles list loading state
  - Submit in-progress state
  - Refresh in-progress state

## Layout
- Top: Page header + brief helper text (what this does; linking is async)
- Main (stacked sections):
  - Person Search (input + results list; selected Person summary beneath)
  - Provisioning Form (fields in a single column; roles multi-select near bottom; submit at bottom-right)
  - Status/Feedback (appears after submit; includes link status + refresh + errors)
- Inline hint (1–2 lines): [Person Search] → [Provisioning Form] → [Status & Refresh]

## Interaction Flow
1. Load screen
   1. Fetch roles list from Security service; show loading state until available.
   2. Initialize provisioning form with provider default selected; submit disabled.
2. Search and select Person (primary path)
   1. Admin enters email/name fragment and triggers search.
   2. UI calls People person search endpoint; show results list.
   3. Admin selects a Person from results.
   4. UI sets personId (read-only) and prefills Email from selected Person; Email becomes locked (editable=false).
   5. Submit remains disabled until required fields are valid (person selected + username non-empty + email valid and matches selected Person + provider valid).
3. Provision user (primary path)
   1. Admin enters Username (required); optionally enters Display name; optionally selects 0..n Roles.
   2. Admin clicks Submit.
   3. UI disables Submit and sends provision request to Security provisioning endpoint with: personId, username (idempotency key), email, optional displayName (omit if empty), provider, roles (empty array or omitted per implementation).
   4. On success response, UI shows success confirmation and persists the last successful response in screen state.
   5. UI displays link status from response (linked vs pending/failed) with message: linking completes asynchronously; no polling.
4. Manual refresh link status (no polling)
   1. Admin clicks “Refresh link status”.
   2. UI calls Security user detail/status endpoint (or appropriate refresh endpoint) and updates displayed link status and timestamps if provided.
   3. If refresh fails, show a non-blocking “status refresh error” message while keeping last known status visible.
5. Edge case: Person not found blocks provisioning
   1. Search returns zero results; UI shows empty-state “No matching person found”.
   2. No Person can be selected; provisioning submit remains disabled.
6. Edge case: Email mismatch prevented and handled if returned
   1. Email field is locked to selected Person email; UI enforces case-insensitive match.
   2. If backend returns email mismatch error, UI shows “Email must match selected Person email”.
   3. Provide action “Reset from selected Person” that re-applies selected Person email into the Email field (and keeps it locked).
7. Edge case: Provisioned but link not complete
   1. If response indicates user provisioned but not linked, show warning text: “The user was provisioned, but linking to Person did not complete.”
   2. Display link status and any provided timestamps; prompt Admin to use manual refresh.

## Notes
- Submit enablement (client-side): requires a selected Person (not free text), username non-empty, email format-valid and equals selected Person email case-insensitively (enforced by prefilling + locking), provider is one of allowed values (default preselected). Backend remains authoritative.
- Idempotency: username is the identity key/idempotency key; UI should prevent double-submit via disabled state during submission.
- Roles: multi-select allows zero roles; UI does not create/edit permissions; permission keys immutable and validated server-side.
- Asynchronous linking: do not auto-poll; only update link status on explicit manual refresh.
- Error handling: render canonical error envelope with actionable messaging; keep last successful response visible after success; show refresh errors without clearing prior status.
- Display fields after success: userId, personId, username, email, provider, linked/provisioned booleans, and created/updated/linked timestamps when available.
