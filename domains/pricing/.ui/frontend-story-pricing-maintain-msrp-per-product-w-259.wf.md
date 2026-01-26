# [FRONTEND] [STORY] Pricing: Maintain MSRP per Product with Effective Dates

## Purpose
Provide Moqui UI screens to maintain effective-dated MSRP records per product, including search/select product context, list timeline, and create/edit/view detail modes. Ensure consistent navigation and error/confirmation patterns while surfacing server-side validation and authorization outcomes. Prevent invalid pricing intervals and overlapping effective ranges, and support audit/history visibility for admin changes.

## Components
- Page header: “Pricing → MSRP”
- Product picker component (Inventory-owned search/lookup)
- Selected product context summary (product name/ID, optional key attributes)
- MSRP list grid (effective-dated timeline)
  - Columns: amount, currency, effectiveStartAt, effectiveEndAt, lastUpdatedAt, lastUpdatedBy (if available), status/flags (e.g., “Historical”)
  - Row actions: View, Edit (permission-gated)
- Primary actions: Create MSRP (permission-gated), Clear/Change Product
- MSRP detail panel/page (mode-based: View / Create / Edit)
  - Read-only field: msrpId (required for view/edit)
  - Field: productId (picker on create; read-only on edit/view)
  - Money input: msrp.amount (decimal-string), msrp.currencyUomId
  - Date-time pickers: effectiveStartAt (required), effectiveEndAt (optional)
  - Read-only metadata: createdAt/createdBy, lastUpdatedAt/lastUpdatedBy (if provided)
- Audit/history panel (standard pricing admin audit contract)
- Validation/error presentation
  - Inline field errors + top-of-form error summary
  - Conflict banner for overlapping effective ranges
  - Forbidden/permission banner
  - Not found banner (product/MSRP)
- Confirmation patterns
  - Save success toast/banner
  - Unsaved changes confirmation on navigation away
- Empty states
  - No product selected instruction
  - No MSRPs for product + CTA to create (permission-gated)

## Layout
- Top: Breadcrumb + page title; right-aligned “Create MSRP” (enabled only with manage permission and product selected)
- Main: [Product Picker + Selected Product Summary] above [MSRP List Grid]
- Detail: opens as dedicated detail page or right-side drawer/panel from list (Moqui convention), with Audit panel below form
- Empty states shown in main list area when applicable

## Interaction Flow
1. User navigates to Pricing → MSRP.
2. If no product selected, show instruction: “Select a product to view MSRPs.” Product picker is the primary control.
3. User searches/selects a product via Inventory lookup.
4. System loads MSRP records for the selected product and displays them in an effective-dated list/timeline.
5. If product has no MSRPs, show empty state and “Create MSRP” CTA only if user has manage permission.
6. User clicks “Create MSRP”:
   1. Open Create mode with productId prefilled (editable only on create).
   2. User enters msrp.amount (positive decimal-string), selects currencyUomId, sets effectiveStartAt, optionally effectiveEndAt.
   3. Client validates required fields and date/time logic (effectiveEndAt, if provided, must be strictly after effectiveStartAt).
   4. On Save, server responses are surfaced:
      - 201/updated: return to list and highlight new record.
      - 400 validation errors: show inline + summary (money shape, required fields, date logic).
      - 404 not found: product existence failure shown as banner; keep form state.
      - 409 conflict: overlapping effective ranges shown as conflict banner and field-level cues.
      - 403 forbidden: show permission banner; disable save.
7. User clicks “View” on a list row:
   1. Open View mode with all fields read-only.
   2. Show Audit/history panel read-only.
8. User clicks “Edit” on a list row (permission-gated):
   1. Open Edit mode; productId is read-only.
   2. Historical immutability handling:
      - If record is historical and user lacks override permission: all fields read-only; Save disabled; show banner explaining restriction.
      - If historical but user has override permission: fields editable; show warning banner.
   3. On Save, handle server outcomes (validation, forbidden, not found, conflict/overlap, optimistic concurrency if applicable).
9. Navigation/unsaved changes:
   1. If user attempts to leave create/edit with unsaved changes, show confirmation dialog (discard vs stay).
10. Error recovery:
   1. If list load fails (403/404), show banner and keep product context visible; allow changing product.

## Notes
- Effective-dated semantics are store-local; enforce “effectiveEndAt strictly after effectiveStartAt” to avoid zero-length intervals.
- Required fields on create: productId, msrp.amount (decimal-string, positive), msrp.currencyUomId, effectiveStartAt; effectiveEndAt optional (null = indefinite).
- Edit/view require msrpId; display as read-only identifier.
- Permission gating:
  - Manage permission required for Create and Edit actions; View may be allowed broadly (implementation-dependent).
  - Editing historical records is restricted unless explicit permission is present; UI must reflect disabled state and show rationale.
- Overlap prevention: UI should surface 409 conflict clearly and guide user to adjust effective ranges; consider highlighting conflicting existing rows.
- Audit/history panel must use the standard pricing admin audit contract; read-only and visible in view/edit (and optionally after create success).
- Maintain Moqui-consistent patterns for banners/toasts, form validation, and transitions between list and detail.
- Risk: incomplete requirements—confirm with backend contract for optimistic concurrency/versioning and exact audit fields returned; ensure UI can display conflict vs validation distinctly.
