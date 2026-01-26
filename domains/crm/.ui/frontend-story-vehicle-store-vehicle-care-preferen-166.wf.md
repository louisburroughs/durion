# [FRONTEND] [STORY] Vehicle: Store Vehicle Care Preferences
## Purpose
Enable Service Advisors to view, create, and update vehicle care preferences from the Vehicle Profile using existing backend endpoints with optimistic locking. Ensure preferences and notes are visible read-only on Estimate and Work Order screens. Provide asynchronous section loading, clear empty/denied/not-found states, and an audit history view for changes.

## Components
- Vehicle Care Preferences section container (asynchronously loaded)
- Section header with title and status/metadata summary (last updated date + updated by, when provided)
- Read-only display fields (preferences + notes)
- Empty state panel (“No preferences saved”) with “Add preferences” button (when editable)
- Edit mode form
  - Preferred tire brand (text)
  - Preferred oil brand/type (text)
  - Service interval value (number; 1–100000)
  - Service interval unit (enum; required if interval value present)
  - Additional preference fields (text) as applicable
  - Short notes (single-line text)
  - Service notes (multi-line textarea)
- Action buttons: Edit, Save, Cancel
- Inline validation messages (field-level) + form-level error banner
- Toast/inline success confirmation message
- Access denied state panel (403)
- Vehicle not found state panel (missing/invalid vehicle context)
- Loading indicator (skeleton/spinner) scoped to the section
- Change history link/button (Vehicle Profile only)
- Change History drawer/modal/page section
  - Audit entries list (timestamp, actor display name/userId fallback, action)
  - Optional diff viewer (backend-provided safe diff structure)

## Layout
- Vehicle Profile: Main content column → “Vehicle Care Preferences” card/section
  - Header row: Title (left) | Last updated + Updated by (right) | Actions (Edit / Add preferences / Change history)
  - Body: Read-only values or editable form; notes at bottom
- Estimate / Work Order: Vehicle info area → “Vehicle Care Preferences” read-only subsection (no actions)
- Inline layout hint: [Section Header: Title | Metadata | Actions] → [Body: Fields] → [Notes] → [Messages]

## Interaction Flow
1. Load section (Vehicle Profile / Estimate / Work Order)
   1. UI reads vehicleId from route/context; if missing/invalid, show “Vehicle not found” within the section and disable any save actions.
   2. UI loads preferences asynchronously (local loading indicator only for this section).
   3. If GET returns 200, render read-only values and metadata (updatedAt/updatedBy when provided).
   4. If GET returns 404, render empty state (“No preferences saved”) and show “Add preferences” only when user has edit permission and screen is Vehicle Profile.
   5. If GET returns 403, render access denied state (no values, no edit controls).

2. Create preferences from Vehicle Profile (Scenario 1)
   1. From empty state, user clicks “Add preferences” → enter edit mode with empty form.
   2. User enters values (e.g., preferred tire brand, service notes).
   3. User clicks “Save” → UI sends UPSERT with fixed-schema fields only (no vehicleId in body; vehicleId from route/context).
   4. On 200/201, UI exits edit mode, shows saved values read-only, updates metadata (version/updatedAt/updatedBy), and shows success confirmation.

3. Update existing preferences with optimistic locking
   1. User clicks “Edit” → form prefilled with current values; version retained (hidden/read-only).
   2. User modifies fields and clicks “Save” → UI sends UPSERT including version.
   3. On 200, UI refreshes display with returned DTO (including updated version) and shows success confirmation.
   4. On 409 conflict, show conflict error banner; if currentRecord is provided, offer “Reload latest” action to replace form values with currentRecord and exit/return to edit mode as appropriate.

4. Validation and error handling on save
   1. On 400 validation error, map backend field errors to inline messages (e.g., interval 1–100000; unit required if interval present).
   2. On 403 forbidden, show access denied message and exit edit mode (no further edits).
   3. On 500 unexpected, show non-sensitive generic error banner; keep user input in the form for retry.

5. Read-only visibility on Estimate and Work Order (Scenarios 5 & 6)
   1. When viewing an Estimate or Work Order, display preferences and notes read-only.
   2. Do not render Add/Edit/Save/Cancel controls; no Change History entry point required.

6. View audit history (Vehicle Profile)
   1. User clicks “Change history” → open drawer/modal/secondary panel.
   2. UI loads audit entries; show loading state and then list entries with localized timestamp and actor (displayName or userId fallback).
   3. If diff is present, allow expanding an entry to view safe diff details.

## Notes
- Data/DTO constraints:
  - vehicleId required, read-only (from route/context).
  - version optional on create; required on update when present; used for optimistic locking.
  - Interval value must be 1–100000; interval unit required if interval value present.
  - updatedAt/updatedBy fields are read-only and shown when provided.
- State/role rules:
  - Vehicle Profile: editable only in explicit edit mode and only with edit permission; otherwise read-only.
  - Estimate/WO: always read-only; no edit controls.
- Empty state behavior: GET 404 is treated as “no preferences yet,” not an error.
- Vehicle not found: if vehicle context is missing/invalid, section must show “Vehicle not found” and must not attempt save.
- Asynchronous load requirement: section loads independently with a local loading indicator (per SD-UX-ASYNC-SECTION-LOAD).
- Error shapes are backend-defined; UI should support field-level errors (400), forbidden (403), conflict (409 with optional currentRecord), and generic unexpected (500) without exposing sensitive details.
- TODO (design): decide whether Change History is a modal vs drawer vs inline panel; define diff presentation for safe diff structure.
