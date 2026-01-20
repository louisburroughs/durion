STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:workexec
- status:draft

### Recommended
- agent:workexec-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** workexec-structured

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Estimate: Add Parts to Estimate (Catalog + Non-Catalog + Price Override)

### Primary Persona
Service Advisor

### Business Value
Enable Service Advisors to build accurate, auditable draft estimates by adding parts (from catalog or non-catalog where allowed), applying pricing defaults, enforcing override permissions/policies, and ensuring totals are recalculated consistently.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want to** search for parts and add them as line items to a Draft estimate (including optional price overrides and controlled non-catalog entry)  
- **So that** I can produce a complete quote for the customer with correct totals and an audit trail of changes.

### In-scope
- Catalog part search (by part number, description, category) and selection.
- Add a part line item to an **Estimate in `DRAFT`** state with quantity validation.
- Default unit price retrieval and display (pricing authority).
- Optional price override flow with permission gating and optional reason code requirement.
- Non-catalog part entry flow (policy-gated) with required description and manual unit price.
- Immediate refresh of estimate totals after add/edit.
- User-visible audit surfacing (who/when for line changes) if available in backend responses; otherwise show “last updated” at estimate level.

### Out-of-scope
- Configuring price lists, discount/markup rules, tax policies, override reason code lists (configuration ownership outside this story).
- Creating/editing labor line items (parts only).
- Estimate approval / signature capture workflows.
- Work order creation/promotion from estimate.
- Inventory allocation, picking, or consumption.

---

## 3. Actors & Stakeholders

- **Primary Actor:** Service Advisor
- **Secondary Actors:** Service Manager (policy/permission owner), Parts Manager (catalog data quality)
- **System Actors / Dependencies:**
  - Inventory/Catalog service (product search & product details)
  - Pricing service (unit price + discounts/markups)
  - Workexec service (estimate + estimate items + totals + audit)
  - Security/Authorization (permission checks)

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in POS frontend.
- An **Estimate** exists and is accessible to the user.
- Estimate status is **`DRAFT`** for add/edit operations.

### Dependencies (blocking if unavailable)
- Backend endpoints for:
  - Loading estimate + items + totals
  - Searching catalog products
  - Adding/updating estimate part items
  - Pricing calculation for selected catalog item (or included in add response)
  - Permission/policy evaluation (override permission; allow non-catalog; require reason)
- Reason codes source (if required) must be queryable or embedded in configuration payload.

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From Estimate detail screen: “Add Part” action within the Parts section.

### Screens to create/modify (Moqui)
1. **Modify** `EstimateDetail` screen (or equivalent) to include:
   - Parts line item table/list
   - “Add Part” button (enabled only when estimate is `DRAFT`)
2. **Create** `EstimateAddPart` dialog/screen (modal-style):
   - Tab/section: **Catalog Part**
   - Tab/section: **Non-Catalog Part** (visible only if policy allows)
3. **Create/Modify** `EstimatePartEdit` inline row editor or modal for:
   - Quantity edits
   - Price override (if permitted)

### Navigation context
- Remain within the estimate context (`/estimate/:estimateId` style route in SPA; Moqui screen parameters `estimateId`).
- After adding a part: return to Estimate detail with updated list and totals; highlight the newly added item.

### User workflows
#### Happy path: add catalog part
1. Service Advisor opens Draft estimate.
2. Clicks “Add Part”.
3. Searches catalog; selects a part.
4. Enters quantity (>0).
5. Confirms “Add”.
6. UI shows new line item and refreshed totals.

#### Alternate: override price
1. After line added (or during add if supported), user attempts to edit unit price.
2. If permission granted: user enters override price, selects reason code if required, saves.
3. Totals refresh; audit displayed (if available).

#### Alternate: non-catalog part
1. Search returns no results (or user switches tab).
2. If policy enabled: user enters description, optional part number, quantity, unit price.
3. Saves; totals refresh.

---

## 6. Functional Behavior

### Triggers
- User clicks “Add Part” on a Draft estimate.
- User changes quantity or requests price override on an existing part line item.

### UI actions
- Search input with debounce and explicit “Search” action (avoid excessive calls).
- Select product from results -> populate read-only part info (part number, description).
- Quantity input with validation.
- “Add” submits create call; “Cancel” closes without changes.
- Row actions per part line item (when `DRAFT`):
  - Edit quantity
  - Override price (permission-gated)
  - (Delete not requested; do not implement unless already present)

### State changes (frontend-visible)
- Line item list updates (new item appears with stable identifier).
- Totals panel refreshes (subtotal/tax/grand total as provided by backend).
- If estimate is not `DRAFT`: disable add/edit controls and show explanatory banner.

### Service interactions (high-level)
- Load estimate details on screen entry.
- Product search calls while in Add Part workflow.
- Pricing retrieval:
  - Preferred: backend create-item response includes pricing fields and totals.
  - If not: call pricing endpoint prior to create to show computed unit price (requires contract).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation rules
