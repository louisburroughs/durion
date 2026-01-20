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

STOP: Clarification required before finalization

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Master: Create & Manage Product Record (Part/Tire) with Identifiers, Attributes, Status, and Search (Moqui Screens)

## Primary Persona
Product Admin (a catalog/product master administrator working in POS backoffice)

## Business Value
Enable consistent product master creation and maintenance (SKU/MPN/UPC, manufacturer, category, UOM, description/attributes, active/inactive) so Inventory and Work Execution can reliably reference `productId`, and auditing supports traceability/compliance.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Product Admin  
- **I want** to create, view, update, deactivate/reactivate, and search product master records including identifiers and attributes  
- **So that** all modules reference a consistent `productId` and product data changes are auditable.

## In-scope
- Moqui UI screens for:
  - Product list/search (SKU/MPN exact + keyword search)
  - Product create
  - Product detail view
  - Product edit (with SKU immutable post-create)
  - Product status change (ACTIVE/INACTIVE)
  - Audit/history viewing (at minimum: display audit events surfaced by backend)
- Frontend validations aligned to backend rules (required fields, immutability hints) without inventing new domain policy.
- Error handling for validation errors, conflicts, unauthorized, and not-found.

## Out-of-scope
- Implementing backend product APIs/entities/business logic (assumed provided by backend).
- Inventory costing, stock movements, reservations, valuation, or consumption workflows.
- Creating/managing manufacturers or categories (only selecting existing references).
- Work order flagging / purchase order notifications implementation details (UI may display backend-provided warnings only).
- Barcode printing, label generation, or bulk import/export.

---

# 3. Actors & Stakeholders
- **Product Admin (Primary):** Creates and maintains product records.
- **Inventory Staff (Stakeholder):** Relies on accurate product master for stock tracking.
- **Work Execution Staff (Stakeholder):** Relies on product master for job planning and parts usage.
- **Auditor/Manager (Stakeholder):** Reviews change history for traceability.
- **System (Moqui + Backend APIs):** Enforces uniqueness, immutability, permissions, and writes audit trail.

---

# 4. Preconditions & Dependencies
- User is authenticated in the frontend and can call Moqui/backend services.
- User has a permission equivalent to backend `product:manage` (exact permission string in Moqui is **unknown**; see Open Questions).
- Reference data exists and is queryable:
  - Manufacturers (`manufacturerId`, `name`)
  - Categories (`categoryId`, `name`)
  - Unit of Measure values (or free-text UOM—unclear; see Open Questions)
- Backend provides endpoints/services for:
  - Product CRUD + status changes
  - Search by SKU/MPN/keyword
  - Identifier uniqueness enforcement and conflict responses
  - Audit event retrieval (or embedded audit data in product detail)

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- Backoffice navigation: **Master Data → Products**
- Deep links:
  - `/master/products` (list/search)
  - `/master/products/new` (create)
  - `/master/products/:productId` (view)
  - `/master/products/:productId/edit` (edit)

## Screens to create/modify (Moqui)
Create a screen tree under (example) `apps/Durion/screens/master/products/`:
- `master/products.xml` (root menu + default transition to list)
- `master/products/ProductList.xml`
- `master/products/ProductCreate.xml`
- `master/products/ProductDetail.xml`
- `master/products/ProductEdit.xml`
- Optional embedded subscreens/sections:
  - `components/ProductForm.xml`
  - `components/ProductIdentifiers.xml`
  - `components/ProductMetricsAudit.xml` (audit/history)

> Exact screen location/naming must follow repo conventions (README/agent guide). If conventions differ, adjust paths accordingly.

## Navigation context
- From Product List, selecting a row navigates to Product Detail.
- From Product Detail:
  - “Edit” navigates to Product Edit
  - “Deactivate/Reactivate” triggers a confirm dialog then calls status change transition
  - “Back to list” returns with prior search preserved (query params)

