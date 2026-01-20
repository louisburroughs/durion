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

### Title
[FRONTEND] [STORY] Promotions: Create Promotion Offer (Basic)

### Primary Persona
Account Manager

### Business Value
Enable Account Managers to create and manage simple promotions (percent off labor/parts or fixed amount off invoice) with valid date ranges and unique codes so downstream estimate/work order flows can retrieve applicable active promotions.

---

## 2. Story Intent

### As a / I want / So that
- **As an** Account Manager  
- **I want** to create a basic Promotion Offer with code, type, value, store scope, start/end dates, and optional usage limit; and activate/deactivate it  
- **So that** eligible customers can have that promotion applied to estimates/work orders when the promotion is active and within its valid dates

### In-scope
- Promotion Offer create (draft)
- Promotion Offer view (details)
- Promotion Offer activate / deactivate actions
- Frontend validations that are deterministic from rules (date range, required fields, non-negative numeric values)
- Handling backend uniqueness errors for promotion code
- Routing/navigation and Moqui screen/form/service wiring for the UI

### Out-of-scope
- Defining or editing promotion eligibility rules (customer eligibility model not specified)
- Applying promotions to estimates/work orders (consumption use-case)
- Promotion stacking/combination logic
- Approval workflows or guardrails (not described for promotions)
- Bulk import/export of promotions

---

## 3. Actors & Stakeholders
- **Account Manager (primary user):** creates and manages promotions.
- **Pricing domain maintainers:** ensure promotions are created with correct constraints and are auditable.
- **Work Execution (downstream consumer):** queries active promotions (integration point; UI not implementing this query).
- **Store/Location managers (stakeholder):** care about store-scoped promotions.
- **Compliance/Audit (stakeholder):** expects status changes to be traceable.

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated in the frontend.
- User has permission to manage promotions (exact permission string(s) **TBD**, see Open Questions).

### Dependencies
- Moqui backend endpoints/services exist (or will be implemented) to:
  - Create PromotionOffer in `DRAFT`
  - Fetch PromotionOffer by ID
  - Transition status to `ACTIVE` / `INACTIVE` (and possibly compute `EXPIRED`)
  - Enforce unique `promotionCode`
  - Validate date range and numeric constraints server-side

### Cross-domain dependencies (read-only)
- Store/location reference data for `storeCode` selection (source of truth **TBD**).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Primary: POS backoffice → Pricing/Promotions → “Create Promotion”
- Secondary: Promotions list (if exists) → “New Promotion” button  
  (If list screen does not exist yet, link from a generic Pricing dashboard screen.)

### Screens to create/modify
1. **`PromotionOfferList`** (create if missing; minimal list to access “Create” and view existing)
2. **`PromotionOfferCreate`** (new)
3. **`PromotionOfferDetail`** (new or extend existing) with activate/deactivate actions

> Screen path naming is project-convention dependent (README not provided here). Implementer should place screens under the pricing/promotions area consistent with repo patterns.

### Navigation context
- After successful create: redirect to `PromotionOfferDetail?promotionOfferId=<id>`
- From detail: provide “Back to Promotions” link to list

### User workflows
#### Happy path: create draft
1. Account Manager opens Create Promotion screen.
2. Enters: code, description, store code (or selects “All stores” if supported), start/end dates, promotion type, promotion value, optional usage limit.
3. Clicks “Create”.
4. System creates promotion in `DRAFT`, shows detail screen.

#### Happy path: activate
1. On detail screen for `DRAFT` or `INACTIVE` promotion, user clicks “Activate”.
2. System transitions status to `ACTIVE` if allowed.
3. UI reflects updated status and disables/enables buttons accordingly.

#### Happy path: deactivate
1. On detail screen for `ACTIVE` promotion, user clicks “Deactivate”.
2. System transitions status to `INACTIVE`.
3. UI reflects updated status.

#### Alternate: validation failure
- Inline field errors for client-side validations; server-side errors shown as form-level messages and field-level messages when mapped.

---

## 6. Functional Behavior

### Triggers
- Navigation to Create screen
- Submit Create form
- Click Activate / Deactivate on Detail screen
- Navigation to Detail screen (load by ID)

