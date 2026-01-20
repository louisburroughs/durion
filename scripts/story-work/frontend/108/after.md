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
[FRONTEND] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)

### Primary Persona
Product Administrator (Product Admin)

### Business Value
Enable Service Advisors (and downstream Work Execution) to quickly narrow candidate parts/tires to those likely compatible with a customer vehicle, reducing incorrect part selection while keeping the system “hint-based” (not a strict fitment engine).

---

## 2. Story Intent

### As a / I want / So that
**As a** Product Administrator,  
**I want** to create, edit, remove, and search products by vehicle applicability “fitment hint” tags (make/model/year range/tire size/axle position),  
**so that** advisors and downstream flows can filter and suggest likely-compatible products for a vehicle.

### In-scope
- Admin UI to manage Vehicle Applicability Hints for a product (CRUD).
- Admin UI to search/filter products by vehicle attributes / tags.
- Display of existing hints/tags on product detail.
- User-visible audit history display for hint changes (read-only).
- Moqui screen/form/service wiring for these flows (transitions, validations, error handling).

### Out-of-scope
- A full fitment/compatibility rules engine or VIN decoding.
- Blocking sales/workorder flows based on fitment (this is advisory only).
- Defining/owning the Product master entity itself (assumed exists).
- Importing tags from external catalogs.
- Any inventory valuation/costing logic.

---

## 3. Actors & Stakeholders
- **Product Administrator:** creates/maintains applicability hints.
- **Service Advisor:** consumes filtered results elsewhere (not primary UI in this story, but stakeholder).
- **Work Execution system / module:** passes vehicle attributes to filter candidates (integration consumer).
- **Auditor / Manager:** reviews change history for compliance/traceability.
- **System Admin/Security:** ensures only authorized users can edit hints.

---

## 4. Preconditions & Dependencies
- User is authenticated in the Moqui frontend.
- Product/SKU records exist and are viewable in the admin UI.
- Backend APIs/services exist (or will exist) to:
  - CRUD VehicleApplicabilityHint + FitmentTag
  - Query products filtered by vehicle attributes/tags
  - Provide audit log entries for hint changes
- Authorization model exists for “product admin” capabilities.

Dependencies (blocking unless already defined elsewhere):
- Exact Moqui service names/paths and request/response payloads for CRUD + search.
- Permission(s) required to manage hints and to view audit logs.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Product Admin area:
  - Product search/list screen → open Product detail.
  - Product detail screen → “Vehicle Applicability (Hints)” section.

### Screens to create/modify
1. **Modify existing Product Detail screen** (or create if missing):
   - Add a related panel/section: “Vehicle Applicability Hints”.
   - Include list of existing hints for the product and their tags.
   - Provide actions: Add Hint, Edit Hint, Delete Hint, View Audit.
2. **Create/Edit Hint screen/dialog**:
   - Form to add/edit hint and manage its tags (add/remove rows).
3. **Product Search with Fitment Filter screen enhancements**:
   - Search input set for vehicle attributes (make/model/year/tire size/axle position).
   - Results list of matching products.
4. **Audit Log viewer screen/dialog** scoped to a product and hint:
   - Read-only list of audit events for hint CRUD.

### Navigation context
- All screens under an Admin route namespace (exact route TBD by project conventions).
- Breadcrumb: Admin → Products → {Product} → Vehicle Applicability Hints.
- Transitions return to Product detail after create/update/delete.

### User workflows
**Happy path (create):**
1. Product Admin opens a Product.
2. In “Vehicle Applicability Hints”, clicks “Add Hint”.
3. Adds tag rows (e.g., MAKE=Subaru, MODEL=Outback, YEAR_RANGE=2020-2023).
4. Saves.
5. Sees new hint in list and audit entry visible in Audit screen.

**Alternate paths:**
- Add multiple hints to same product (if allowed).
- Edit existing hint to change tags.
- Delete hint (confirmation required).
- Search products by vehicle attributes and get empty results state.