## User workflows
### Happy path: Create product
1. Product Admin opens Product Create screen.
2. Enters required fields + identifiers and optional attributes.
3. Submits.
4. On success: navigate to Product Detail for the new `productId` with a success banner.

### Happy path: Edit product
1. Admin opens Product Detail, chooses Edit.
2. Updates editable fields (manufacturer, mpn, description, etc.), but **SKU is read-only**.
3. Saves.
4. On success: returns to detail and shows updated values + success banner.

### Happy path: Deactivate/reactivate
1. Admin clicks “Deactivate” (or “Reactivate”).
2. Confirm modal explains impact: may affect open work orders/POs (exact messaging depends on backend response).
3. Submit status change.
4. On success: status pill updates and an audit entry appears (if audit is fetched).

### Alternate path: Search
1. Admin searches by:
   - exact SKU
   - exact MPN (+ manufacturer filter if required by backend)
   - keyword query
2. Results list shows key fields and status.
3. Click through to detail.

---

# 6. Functional Behavior

## Triggers
- Screen load of list/detail/edit/create.
- User submits create/update/status-change forms.
- User executes search.

## UI actions
### Product list/search
- Search form fields:
  - `sku` (text, optional)
  - `mpn` (text, optional)
  - `manufacturerId` (select, optional; may be required for MPN exact search if backend needs it—unclear)
  - `q` keyword (text, optional)
  - `status` filter (ACTIVE/INACTIVE/ALL; default ALL or ACTIVE—see Open Questions if not in backend)
- Results table columns (minimum):
  - Name
  - SKU
  - Manufacturer
  - MPN
  - Category
  - UOM
  - Status
  - UpdatedAt (if available)

### Create/edit form
Fields (see Data Requirements) with inline validation and clear required markers.
- SKU editable on create; read-only on edit.
- Status shown on detail; not directly editable in create/edit except via status action (preferred to match backend rules).

### Status change
- Confirm modal with action-specific text.
- On backend response, show any dependency flags/warnings returned (if provided) in a dismissible banner.

## State changes
- Product status transitions between `ACTIVE` and `INACTIVE` only.

## Service interactions
- List/search uses a read service.
- Detail uses a read service by `productId`.
- Create uses a create service, receives new `productId`.
- Update uses an update service.
- Status change uses a dedicated service/transition.

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Required fields enforced client-side (and re-validated server-side):
  - `name`, `description`, `unitOfMeasure`, `manufacturerId`, `categoryId`, `sku`, `mpn`
- SKU:
  - Must be present on create.
  - Shown as immutable/read-only on edit with helper text: “SKU cannot be changed after creation.”
- Manufacturer + MPN uniqueness:
  - UI should not attempt to pre-check uniqueness (avoid race conditions).
  - If backend returns conflict, show field-specific error message (see Error messaging expectations).

## Enable/disable rules
- “Save” disabled until required fields valid (basic client-side checks).
- “Deactivate” button visible only when current status is ACTIVE.
- “Reactivate” button visible only when current status is INACTIVE.

## Visibility rules
- Audit/history panel visible if backend provides audit access and user has permission; otherwise hide panel and show “Not authorized” or omit.

## Error messaging expectations
- Validation (400): show inline field errors when provided; otherwise show a banner “Please correct the highlighted fields.”
- Conflict (409): show message:
  - For SKU duplicate: “SKU already exists.”
  - For Manufacturer+MPN duplicate: “A product with this Manufacturer and MPN already exists.”
- Unauthorized (401/403): show a blocking page/banner “You do not have access to manage products.”
- Not found (404) on detail/edit: show “Product not found” with link back to list.

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- `Product`
- `ProductIdentifier` (SKU, MPN, UPC)
- `ProductAttribute` (key/value attributes)
- `Manufacturer` reference
- `Category` reference
- `AuditLog` / `ItemCostAudit` is **not** applicable here; product audit is required but entity name is backend-defined (clarify).

