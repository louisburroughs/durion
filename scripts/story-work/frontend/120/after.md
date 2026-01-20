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

**Title:** [FRONTEND] [STORY] Master: Manage UOM and Pack/Case Conversions

**Primary Persona:** Product Administrator (a.k.a. Product Admin)

**Business Value:** Ensures pack/case vs each quantities transact consistently across POS, Inventory stock moves, and Work Execution lines by maintaining a single authoritative set of UOM conversions with validation and auditability.

---

## 2. Story Intent

### As a / I want / So that
**As a** Product Administrator,  
**I want** to create, view, update, and deactivate Unit of Measure (UOM) conversion rules (e.g., Case → Each),  
**So that** the system can correctly convert quantities between packaging units (each/pack/case) for inventory movements and operational transactions, with enforced validation and audit trails.

### In-scope
- A Master Data UI to:
  - List/query UOM conversions
  - Create a new conversion (fromUom → toUom with factor)
  - Update an existing conversion’s factor (with constraints)
  - Deactivate a conversion (no hard delete)
  - View audit metadata for conversions (created/updated by/at); link or panel for audit entries if available
- Frontend validation mirroring backend rules:
  - Factor must be > 0
  - Prevent duplicates for same from/to pair
  - Prevent invalid self-conversion (unless factor = 1)
- Moqui screen(s), forms, transitions, and service calls wiring for the above

### Out-of-scope
- Creating/editing UnitOfMeasure master records themselves (unless backend explicitly supports it)
- Applying conversions in sales pricing/tax calculations
- Designing inventory reservation/allocation logic (downstream consumers)
- Bulk import/export of conversions
- Defining conversion “per product” vs “global” beyond what backend supports (see Open Questions)

---

## 3. Actors & Stakeholders
- **Primary Actor:** Product Administrator
- **Stakeholders / Consumers:**
  - Inventory operations (stock moves use UOM conversions)
  - Work Execution (work order/parts lines use UOM conversions)
  - Audit/Compliance reviewer (needs change traceability)
- **System Components:**
  - Moqui backend services/entities for `UnitOfMeasure`, `UomConversion`, `AuditLog` (or equivalent)

---

## 4. Preconditions & Dependencies
- User is authenticated.
- User has permission to manage UOM conversions (permission name not provided; see Open Questions).
- Base UOMs exist (e.g., EA/PK/CS) and are queryable for selection in the UI.
- Backend endpoints/services exist to:
  - Query UnitOfMeasure list
  - Query UomConversion list
  - Create/update/deactivate UomConversion
  - Provide audit fields and/or audit log entries (contract TBD)

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Master / Admin navigation entry: **Master Data → Units of Measure → Conversions** (exact menu path may vary; implement consistent with existing Master screens).

### Screens to create/modify
1. **Screen:** `master/UomConversionList` (new)
   - Purpose: search/list conversions; access create/edit/deactivate.
2. **Screen:** `master/UomConversionDetail` (new or embedded in list as dialog)
   - Purpose: create or edit a conversion; show audit metadata; deactivate action.
3. (Optional if conventions exist) **Component/section:** `master/AuditLogPanel` (reuse if present) to display audit entries for the selected conversion.

### Navigation context
- From Master landing → conversions list
- From list row → detail screen (or side panel/dialog)
- “Create Conversion” opens create mode

### User workflows
**Happy path (create)**
1. User opens conversions list
2. Clicks “Create Conversion”
3. Selects From UOM, To UOM, enters conversion factor
4. Submits; sees success; new conversion appears active in list

**Happy path (update factor)**
1. User opens an existing conversion
2. Changes conversion factor
3. Submits; sees updated factor; audit metadata updated

**Happy path (deactivate)**
1. User opens an existing conversion
2. Clicks “Deactivate”
3. Confirms; conversion becomes inactive; list reflects status

**Alternate paths**
- Duplicate pair attempt rejected with inline error + toast/banner
- Invalid factor rejected (<=0, non-numeric)
- Self conversion rejected unless factor is exactly 1
- Unauthorized user sees forbidden and cannot access actions

---

## 6. Functional Behavior

### Triggers
- Entering conversions list screen triggers loading:
  - UOM options (for filters and create form)
  - UomConversion list (paged)
