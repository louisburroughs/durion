STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** pricing-strict

---

## 1. Story Header

**Title:** [FRONTEND] Pricing: Maintain MSRP per Product with Effective Dates

**Primary Persona:** Pricing Manager

**Business Value:** Maintain auditable, effective-dated MSRP records per product so downstream pricing calculations, reporting, and compliance can reliably reference the correct MSRP for a given date.

---

## 2. Story Intent

### As a / I want / So that
**As a** Pricing Manager  
**I want** to create, update, and view MSRP records for a product with effective start/end dates (including indefinite end dates)  
**So that** the system has a single authoritative MSRP per product at any point in time, with clear history and validation against overlaps.

### In-scope
- Moqui UI screens to:
  - Search/select a product context for MSRP maintenance
  - List MSRP records for a product (effective-dated timeline)
  - Create a new MSRP record
  - Edit an existing MSRP record (subject to immutability/permissions policy)
  - View an MSRP record (read-only)
- Client-side and server-side validation surfacing for:
  - Required fields, date logic, positive amount, currency format
  - Overlapping effective date ranges conflict
  - Product existence validation failures
  - Permission/authorization failures
- Navigation, transitions, and confirmation/error handling patterns consistent with the Moqui frontend

### Out-of-scope
- Defining/altering pricing formulas, taxes, rounding policies, discount precedence
- Product master data management (owned by Inventory domain)
- Bulk import/export of MSRPs
- Approval workflows for MSRP changes (not specified)
- Promotion management, price books, location overrides

---

## 3. Actors & Stakeholders
- **Pricing Manager (primary user):** Maintains MSRP records.
- **Auditor (stakeholder):** Needs to review change history and effective-dated records.
- **Pricing Engine / Downstream consumers (system):** Consume active MSRP by product/date (frontend only surfaces; backend contract required).
- **Inventory domain (external dependency):** System of record for products; MSRP references a `productId` owned elsewhere.

---

## 4. Preconditions & Dependencies
- User is authenticated.
- User has permission to manage MSRP (permission string assumed from pricing guide: `pricing:msrp:manage`; exact mapping to Moqui artifact must exist).
- Backend endpoints exist (or will exist) to:
  - List MSRPs by product
  - Create MSRP
  - Update MSRP
  - Retrieve active MSRP by product/date (for view/testing)
  - Validate product existence (directly or indirectly)
- Inventory product lookup capability exists for selecting `productId` (contract TBD).
- System uses dates as **DATE** (not timestamp) for effective ranges; timezone rule must be confirmed.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main menu: **Pricing → MSRP Maintenance** (new top-level entry under Pricing).
- Optional contextual entry (if product detail screens exist): **Product → Pricing → MSRP** (TBD based on existing frontend IA; do not implement without confirmation).

### Screens to create/modify
Create:
1. **`apps/pos/pricing/msrp/ProductMsrpList.xml`** (screen)
   - Product selection context + list of MSRP records for selected product.
2. **`apps/pos/pricing/msrp/ProductMsrpDetail.xml`** (screen)
   - View/create/edit MSRP record.

Supporting components/forms:
- Form: `findProductMsrp` (filters by productId; may include effective date filter)
- Grid/list: `productMsrpList`
- Form: `createProductMsrp`
- Form: `updateProductMsrp`

### Navigation context
- `ProductMsrpList` is the hub.
- From list:
  - “Create MSRP” → `ProductMsrpDetail` in create mode with `productId` prefilled.
  - Select a record → `ProductMsrpDetail` view/edit mode with `msrpId`.
- Breadcrumbs:
  - Pricing / MSRP / <Product> / <MSRP effective start>

### User workflows
**Happy path A (create time-bound MSRP)**
1. Pricing Manager opens MSRP Maintenance.
2. Selects product (via lookup/search).
3. Clicks “Create MSRP”.
4. Enters amount, currency, start date, optional end date.
5. Submits; sees success message and returned record appears in list.

**Happy path B (create indefinite MSRP)**
- Same as A, but end date left blank; UI clearly labels as “No end date (indefinite)”.

**Happy path C (edit MSRP)**
1. Open product MSRP list.
2. Open an MSRP record.
3. Edit allowed fields and save.
4. Returns to detail with updated audit metadata.

**Alternate path (view-only historical)**
- If record is historical and editing is disallowed, detail screen renders fields read-only with reason.

---

## 6. Functional Behavior

### Triggers
- User navigates to MSRP Maintenance menu item.
- User selects a product.
- User creates/edits an MSRP record.

