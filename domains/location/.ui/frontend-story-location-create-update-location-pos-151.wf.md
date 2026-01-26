# [FRONTEND] [STORY] Location: Create/Update Location (pos-location) Including Timezone
## Purpose
Enable authenticated users to create new Locations and update existing Locations, including selecting an IANA timezone. Ensure the Location code is immutable after creation, while other non-identity fields remain editable even when the Location is INACTIVE. Provide clear success feedback, field-level validation for duplicate codes, and error details including a copyable correlation/request ID when available.

## Components
- Page header with title (Create Location / Edit Location / Location Detail)
- Breadcrumbs or back link to Location list
- Location form
  - Code (text; required on create; read-only on edit)
  - Name (text; required)
  - Address (contract-driven field group; editable if supported)
  - Timezone (select/dropdown; required; IANA TZ)
  - Parent Location (picker/select with search; optional)
  - Check-in Buffer Minutes (number input; optional)
  - Cleanup Buffer Minutes (number input; optional)
- Status section (detail/edit)
  - Current status badge (ACTIVE/INACTIVE)
  - Explicit status action buttons (e.g., Deactivate / Activate)
- Read-only metadata (detail view)
  - Location ID (read-only)
  - Created At / Updated At (read-only)
- Hidden field handling
  - Version (hidden, read-only; included in update payload if provided)
- Primary actions
  - Save / Create button
  - Cancel button
- Notifications
  - Success confirmation toast/banner
  - Error banner with optional correlation/request ID (copy control)
  - Field-level validation messages (e.g., duplicate code)
- Loading and empty states
  - Form loading skeleton/spinner
  - Parent picker loading/search results

## Layout
- Top: Page header (title) + breadcrumbs/back link; right side actions (Save/Create, Cancel)
- Main: Single-column form sections stacked: Identity (Code/Name) → Address → Timezone → Parent → Buffers
- Right/top of main (or inline near header): Status badge + Activate/Deactivate actions (detail/edit)
- Bottom: Read-only metadata panel (Location ID, Created At, Updated At); error details area with correlation/request ID when present

## Interaction Flow
1. Create Location (success; status defaults to ACTIVE)
   1. User navigates to “Create Location”.
   2. User enters Code (unique), Name, selects a valid IANA Timezone, optionally fills Address/Parent/Buffers.
   3. User clicks Create.
   4. UI calls create location endpoint; request must not include a status field.
   5. On success, show success confirmation and navigate to Location Detail for the new location (status displayed as ACTIVE).
   6. Log at INFO: route entered (create), create submitted (locationId returned).

2. Update Location fields (success; code immutable)
   1. User navigates to Location Detail, then chooses Edit (or lands on Edit screen).
   2. UI loads location via get location; populate form fields.
   3. Code is displayed read-only (immutable); other fields editable (Name, Address if supported, Timezone, Parent, Buffers).
   4. User edits Name and Timezone (and/or other editable fields) and clicks Save.
   5. UI calls update location endpoint including version if provided by backend (optimistic locking).
   6. On success, navigate back to Location Detail (or update in place) showing updated values; show success confirmation.
   7. Log at INFO: route entered (detail/edit), update submitted (locationId).

3. Status change (explicit actions; editable even when INACTIVE for non-identity fields)
   1. From Detail (or Edit), user clicks Deactivate (ACTIVE → INACTIVE) or Activate (INACTIVE → ACTIVE).
   2. UI calls update location (or status change via update) to apply status transition.
   3. On success, update status badge and show success confirmation.
   4. Ensure when status is INACTIVE, user can still edit non-identity fields (Name/Address/Timezone/Parent/Buffers); Code remains read-only.
   5. Log at INFO: status transition submitted (locationId, from/to).

4. Create Location (duplicate code conflict)
   1. User attempts to create a Location with Code = “MAIN-WH” that already exists.
   2. Backend returns conflict response.
   3. UI shows field-level error on Code indicating it already exists; keep user inputs intact; no navigation occurs.
   4. If a correlation/request ID header is present, display it in the error details UI (copyable) and include it in client logs.

5. Error handling (general)
   1. For any failed list/get/create/update/status request, show an error banner with actionable message.
   2. If correlation/request ID is returned, surface it in the UI and logs.
   3. Avoid logging PII; log only route and submission events with locationId and status transitions.

## Notes
- Service interactions follow Moqui screen actions calling REST endpoints: list locations (for list and parent picker), get location, create location, update location (including status changes).
- Field rules:
  - Code: required on create; immutable thereafter; read-only on edit.
  - Name: required; editable.
  - Timezone: required; editable; must be valid IANA TZ.
  - Address: editable only if supported by the contract; otherwise display read-only if present.
  - Status: server-managed default ACTIVE on create; do not send status on create; editable only via explicit Activate/Deactivate actions.
  - Parent Location + buffer minutes: optional; editable.
  - createdAt/updatedAt/locationId: read-only display on detail.
  - version: hidden read-only; include in update payload if provided for optimistic locking.
- Traceability/logging (INFO, non-PII): route entered (list/detail/create/edit), create submitted (locationId returned), update submitted (locationId), status transition submitted (locationId, from/to).
- Risk/requirements completeness: treat address field rendering as contract-driven; implement defensively (graceful handling if address is string vs object).