- Selecting a row triggers loading detail if not already present
- Submitting forms triggers create/update service call
- Clicking deactivate triggers transition/service call

### UI actions
- List controls:
  - Filter by From UOM, To UOM, Active status (Active/Inactive/All)
  - Text filter (optional) by UOM code/name (safe default)
- Row actions:
  - View/Edit
  - Deactivate (only if active)
- Create/Edit form:
  - From UOM (required select)
  - To UOM (required select)
  - Conversion factor (required decimal input)
  - Active (read-only on create default true; editable only via deactivate/activate if supported—see Open Questions)

### State changes
- Create: new `UomConversion` record created `isActive=true`
- Update: existing record updated (at minimum factor)
- Deactivate: record `isActive=false` (no deletion)

### Service interactions
- `GET` list of UnitOfMeasure for selects
- `GET` list/query of UomConversion with filters/paging
- `POST` create UomConversion
- `POST/PUT` update UomConversion (factor only)
- `POST` deactivate UomConversion (or update isActive=false)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **R-UOM-01:** `conversionFactor` must be a positive, non-zero decimal
  - UI: numeric input with min > 0; reject 0/negative; show message “Conversion factor must be a positive number.”
- **R-UOM-02:** unique (fromUomId, toUomId) pair
  - UI: on submit, if backend returns duplicate violation, show message “A conversion for this From/To pair already exists.”
  - (Optional pre-check is not required; avoid race conditions)
- **R-UOM-03:** implicitly reversible
  - UI: No separate reverse record required; show informational hint: “Reverse conversion is computed implicitly.” (informational only)
- **R-UOM-04:** no hard delete
  - UI: no “Delete” action; only “Deactivate”

### Enable/disable rules
- Deactivate button enabled only when conversion `isActive=true`
- Update form:
  - If backend disallows changing From/To, UI must render them read-only in edit mode (or disable selects) and only allow factor edit
- Self conversion:
  - If From == To then factor must equal 1; otherwise block submit with inline error

### Visibility rules
- Inactive conversions are either hidden by default (filter Active only) or shown with status badge (safe default: show Active only with toggle “Show inactive”).

### Error messaging expectations
- Validation errors shown inline at field level when possible; otherwise show page-level banner with backend message.
- Forbidden shows “You do not have permission to manage UOM conversions.”

---

## 8. Data Requirements

### Entities involved
- `UnitOfMeasure` (reference data)
- `UomConversion` (managed by this UI)
- `AuditLog` (or `ItemCostAudit`-like equivalent; actual name TBD)

### Fields
**UnitOfMeasure**
- `uomId` (UUID/string): required
- `uomCode` (string): required (display)
- `uomName` (string): optional (display)

**UomConversion**
- `id` (UUID): required
- `fromUomId` (UUID): required
- `toUomId` (UUID): required
- `conversionFactor` (decimal): required; > 0
- `isActive` (boolean): required; default true
- Audit fields (if present on entity):
  - `createdAt`, `createdBy`, `updatedAt`, `updatedBy`

**AuditLog** (if exposed)
- `auditId`
- `entityName`
- `entityId`
- `action` (CREATE/UPDATE/DEACTIVATE)
- `changedBy`, `changedAt`
- `beforeJson`, `afterJson` (or field diffs)

### Read-only vs editable
- Create mode: editable `fromUomId`, `toUomId`, `conversionFactor`
- Edit mode: `conversionFactor` editable; `fromUomId/toUomId` read-only (per backend reference)
- `isActive` toggled only by deactivate action (unless backend supports re-activate; see Open Questions)

### Derived/calculated fields
- Optional derived display: “Inverse factor” = `1 / conversionFactor` for informational display only (do not persist).

---

## 9. Service Contracts (Frontend Perspective)

> Backend contract is not provided for Moqui services/REST paths. The frontend must integrate with whichever mechanism exists (Moqui REST, screen services, or JSON RPC). The following describes required operations and payload shapes.

### Load/view calls
1. **List UOMs**
   - Request: `GET UnitOfMeasure` (filter active if applicable)
   - Response: array of `{uomId, uomCode, uomName}`
