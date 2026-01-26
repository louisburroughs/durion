# [FRONTEND] [STORY] Rules: Maintain Substitute Relationships and Equivalency Types
## Purpose
Provide Moqui-based Admin UI screens for Product Admins to manage substitute relationships for parts. Users must be able to select a base part, view its substitutes, create new substitute links, and view/update/deactivate existing links. The UI must enforce domain-aligned validation, handle duplicates and optimistic locking conflicts, and display audit metadata and correlation IDs from backend responses.

## Components
- Global page header: “Rules” section + screen title
- Navigation/breadcrumbs: Rules → Substitutes (and back links)
- Base Part search/picker (typeahead/search modal)
- Base Part context display (selected part summary)
- Substitutes list/table for selected base part
  - Columns: Substitute Part, Equivalency Type, Active/Inactive badge, Priority, Effective From/Thru, Updated At/By
  - Row click to open details
- List controls
  - Toggle: Show inactive (include inactive links)
  - Optional toggle/badge: Effective now (client-side indicator)
  - Button: Add Substitute
  - Refresh/reload action (implicit on selection/toggle)
- Create Substitute screen form (base part prefilled/locked)
  - Fields: Base Part (read-only), Substitute Part picker (required), Equivalency Type enum (required), Preferred checkbox (default false), Priority integer (default 0), Effective From datetime, Effective Thru datetime, Active boolean (default true; may be hidden on create)
  - Actions: Create, Cancel/Back
- Substitute Link detail screen
  - Read-only key fields: Link ID, Base Part, Substitute Part
  - Status: Active/Inactive badge
  - Audit/metadata panel: Created At/By, Updated At/By, Version (if present)
  - Actions: Edit (toggle), Deactivate, Back to base part substitutes
- Edit mode (detail screen)
  - Editable fields only: Equivalency Type, Preferred, Priority, Effective From/Thru (and Active only via deactivate)
  - Save, Cancel
- Deactivate confirmation modal (soft-delete)
- Inline field validation messages + form-level error banner
- Error handling UI
  - Duplicate constraint (409) message on create/update
  - Version conflict (409) message with guidance to reload
  - Standard errors (400/403/404) banner
  - CorrelationId display on error banner (when provided)

## Layout
- Top: Breadcrumbs + page title
- Main (List screen): [Base Part Picker + Selected Part Summary] above [Substitutes Table + toggles + “Add Substitute”]
- Main (Detail/Create): Left/Top summary (Base/Substitute/Status) + Main form fields + Right/Bottom audit metadata + Footer actions (Back/Save/Create/Deactivate)

## Interaction Flow
1. Open Rules → Substitutes screen.
2. Select a base part via search/picker.
3. UI loads substitutes for the selected base part (default active-only) and renders rows in server-provided order.
4. Toggle “Show inactive” to include inactive links; list reloads or refilters per endpoint capability.
5. Click “Add Substitute” to navigate to Create screen with base part prefilled and not editable.
6. On Create:
   1. Choose Substitute Part (required) and Equivalency Type (required); optionally set Preferred, Priority, Effective From/Thru.
   2. Submit Create; on 201, navigate to Detail screen (or back to list) showing the created link (including id and version if supported).
   3. If 409 duplicate, show duplicate-specific error and keep user on form with inputs preserved.
   4. If 400 validation, highlight fields and show form banner; if 403, show access error.
7. From list, click a substitute row to open Detail screen.
8. On Detail:
   1. Display key fields read-only and show Active/Inactive badge.
   2. Display audit metadata (created/updated fields; version if present).
9. Edit flow:
   1. Click “Edit” to toggle edit mode (or navigate to edit state).
   2. Modify non-key fields only; submit Update including version if present.
   3. On 200, return to read-only detail with updated metadata.
   4. On 409 version conflict, show conflict message and prompt to reload; include correlationId if provided.
   5. On 404, show not found and offer back navigation.
10. Deactivate flow:
   1. If link is active, click “Deactivate” to open confirmation modal.
   2. Confirm triggers DELETE (soft-delete sets active=false).
   3. On success, show inactive badge, disable Deactivate, and return to list or remain on detail per UX choice.
11. Back navigation:
   1. “Back to base part substitutes” returns to list with the previously selected base part preserved and list state (e.g., show inactive) retained.

## Notes
- Base part is fixed from context on Create; basePartId and substitutePartId are required and read-only after create.
- Enforce required fields and enum validation for equivalency type; validate priority as integer with default 0; preferred default false.
- Unique constraint handling: duplicate substitute link must surface as a clear, non-generic 409 message (do not silently fail).
- Optimistic locking: include version in update payload when provided by backend; handle 409 conflicts distinctly from duplicates.
- Inactive behavior: show inactive badge; disable Deactivate; editing should be disabled by default when inactive unless backend explicitly permits—if allowed, keep inactive status prominent.
- Display correlationId from backend error envelopes in the error banner for support/debugging.
- Effective now: client-side badge/indicator based on effectiveFrom/effectiveThru; server-side filtering only if endpoint supports it.
- Ensure list ordering matches server response order; do not resort client-side unless explicitly required.
