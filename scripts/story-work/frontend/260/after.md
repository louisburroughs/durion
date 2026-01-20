STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:inventory
- status:draft

### Recommended
- agent:inventory-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** inventory-flexible

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Cost: Store Supplier/Vendor Cost Tiers (Optional)

### Primary Persona
Inventory Manager (or Purchasing Admin responsible for supplier purchasing costs)

### Business Value
Enables the organization to store supplier-specific volume price breaks for inventory items so purchasing and downstream cost calculations can select an accurate unit cost based on ordered quantity, improving procurement decisions and cost accuracy.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Inventory Manager  
- **I want to** create, view, update, and delete optional supplier/vendor cost tiers for a specific inventory item  
- **So that** purchasing workflows can use quantity-based unit costs (price breaks) when building purchase orders and reporting.

### In-scope
- Frontend screens and forms to manage a supplier-item cost record and its tier rows (min/max quantity + unit cost).
- Client-side validation that matches backend rules where determinable (contiguous non-overlapping tiers, min qty rules, positive cost).
- Integration wiring to backend endpoints (GET/POST/PUT/DELETE or Moqui service calls) and error mapping.
- Audit visibility: show basic “who/when” metadata if available from API.

### Out-of-scope
- Purchase Order creation and selecting tier cost during PO line pricing (consumer behavior).
- Defining suppliers/vendors or inventory items (assumed existing).
- Cost valuation methods (WAC, etc.) beyond storing tiered purchase costs.
- Import/bulk upload of tiers.

---

## 3. Actors & Stakeholders
- **Inventory Manager (Primary)**: maintains supplier/item purchasing costs and tiers.
- **Purchasing Users/System (Consumer)**: uses the stored tier data when creating POs (future/other story).
- **Accounting/Reporting (Downstream)**: benefits from more accurate purchase cost inputs.
- **System Admin/Security**: ensures only authorized users can edit purchasing cost data.

---

## 4. Preconditions & Dependencies
- User is authenticated.
- User has permission to manage supplier-item costs (permission name **TBD**; backend story mentions “permissions to manage supplier and inventory cost data” but does not specify exact permission).
- Supplier/Vendor exists.
- Inventory Item (SKU) exists.
- Backend provides APIs/services for:
  - retrieving supplier-item cost + tiers
  - creating/updating/deleting supplier-item cost + tiers
- Supplier has an assigned currency (tiers must share currency determined by supplier config).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From an **Inventory Item detail** screen: action “Supplier Costs” (or “Vendor Costs”).
- From a **Supplier detail** screen: action “Item Costs” (optional secondary entry point).

### Screens to create/modify
1. **Modify** (or create) Inventory Item detail screen to include navigation to supplier costs.
2. **New Screen**: `InventoryItemSupplierCostList` (list supplier cost records for an item).
3. **New Screen**: `SupplierItemCostDetail` (view/edit a single supplier-item cost and tier rows).

> Moqui pattern: list screen → detail/edit screen with transitions for create/update/delete.

### Navigation context
- Breadcrumb includes Inventory > Items > {itemId} > Supplier Costs > {supplierId}
- Deep-linkable by IDs: item + supplier.

### User workflows
**Happy path: create tiers**
1. User opens item, navigates to Supplier Costs.
2. User selects supplier (or clicks “Add Supplier Cost” and chooses supplier).
3. User enters optional base cost (if supported) and adds one or more tier rows.
4. User saves; UI shows success and returns to detail view.

**Alternate paths**
- Edit existing tiers: load existing tiers, modify rows, save.
- Delete tier structure: remove all tiers / delete supplier-item cost record.
- Read-only view if user lacks edit permissions.

---

## 6. Functional Behavior

### Triggers
- User opens supplier cost management for an item (load existing supplier-item costs).
- User selects a supplier-item cost to view/edit (load tiers).
- User clicks Save (create/update).
- User clicks Delete (delete supplier-item cost / tier structure).