2. **Query conversions**
   - Request params:
     - `fromUomId?`, `toUomId?`, `isActive?`, `pageIndex`, `pageSize`, `sortBy`
   - Response:
     - `data[]` conversions
     - `totalCount`

### Create/update calls
3. **Create conversion**
   - Request body: `{fromUomId, toUomId, conversionFactor}`
   - Response: created conversion including `id` and audit fields
4. **Update conversion factor**
   - Request body: `{id, conversionFactor}`
   - Response: updated conversion

### Submit/transition calls
5. **Deactivate conversion**
   - Request body: `{id}` (or `{id, isActive:false}`)
   - Response: updated conversion with `isActive=false`

### Error handling expectations
- `400` validation errors: map to field errors when keys match (e.g., `conversionFactor`)
- Duplicate constraint: surfaced as `400`/`409` (TBD); show friendly duplicate message
- `403`: show forbidden; disable actions
- Network/server error: show generic “Unable to save conversion. Try again.” with request correlation if available

---

## 10. State Model & Transitions

### Allowed states (for UomConversion)
- `Active` (`isActive=true`)
- `Inactive` (`isActive=false`)

### Role-based transitions
- Product Administrator can:
  - Create Active conversions
  - Update factor (Active and possibly Inactive—TBD; default: Active only)
  - Deactivate Active conversions
- Non-authorized users:
  - Read-only access (TBD) or no access

### UI behavior per state
- Active: editable (factor), deactivate available
- Inactive: read-only; deactivate hidden/disabled; optionally show “Inactive” badge

---

## 11. Alternate / Error Flows

### Validation failures
- Factor <= 0 → block submit; show inline error
- From/To missing → required errors
- From == To and factor != 1 → block submit; show message “When converting a UOM to itself, factor must be 1.”

### Concurrency conflicts
- If backend returns optimistic lock/conflict (409):
  - UI shows “This conversion was updated by another user. Reload to continue.”
  - Provide “Reload” action to refetch detail/list

### Unauthorized access
- If list endpoint returns 403:
  - Show forbidden state page; no data displayed
- If action endpoints return 403:
  - Show forbidden toast and keep form unchanged

### Empty states
- No conversions match filters:
  - Show “No conversions found” and CTA “Create Conversion”
- No UOMs available:
  - Block create with message “No Units of Measure available. Create UOMs first.” (link if screen exists; otherwise informational)

---

## 12. Acceptance Criteria

### Scenario 1: List and filter UOM conversions
**Given** I am authenticated as a Product Administrator  
**When** I open the UOM Conversions list screen  
**Then** I can view a paged list of existing conversions including From UOM, To UOM, conversion factor, and Active/Inactive status  
**And** I can filter the list by From UOM, To UOM, and Active status

### Scenario 2: Create a valid conversion
**Given** I am authenticated as a Product Administrator  
**And** UnitOfMeasure “CS” (Case) and “EA” (Each) exist  
**When** I create a conversion from “CS” to “EA” with factor “24”  
**Then** the system saves the conversion successfully  
**And** the conversion appears in the list as Active  
**And** viewing the conversion shows createdBy/createdAt populated (if provided by backend)

### Scenario 3: Reject zero or negative conversion factor
**Given** I am authenticated as a Product Administrator  
**And** UnitOfMeasure “PK” (Pack) and “EA” (Each) exist  
**When** I attempt to create a conversion from “PK” to “EA” with factor “0”  
**Then** the UI prevents submission or the backend rejects the request  
**And** I see an error stating the conversion factor must be a positive number  
**And** no conversion is created

### Scenario 4: Reject duplicate From/To conversion
**Given** I am authenticated as a Product Administrator  
**And** a conversion from “CS” to “EA” already exists  
**When** I attempt to create another conversion from “CS” to “EA”  
**Then** the system rejects the request  
**And** I see an error indicating that this conversion already exists

### Scenario 5: Update conversion factor
**Given** I am authenticated as a Product Administrator  
**And** a conversion from “CS” to “EA” exists with factor “12”  
**When** I edit the conversion and change the factor to “24” and save  
**Then** the system saves the updated factor  
**And** the list and detail view show factor “24”  
**And** updatedBy/updatedAt are updated (if provided)