## Fields (type, required, defaults)
### Product
- `productId` (UUID, read-only, system-generated)
- `status` (enum: `ACTIVE` | `INACTIVE`, read-only on form; default `ACTIVE` on create)
- `name` (string, required)
- `description` (string, required; may include tire size/spec text)
- `unitOfMeasure` (string, required; source list vs free text unclear)
- `manufacturerId` (UUID, required)
- `categoryId` (UUID, required)
- `createdAt` (timestamp, read-only)
- `updatedAt` (timestamp, read-only)

### ProductIdentifier
- `sku` (string, required, unique, immutable after create)
- `mpn` (string, required; uniqueness in combination with manufacturerId)
- `upc` (string, optional)

### ProductAttribute
- `attributes` (collection of key/value pairs or JSON; optional)
  - Because storage is backend-defined (JSONB/text), UI should treat as:
    - repeatable rows: `key` (string), `value` (string)
    - enforce non-empty key when row exists; values may be empty only if backend allows (unclear)

## Read-only vs editable by state/role
- By role: Product Admin can create/edit/status-change; others read-only (exact roles/permissions unclear).
- By product state:
  - ACTIVE and INACTIVE products are both viewable.
  - Editing allowed regardless of status? Backend allows status toggling; edit rules are not explicitly restricted by status (assume editable unless backend restricts; if backend blocks, surface error).

## Derived/calculated fields
- Display-only:
  - manufacturer name, category name (resolved via reference data or embedded in product response)

---

# 9. Service Contracts (Frontend Perspective)

> Backend contract is referenced from backend story but not expressed as concrete Moqui services/routes. The frontend must integrate via Moqui screens/transitions that call services; exact service names/paths are **Open Questions**.

## Load/view calls
- `ProductSearch` (GET-like)
  - Inputs: `sku?`, `mpn?`, `manufacturerId?`, `q?`, `status?`, pagination params
  - Output: list of products with summary fields
- `ProductGet` by `productId`
  - Output: product detail including identifiers and attributes

- Reference data:
  - `ManufacturerList` (id, name)
  - `CategoryList` (id, name)
  - `UomList` (if UOM is enumerated)

- Audit:
  - `ProductAuditList(productId)` returning events (created/updated/status-changed) with actor and timestamps

## Create/update calls
- `ProductCreate`
  - Inputs: product fields + identifiers + attributes
  - Output: `productId`
  - Errors: 400 validation; 409 duplicate SKU or duplicate (manufacturerId+mpn)
- `ProductUpdate`
  - Inputs: `productId` + editable fields (SKU excluded)
  - Errors: 400, 409 (if MPN uniqueness violated)

## Submit/transition calls
- `ProductStatusChange`
  - Inputs: `productId`, `status` target (`ACTIVE`/`INACTIVE`)
  - Output: updated status; optional warnings/impacted dependencies info

## Error handling expectations
- Map HTTP status or Moqui service errors to:
  - Field-level errors where possible
  - Otherwise global notification banner
- For 409, preserve entered values and highlight relevant fields.

---

# 10. State Model & Transitions

## Allowed states
- `ACTIVE`
- `INACTIVE`

## Role-based transitions
- With `product:manage` (or Moqui equivalent):
  - ACTIVE → INACTIVE
  - INACTIVE → ACTIVE
- Without permission:
  - No create/edit/status transitions; list/detail may still be accessible depending on permission model (unclear)

## UI behavior per state
- ACTIVE:
  - Show “Deactivate” action
- INACTIVE:
  - Show “Reactivate” action
  - Detail header should visibly indicate inactive status (badge/pill)

---

# 11. Alternate / Error Flows

## Validation failures
- Missing required fields: prevent submit client-side; also handle backend 400 with messages.
- Invalid manufacturer/category: if backend returns 400, show error near the select field and keep selections.