---

## 6. Functional Behavior

### Triggers
- User navigates to product detail → frontend loads hints for product.
- User opens search screen and enters vehicle attributes → frontend requests filtered product list.
- User submits create/update/delete hint → frontend calls corresponding backend service and refreshes view.
- User opens audit viewer → frontend loads audit entries.

### UI actions
- Add/remove tag row in a hint form.
- Save (create/update) hint.
- Delete hint with confirmation.
- Apply filters / clear filters in product search.

### State changes (frontend)
- Form state: dirty/pristine; disable submit until valid.
- Loading indicators while services run.
- Upon successful write: show confirmation and refresh hint list.
- On errors: show field-level validation messages and/or global error banner.

### Service interactions
- Load product hints by productId.
- Create hint for productId with tags.
- Update hint by hintId with tags.
- Delete hint by hintId.
- Search products by vehicle attribute filters (tag intersection semantics are owned by backend).
- Load audit log by productId and/or hintId.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Tag entries are key/value pairs (or tagType/tagValue).
- Prevent submitting a hint with:
  - zero tags (unless backend allows; clarify)
  - any blank tag type/key or blank tag value
  - invalid YEAR_RANGE formatting (clarify exact format/validation rules)
- UI must not allow editing system-managed fields (none specified here besides IDs).

### Enable/disable rules
- Save button disabled while:
  - form invalid
  - request in-flight
- Edit/Delete actions disabled if user lacks permission (based on backend response/permissions model).

### Visibility rules
- “Vehicle Applicability Hints” section visible only to users with appropriate read permission (clarify).
- Audit viewer visible only to users with audit-view permission (clarify).

### Error messaging expectations
- 400 validation errors: show actionable messages, map to fields when possible (tag row index + field).
- 403: show “You don’t have permission to perform this action.”
- 404 (product or hint missing): show not-found and navigate back to product list if appropriate.
- 409 concurrency (if used): prompt user to refresh and retry.

---

## 8. Data Requirements

### Entities involved (frontend-facing)
- **VehicleApplicabilityHint**
  - `hintId` (string/UUID; read-only)
  - `productId` (string/UUID; read-only in edit)
  - `fitmentTags` (array of FitmentTag; editable)
  - (optional) `createdAt`, `createdBy`, `updatedAt`, `updatedBy` (read-only; if provided)
- **FitmentTag**
  - `tagType` (enum/string; required)
  - `tagValue` (string; required)
- **AuditLog / AuditEvent**
  - `eventType` (e.g., VEHICLE_HINT_CREATED/UPDATED/DELETED)
  - `timestamp`
  - `actorUserId` / actor display name (if available)
  - `entityRef` (productId, hintId)
  - `delta` (before/after snapshot or change summary)

### Fields (type, required, defaults)
- `tagType`: required; UI offers a controlled list for “basic tags”:
  - MAKE, MODEL, YEAR_RANGE, TIRE_SIZE, AXLE_POSITION
  - Allow free-form additional keys only if backend supports it (clarify; backend mentions extensibility without schema change—UI needs rule).
- `tagValue`: required string; trimming whitespace on submit.

### Read-only vs editable
- IDs always read-only.
- Tag list editable in create/edit.
- Audit fields read-only.

### Derived/calculated fields
- Display-only “Human readable hint summary” assembled client-side (e.g., “Subaru Outback 2020-2023, 225/45R17, FRONT”)—optional and safe for UX, not persisted.

---

## 9. Service Contracts (Frontend Perspective)

> **Blocking**: Exact service names/endpoints are not provided in the inputs. Below is the required contract shape the frontend needs; map to actual Moqui services once confirmed.

### Load/view calls
1. **Get hints by product**
   - Input: `productId`
   - Output: list of `VehicleApplicabilityHint` with `fitmentTags`
2. **Get audit log for hints**
   - Input: `productId` (and optional `hintId`)
   - Output: list of audit events sorted desc by timestamp