### Scenario 6: Deactivate a conversion (no delete)
**Given** I am authenticated as a Product Administrator  
**And** a conversion exists and is Active  
**When** I deactivate the conversion and confirm  
**Then** the conversion is marked Inactive  
**And** it is not removed from the system (still queryable when filtering to include inactive)  
**And** the UI does not offer a hard-delete action anywhere

### Scenario 7: Unauthorized user cannot manage conversions
**Given** I am authenticated as a user without permission to manage UOM conversions  
**When** I attempt to access the create or edit actions  
**Then** the UI blocks the action or the backend returns 403  
**And** I see a “not authorized” message  
**And** no changes are persisted

---

## 13. Audit & Observability

### User-visible audit data
- Show on detail view:
  - createdAt/createdBy, updatedAt/updatedBy (if available)
- If an audit log endpoint exists:
  - Provide an “Audit History” panel listing create/update/deactivate entries with timestamp + actor and before/after (or diff summary)

### Status history
- Deactivation should produce an audit event/log entry so the change is traceable.

### Traceability expectations
- All create/update/deactivate calls should include a correlation/request ID if the frontend framework supports it; display it in an error details drawer (optional).

---

## 14. Non-Functional UI Requirements
- **Performance:** list loads within 2s for up to 1,000 conversions with paging; avoid N+1 by loading UOM options once per session/page.
- **Accessibility:** all form controls labeled; validation errors announced; keyboard navigable; sufficient contrast for status badges.
- **Responsiveness:** usable on tablet widths; list may collapse columns but must keep core actions accessible.
- **i18n/timezone:** display timestamps in user locale/timezone; do not assume currency.

---

## 15. Applied Safe Defaults
- **SD-UX-EMPTY-STATE**
  - Assumed: Provide standard empty state with CTA to create conversion.
  - Why safe: UI-only ergonomics; does not affect domain logic.
  - Impacted sections: UX Summary, Alternate / Error Flows.
- **SD-UX-PAGINATION**
  - Assumed: Server-backed paging with default page size (e.g., 25) and sorting.
  - Why safe: Presentation concern; does not change business rules.
  - Impacted sections: UX Summary, Service Contracts, Non-Functional.
- **SD-ERR-MAP-VALIDATION**
  - Assumed: Map backend 400 validation responses to inline field errors when possible, else banner.
  - Why safe: Standard error-handling; does not alter policy.
  - Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions
1. **Domain ownership/labeling conflict:** The frontend story input says Domain “user” and “Product / Parts Management”, while backend story is labeled `domain:inventory`. Confirm the correct primary domain label for the frontend issue (Inventory vs Product). If Product is the owner, story must be relabeled and rewrite variant changed.
2. **Backend contract for Moqui:** What are the actual Moqui service names or REST endpoints for:
   - listing UnitOfMeasure
   - querying UomConversion
   - create/update/deactivate UomConversion
   - retrieving AuditLog entries for a conversion?
3. **Permissions:** What permission(s)/roles gate create/update/deactivate of UOM conversions? (Exact permission IDs needed for UI access control.)
4. **Scope of conversions:** Are UOM conversions **global** (UOM↔UOM) or **product-specific** (Product + From/To)? The provided entities suggest global, but POS usage often needs per-product pack size.
5. **Edit constraints:** Is editing `fromUomId` / `toUomId` prohibited (as backend reference implies), or allowed with additional validation?
6. **Re-activation:** Should inactive conversions be re-activated via UI, or is deactivation final?
7. **Audit UI:** Is there an existing audit log viewer component/screen convention in this frontend repo that should be reused, or should we only show created/updated metadata?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Master: Manage UOM and Pack/Case Conversions — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/120

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Master: Manage UOM and Pack/Case Conversions  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/120  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Master: Manage UOM and Pack/Case Conversions

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **Product Admin**, I want to define UOM conversions so that packs/cases vs each transact correctly.

## Details
- Base UOM plus alternate UOM conversions.
- Validate non-zero and reversible conversions.

## Acceptance Criteria
- UOM conversions created and queryable.
- Conversion rules enforced.
- Audited changes.

## Integrations
- Inventory uses UOM on stock moves; Workexec uses UOM on lines.

## Data / Entities
- UnitOfMeasure, UomConversion, AuditLog

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