### UI actions
- **Product selection**
  - Provide a product picker/lookup to populate `productId`.
  - On selection, load MSRP list for that product.
- **List MSRPs**
  - Show effective start/end, amount, currency, status indicator (derived: Active/Upcoming/Expired based on “today” and date range).
- **Create MSRP**
  - Open create form with `productId` locked (to prevent accidental cross-product creation).
- **Edit MSRP**
  - Open edit form for selected record.
  - Enforce read-only constraints if record is immutable or user lacks elevated permission.

### State changes (UI-level)
- After successful create/update:
  - Refresh MSRP list for product
  - Navigate to detail (or remain and show updated values) deterministically:
    - Create: navigate to detail view for new `msrpId`
    - Update: stay on detail, show toast/banner “Saved”
- On validation errors:
  - Keep user on form, highlight fields, show actionable messages.
- On conflict (overlap):
  - Show conflict banner “Overlapping effective dates exist for this product” and optionally link back to list filtered by date (if supported).

### Service interactions (frontend invoking backend)
- Load product lookup data (Inventory-owned; endpoint TBD)
- Load MSRP list by productId
- Create MSRP
- Update MSRP
- (Optional for screen) Load active MSRP for date filter preview (endpoint TBD)

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- **Required fields (create):**
  - `productId` (required; selected via lookup)
  - `amount` (required; must be > 0)
  - `currency` (required; must be 3-letter ISO 4217)
  - `effectiveStartDate` (required)
  - `effectiveEndDate` (optional)
- **Date logic:**
  - If `effectiveEndDate` provided, must be `>= effectiveStartDate` (same day allowed).
- **Overlap prevention:**
  - UI should pre-validate by showing existing ranges (informational), but authoritative validation happens server-side.
  - On server `409 Conflict` overlap error: display non-field error and keep form open.
- **Indefinite end date rule (BR2 in backend reference):**
  - If backend enforces “null end date must be latest-starting record”, UI must surface backend error clearly. (Exact error code/message mapping TBD.)

### Enable/disable rules
- If record is deemed **historical immutable** by backend policy:
  - Disable Save and make fields read-only (except maybe notes; not defined).
  - Show message: “This MSRP ended in the past and cannot be edited.”
- If user lacks permission:
  - Hide Create/Edit actions, show read-only list.
  - If user deep-links into edit, show 403 handling (see error flows).

### Visibility rules
- Show audit metadata (created/updated timestamps and users) on detail screen.
- Show computed “Status” chip/label:
  - `Active` if today within start/end (or end null)
  - `Upcoming` if today < start
  - `Expired` if end < today

### Error messaging expectations
- Field-level errors for missing/invalid input.
- Banner-level errors for:
  - Overlap conflicts
  - Product not found
  - Authorization failures
  - Backend unavailable / timeout

---

## 8. Data Requirements

### Entities involved
- **Pricing domain entity:** `ProductMSRP` (authoritative for MSRP records)
- **External reference:** Product (Inventory-owned) referenced by `productId`

### Fields
`ProductMSRP` (frontend model)
- `msrpId` (UUID, read-only, required for edit/view)
- `productId` (UUID, required; editable on create only; read-only on edit)
- `amount` (DECIMAL(19,4), required, > 0)
- `currency` (string(3), required, ISO 4217)
- `effectiveStartDate` (DATE, required)
- `effectiveEndDate` (DATE, optional; null = indefinite)
- `createdAt` (timestamp, read-only)
- `updatedAt` (timestamp, read-only)
- `updatedBy` (string, read-only)

Derived (UI-only; not persisted)
- `status` enum: `ACTIVE | UPCOMING | EXPIRED` (derived from effective dates + “today”)
- `isHistorical` boolean (derived; end date < today)

### Read-only vs editable by state/role
- Create:
  - Editable: productId (via picker), amount, currency, start/end dates
- Edit:
  - productId always read-only
  - amount/currency/dates editable unless backend indicates immutable or permission denies
- View:
  - all read-only

---

## 9. Service Contracts (Frontend Perspective)

> Backend contracts are not present in the provided frontend issue; the below names are placeholders and must be confirmed against actual Moqui service names / REST routes in this project.

### Load/view calls
1. **List MSRPs by product**
   - Request: `GET /api/pricing/msrp?productId=<uuid>`
   - Response: `200` list of `ProductMSRP`
2. **Get MSRP by id**
   - Request: `GET /api/pricing/msrp/<msrpId>`
   - Response: `200` `ProductMSRP`, `404` if not found
