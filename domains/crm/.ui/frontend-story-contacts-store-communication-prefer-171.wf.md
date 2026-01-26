# [FRONTEND] [STORY] Contacts: Store Communication Preferences and Consent Flags
## Purpose
Enable CSRs to view and (when permitted) edit a person’s CRM-owned communication preferences from the Contact/Person detail experience. The UI must load existing preferences, handle missing preference records with a clear empty state, and support creating/updating preferences with validation and standard error handling. The section also surfaces audit metadata (“last updated” and “update source”) to provide traceability.

## Components
- Contact/Person Detail screen
  - “Communication Preferences” section header
  - Read-only summary fields
    - Preferred communication channel (display value)
    - Email marketing consent (display helper: Opted in/Opted out/Not set)
    - SMS notification consent (display helper: Opted in/Opted out/Not set)
    - Last updated timestamp (local timezone)
    - Update source (read-only)
  - Actions
    - Edit button (permission-gated)
- Edit experience (inline form or nested screen/modal)
  - Preferred channel dropdown (enum values from backend)
  - Email marketing consent select/toggle (Yes / No / Unset)
  - SMS notification consent select/toggle (Yes / No / Unset)
  - Read-only audit metadata display (last updated, update source) if available
  - Save button
  - Cancel button
  - Dirty-form detection prompt on cancel/navigation
- States & messaging
  - Loading indicator/skeleton for section
  - Empty state (no preferences record yet)
  - Inline field validation errors (400)
  - Permission denied message (403 on save)
  - Party not found/context error (404 party missing)
  - Conflict message and retry guidance (409 optimistic locking, if applicable)

## Layout
- Top: Contact/Person header (name/identifiers) + existing detail content
- Main: Contact/Person detail sections stacked vertically
  - …other sections…
  - Communication Preferences
    - Rowed summary fields + audit metadata
    - Right-aligned actions (Edit when allowed)
    - Edit mode replaces summary with form (or opens nested screen/modal)

## Interaction Flow
1. Load & view preferences (happy path)
   1. CSR opens Contact/Person detail for a given party/person UUID.
   2. UI calls “Get communication preferences for a party/person” with partyId.
   3. On 200 with preferences DTO, render read-only summary:
      - Preferred channel, email consent, SMS consent, last updated, update source (when provided).
   4. If CSR has update permission, show Edit action; otherwise keep view-only.

2. Load when preference record is missing
   1. If backend indicates “preferences not found” (either 200 with empty DTO or 404 with specific error like PREFERENCES_NOT_FOUND), show empty state:
      - Message: “No communication preferences set yet.”
      - If permitted, show “Edit” (or “Add preferences”) action to create initial record.

3. Edit/update existing preferences
   1. CSR clicks Edit (only if permitted).
   2. Show edit form (inline or nested screen) prefilled with current values.
   3. CSR changes preferred channel and/or consent flags.
   4. On Save, call create/update service:
      - Include optimistic locking field if required for updates (e.g., version/ETag/lastUpdatedStamp).
      - Include updateSource if UI must inject; otherwise rely on backend inference.
   5. On success, return to read-only summary and refresh displayed values + audit metadata.

4. Create initial preferences (when missing)
   1. From empty state, CSR enters values and clicks Save.
   2. UI calls create service (no id/version field).
   3. On success, show read-only summary with newly stored values.

5. Validation and error handling
   1. 400 validation errors:
      - Preferred channel invalid → show field error “Select a valid channel.”
      - Consent invalid → show field error “Consent must be yes/no/unset.”
      - Missing updateSource → show blocking error (should be prevented by backend inference or UI injection).
   2. 401 → show authentication error and prevent editing.
   3. 403 on load → show read-only view without edit actions (no edit entry points).
   4. 403 on save → show permission denied error; keep user in edit mode with inputs preserved.
   5. 404 on load:
      - If party not found → show context error (cannot display preferences).
      - If preferences not found → show empty state (as above).
   6. 409 conflict (optimistic locking) → show conflict message and prompt reload/retry; preserve user changes if possible.

6. Consent display helper behavior
   1. Map consent values:
      - true → “Opted in”
      - false → “Opted out”
      - null/unset → “Not set”
   2. If backend defines null-as-opt-out, show secondary text under “Not set”: “Treated as opted out.”

## Notes
- Role-gating must be fail-closed: hide/disable Edit unless explicit update permission is confirmed; on 403 load, render view-only with no edit actions.
- Must support both “missing record” patterns: 200 with empty DTO and/or 404 with a specific “PREFERENCES_NOT_FOUND” indicator; distinguish from “party not found.”
- Preferred channel options should come from backend-provided enum values; do not hardcode.
- Audit metadata (“last updated” and “update source”) is view-only; update source may be system-populated.
- Dirty-form detection required: warn on cancel/navigation if changes are unsaved.
- Open implementation decisions to confirm with backend:
  - Optimistic locking field name and 409 handling strategy.
  - Whether frontend must send updateSource (constant) or backend infers from context.
  - Consent storage model (separate consent entity vs fields on preferences DTO) and null semantics.
- Testing focus: permission denial (403), consent value mapping (true/false/null), empty state behavior, and validation error mapping.
