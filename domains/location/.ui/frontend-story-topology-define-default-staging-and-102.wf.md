# [FRONTEND] [STORY] Topology: Define Default Staging and Quarantine Locations for Receiving
## Purpose
Provide a configuration screen to view and update a site’s default receiving Staging and Quarantine storage locations. The UI must support nullable defaults during rollout, enforce that the two defaults are distinct, and restrict selection to ACTIVE storage locations. It should display current configuration (and audit metadata when available) and handle authorization failures gracefully.

## Components
- Page header: “Site Default Locations” + site identifier/name (e.g., WH-1)
- Read-only summary panel: current defaults (staging/quarantine) with status indicators
- Optional metadata row: Last updated at/by (if provided by backend)
- Form section: “Receiving Defaults”
  - Picker/select: Default Staging Location (ACTIVE-only options)
  - Picker/select: Default Quarantine Location (ACTIVE-only options)
  - Inline validation messages (required, must be distinct, inactive current default)
- Callouts/alerts:
  - “Not configured” state for null defaults
  - “Inactive (not selectable)” display for existing inactive defaults
  - Permission warning: “You do not have permission to update site defaults.”
  - Error alert area for API/service errors (including 401/403 and business rule violations)
- Actions:
  - Save button (disabled/hidden when unauthorized; disabled until valid)
  - Cancel/Reset (optional) to revert to last loaded values
- Loading states: skeleton/spinner for initial fetch and for saving

## Layout
- Top: Page header + site context
- Main:
  - Summary panel (current staging/quarantine + optional last-updated metadata)
  - Form card: two stacked pickers with helper text and inline errors
  - Bottom-right of form: Actions (Save, optional Cancel/Reset)
- Footer/inline area: global alert/toast region for success/error

## Interaction Flow
1. Navigate to “Site Default Locations” screen for a specific site.
2. Screen loads current site defaults (may be null) and eligible storage locations (ACTIVE only); show loading state until ready.
3. Display current values:
   1) If default is null, show “Not configured”.
   2) If default exists but is INACTIVE, show value as “Inactive (not selectable)” and flag that a new ACTIVE selection is required before saving.
4. User selects Default Staging Location from ACTIVE options.
5. User selects Default Quarantine Location from ACTIVE options.
6. Client-side validation runs:
   1) Both fields required to save.
   2) Staging and Quarantine must be different; if same, show inline error and disable Save.
7. User clicks Save:
   1) Submit update with both storageLocationIds.
   2) On success, show confirmation and refresh/reload displayed current defaults (and metadata if available).
8. Authorization edge cases:
   1) If GET is allowed but update is not: show read-only current defaults; disable/hide Save and show permission message.
   2) If request returns 401/403: show error state/message and prevent saving.
9. Backend validation/error edge cases:
   1) If backend rejects because staging == quarantine, display returned error and keep user selections for correction.
   2) If backend rejects because location not in site, show error and require re-selection.
   3) If storage location list fails to load, show error and disable pickers/Save.

## Notes
- Enforce business rules:
  - Staging and quarantine defaults must be distinct (DECISION-LOCATION-010); validate client-side and expect server-side enforcement.
  - Picker options must include ACTIVE storage locations only (DECISION-LOCATION-024).
- Rollout/migration: defaults may be null; UI must clearly show “Not configured” and allow setting.
- If a current default is now INACTIVE, it must not be selectable in the picker; display it as “Inactive (not selectable)” and require choosing a new ACTIVE location before saving.
- Display last-updated metadata (updatedAt/updatedBy) only if backend provides it; otherwise show core fields only (DECISION-LOCATION-020 pattern).
- Frontend must handle 401/403 gracefully; authorization is enforced server-side (DECISION-LOCATION-007).
- Save implementation may be via direct REST from Vue/Quasar or via Moqui screen transitions that proxy to backend services; UI should not depend on a single approach.
- Success criteria: after saving, reloading the screen shows the saved defaults and the UI reflects persisted values.