3. **(Optional) Get active MSRP by product/date**
   - Request: `GET /api/pricing/msrp/active?productId=<uuid>&date=YYYY-MM-DD`
   - Response: `200` single `ProductMSRP` or `404`/`204` if none (TBD)

### Create/update calls
4. **Create MSRP**
   - Request: `POST /api/pricing/msrp`
   - Body: `{ productId, amount, currency, effectiveStartDate, effectiveEndDate? }`
   - Responses:
     - `201` created entity (including `msrpId`)
     - `400` validation errors (field errors)
     - `404` or `400` product not found (TBD)
     - `409` overlap conflict
     - `403` forbidden
5. **Update MSRP**
   - Request: `PUT /api/pricing/msrp/<msrpId>` (or `PATCH`; TBD)
   - Body: `{ amount, currency, effectiveStartDate, effectiveEndDate? }`
   - Responses:
     - `200` updated entity
     - `400` validation
     - `403` forbidden
     - `404` not found
     - `409` overlap conflict
     - `409` immutable/historical conflict (could also be `403`; TBD)

### Error handling expectations
- Frontend must map:
  - `400` → show field errors if structured, else banner
  - `403` → show “You do not have permission to manage MSRPs.”
  - `404` → show “Record not found” (and route back to list)
  - `409` → show overlap/immutability conflict message (banner)
  - `5xx`/network → show retry option and preserve form values

---

## 10. State Model & Transitions

### Allowed states (UI-derived; entity itself may not have a status field)
- `UPCOMING`: effectiveStartDate in future
- `ACTIVE`: today within effective range
- `EXPIRED`: effectiveEndDate in past

### Role-based transitions
- User with `pricing:msrp:manage`:
  - Can create MSRP
  - Can update MSRP unless historical immutability enforced
- User without permission:
  - Can view list/detail only (if screen accessible at all; auth gating TBD)

### UI behavior per state
- UPCOMING: editable (unless overridden by policy)
- ACTIVE: editable (unless restricted by policy)
- EXPIRED: default read-only; editing depends on “Historical Immutability” policy (TBD)

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields: inline errors; Save disabled until valid (client-side), but server remains authoritative.
- End date before start date: inline error; block submission.

### Concurrency conflicts
- If backend returns optimistic-lock style error (e.g., version mismatch):
  - Show banner “This MSRP was updated by another user. Reload to continue.”
  - Provide “Reload” action; warn unsaved changes will be lost.  
  *(Exact mechanism/version field not provided; may require backend support.)*

### Unauthorized access
- User navigates to create/edit without permission:
  - Show 403 page/banner and hide editing controls.
  - If on detail, render read-only and show permission message.

### Empty states
- No product selected:
  - Show instructions “Select a product to view MSRPs.”
- Product selected but no MSRP records:
  - Show empty-state with CTA “Create MSRP”.

### Dependency failures
- Product lookup fails (Inventory unavailable):
  - Disable product picker; show error and “Retry”.

---

## 12. Acceptance Criteria

### Scenario 1: View MSRP list for a product
**Given** I am authenticated  
**And** I have `pricing:msrp:manage` permission  
**When** I navigate to Pricing → MSRP Maintenance  
**And** I select a product with id `<productId>`  
**Then** I see a list of MSRP records for `<productId>` including amount, currency, effective start, effective end (or “indefinite”)  
**And** each row shows a derived status of Active/Upcoming/Expired based on today’s date.

### Scenario 2: Create a time-bound MSRP successfully
**Given** I am on the MSRP list for product `<productId>`  
**And** no existing MSRP overlaps 2025-01-01 through 2025-12-31  
**When** I choose Create MSRP  
**And** I enter amount `99.99`, currency `USD`, start date `2025-01-01`, end date `2025-12-31`  
**And** I submit  
**Then** the system creates the MSRP successfully  
**And** I see a success confirmation  
**And** the new MSRP appears in the product’s MSRP list.

### Scenario 3: Create an indefinite MSRP successfully
**Given** I am on the MSRP list for product `<productId>`  
**When** I choose Create MSRP  
**And** I enter amount `150.00`, currency `EUR`, start date `2026-01-01`  
**And** I leave end date blank  
**And** I submit  
**Then** the system creates the MSRP successfully  
**And** the list displays the end date as “indefinite”.

### Scenario 4: Prevent invalid date logic in the UI
**Given** I am creating or editing an MSRP  
**When** I set effective start date to `2025-12-31`  
**And** I set effective end date to `2025-01-01`  
**Then** the Save action is disabled (or submission blocked)  
**And** I see an inline error that end date must be on/after start date.