### UI actions
#### Create screen actions
- `Create` button submits form.
- `Cancel` returns to list without saving.

#### Detail screen actions
- `Activate` button (visible/enabled when status allows)
- `Deactivate` button (visible/enabled when status allows)
- Read-only display of immutable fields (see Data Requirements)

### State changes
- On create: status set to `DRAFT` (backend authoritative)
- On activate: `DRAFT` → `ACTIVE` or `INACTIVE` → `ACTIVE`
- On deactivate: `ACTIVE` → `INACTIVE`
- `EXPIRED` handling: UI must treat as non-activatable; how status becomes `EXPIRED` is backend-defined.

### Service interactions (Moqui)
- Create screen form submits to a Moqui service (synchronous) that returns `promotionOfferId`.
- Detail screen actions call transition services.

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
Client-side validations (mirrors backend; backend remains authoritative):
- **promotionCode**: required; trim whitespace; reject empty string.
- **description**: required; reject empty string.
- **storeCode**: required vs optional **TBD** (Open Question); if optional, allow null meaning “all stores”.
- **startDate/endDate**: required; validate `startDate <= endDate`.
- **promotionType**: required; must be one of:
  - `PERCENT_LABOR`
  - `PERCENT_PARTS`
  - `FIXED_INVOICE`
- **promotionValue**: required; must be > 0 (or >= 0?) **TBD** (Open Question).  
  UI must prevent negative values at minimum.
- **usageLimit**: optional; if present must be integer and >= 0.

Server-side validation/error mapping:
- Duplicate code: show field error on `promotionCode`: “Promotion code must be unique”.
- Invalid dates: show field error on date fields: “Start date must be on or before the end date”.
- Activation constraints (e.g., endDate in past): show banner/toast: “Cannot activate an expired promotion”.

### Enable/disable rules
- `promotionCode` becomes read-only after creation (immutable).
- Activate button enabled when current status in {`DRAFT`, `INACTIVE`} and promotion is not expired (backend decides).
- Deactivate button enabled when status == `ACTIVE`.

### Visibility rules
- Show status badge/value prominently on detail.
- Show `usageLimit` as “Unlimited” when null.

### Error messaging expectations
- Form submit errors shown at top with a concise summary plus field-level messages where possible.
- Preserve backend message text for uniqueness/date errors if provided, otherwise use standardized messages above.

---

## 8. Data Requirements

### Entities involved
- `PromotionOffer` (pricing domain)

### Fields (type, required, defaults)
> Types are frontend types; backend types expected per pricing rules.

| Field | Frontend Type | Required (Create) | Default | Notes |
|---|---|---:|---|---|
| promotionOfferId | string (UUID) | no | n/a | Assigned by backend |
| promotionCode | string | yes | none | Immutable after create |
| description | string | yes | none | Internal description |
| storeCode | string \| null | **TBD** | **TBD** | Scope semantics unclear |
| startDate | string (YYYY-MM-DD) | yes | none | Date-only |
| endDate | string (YYYY-MM-DD) | yes | none | Date-only |
| promotionType | enum string | yes | none | Values listed above |
| promotionValue | number/string decimal | yes | none | Precision/format **TBD** (do not assume rounding) |
| usageLimit | number (int) \| null | no | null | Null = unlimited |
| status | enum string | no | `DRAFT` | Backend authoritative |

### Read-only vs editable by state/role
- After create (detail screen):
  - **Read-only always:** `promotionOfferId`, `promotionCode`, `status`
  - **Editable fields:** Not in scope for this basic story (create + activate/deactivate only).  
    If edit is desired, must be separate story.

### Derived/calculated fields
- `isAvailableForUse` (derived UI hint only): status == `ACTIVE` AND today within start/end date  
  Note: backend is authoritative; UI can display informational hint but must not make enforcement decisions beyond button enablement and messaging.

---

## 9. Service Contracts (Frontend Perspective)

> Exact service names/paths are not provided. Define Moqui service interfaces and screen transitions consistent with repo conventions. The frontend must be coded to those Moqui endpoints.

### Load/view calls
1. **Get Promotion Offer**
   - Input: `promotionOfferId`
   - Output: full `PromotionOffer` record
   - Errors:
     - 404/not found → show “Promotion not found” and link back to list
     - 403 → show unauthorized message

