# [FRONTEND] [STORY] Allocations: Reallocate Reserved Stock When Schedule Changes
## Purpose
Provide an Inventory Availability query UI that lets users view backend-authoritative on-hand, allocated, and ATP quantities by SKU and location, optionally narrowed to a storage location. Support deep-linkable searches via canonical URL query parameters so users can share and revisit the same results without client-side computation. Ensure the UI validates required inputs and only auto-runs when deep-link params are complete and valid.

## Components
- Page header/title: “Inventory Availability”
- Availability search form
  - SKU input text field (trimmed; required, non-empty)
  - Location picker (LocationRef; required)
  - Storage Location picker (StorageLocation; optional; filtered by selected Location; disabled until Location selected)
- Action buttons
  - Search (enabled only when required fields valid)
  - Copy link (copies current URL with canonical query params)
  - Clear (resets form and clears results)
- Validation hints / inline error messages for required fields and invalid deep-link params
- Results section
  - Results summary (e.g., “Showing availability for SKU X at Location Y [and Storage Z]”)
  - Availability results table/list rendering backend-returned fields:
    - Required: sku, locationId, storageLocationId (nullable), onHandQty, allocatedQty, atpQty
    - Optional (render only if present): asOfTimestamp, uom, notes/description fields (as provided)
- Loading indicator (during search)
- Empty state (no results yet / no matches)
- Error state (API/proxy failure)

## Layout
- Top: Page header/title
- Main: Search form (SKU input + Location picker + Storage Location picker) with actions aligned to the right/bottom
- Below form: Results area (status line + table/list)
- [Header]
  - [Form: SKU | Location | Storage Location] [Search] [Copy link] [Clear]
  - [Results: summary + table/list / empty / error]

## Interaction Flow
1. Manual availability search
   1. User lands on the page (logged in; authorized for Inventory Availability).
   2. User selects a Location via the Location picker (required).
   3. User enters a non-empty SKU (trim on blur/submit).
   4. (Optional) User selects a Storage Location; picker is enabled only after Location is selected and is filtered by that Location.
   5. Search button becomes enabled only when SKU and Location are valid.
   6. User clicks Search; UI calls the availability endpoint via the Moqui proxy with sku, locationId, and optional storageLocationId.
   7. UI shows loading state, then renders onHandQty, allocatedQty, and atpQty exactly as returned (no client-side computation), along with required identifiers and any optional fields if present.

2. Deep-link auto-run on page load
   1. Page reads canonical query params (sku, locationId, optional storageLocationId).
   2. If required params are present and valid, the form is prefilled and the search auto-runs once per load.
   3. If required params are missing/invalid, do not auto-run; show the form with validation hints indicating what’s needed.

3. Copy link
   1. After user has a current valid form state (and/or results), user clicks Copy link.
   2. UI copies the current URL containing canonical query params (sku, locationId, optional storageLocationId).

4. Clear (DECISION-INVENTORY-001)
   1. User clicks Clear.
   2. UI resets SKU input and pickers, disables Storage Location picker, clears validation messages, and clears any displayed results.

5. Edge cases
   1. User changes Location after selecting a Storage Location: Storage Location selection is cleared and picker re-filters to the new Location.
   2. API/proxy error: show error state in results area; keep form values so user can retry.
   3. Empty response/no matches: show empty state message in results area (no client computation or inferred values).

## Notes
- Backend is authoritative: render on-hand, allocated, and ATP values exactly as returned; do not compute missing optional fields.
- Required pickers/fields: SKU (trimmed, non-empty) and LocationRef are required; StorageLocation is optional and must be filtered by selected Location.
- Deep-linking: use canonical query params (sku, locationId, optional storageLocationId); auto-run only when required params are valid; otherwise show validation hints and wait for user action.
- Clear behavior must reset form and clear results (per DECISION-INVENTORY-001).
- Ensure “Search” is disabled until required fields are valid; Storage Location picker disabled until Location selected.
- Risk: requirements may be incomplete; keep UI modular to accommodate additional response fields or future filters without breaking deep-linking.