### Create/update calls
1. **Create hint**
   - Input: `productId`, `fitmentTags[]`
   - Output: created hint with `hintId`
2. **Update hint**
   - Input: `hintId`, `fitmentTags[]` (and productId if required)
   - Output: updated hint
3. **Delete hint**
   - Input: `hintId`
   - Output: success acknowledgement

### Search/filter calls
1. **Search products by vehicle attributes**
   - Input: `filters` object / list of tags (make/model/year/tire size/axle position)
   - Output: product list (minimum: productId, sku, name/description)

### Error handling expectations
- 400 returns structured validation errors with per-field keys where possible; otherwise a message.
- 403 forbidden for permission failures.
- 404 for missing entities.
- 409 for stale updates (if implemented).

---

## 10. State Model & Transitions

### Allowed states
- VehicleApplicabilityHint lifecycle in this story is CRUD-only (no explicit workflow states provided).

### Role-based transitions
- Product Admin:
  - can create/update/delete hints (permission-gated; exact permission keys TBD).
- Read-only users:
  - can view hints (if allowed) and may view audit (if allowed).

### UI behavior per state
- Create mode: blank form with at least one empty tag row.
- Edit mode: populated tag rows.
- Delete: confirmation modal; on confirm call delete; on success refresh list.

---

## 11. Alternate / Error Flows

### Validation failures
- User adds a tag row but leaves `tagValue` blank → inline error; prevent submit.
- User enters malformed year range → inline error; prevent submit (needs exact rule).

### Concurrency conflicts
- If backend returns 409 on update (hint modified elsewhere):
  - show message “This hint changed since you opened it. Refresh to get the latest.”
  - provide “Refresh” action that reloads hint and resets form.

### Unauthorized access
- User without permission attempts to open create/edit/delete:
  - hide actions if permissions are available client-side
  - if attempted and backend returns 403, show error and remain on page.

### Empty states
- Product has no hints → show “No applicability hints yet” with “Add Hint” action (if permitted).
- Product search by vehicle attributes returns none → show empty results (not an error).

---

## 12. Acceptance Criteria

### Scenario 1: View existing applicability hints on a product
**Given** I am logged in as a Product Administrator  
**And** I am viewing an existing product detail page  
**When** the page loads the “Vehicle Applicability Hints” section  
**Then** I see the list of existing hints for the product  
**And** each hint displays its tags (type and value)

### Scenario 2: Create a new applicability hint with basic tags
**Given** I am logged in as a Product Administrator with permission to manage hints  
**And** I am viewing an existing product  
**When** I add a new hint with tags MAKE=Subaru, MODEL=Outback, YEAR_RANGE=2020-2023  
**And** I click Save  
**Then** the hint is persisted via the backend create service  
**And** the product detail page refreshes to show the new hint  
**And** an audit entry is visible in the audit viewer indicating a create event

### Scenario 3: Update an existing hint
**Given** a product has an existing hint with YEAR_RANGE=2020-2023  
**And** I am editing that hint  
**When** I change YEAR_RANGE to 2020-2024 and save  
**Then** the backend update service is called with the updated tags  
**And** the updated tag is displayed after save  
**And** an audit entry is visible indicating an update event

### Scenario 4: Delete an existing hint
**Given** a product has an existing applicability hint  
**And** I have permission to delete hints  
**When** I choose Delete and confirm  
**Then** the backend delete service is called  
**And** the hint no longer appears in the product’s hint list  
**And** an audit entry is visible indicating a delete event

### Scenario 5: Prevent invalid hint submission
**Given** I am creating a hint  
**When** I attempt to save with a tag missing a type or value  
**Then** the UI blocks submission  
**And** I see an inline validation message identifying the missing field(s)

### Scenario 6: Search products by vehicle attributes
**Given** Product A has tags MAKE=Ford and MODEL=F-150  
**And** Product B has tags MAKE=Toyota and MODEL=Camry  
**When** I search products with filters MAKE=Ford and MODEL=F-150  
**Then** the results include Product A  
**And** the results do not include Product B