- **Estimate status gate:** If estimate status != `DRAFT`, block add/edit actions and show:  
  “This estimate can’t be modified because it is <STATUS>.”
- **Quantity:** must be numeric and **> 0**. Error: “Quantity must be a number greater than 0.”
- **Catalog selection:** productId required for catalog add.
- **Non-catalog policy gate:** non-catalog form visible/enabled only if `ALLOW_NON_CATALOG_PARTS=true`.
- **Non-catalog required fields:** description required (non-empty), unitPrice required, quantity > 0.
- **Price override permission:** if user lacks permission, disable price editing and show tooltip/help text; if attempted via direct input, show:  
  “You do not have permission to override prices.”
- **Override reason code:** if policy `REQUIRE_REASON_FOR_OVERRIDE=true`, require selection before saving override; error: “A reason code is required for all price overrides.”

### Enable/disable rules
- “Add Part” button enabled only when estimate status is `DRAFT`.
- “Non-Catalog Part” tab enabled only when policy allows.
- Price override control enabled only when:
  - estimate status is `DRAFT`
  - user has override permission

### Visibility rules
- Show “Override reason” field only when:
  - override is being applied AND policy requires reason code

### Error messaging expectations
- Validation errors should be field-level + a top-level summary.
- Backend errors mapped to user-friendly messages (see Error Flows).

---

## 8. Data Requirements

### Entities involved (frontend perspective)
- `Estimate`
- `EstimateItem` (parts line items)
- `Product` (catalog)
- `ApprovalConfiguration` / policy source (for method; here needed for non-catalog + override reason requirement) **(contract unclear)**
- Audit event/history entity if exposed (`AuditEvent` or line-item change log) **(contract unclear)**

### Fields

#### Estimate (read)
- `estimateId` (string/UUID) **required**
- `status` (enum: `DRAFT`, `APPROVED`, `DECLINED`, `EXPIRED`) **required**
- Totals (backend-calculated; types depend on API):
  - `subtotal` (money) required
  - `taxTotal` (money) required
  - `grandTotal` (money) required
- Optional:
  - `currencyUomId` (string) (if multi-currency)
  - `lastUpdatedStamp` (datetime)

#### EstimateItem (parts) (read/write)
- Identifiers:
  - `estimateItemId` (string/UUID) **required**
  - `estimateId` **required**
  - `itemSeqId` (number) read-only
- Classification:
  - `itemType` = `PART` (read-only)
  - `isNonCatalog` (boolean) read-only
- Catalog fields:
  - `productId` (string/UUID) required for catalog, null for non-catalog
  - `partNumber` (string) optional (required for catalog if provided by product)
- Common fields:
  - `description` (string) required
  - `quantity` (decimal) required, >0
  - `unitPrice` (money) read-only if pricing authority; editable only through override flow
  - `overrideUnitPrice` (money) optional
  - `overrideReasonCode` (string) optional/required-by-policy
  - `taxCodeSnapshot` (string) read-only (source unclear)

### Read-only vs editable (by state/role)
- When estimate status != `DRAFT`: all estimate items are read-only in UI.
- When `DRAFT`:
  - quantity editable
  - overrideUnitPrice editable only with permission
  - non-catalog fields editable only at create time unless backend supports edits (not specified)

### Derived/calculated fields
- Line extended price = quantity * effective unit price (unitPrice or overrideUnitPrice) (display-only; do not calculate as authority—prefer backend values if provided).
- Totals always treated as backend-authoritative.

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend API paths for estimates are defined in `CUSTOMER_APPROVAL_WORKFLOW.md` but “add parts” endpoints are **not explicitly defined** there. This story requires clarification on the actual endpoints in Moqui/REST.

### Load/view calls
- `GET /api/estimates/{id}`  
  **Returns:** estimate header, status, totals, line items (or separate call).
- If line items are separate:
  - `GET /api/estimates/{id}/items?itemType=PART`

### Product search calls
- `GET /api/products/search?query=&category=&partNumber=` (example)  
  **Returns:** list of products with `productId`, `partNumber`, `description`.

### Create/update calls
One of the following patterns must be confirmed:

**Option A (preferred):**
- `POST /api/estimates/{id}/items/parts`
  ```json
  {
    "productId": "…",
    "quantity": 2
  }
  ```
  For non-catalog:
  ```json
  {
    "isNonCatalog": true,
    "description": "…",
    "partNumber": "optional",
    "quantity": 1,
    "unitPrice": 49.99
  }
  ```
  **Response:** created `EstimateItem` + updated totals.

**Option B:**
- `PUT /api/estimates/{id}` with embedded items patch (less ideal for concurrency).