### UI actions
- Add tier row
- Remove tier row
- Reorder tiers (optional; if allowed, UI should still sort/validate by min qty before save)
- Save
- Cancel/back

### State changes (UI)
- `idle` → `loading` when fetching
- `editing` when user modifies form
- `saving` during submit
- `error` if backend rejects

### Service interactions
- Load supplier-item cost and tiers for item+supplier.
- Persist full tier set atomically (preferred) to avoid partial updates.
- Delete supplier-item cost record (and associated tiers).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, mirrored from backend rules)
- Tier rows must have:
  - `minQuantity` integer, **>= 1**
  - `maxQuantity` integer **>= minQuantity**, or **null** (final/open-ended tier)
  - `unitCost` decimal **> 0**
- Tier set must:
  - Start at `minQuantity = 1`
  - Be **contiguous** (no gaps)
  - Be **non-overlapping**
  - Preferably sorted by `minQuantity` (UI should enforce order or auto-sort before save)
- A supplier-item pair can have **at most one active tier structure**:
  - UI prevents creating a second record for same supplier-item; instead routes to edit existing.

### Enable/disable rules
- Save disabled until:
  - required fields present (supplier, item)
  - tiers validate (or base cost only if tiers are optional—**TBD**)
- If user lacks edit permission:
  - all inputs disabled; hide Save/Delete; show “Read only”.

### Visibility rules
- Display `currencyCode` read-only, derived from supplier configuration (not editable).
- Show base cost field only if backend supports it (exists in backend story data model but not guaranteed exposed via API).

### Error messaging expectations
- Map backend validation error codes to field-level messages when possible:
  - `INVALID_TIER_STRUCTURE` → show summary banner + highlight tier table with specific hints:
    - overlap
    - not contiguous
    - start not at 1
- `403` → “You do not have permission to manage supplier costs.”
- `404` → “Supplier or item not found (may have been removed).”
- `409` (if used) → “This cost tier set was modified by another user; reload to continue.” (status code usage **TBD**)

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- `Supplier` (read-only reference): `supplierId`, `name`, `currencyCode`
- `InventoryItem` (read-only reference): `itemId` (or `inventoryItemId`), `sku`, `description`
- `SupplierItemCost`:
  - `id` (uuid)
  - `supplierId` (uuid, required)
  - `itemId` (uuid, required)
  - `currencyCode` (ISO-4217, required, read-only)
  - `baseCost` (decimal, optional) **TBD if used**
  - `createdAt`, `updatedAt` (timestamps, read-only)
- `CostTier`:
  - `id` (uuid)
  - `supplierItemCostId` (uuid, read-only on UI)
  - `minQuantity` (int, required)
  - `maxQuantity` (int|null, optional)
  - `unitCost` (decimal, required)

### Field types & constraints
- `unitCost`: decimal; must support at least 4 decimal places if backend expects same precision as other costs (**recommended**, but backend story did not explicitly say 4dp here; inventory domain guide says costs min 4dp generally).
- `minQuantity/maxQuantity`: integers.

### Read-only vs editable
- Editable: tier rows, baseCost (if supported).
- Read-only: supplier, item identifiers once record exists; currencyCode; audit timestamps.

### Derived/calculated fields (UI only)
- Display-only “Range” label per tier: e.g., `1–10`, `11–50`, `51+`.
- Validation-derived issues list (overlap/gap).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not explicitly defined in the provided frontend issue; below aligns to backend reference story but requires confirmation.

### Load/view calls
- **GET** supplier-item cost by `supplierId` + `itemId`  
  - Returns: supplierItemCost header + ordered tiers
- **GET** list supplier costs for an item (optional)  
  - Returns: suppliers that have cost configured + summary (baseCost? tier count?)

### Create/update calls
- **POST** create supplier-item cost with tiers (atomic)
- **PUT/PATCH** update supplier-item cost with tiers (atomic replace of tiers recommended)
- Request payload should include:
  - `supplierId`, `itemId`
  - optional `baseCost`
  - `tiers[]`: `{ minQuantity, maxQuantity, unitCost }`