### Scenario 7: Search returns empty list (not an error)
**Given** no products have MAKE=Tesla  
**When** I search products with filter MAKE=Tesla  
**Then** I see an empty results state  
**And** no error is shown

---

## 13. Audit & Observability

### User-visible audit data
- Provide an “Audit” view showing:
  - event type (created/updated/deleted)
  - timestamp
  - actor (user)
  - change summary (before/after or tag delta if provided)
- Audit is read-only.

### Status history
- Not applicable (no explicit state machine), but change history must be viewable.

### Traceability expectations
- All CRUD operations should include correlation/trace ID in logs (if frontend has access to request IDs).
- UI should display backend validation messages without losing context (keep user on the same form).

---

## 14. Non-Functional UI Requirements

- **Performance:** Product detail should load hints with one request; avoid per-hint N+1 calls.
- **Accessibility:** All form inputs labeled; validation messages associated to inputs; keyboard navigable tag rows; confirmation dialogs accessible.
- **Responsiveness:** Works on tablet widths used in-store; tag editor usable without horizontal scrolling where possible.
- **i18n/timezone:** Audit timestamps displayed in user locale/timezone; no currency handling required.

---

## 15. Applied Safe Defaults
- Default ID: UI-EMPTY-STATE-001  
  - Assumed: Standard empty state messaging + “Add Hint” CTA when no hints exist.  
  - Why safe: Pure UX; does not affect domain rules or persisted data.  
  - Impacted sections: UX Summary, Alternate / Error Flows, Acceptance Criteria.
- Default ID: UI-LOADING-INDICATOR-001  
  - Assumed: Show loading spinners/disabled actions during service calls.  
  - Why safe: UX ergonomics only; no business policy inferred.  
  - Impacted sections: Functional Behavior, Alternate / Error Flows.
- Default ID: UI-ERROR-MAP-400-001  
  - Assumed: Map HTTP 400/403/404/409 to standard banners/field errors.  
  - Why safe: Standard error-handling; does not define backend policy.  
  - Impacted sections: Business Rules, Service Contracts, Alternate / Error Flows.

---

## 16. Open Questions

1. **Backend contract mapping (blocking):** What are the exact Moqui service names (or REST endpoints) and payload schemas for:
   - list hints by productId, create/update/delete hint, search products by vehicle attributes, and load audit log?
2. **Permissions (blocking):** What permission IDs/roles govern:
   - viewing hints, creating/updating/deleting hints, and viewing audit logs?
3. **Tag model (blocking):** Are tags stored as free-form `key/value` pairs, or as `tagType` constrained to an enum? If enum, what is the authoritative list and display labels?
4. **Year range validation (blocking):** What formats are accepted (e.g., `2020-2023`, separate min/max year fields, inclusive/exclusive), and how should invalid input be reported?
5. **Cardinality (blocking):** Can a product have multiple hints, or exactly one hint with multiple tags? If multiple, is there any uniqueness constraint (e.g., no duplicate tagType within the same hint)?
6. **Audit payload/display (non-blocking if minimal display allowed):** Will the audit API return a delta/before-after snapshot, or only event metadata? What fields are safe to show in UI?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic) — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/108

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/108  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Rules: Store Fitment Hints and Vehicle Applicability Tags (Basic)

**Domain**: user

### Story Description

/kiro  
# User Story  
## Narrative  
As a **Product Admin**, I want fitment hints so that advisors can pick correct parts/tires for a vehicle.

## Details  
- Basic tags: make/model/year ranges, tire size specs, axle position.  
- Hints only (not full fitment engine).

## Acceptance Criteria  
- Fitment tags stored and retrievable.  
- Search/filter by tag.  
- Audited.

## Integrations  
- Workexec can pass vehicle attributes from CRM to filter candidates.

## Data / Entities  
- FitmentTag, VehicleApplicabilityHint, AuditLog

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