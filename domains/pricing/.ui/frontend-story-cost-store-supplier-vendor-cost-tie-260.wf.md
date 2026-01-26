# [FRONTEND] [STORY] Cost: Store Supplier/Vendor Cost Tiers (Optional)
## Purpose
Enable users to view and manage supplier/vendor cost configurations for a specific inventory item, including optional tiered pricing. Provide a list view for all supplier-item cost records on an item and a detail view to load a single record with its tiers. Support create/update/delete of supplier-item cost records and tier rows while enforcing tier structure rules and uniqueness per supplier+item.

## Components
- Page header: “Supplier Costs” + item context (item name/identifier)
- Supplier cost list table
  - Columns: Supplier (name + id), Currency, Tier Count, Updated At, Actions
  - Pagination controls (cursor-based if provided; no total count required)
- Empty state panel (read-only vs authorized)
- Primary action button: “Add Supplier Cost” (permission-gated)
- Detail drawer or separate detail page (Supplier Cost Detail)
  - Read-only header fields: Supplier, Item, Currency Code, Audit fields (createdAt/updatedAt, createdBy/updatedBy if available)
  - Optional field: Base Cost (editable only if supported)
  - Tier editor table (editable)
    - Columns: From Qty, To Qty, Unit Cost, Row actions (Delete)
    - Add tier row button
- Save/Cancel controls for create/edit
- Delete supplier cost record action (with confirmation modal)
- Inline validation messages and form-level error banner
- Loading states (skeleton/spinner) and API error state banner/toast

## Layout
- Top: Breadcrumbs (Inventory > Item > Supplier Costs) + Page title + “Add Supplier Cost” button (right)
- Main: Supplier Costs List (table) or Empty State
- Detail (on row click or “View/Edit”): right-side drawer or navigated detail view with header summary + tier table + Save/Cancel at bottom

## Interaction Flow
1. Navigate to an item’s “Supplier Costs” section.
2. System calls list API with itemId (or item identifier) and renders:
   1) Table of supplier-item cost summaries (supplier name/id, currency, tier count, updatedAt), or
   2) Empty state: “No supplier costs configured for this item.”
3. If user is authorized, show “Add Supplier Cost” in empty and non-empty states; otherwise show read-only empty state and hide create/edit actions.
4. User selects a supplier cost row (or clicks “View/Edit”):
   1) System loads detail by supplierItemCostId OR by composite key (supplier + item).
   2) Render read-only header fields (supplier, item, currency, audit) and editable tier table (and baseCost if supported).
5. User adds/edits tiers:
   1) Add tier row, enter From Qty (>=1), optional To Qty (>= From Qty or null), Unit Cost (>0).
   2) UI validates structural rules: first tier starts at 1; tiers contiguous; no overlaps; at most one open-ended tier and it must be last.
   3) On validation failure, block save and show inline errors on offending rows plus a summary message.
6. User saves changes:
   1) System calls create/update API for supplier-item cost record and tiers (support atomic tier replacement if applicable).
   2) On success, return to list (or close drawer) and refresh row summary (tier count, updatedAt).
   3) On API error, show error banner and keep user inputs intact.
7. User deletes a supplier-item cost record:
   1) Click “Delete” action, confirm in modal.
   2) System calls delete API; on success, remove from list and show empty state if none remain.
8. Pagination behavior:
   1) User navigates pages using next/previous (or “Load more”) based on cursor tokens if provided.
   2) UI does not require or display total count.

## Notes
- Uniqueness constraint: only one supplier-item cost record per supplier+item pair; once created, supplier and item identifiers become read-only to preserve uniqueness.
- Read-only fields: supplier identifiers, item identifiers, currencyCode, audit fields; editable fields (with manage permission): tiers and baseCost (only if supported).
- Currency handling is TBD: story assumes supplier currency determines display currency, but source-of-truth must be confirmed; UI should display currencyCode from API and avoid allowing edits.
- Tier row field rules:
  - From Qty: integer, >= 1
  - To Qty: integer, >= From Qty, or null for open-ended final tier
  - Unit Cost: decimal > 0
- Tier set structural rules:
  - First tier must start at 1
  - Tiers must be contiguous (no gaps)
  - Tiers must not overlap
  - At most one open-ended tier, and it must be the last tier
- List API response should include supplier name/id, currency, tier count, updatedAt; detail response includes header + tiers.
- Empty state copy required: “No supplier costs configured for this item.”
- Risk/requirements incomplete: identifiers for supplier/item (id vs externalId/canonical identifier) and currency source-of-truth are TBD; design should accommodate either id type and treat them as references.