### Delete calls
- **DELETE** supplier-item cost by id (or by supplierId+itemId), removing tiers as well.

### Error handling expectations
- `400` with structured validation errors; must include `code` (e.g., `INVALID_TIER_STRUCTURE`) and message.
- `403` forbidden.
- `404` not found.
- `409` conflict (optional) for optimistic locking if supported (needs confirmation whether `updatedAt`/ETag is used).

---

## 10. State Model & Transitions

### Allowed states
This UI is CRUD-oriented; no explicit lifecycle states provided. Treat record as:
- `NotCreated` (no supplier-item cost exists)
- `Exists` (supplier-item cost exists)
- `Deleted` (after delete)

### Role-based transitions
- Users with manage permission:
  - `NotCreated` → Create
  - `Exists` → Update
  - `Exists` → Delete
- Users without manage permission:
  - View only; no transitions.

### UI behavior per state
- `NotCreated`: show create form (select supplier + add tiers).
- `Exists`: show detail with edit enabled; show Delete.
- `Deleted`: return to list with success toast.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- User enters tier 2 min that causes gap: block save; show “Tier ranges must be contiguous starting at 1.”
- User sets max < min: block save; field error.
- User sets negative/zero unitCost: block save; field error.

### Backend validation failures
- Backend returns `INVALID_TIER_STRUCTURE`: keep user inputs intact; show banner; highlight tier grid.

### Concurrency conflicts
- If backend supports optimistic locking and returns conflict:
  - show modal: “Changes detected. Reload / Cancel.”
  - reload action refetches and discards local edits.

### Unauthorized access
- If load returns 403: show access denied screen state.

### Empty states
- No supplier costs for item: show empty state with “Add Supplier Cost”.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: Create a valid cost tier structure
**Given** I am an authenticated user with permission to manage supplier-item costs  
**And** an Inventory Item and Supplier exist  
**When** I create a supplier-item cost with tiers starting at min quantity 1, contiguous, non-overlapping, and unit costs greater than 0  
**Then** the system saves successfully  
**And** when I reload the detail view I see the same tiers in ascending min quantity order.

### Scenario 2: Prevent overlapping tiers before save
**Given** I am editing supplier-item cost tiers  
**When** I enter tiers that overlap (e.g., 1–10 and 5–15)  
**Then** the Save action is disabled (or save is blocked)  
**And** I see an error explaining that quantity ranges must not overlap.

### Scenario 3: Prevent gaps in tiers before save
**Given** I am editing supplier-item cost tiers  
**When** I enter tiers with a gap (e.g., 1–10 and 12–20)  
**Then** the Save action is disabled (or save is blocked)  
**And** I see an error explaining that tiers must be contiguous.

### Scenario 4: Backend rejects invalid tier structure
**Given** I attempt to save tier data  
**And** the backend responds `400` with error code `INVALID_TIER_STRUCTURE`  
**When** the response is received  
**Then** the UI shows a non-destructive error message  
**And** my entered tiers remain on screen for correction.

### Scenario 5: View existing tiers read-only without permission
**Given** I am authenticated but do not have permission to manage supplier-item costs  
**When** I open an existing supplier-item cost record  
**Then** I can view tiers and currency  
**And** I cannot edit fields or save/delete changes  
**And** if I attempt a save via direct action, I receive a forbidden message.

### Scenario 6: Delete a supplier-item cost tier structure
**Given** a supplier-item cost exists for a supplier and item  
**And** I have permission to manage supplier-item costs  
**When** I delete the supplier-item cost  
**Then** it is removed  
**And** the list view no longer shows tiers for that supplier-item combination.

---

## 13. Audit & Observability

### User-visible audit data
- Show `createdAt`, `updatedAt` (and `updatedBy` if provided) on detail screen.
- If backend provides an audit log endpoint, show a link “View audit history” (implementation **TBD**).

### Status history
- Not applicable (no explicit lifecycle), but changes should be traceable via backend audit logs.