## Concurrency conflicts
- If backend supports optimistic locking (e.g., version/updatedAt mismatch) and returns 409/412:
  - Show “This product was updated by someone else. Reload to continue.”
  - Provide “Reload” action which re-fetches detail and re-opens edit with latest data (behavior depends on available version field—Open Question).

## Unauthorized access
- On create/edit/status change attempt returning 403:
  - Show “Not authorized” page/banner.
  - Disable/hidden action buttons after receiving 403 (don’t keep retrying).

## Empty states
- Search with no results:
  - Show “No products found” and a CTA to “Create product” if user has permission.

---

# 12. Acceptance Criteria

## Scenario 1: Create product (success)
**Given** I am authenticated as a Product Admin with product manage permission  
**And** manufacturers and categories are available to select  
**When** I create a product with unique SKU, valid manufacturer, MPN, name, description, category, unit of measure, and optional UPC/attributes  
**Then** the system creates the product with status ACTIVE  
**And** I am navigated to the Product Detail screen for the new productId  
**And** I see a success message confirming creation.

## Scenario 2: Create product rejected due to duplicate SKU
**Given** a product already exists with SKU `ABC-1001`  
**When** I attempt to create a new product with SKU `ABC-1001`  
**Then** the create request fails with a conflict error  
**And** the UI displays “SKU already exists” associated to the SKU field  
**And** my entered form values remain intact for correction.

## Scenario 3: Create product rejected due to duplicate Manufacturer+MPN
**Given** a product already exists with manufacturer `mfg-123` and MPN `XYZ-2002`  
**When** I attempt to create a new product with manufacturer `mfg-123` and MPN `XYZ-2002`  
**Then** the create request fails with a conflict error  
**And** the UI displays “A product with this Manufacturer and MPN already exists” associated to the MPN/manufacturer inputs.

## Scenario 4: Edit product (SKU immutable)
**Given** I am viewing an existing product as a Product Admin  
**When** I open the edit screen  
**Then** SKU is displayed as read-only  
**And** I can edit manufacturer, MPN, description, category, UOM, name, UPC, and attributes (subject to backend rules)  
**When** I save valid edits  
**Then** I return to the detail screen and see the updated values.

## Scenario 5: Deactivate product (success)
**Given** a product is ACTIVE  
**And** I have permission to manage products  
**When** I confirm deactivation  
**Then** the product status becomes INACTIVE in the UI  
**And** I see confirmation of the status change  
**And** if the backend returns impacted dependency warnings, they are displayed to the user.

## Scenario 6: Reactivate product (success)
**Given** a product is INACTIVE  
**When** I reactivate it  
**Then** the product status becomes ACTIVE  
**And** the UI updates available actions accordingly.

## Scenario 7: Search products by SKU/MPN/keyword
**Given** multiple products exist with varying SKUs, MPNs, names, categories, attributes, and descriptions  
**When** I search by exact SKU or exact MPN  
**Then** matching products are returned  
**When** I search by keyword  
**Then** products matching in name, description, category name, or attributes are returned  
**And** results indicate each product’s ACTIVE/INACTIVE status.

## Scenario 8: Unauthorized management attempt
**Given** I am authenticated without product manage permission  
**When** I attempt to access Product Create or submit an update/status change  
**Then** the system denies the action  
**And** the UI shows a not-authorized message and prevents further submission.

---

# 13. Audit & Observability

## User-visible audit data
- On Product Detail, show an “Audit/History” section (if available) listing:
  - event type: PRODUCT_CREATED / PRODUCT_UPDATED / PRODUCT_STATUS_CHANGED
  - timestamp
  - actor (user id/name if provided)
  - for updates: before/after field changes (when backend provides)

## Status history
- At minimum, show status change events in audit list.

## Traceability expectations
- Frontend should include correlation/request IDs if supported by Moqui/client (log context) when calling services.
- Do not log sensitive data; log productId and error codes/messages for troubleshooting (client-side logging policy depends on repo conventions).