### Create/update calls
2. **Create Promotion Offer**
   - Input: `promotionCode`, `description`, `storeCode`, `startDate`, `endDate`, `promotionType`, `promotionValue`, `usageLimit`
   - Output: `promotionOfferId`, created entity (or fetch after create)
   - Errors:
     - Duplicate code (409 or validation error) → field error on `promotionCode`
     - Validation errors → field errors
     - 403 → unauthorized

### Submit/transition calls
3. **Activate Promotion Offer**
   - Input: `promotionOfferId`
   - Output: updated status (or entity)
   - Errors:
     - Cannot activate expired/endDate past → show actionable error
     - Concurrency (status changed) → reload and show “Promotion status changed; page refreshed”

4. **Deactivate Promotion Offer**
   - Input: `promotionOfferId`
   - Output: updated status (or entity)

### Error handling expectations
- Moqui service errors map to:
  - Field errors: when error payload identifies field (preferred)
  - Form-level errors: otherwise
- Always preserve correlation/request ID if provided by backend in error response for support.

---

## 10. State Model & Transitions

### Allowed states
- `DRAFT`
- `ACTIVE`
- `INACTIVE`
- `EXPIRED` (may be computed; UI treats as terminal/non-activatable)

### Role-based transitions
- Account Manager with promotion-management permission:
  - `DRAFT` → `ACTIVE`
  - `ACTIVE` → `INACTIVE`
  - `INACTIVE` → `ACTIVE`
- Any user without permission:
  - Can view list/detail only if allowed; otherwise blocked (permission model **TBD**)

### UI behavior per state
- `DRAFT`: show Activate enabled; show Deactivate hidden/disabled
- `ACTIVE`: show Deactivate enabled; show Activate hidden/disabled
- `INACTIVE`: show Activate enabled
- `EXPIRED`: show Activate disabled with tooltip/message; show Deactivate hidden/disabled

---

## 11. Alternate / Error Flows

### Validation failures
- Start date after end date → prevent submit client-side; if server returns same, show field errors.
- Negative promotionValue or usageLimit → prevent submit client-side; show field errors.

### Concurrency conflicts
- If activation/deactivation fails due to stale status:
  - UI reloads PromotionOffer and displays non-blocking message: “Promotion status updated by another user.”

### Unauthorized access
- If create screen accessed without permission:
  - Show “You don’t have access to manage promotions.”
  - Do not render form submit actions.

### Empty states
- Promotions list empty: show empty state and “Create Promotion” CTA.

### Backend dependency failures
- If service unavailable/timeouts:
  - Show retry affordance; do not assume operation succeeded.

---

## 12. Acceptance Criteria

### Scenario 1: Create a new draft promotion (percent off labor)
**Given** I am authenticated as an Account Manager with permission to manage promotions  
**And** I am on the Create Promotion screen  
**When** I enter a unique promotion code "LABOR15" and a description  
**And** I select promotion type "PERCENT_LABOR" with promotion value "15"  
**And** I set startDate "2026-02-01" and endDate "2026-02-28" with startDate on/before endDate  
**And** I submit the form  
**Then** a PromotionOffer is created successfully  
**And** the created PromotionOffer has status "DRAFT"  
**And** I am navigated to the PromotionOffer detail screen for the new promotion

### Scenario 2: Reject create when date range is invalid (client-side)
**Given** I am on the Create Promotion screen  
**When** I set startDate after endDate  
**Then** the UI shows a validation message "Start date must be on or before the end date"  
**And** the Create action is blocked until corrected

### Scenario 3: Reject create when promotion code is not unique (server-side)
**Given** a PromotionOffer already exists with promotionCode "SUMMER2024"  
**And** I am on the Create Promotion screen  
**When** I submit a new promotion with promotionCode "SUMMER2024"  
**Then** the backend rejects the request  
**And** the UI shows a field error on promotionCode "Promotion code must be unique"  
**And** no promotion is created

### Scenario 4: Activate a draft promotion
**Given** a PromotionOffer exists with status "DRAFT" and endDate is not in the past  
**And** I am on its PromotionOffer detail screen  
**When** I click "Activate"  
**Then** the promotion status becomes "ACTIVE"  
**And** the UI disables/hides the Activate action and enables the Deactivate action