### Traceability expectations
- Frontend should include correlation/request ID if available in response headers in error logs (console/log framework).
- Log UI events: load/success/failure for supplier-item cost detail (non-PII).

---

## 14. Non-Functional UI Requirements
- **Performance**: Detail load should render initial skeleton/loading state; avoid blocking UI while validating tiers (validate locally).
- **Accessibility**: Tier table inputs must be keyboard navigable; error messages associated to fields (WCAG 2.1).
- **Responsiveness**: Tier editor usable on tablet widths; table may stack fields per row on small screens.
- **i18n/timezone/currency**:
  - Display costs formatted with supplier currency code.
  - Store and submit costs as raw decimals (no localized separators in payload).
  - Timestamps displayed in user timezone if app standard supports it.

---

## 15. Applied Safe Defaults
- SF-UI-EMPTY-STATE: Added an explicit empty state on list screen (“No supplier costs configured yet”) because it is UI ergonomics and does not change domain logic. Impacted sections: UX Summary, Error Flows.
- SF-UI-LOADING-STATE: Defined loading/saving states with disabled actions to prevent duplicate submits; safe for UX and recoverable. Impacted sections: Functional Behavior, Non-Functional UI Requirements.

---

## 16. Open Questions
1. What are the **exact backend API endpoints and schemas** for supplier-item cost + tiers (paths, request/response bodies, and whether updates are PUT replace vs PATCH)? (blocking)
2. What is the **exact permission/role/authority string** the frontend should use to enable editing (e.g., `inventory.cost.supplier.update`)? (blocking)
3. Is `base_cost` part of the supported UX (fallback cost when no tiers), or should the UI be **tiers-only**? If supported, can a record exist with **no tiers but baseCost set**? (blocking)
4. What is the **currency source of truth**: supplier currency only, or can supplier-item cost override currency? Backend story says determined by supplier; confirm field should be read-only. (blocking)
5. Does backend enforce/expect **cost precision** (e.g., 4 decimal places) for `unit_cost` and `base_cost`? (blocking for validation + formatting)
6. Is there **optimistic locking** (ETag/version/updatedAt) and expected handling for concurrent edits? If yes, what status code/body is returned? (blocking for conflict handling)

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Cost: Store Supplier/Vendor Cost Tiers (Optional) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/260

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Cost: Store Supplier/Vendor Cost Tiers (Optional)
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/260
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #197 - Cost: Store Supplier/Vendor Cost Tiers (Optional)
**URL**: https://github.com/louisburroughs/durion/issues/197
**Domain**: general

### Implementation Requirements
This issue was automatically created by the Missing Issues Audit System to address a gap in the automated story processing workflow.

The original story processing may have failed due to:
- Rate limiting during automated processing
- Network connectivity issues
- Temporary GitHub API unavailability
- Processing system interruption

### Implementation Notes
- Review the original story requirements at the URL above
- Ensure implementation aligns with the story acceptance criteria
- Follow established patterns for frontend development
- Coordinate with corresponding backend implementation if needed

### Technical Requirements
**Frontend Implementation Requirements:**
- Use Vue.js 3 with Composition API
- Follow TypeScript best practices
- Implement using Quasar UI framework components
- Ensure responsive design and accessibility (WCAG 2.1)
- Handle loading states and error conditions gracefully
- Implement proper form validation where applicable
- Follow established routing and state management patterns


### Notes for Agents
- This issue was created automatically by the Missing Issues Audit System
- Original story processing may have failed due to rate limits or network issues
- Ensure this implementation aligns with the original story requirements
- Frontend agents: Focus on Vue.js 3 components, TypeScript, Quasar UI framework. Coordinate with backend implementation for API contracts.

### Labels Applied
- `type:story` - Indicates this is a story implementation
- `layer:functional` - Functional layer implementation
- `kiro` - Created by Kiro automation
- `domain:general` - Business domain classification
- `story-implementation` - Implementation of a story issue
- `frontend` - Implementation type

---
*Generated by Missing Issues Audit System - 2025-12-26T17:37:22.118751069*