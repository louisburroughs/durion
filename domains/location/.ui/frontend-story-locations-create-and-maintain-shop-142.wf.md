# [FRONTEND] [STORY] Locations: Create and Maintain Shop Locations
## Purpose
Enable authenticated users to create new shop locations with required details while defaulting new locations to ACTIVE without exposing a status selector. Allow users to edit existing locations while keeping the location code immutable. Provide a deactivation flow to change an ACTIVE location to INACTIVE and ensure list filtering reflects status changes.

## Components
- Global header with page title and breadcrumb (e.g., Locations / Create or Locations / {Location Name})
- Locations list view
  - Status filter control (e.g., ACTIVE / INACTIVE / ALL)
  - Locations table/list rows (name, code, address summary, timezone, status)
  - Row click navigates to location detail
- Location detail view
  - Summary panel (name, code, status badge, address, timezone)
  - Operating hours display section
  - Primary actions: Edit, Deactivate (conditional)
  - Success/toast confirmation area
- Create Location form
  - Fields: Code (if required), Name, Address (multi-field), Timezone (IANA), Operating Hours editor
  - No Status selector (status defaults to ACTIVE)
  - Buttons: Save, Cancel
  - Inline validation/error messages
- Edit Location form
  - Code field displayed read-only/disabled
  - Editable fields: Name, Address, Timezone, Operating Hours
  - Buttons: Save, Cancel
- Deactivate confirmation modal/dialog
  - Message + Confirm Deactivate / Cancel buttons
- Loading and error states (spinners, inline banners)

## Layout
- Top: Global header + breadcrumb + page title
- Main (Create/Edit): Form in a single column; actions (Save/Cancel) aligned bottom-right or top-right
- Main (Detail): Summary header with status badge + action buttons; details sections stacked below (Address, Timezone, Operating Hours)
- Main (List): Filter bar above table/list; table/list fills remaining space

## Interaction Flow
1. Create Location (defaults to ACTIVE)
   1. Authenticated user navigates to Locations → Create Location.
   2. UI shows Create form with fields for code (if required), name, address, timezone (IANA), and operating hours.
   3. UI does not show any status selector on the create form.
   4. User enters valid values and clicks Save.
   5. UI sends a create request with entered data and omits status (status field not sent or ignored if present).
   6. On success, UI shows a success confirmation and navigates to the new Location detail screen showing the created location data (implicitly ACTIVE).
2. Update Location (code immutable)
   1. User opens an existing Location detail and clicks Edit.
   2. UI opens Edit form with Code displayed as read-only/disabled.
   3. User changes Name (and/or other editable fields) and clicks Save.
   4. UI sends an update request including the location identifier and updated fields, and does not attempt to change the code.
   5. UI displays updated values from the response (or refetches and then displays updated values) and shows a success confirmation.
3. Deactivate Location (ACTIVE → INACTIVE)
   1. User views an ACTIVE location detail where Deactivate action is available.
   2. User clicks Deactivate; UI opens confirmation modal.
   3. User confirms; UI sends a status update request including status=INACTIVE.
   4. On success, UI updates the detail view to show status as INACTIVE.
   5. UI removes/hides the Deactivate action once status is INACTIVE.
   6. User returns to Locations list; when filter is ACTIVE, the deactivated location is not shown.

## Notes
- Create flow acceptance: no status selector; create request must not include status (or status must be omitted/ignored); new location should appear as ACTIVE on detail by default.
- Edit flow acceptance: code is immutable; code field must be read-only and update requests must not attempt to change it.
- Deactivate flow acceptance: confirmation required; status update sets INACTIVE; UI reflects status change; Deactivate action unavailable afterward; ACTIVE filter excludes INACTIVE locations.
- Validation constraints implied: timezone must be valid IANA; operating hours must be valid; address required; code required only if API requires it.
- Risk/requirements incomplete: confirm exact API payload shapes/fields for create/update/status update; confirm whether create response includes status and whether detail view should display status badge consistently.
