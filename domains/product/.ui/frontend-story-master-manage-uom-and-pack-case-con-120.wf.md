# [FRONTEND] [STORY] Master: Manage UOM and Pack/Case Conversions
## Purpose
Provide a Master Data UI for Product Administrators to list, create, update, and deactivate Unit of Measure (UOM) conversions (e.g., Case → Each) with a conversion factor. The UI must enforce key validation rules (positive factor, unique From/To pair, self-conversion constraints) and avoid hard deletes by using deactivate. Users also need visibility into audit metadata (created/updated by/at) and, if available, access to detailed audit entries.

## Components
- Page header: “UOM Conversions” + brief helper text (includes “Reverse conversion is computed implicitly.”)
- Filter bar
  - From UOM select (optional)
  - To UOM select (optional)
  - Active status select: Active / Inactive / All
  - Optional text search (UOM code/name)
  - Buttons: Apply/Refresh, Clear
- Primary CTA button: “Create Conversion”
- Results table (paged)
  - Columns: From UOM, To UOM, Factor, Status (Active/Inactive), Updated At (optional), Updated By (optional)
  - Row actions: View/Edit, Deactivate (only when Active)
- Create/Edit conversion form (screen or modal)
  - From UOM (required select)
  - To UOM (required select)
  - Conversion factor (required decimal input)
  - Active status display (read-only on create; edit via Deactivate/Activate only if supported)
  - Audit panel: Created At/By, Updated At/By; link/button “View Audit Entries” (if available)
  - Buttons: Save, Cancel
- Confirmation modal: Deactivate conversion (confirm/cancel)
- Inline validation messages + top-level error banner for non-field errors
- Empty states
  - “No conversions found” + CTA “Create Conversion”
  - “No Units of Measure available” (blocks create)

## Layout
- Top: Page header (title + helper text) and primary CTA “Create Conversion” aligned right
- Below header: Filter bar (single row; wraps on smaller screens)
- Main: Results table with pagination footer
- Secondary panel (on View/Edit): right-side details/audit panel or below form (Created/Updated metadata + audit link)
- Inline ASCII hint: Header/CTA → Filters → Table (rows + actions) → Pagination; Create/Edit opens Form + Audit panel

## Interaction Flow
1. Open list screen
   1. System loads conversions list (paged) and UOM options for filters.
   2. If no conversions match: show “No conversions found” and “Create Conversion”.
2. Filter and browse conversions
   1. User selects From UOM / To UOM / Active status (and optional text filter).
   2. User clicks Apply/Refresh; table updates.
   3. Row actions show: View/Edit always; Deactivate only if conversion is Active.
3. Create conversion
   1. User clicks “Create Conversion”.
   2. If no UOMs available: block with message “No Units of Measure available.”
   3. User selects From UOM and To UOM; enters Factor (> 0).
   4. Client-side validation:
      1. If Factor ≤ 0: block submit; show “Conversion factor must be a positive number.”
      2. If From == To and Factor != 1: block submit with inline error on Factor.
   5. User clicks Save; UI calls backend create service.
   6. If duplicate (fromUomId,toUomId) violation returned: show “A conversion for this From/To pair already exists.”
   7. On success: return to list (or show detail) and display new conversion as Active; show createdBy/createdAt if provided.
4. View/Edit conversion (update factor)
   1. User clicks View/Edit on a row.
   2. Form loads conversion details + audit metadata.
   3. If backend disallows changing From/To: render From/To as read-only/disabled in edit mode; allow Factor edit only.
   4. User updates Factor; same validation rules apply (positive; self-conversion requires 1).
   5. Save triggers backend update; handle 403 (forbidden) by disabling save and showing forbidden message.
5. Deactivate conversion (no hard delete)
   1. User clicks Deactivate on an Active conversion.
   2. Confirmation modal appears; user confirms.
   3. UI calls backend deactivate/update status service; on success, row status becomes Inactive and Deactivate action is removed/disabled.
6. Error handling (general)
   1. Map backend 400 validation errors to field-level messages when keys match.
   2. Network/server errors show banner: “Unable to save conversion.”
   3. Unauthorized/forbidden: show forbidden state; disable create/edit/deactivate actions as applicable.

## Notes
- Constraints/Rules
  - R-UOM-01: Factor must be positive, non-zero decimal; show exact message: “Conversion factor must be a positive number.”
  - R-UOM-02: Unique (fromUomId, toUomId); handle backend duplicate response with: “A conversion for this From/To pair already exists.” (avoid optional pre-check to reduce race conditions).
  - R-UOM-03: No reverse record required; show informational hint: “Reverse conversion is computed implicitly.”
  - R-UOM-04: No hard delete; do not present any Delete action.
  - Self-conversion: if From == To then Factor must equal 1; otherwise block submit.
- Role/Access
  - Product Administrator: create, update factor (Active; Inactive update TBD), deactivate Active conversions.
  - Non-authorized users: read-only or no access (TBD); ensure UI handles 403 by disabling actions and showing forbidden messaging.
- Audit
  - Display createdBy/createdAt and updatedBy/updatedAt when available.
  - Provide link/panel for audit entries if backend supports it; otherwise omit link gracefully.
- Moqui wiring
  - Implement Moqui screen(s), transitions, and service calls for list/query, create, update factor, and deactivate.
- Out of scope reminders
  - No creation/edit of UnitOfMeasure master records unless backend explicitly supports it.
  - No bulk import/export; no downstream application of conversions in pricing/tax/inventory logic.
- Open/TBD items (risk: incomplete requirements)
  - Whether Active can be re-activated (activate action) and whether Inactive conversions can be edited.
  - Exact backend entities/services and duplicate error shape (e.g., error code/key) for mapping.