---

# 14. Non-Functional UI Requirements

- **Performance:** Product list/search should render first page quickly; support pagination to avoid loading unbounded results.
- **Accessibility:** Forms must have labels, required indicators, keyboard navigation, and accessible error messages.
- **Responsiveness:** Screens usable on tablet-sized widths; tables allow horizontal scroll or responsive column behavior.
- **i18n/timezone/currency:** Display timestamps in user locale/timezone if the app supports it; no currency behavior required.

---

# 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide a “No results” empty state with guidance and optional “Create product” CTA; qualifies as safe because it does not change domain behavior, only UX. Impacted sections: UX Summary, Alternate / Error Flows.
- SD-UX-PAGINATION: Paginate search results with conservative default page size; qualifies as safe because it is a UI ergonomics pattern and does not affect business rules. Impacted sections: UX Summary, Service Contracts, Non-Functional UI Requirements.
- SD-ERR-STANDARD-MAPPING: Map 400/401/403/404/409 to inline errors + banners; qualifies as safe because it is standard error handling implied by backend contract. Impacted sections: Business Rules, Alternate / Error Flows, Acceptance Criteria.

---

# 16. Open Questions

1. **Domain label mismatch:** The frontend issue describes Product/Parts Management, but available canonical domains in this prompt do not include `domain:product`. Should this story truly be labeled `domain:inventory`, or is there a missing `domain:product` label family that must be used?
2. **Moqui service naming/paths:** What are the exact Moqui service names (or REST endpoints proxied through Moqui) for product search/get/create/update/status-change and audit retrieval?
3. **Permission model in Moqui:** What is the exact permission string/role required in the frontend to show create/edit/status actions (backend mentions `product:manage`)? Is read-only access allowed for non-admins?
4. **UOM source:** Is `unitOfMeasure` free text, a controlled vocabulary (entity list), or a reference to a UOM entity? If controlled, what are valid values and how are they fetched?
5. **Attributes format:** Are product attributes stored as JSON, as discrete rows (key/value entity), or both? What constraints exist (allowed keys, max lengths, duplicates)?
6. **Search contract:** For “exact MPN” search, does backend require manufacturerId to disambiguate, or does it search MPN globally? What are supported query params and precedence rules if multiple are provided?
7. **Optimistic locking:** Does the backend provide a version field (or ETag/updatedAt check) to detect concurrent edits? If yes, what is the expected request field and error code?
8. **Deactivation dependency feedback:** When deactivating with open work orders/POs, does backend return a structured list of impacted items, or only performs side effects (flag/notify) without returning details? What should the UI display?

---

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/121

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/121  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Master: Create Product Record (Part/Tire) with Identifiers and Attributes

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **Product Admin**, I want to create a product with SKU/MPN, manufacturer, type, and attributes so that all modules reference a consistent product master.

## Details  
- Identifiers: internal SKU, manufacturer part number, optional UPC.  
- Attributes: description, category, tire size/spec, UOM, active/inactive.

## Acceptance Criteria  
- Create/update/deactivate product.  
- Search by SKU/MPN/keywords.  
- Changes audited.

## Integrations  
- Inventory and Workexec reference productId consistently.

## Data / Entities  
- Product, ProductIdentifier, ProductAttribute, ManufacturerRef, AuditLog

## Classification (confirm labels)  
- Type: Story  
- Layer: Domain  
- Domain: Product / Parts Management

### Frontend Requirements

- Implement Vue.js 3 components with TypeScript  
- Use Quasar framework for UI components  
- Integrate with Moqui Framework backend  
- Ensure responsive design and accessibility

### Technical Stack

- Vue.js 3 with Composition API  
- TypeScript 5.x  
- Quasar v2.x  
- Moqui Framework integration

---  
*This issue was automatically created by the Durion Workspace Agent*