### Scenario 5: Show overlap conflict returned by backend
**Given** an MSRP already exists for product `<productId>` from `2025-01-01` to `2025-12-31`  
**When** I attempt to create a new MSRP for `<productId>` with start date `2025-06-01`  
**Then** the backend responds with a conflict (HTTP 409)  
**And** the UI shows a non-field error indicating the effective dates overlap  
**And** my entered values remain in the form for correction.

### Scenario 6: Handle product not found during create
**Given** I attempt to create an MSRP with a productId that does not exist  
**When** I submit the form  
**Then** I see an error message indicating the product is invalid/not found  
**And** no MSRP record is created.

### Scenario 7: Permission enforcement in UI
**Given** I am authenticated  
**But** I do not have `pricing:msrp:manage` permission  
**When** I navigate to MSRP Maintenance  
**Then** I can view MSRP lists/details (if allowed)  
**And** I do not see Create or Edit actions  
**And** if I attempt to access a create/edit URL directly, I see a forbidden message and cannot save changes.

---

## 13. Audit & Observability

### User-visible audit data
- Detail screen shows:
  - createdAt, updatedAt, updatedBy (as provided by backend)
- List shows:
  - last updated timestamp (optional; if available)

### Status history
- UI supports viewing multiple records per product as the effective-dated “history”.
- No separate “history log” is created in this story.

### Traceability expectations
- Frontend logs (browser console in dev) should include correlation/request IDs if returned by backend headers (e.g., `X-Correlation-Id`) and surface them in error dialogs for support (header name TBD).
- Moqui server logs are out-of-scope for frontend, but UI must not drop backend error identifiers if provided.

---

## 14. Non-Functional UI Requirements
- **Performance:** MSRP list load should feel instantaneous for typical volumes; show loading indicator and avoid blocking UI.
- **Accessibility:** WCAG 2.1 AA with keyboard navigation for forms and tables; errors announced via ARIA live region where applicable.
- **Responsiveness:** Works on tablet widths commonly used in POS back office; list/table adapts (e.g., stacked rows).
- **i18n/timezone/currency:**
  - Currency shown as code (USD) at minimum; locale formatting may be applied if project has a standard.
  - Effective dates displayed in user locale but submitted as `YYYY-MM-DD`.
  - Timezone defining “today” must be clarified (UTC vs store local).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide standard empty-state messaging and CTA when no MSRP records exist; safe UI ergonomics default with no domain impact. (Impacted sections: UX Summary, Error Flows)
- SD-UX-LOADING-ERROR: Standard loading spinners and retry on network failure; safe error-handling boilerplate. (Impacted sections: Service Contracts, Alternate/Error Flows)
- SD-UX-PRESERVE-FORM: Preserve user-entered values on server-side validation/conflict errors; safe UX behavior. (Impacted sections: Alternate/Error Flows, Acceptance Criteria)

---

## 16. Open Questions
1. **Inventory product lookup contract (blocking):** What is the frontend-supported way to select/validate `productId` (Moqui entity sync, REST endpoint, or existing product search screen)? Provide route/service name and minimal product fields for display (SKU/name).  
2. **Historical immutability policy (blocking):** Are MSRP records with `effectiveEndDate` in the past editable at all? If yes, what permission is required and what UI behavior should occur (warn + allow, or block)?  
3. **Indefinite end-date rule details (blocking):** Confirm BR2 “null end date only if latest-starting record” is enforced. If a user tries to create an indefinite record while a later-starting record exists, what error code/message should be shown?  
4. **Date “today” timezone (blocking):** Are effective dates evaluated in UTC, store local time, or user locale? This affects derived status labels and validations.  
5. **Backend endpoint shapes (blocking):** Confirm actual endpoints (or Moqui service names) and error payload format for field errors and conflicts so the frontend can map errors deterministically.  
6. **Authorization model (blocking):** Confirm permission string(s) and whether read-only viewing requires a separate permission or is open to any authenticated user.

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Pricing: Maintain MSRP per Product with Effective Dates
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/259
Labels: frontend, story-implementation, type:story, layer:functional, kiro

## 🤖 Implementation Issue - Created by Durion Workspace Agent

### Original Story
**Story**: #198 - Pricing: Maintain MSRP per Product with Effective Dates
**URL**: https://github.com/louisburroughs/durion/issues/198
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
*Generated by Missing Issues Audit System - 2025-12-26T17:37:19.493664193*