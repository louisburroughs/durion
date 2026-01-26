# [FRONTEND] [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)
## Purpose
Enable Product Administrators to view, create, and delete “Vehicle Applicability Hint” records on a product, where each hint is composed of multiple fitment tags (e.g., make/model/year range). Provide basic client-side validation (no blanks, no duplicate tag rows within a hint) and display backend validation errors in a field-mapped, actionable way. Surface a read-only audit view so users can confirm create/update/delete activity and understand what changed.

## Components
- Product Detail Page header (product name/ID; existing page context)
- Section: “Vehicle Applicability Hints”
- Hint list (cards/rows) showing tags per hint
- Button: “Add Hint”
- Hint editor (inline panel or modal)
  - Tag rows repeater (FitmentTag list)
    - Field: Tag Type (dropdown/select)
    - Field: Tag Value (text input)
    - Button: Remove tag row
  - Button: Add tag row
  - Button: Save
  - Button: Cancel
- Delete action per hint
  - Button: Delete
  - Confirmation modal/dialog (Confirm / Cancel)
- Inline validation messaging per tag row/field
- Global form error banner (for non-field-mapped errors)
- Section/Tab: “Audit” (read-only viewer)
  - Audit event list/table (event type, timestamp, actor, change summary)
  - Optional: expand/collapse details per event (before/after or delta)

## Layout
- Top: Product header + existing product navigation
- Main content:
  - Vehicle Applicability Hints section
    - Left/top: “Add Hint” button
    - Below: list of existing hints (each with tags + actions)
    - Inline editor appears above list or as modal when adding/editing
  - Audit view (below hints or as a tab within the product page)
- Hint list item layout: [Tags display] [Delete action] (and optional Edit if later enabled)

## Interaction Flow
1. View existing hints
   1. User opens an existing Product Detail page.
   2. Page loads “Vehicle Applicability Hints” section.
   3. System displays a list of existing hints for the product.
   4. Each hint renders its tags as type + value pairs (read-only display).

2. Create a new hint (basic tags)
   1. User clicks “Add Hint”.
   2. System opens the hint editor with at least one empty tag row.
   3. User adds tag rows and selects/enters values (e.g., MAKE=Subaru, MODEL=Outback, YEAR_RANGE=2020-2023).
   4. On Save, UI validates:
      1. No blank tag type/key.
      2. No blank tag value.
      3. No duplicate tag rows within the hint (same type + value); duplicates are flagged and must be resolved before submit.
   5. If validation passes, system calls backend create service (input includes productId and tags).
   6. On success, editor closes and the product detail page refreshes/reloads the hints list to include the new hint.
   7. Audit view shows a new “created” event for the hint (if audit data is available).

3. Error handling on create (400/422)
   1. If backend returns 400/422 validation errors, system displays actionable messages.
   2. When possible, errors map to specific tag row index + field (e.g., Tag #2 Type, Tag #3 Value).
   3. Unmapped errors appear in a global error banner within the editor.

4. Delete an existing hint
   1. User clicks “Delete” on a hint.
   2. System shows confirmation dialog (Confirm / Cancel).
   3. On Confirm, system calls backend delete service for the selected hint.
   4. On success, hint is removed from the list.
   5. Audit view shows a new “deleted” event (if audit data is available).

## Notes
- Client-side constraints (UI-only; backend remains authoritative):
  - Prevent submit with any blank tag type or blank tag value.
  - Prevent submit with duplicate tag rows within a single hint (same type + value).
- Error messaging expectations:
  - For 400/422, show actionable messages and map to fields when possible, including tag row index + field.
- Entities (frontend-facing; contract/ownership clarification needed):
  - VehicleApplicabilityHint: id (read-only), productId (read-only in edit), tags[] (editable), optional read-only metadata (timestamps/actor) if provided.
  - FitmentTag: type (required), value (required).
  - AuditEvent: event type (created/updated/deleted), timestamp, actor display name (if available), context (productId, hintId), change summary (before/after or delta if provided).
- Audit view is read-only; show event type, timestamp, actor, and change summary when available.
- Scope note: story text emphasizes create/view/delete; “edit/remove/search” is mentioned broadly, but only create + delete scenarios are specified. Treat edit/search as out of scope unless backend/UI contracts confirm.
