# [FRONTEND] [STORY] Estimate: Add Parts to Estimate
## Purpose
Enable a Service Advisor to add parts line items to an estimate while it is in Draft status, using catalog search and backend-authoritative pricing/totals. Provide per-line editing for quantity and (capability-gated) unit price overrides, including required override reason codes when policy demands. Ensure all create/update actions include the required source contract header and that UI reflects backend-calculated totals and validation errors.

## Components
- Estimate header (estimate ID, status, optional customer name, last updated)
- Totals summary (parts subtotal, tax, total)
- Parts line items table/list
  - Columns: Part/Description, Part #/SKU, Qty, Unit Price, Line Total, Tax (optional), Actions
  - Row state indicators (catalog vs non-catalog, taxable flag if shown)
- Primary action button: “Add Part” (enabled/disabled based on estimate status)
- “Add Part” modal/drawer
  - Search input (part number/SKU/description)
  - Search results list (name/description, SKU/part number)
  - Selected part preview
  - Quantity input (>0)
  - Confirm/Add button, Cancel button
- Line item edit controls
  - Inline quantity edit (or row edit modal)
  - Price override input (capability-gated)
  - Override reason code dropdown (shown/required when policy indicates)
  - Save/Cancel controls
- Inline validation and error messaging (field-level + row-level)
- Permission/policy messaging banner or inline helper text for price override restriction
- Loading states (searching, saving line item, refreshing totals) and empty state for no parts

## Layout
- Top: Estimate header (left: ID/status; right: optional updated timestamp)
- Main: Totals summary strip above Parts section
- Main: Parts section header (left: “Parts”; right: “Add Part” button)
- Main: Parts line items table beneath; footer row or side panel shows refreshed totals
- Overlay: “Add Part” modal/drawer centered/right with search → results → quantity → confirm

## Interaction Flow
1. View estimate and parts list
   1. User opens an estimate detail view; UI displays estimate status and current totals from backend.
   2. UI renders parts line items table (or empty state) and shows “Add Part” action.
   3. If estimate status is not Draft, disable “Add Part” and disable per-line edit actions; show helper text indicating parts cannot be modified in current status.

2. Add a catalog part to a Draft estimate (primary flow)
   1. User clicks “Add Part” (Draft only).
   2. Modal opens with search input focused.
   3. User searches by part number/SKU or description; UI calls product/catalog search service and shows results.
   4. User selects a catalog part; UI shows selected part details (description + part number/SKU).
   5. User enters quantity (must be > 0).
   6. User confirms “Add”; UI sends create estimate-item request including required source contract header.
   7. On success, modal closes; new line item appears in table with backend-returned unit price and computed line total.
   8. UI refreshes and displays backend-calculated estimate totals (parts subtotal, tax, total).

3. Edit quantity on an existing part line item (Draft only)
   1. User selects “Edit Qty” (or edits inline) for a line item.
   2. User updates quantity (>0) and saves.
   3. UI sends update request with required source contract header.
   4. On success, row updates using backend response and totals refresh to backend-calculated values.

4. Override unit price (capability-gated) with required reason (Scenario 6)
   1. User opens price override control for a line item (Draft only).
   2. If price override capability is not provided/false, disable override UI and show: “You do not have permission to override prices.”
   3. If override is allowed, user enters override unit price.
   4. If override reason codes are provided and policy requires a reason, user must select a reason code before saving.
   5. User saves; UI sends override/update request with required source contract header.
   6. On success, row reflects overridden unit price from backend response and totals refresh to backend-calculated values.

5. Key validation and error handling (edge cases)
   1. If quantity is missing/≤0, block save and show field error.
   2. If non-catalog add/edit is supported in UI: block save when description is missing (“Description is required.”) or unit price is missing (“Unit price is required.”).
   3. If override reason is required but not selected, block save and show: “A reason code is required for all price overrides.”
   4. If backend returns validation/auth errors, display message at row/modal level and keep user inputs for correction.

## Notes
- “Add Part” and all edit actions are only enabled when estimate status is Draft; other statuses are read-only.
- Backend is authoritative for unit price, line totals, tax fields (if provided), and estimate totals; UI should not compute totals beyond display formatting.
- All create/update/override requests must include the required source contract header.
- Price override UI must be capability/permission gated; when disallowed, show the exact message: “You do not have permission to override prices.”
- If policy requires override reason codes, enforce selection before save; show the exact error: “A reason code is required for all price overrides.”
- Optional tax display fields (tax code/amounts) should render only if present in backend response.
- Consider audit metadata display (created/updated stamps) as optional; do not block core flows if absent.
- Ensure loading states prevent double-submit (Add/Save) and that totals refresh after each successful line item mutation.