### Scenario 5: Deactivate an active promotion
**Given** a PromotionOffer exists with status "ACTIVE"  
**And** I am on its PromotionOffer detail screen  
**When** I click "Deactivate"  
**Then** the promotion status becomes "INACTIVE"  
**And** the UI enables the Activate action

### Scenario 6: Prevent activation of an expired promotion
**Given** a PromotionOffer exists with endDate in the past  
**And** I am on its PromotionOffer detail screen  
**When** I attempt to activate the promotion  
**Then** activation is not allowed  
**And** the UI shows an error indicating the promotion is expired  
**And** the status remains unchanged after refresh

---

## 13. Audit & Observability

### User-visible audit data
- On detail screen, display (if available from backend):
  - `createdBy`, `createdDate`
  - `lastUpdatedBy`, `lastUpdatedDate`
- If not available, omit (do not fabricate).

### Status history
- If backend provides status change history, show a “Status History” section (read-only).  
  Otherwise, not in scope.

### Traceability expectations
- All create/activate/deactivate calls must include correlation ID headers if the frontend has a standard mechanism (per repo convention).
- UI should log (frontend console/logger) structured events for:
  - promotion.create.submit
  - promotion.create.success/failure
  - promotion.activate.submit/success/failure
  - promotion.deactivate.submit/success/failure  
  (Do not log sensitive data; promotionCode is acceptable.)

---

## 14. Non-Functional UI Requirements

- **Performance:** Detail load should render skeleton/placeholder while fetching; avoid blocking UI thread.  
- **Accessibility:** All form fields labeled; errors announced; buttons keyboard accessible.  
- **Responsiveness:** Create/detail usable on tablet widths (Quasar standard breakpoints).  
- **i18n/timezone/currency:** Dates are date-only; timezone handling must be consistent (UTC vs store locale **TBD**). Currency formatting for FIXED_INVOICE value is **TBD** (do not assume currency).

---

## 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide an empty-state message and CTA on promotions list when no records exist; safe because it does not change domain behavior; impacts UX Summary, Alternate/Empty states.
- SD-ERR-MAP-VALIDATION: Map backend validation errors to field-level messages when field key is present, otherwise show form-level error banner; safe because it is standard error presentation without altering rules; impacts Business Rules, Service Contracts, Error Flows.

---

## 16. Open Questions

1. **Permission model (blocking):** What permission string(s) should the frontend enforce for create/activate/deactivate (e.g., `pricing:promotion:manage` per pricing guide), and is view access allowed to users without manage permission?
2. **Store scope semantics (blocking):** Is `storeCode` required? If optional, does null/empty mean “all locations”, and how should the UI represent this (dropdown option “All stores”)?
3. **Promotion value constraints (blocking):** For percent types, what is the allowed range (e.g., 0–100)? For fixed invoice amount, must it be > 0 and is there a maximum? (Do not assume.)
4. **Currency and formatting (blocking):** For `FIXED_INVOICE`, which currency applies (store currency? company currency?), and what decimal precision should the UI enforce/display?
5. **Status `EXPIRED` behavior (blocking):** Is `EXPIRED` a persisted status updated by backend job, or computed on read? What should activation service return if endDate is past but status isn’t `EXPIRED`?
6. **Eligibility (not implemented but impacts wording):** The story mentions “eligible customers.” Is eligibility out of scope for this basic create flow (recommended), or must the create screen capture any eligibility fields now?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotions: Create Promotion Offer (Basic)  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/161  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotions: Create Promotion Offer (Basic)

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Account Manager**, I want **to create a simple promotion (discount amount/percent) with start/end dates** so that **I can apply it to estimates for eligible customers**.

## Details
- Offer types: % off labor, % off parts, fixed amount off invoice.
- Store code, description, active dates, optional usage limit.

## Acceptance Criteria
- Create/activate/deactivate offer.
- Validate date range.
- Unique code enforced.

## Integration Points (Workorder Execution)
- Workorder Execution can query active offers for a customer.

## Data / Entities
- PromotionOffer

## Classification (confirm labels)
- Type: Story
- Layer: Domain
- Domain: CRM


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