### Price override calls
- `POST /api/estimates/{estimateId}/items/{estimateItemId}/override-price`
  ```json
  { "overrideUnitPrice": 39.99, "overrideReasonCode": "PRICE_MATCH" }
  ```
  **Response:** updated item + updated totals.

### Error handling expectations
- `400` validation errors: show field-level errors.
- `403` forbidden: show permission/policy message.
- `404` not found: show “Estimate not found” / “Part not found”.
- `409` conflict: show “This estimate was updated by someone else. Refresh to continue.” and provide refresh action.

---

## 10. State Model & Transitions

### Estimate states (authoritative from workexec rules)
- `DRAFT`
- `APPROVED`
- `DECLINED`
- `EXPIRED`

### Allowed transitions (not implemented here, but gate UI)
- Only `DRAFT` is editable for adding/modifying parts.

### Role-based transitions (UI enforcement for this story)
- Service Advisor can add/edit parts only in `DRAFT`.
- Price override requires explicit permission (exact permission string TBD).

### UI behavior per state
- `DRAFT`: enable add part, quantity edit, and price override if permitted.
- `APPROVED`/`DECLINED`/`EXPIRED`: read-only; show banner indicating status and that edits are disabled.

---

## 11. Alternate / Error Flows

### Validation failures
- Quantity <= 0: block submit; show inline error.
- Non-catalog without description: block; error “Description is required.”
- Override without reason when required: block; error “A reason code is required…”

### Concurrency conflicts
- If backend returns `409` on add/update due to stale version:
  - UI shows blocking dialog with “Refresh estimate” action.
  - Refresh reloads estimate + items; user can retry.

### Unauthorized access
- If `403` on override action:
  - Keep original price displayed.
  - Show toast/banner: “You do not have permission to override prices.”

### Empty states
- No parts yet: show empty state “No parts added” with “Add Part” CTA (only in `DRAFT`).
- Search returns no results:
  - Show “No parts found.”
  - If non-catalog policy enabled: show action “Add non-catalog part.”

---

## 12. Acceptance Criteria

### Scenario 1: Add a catalog part line item to a Draft estimate
**Given** I am authenticated as a Service Advisor  
**And** I am viewing an estimate with status `DRAFT`  
**When** I open “Add Part” and search by part number or description  
**And** I select a catalog part  
**And** I enter a quantity of `2`  
**And** I confirm adding the part  
**Then** a new part line item is added to the estimate  
**And** the line item shows the selected part’s description/part number and quantity `2`  
**And** the unit price shown matches the backend/pricing authority response  
**And** the estimate totals are refreshed to the backend-calculated values.

### Scenario 2: Block adding a part when estimate is not Draft
**Given** I am viewing an estimate with status `APPROVED`  
**When** I attempt to add a part  
**Then** the UI prevents the action (button disabled or blocked)  
**And** I see a message indicating the estimate cannot be modified in `APPROVED` status  
**And** no create-item service call is executed.

### Scenario 3: Reject invalid quantity
**Given** I am viewing an estimate with status `DRAFT`  
**When** I attempt to add a catalog part with quantity `0`  
**Then** the system prevents submission  
**And** I see the validation message “Quantity must be a number greater than 0”  
**And** no create-item service call is executed.

### Scenario 4: Add a non-catalog part when policy allows
**Given** I am viewing an estimate with status `DRAFT`  
**And** the policy `ALLOW_NON_CATALOG_PARTS` is enabled  
**When** I choose “Add non-catalog part”  
**And** I enter description “Brake pad hardware kit”, quantity `1`, unit price `19.99`  
**Then** a new line item is added with `isNonCatalog=true`  
**And** the estimate totals are refreshed to the backend-calculated values.

### Scenario 5: Hide/disable non-catalog entry when policy disallows
**Given** I am viewing an estimate with status `DRAFT`  
**And** the policy `ALLOW_NON_CATALOG_PARTS` is disabled  
**When** my catalog search returns no results  
**Then** I do not see an option to add a non-catalog part  
**And** I only see the “No parts found” empty search result.

### Scenario 6: Override price with permission and required reason
**Given** I am viewing an estimate with status `DRAFT`  
**And** I have permission to override prices  
**And** the policy `REQUIRE_REASON_FOR_OVERRIDE` is enabled  
**And** the estimate has a part line item  
**When** I set an override unit price  
**And** I select an override reason code  
**And** I save the override  
**Then** the line item reflects the overridden price  
**And** the reason code is stored/shown (where available)  
**And** the estimate totals refresh to backend-calculated values.

### Scenario 7: Block override price without permission
**Given** I am viewing an estimate with status `DRAFT`  
**And** I do not have permission to override prices  
**When** I attempt to edit the unit price on a part line item  
**Then** the UI blocks the edit  
**And** I see the message “You do not have permission to override prices”  
**And** no override service call is executed.

### Scenario 8: Audit visibility after add/edit (if provided)
**Given** I added a part line item to a Draft estimate  
**When** the add call succeeds  
**Then** the UI displays the updated “last modified” metadata for the estimate or line item (user + timestamp) if included in the response  
**And** the UI does not display internal-only audit fields not intended for customer view.

---

## 13. Audit & Observability

### User-visible audit data
- Display (if available from API):
  - Estimate “Last updated at/by”
  - For each part line item: “Added by/at” and “Last modified by/at”
- Do **not** display sensitive/internal audit payloads.

### Status history
- Not required to implement estimate state history view; only enforce edit gating by current status.

### Traceability expectations
- Every successful create/update should include or propagate a correlation/request id (e.g., `X-Request-Id`) to logs (frontend console logger) and to backend headers when supported.
- UI should store the `estimateItemId` and use it for subsequent edits to ensure stable identifiers.

---

## 14. Non-Functional UI Requirements

- **Performance:** product search should debounce (e.g., 300–500ms) and show loading state; results paginated if backend supports.
- **Accessibility:** all form inputs have labels, errors are announced (aria-live), keyboard navigation works in modal/dialog.
- **Responsiveness:** add-part dialog usable on tablet; results list scrollable.
- **i18n/timezone/currency:** display money using location/estimate currency formatting if provided; timestamps shown in user’s locale but stored as UTC from backend.

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE: Provide standard empty state messaging and CTA for “No parts added” / “No search results”; qualifies as safe because it does not change domain behavior. Impacted sections: UX Summary, Alternate/Empty states.
- SD-UX-DEBOUNCE-SEARCH: Debounce catalog search input to reduce request volume; safe because it only affects ergonomics and not business rules. Impacted sections: Functional Behavior, Non-Functional.
- SD-ERR-HTTP-MAP: Map standard HTTP codes (400/403/404/409/5xx) to user messages; safe because it follows implied backend contract without inventing policies. Impacted sections: Error Flows, Service Contracts.

---

## 16. Open Questions

1. **Backend API endpoints (blocking):** What are the exact Moqui/REST endpoints for:
   - Searching products (catalog)
   - Adding a catalog part line item to an estimate
   - Adding a non-catalog part line item
   - Updating quantity
   - Performing a price override (and persisting reason codes)
2. **Policy & permission contracts (blocking):**
   - What is the exact permission identifier for price overrides (e.g., `workexec.estimate.overridePrice`) and how should frontend check it (claims in session? endpoint?)?
   - How does frontend retrieve policy flags `ALLOW_NON_CATALOG_PARTS` and `REQUIRE_REASON_FOR_OVERRIDE` (estimate payload, shop config endpoint, or separate service)?
3. **Override reason codes source (blocking if required):** If reason codes are required, where does the UI fetch the valid list (endpoint and schema)? Is it location-specific?
4. **Tax fields (blocking for display correctness):** Is `taxCodeSnapshot` returned for items, and are line-level taxes returned? Should UI display tax per line or only totals?
5. **Edit vs add pricing flow (clarification):** Should unit price be shown before adding the catalog item (requires pricing lookup pre-create), or is it acceptable to add item first and then display returned price?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Estimate: Add Parts to Estimate  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/238  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Estimate: Add Parts to Estimate

**Domain**: user

### Story Description

/kiro
Produce implementation-ready acceptance criteria, validations, and edge cases. Keep Moqui state transitions and audit requirements explicit.

# Functional Requirement

## Classification (confirm labels)
- Type: story
- Layer: functional
- Domain: workexec

## Actor
Service Advisor

## Trigger
A Draft estimate exists and parts need to be quoted.

## Main Flow
1. User searches parts catalog by part number, description, or category.
2. User selects a part and specifies quantity.
3. System defaults unit price from configured price list and applies discounts/markups as configured.
4. User optionally overrides price if permitted and provides a reason code if required.
5. System adds the part line item and recalculates totals.

## Alternate / Error Flows
- Part not found → allow controlled non-catalog part entry (if enabled) with mandatory description.
- Quantity invalid (<=0) → block and prompt correction.
- Price override not permitted → block and show policy message.

## Business Rules
- Each part line item references a part number (or controlled non-catalog identifier).
- Totals must be recalculated on line change.
- Price overrides require permission and may require reason codes.

## Data Requirements
- Entities: Estimate, EstimateItem, Product, PriceList, DiscountRule, AuditEvent
- Fields: itemSeqId, productId, partNumber, quantity, unitPrice, discountAmount, taxCode, isNonCatalog, overrideReason

## Acceptance Criteria
- [ ] User can add a catalog part line item to a Draft estimate.
- [ ] Totals update immediately after adding or editing part items.
- [ ] Audit records who changed quantity/price.

## Notes for Agents
Model part items so later promotion preserves stable identifiers and tax snapshots.

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