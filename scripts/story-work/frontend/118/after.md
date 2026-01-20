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

STOP: Clarification required before finalization

---

## 1. Story Header

### Title
[FRONTEND] [STORY] Pricing: Define Base Company Price Book Rules (Moqui Screens)

### Primary Persona
Pricing Analyst / Pricing Administrator

### Business Value
Enable consistent, auditable company-wide pricing configuration (base price books + rules) so downstream quoting uses deterministic rule evaluation and controlled variation by location and customer tier.

---

## 2. Story Intent

### As a / I want / So that
**As a** Pricing Analyst (authorized pricing admin),  
**I want** to create and manage base company price books and their effective-dated pricing rules (global/category/SKU, optional tier/location conditions),  
**so that** pricing is consistent across stores, deterministically evaluated, and all changes are auditable.

### In-scope
- Moqui UI to:
  - List/search base Price Books
  - View a Price Book and its Rules
  - Create/update/deactivate Price Books (within allowed fields)
  - Create/update/deactivate Price Book Rules with effective dating, priority, and targeting
  - View audit history for rule changes (read-only)
- Frontend validation aligned to pricing-domain rules where possible (date windows, required fields, immutability indicators).
- Integration wiring from Moqui screens/forms to Moqui services/endpoints for CRUD + audit retrieval.

### Out-of-scope
- Price quote calculation UI (Workexec “PriceQuote” consumer flows)
- Store/location override workflows (explicitly referenced as layered after base; handled elsewhere)
- Building/maintaining product, category taxonomy, customer tier, inventory cost, or MSRP (owned by other domains)
- Defining the backend pricing formula implementation (frontend only config + display)

---

## 3. Actors & Stakeholders
- **Pricing Analyst / Pricing Administrator (primary user):** creates and maintains base books and rules.
- **Store Manager (indirect):** depends on consistent base pricing; may later apply overrides.
- **Workexec users (indirect):** rely on deterministic price outputs from configured rules.
- **Compliance/Audit (stakeholder):** requires change traceability and immutability of historical rules.
- **System Admin/Security (stakeholder):** ensures only authorized users can change pricing.

---

## 4. Preconditions & Dependencies
- User is authenticated in the frontend shell and has permission to manage pricing rules (exact permission string must match backend; see Open Questions).
- Backend services/entities exist (or will exist per backend story #54) for:
  - `PriceBook` and `PriceBookRule` CRUD
  - conflict detection on overlapping effective windows
  - audit log retrieval for create/update/deactivate operations
- Reference data dependencies for selectors:
  - Product (SKU/productId) lookup
  - Category taxonomy lookup
  - Location lookup
  - Customer tier list/lookup
- Timezone handling: effective timestamps must be interpreted consistently between UI and backend (see Open Questions).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- Main navigation: **Pricing → Base Price Books**
- Deep links:
  - `/pricing/base-price-books` (list)
  - `/pricing/base-price-books/{priceBookId}` (detail)
  - `/pricing/base-price-books/{priceBookId}/rules/new` (new rule)
  - `/pricing/base-price-books/{priceBookId}/rules/{ruleId}` (rule detail/edit)
  - `/pricing/base-price-books/{priceBookId}/audit` (audit timeline/list)

### Screens to create/modify (Moqui)
Create screens under a consistent root (example):
- `screens/pricing/BasePriceBookList.xml`
- `screens/pricing/BasePriceBookDetail.xml`
- `screens/pricing/PriceBookRuleEdit.xml`
- `screens/pricing/PriceBookAudit.xml`

(Exact folder naming must align with repo conventions; see Open Questions.)

### Navigation context
- List → select a PriceBook → view tabs:
  - **Rules**
  - **Details**
  - **Audit**
- Rule edit is reachable from Rules tab (Create) and from a rule row (Edit/View).

### User workflows
**Happy path: create a base price book rule**
1. Navigate to Base Price Books list.
2. Open a specific Price Book (company default or scoped).
3. Click “Add Rule”.
4. Select target type (Global / Category / SKU).
5. Provide target id if needed (categoryId/productId).
6. Choose pricing logic type (Markup over cost / Discount from MSRP / Fixed price).
7. Optionally set condition (customer tier and/or location — see Open Questions because backend story defines a single conditionType/value).
8. Set priority and effective start/end.
9. Save → rule appears in Rules table, status ACTIVE (or INACTIVE if end date in past / explicitly set).
10. View audit entry created.

**Alternate path: deactivate rule**
- From rule detail: set status INACTIVE or set effectiveEndAt to a past time (backend supports either; UI supports both per Open Questions) → save → rule no longer affects pricing but remains visible.

**Alternate path: view conflicts**
- If overlapping/conflicting rule exists, save fails with a validation error showing the conflicting rule(s); UI highlights the conflicting dimensions (target + condition + window).

---

## 6. Functional Behavior

### Triggers
- User opens list/detail screens
- User submits create/update forms
- User attempts edits on immutable historical rules

### UI actions
- List PriceBooks with filters (status, scope, isDefault, locationId/tierCode where applicable).
- Create PriceBook:
  - fields: name, scope, scopeId (nullable), isDefault, status
- Update PriceBook (limited to allowed fields; if backend restricts, UI must enforce).
- Rules tab:
  - table columns (minimum): targetType, targetId (display name via lookup), pricingLogic summary, condition, priority, effectiveStartAt, effectiveEndAt, status, updatedAt
  - actions: view/edit, deactivate
- Rule editor:
  - Selectors with async search for product/category/location/tier
  - Pricing logic input form changes by type:
    - markup over cost: numeric percent
    - discount from MSRP: numeric percent
    - fixed price: amount + currency (currency locked if backend expects single currency; see Open Questions)
  - Save/cancel transitions

### State changes
- On save:
  - Create: new PriceBookRule persisted with status ACTIVE/INACTIVE
  - Update: existing PriceBookRule updated if not immutable
  - Deactivate: effectiveEndAt set or status changed to INACTIVE
- UI should reflect backend-calculated immutability: rules with ended effective window must be read-only.

### Service interactions
- Fetch price books and rules
- CRUD for books/rules
- Lookups for product/category/location/tier
- Fetch audit logs for a priceBookId and/or ruleId

---

## 7. Business Rules (Translated to UI Behavior)

### Validation (client-side, mirrored server-side)
- Required fields:
  - PriceBook: `name`, `scope`, `status`, `isDefault` (and `scopeId` required when scope != company default)
  - PriceBookRule: `priceBookId`, `targetType`, `pricingLogicType`, `priority`, `effectiveStartAt`, `status`
  - If `targetType != GLOBAL`, `targetId` required
  - If `effectiveEndAt` provided: `effectiveEndAt >= effectiveStartAt`
- Determinism support inputs:
  - `priority` required integer (no client assumption of range)
- Conflict awareness:
  - On save failure with conflict error, UI must show:
    - message “Conflicting rule exists for the same target/condition with overlapping effective dates”
    - list of conflicting rule identifiers if provided by backend
- Immutability:
  - If rule effective period has ended (backend BR6), UI must render fields read-only and disable Save; show inline message “Historical rules are immutable; create a new rule to change pricing.”

### Enable/disable rules
- Disable targetId input when targetType = GLOBAL.
- Disable condition inputs when conditionType = NONE.
- Disable all edits when rule immutable.
- Disable “Set as Default” toggle if backend rejects multiple defaults per scope; UI should pre-check existing defaults and warn before submit.

### Visibility rules
- Show condition section only if backend supports conditions on base rules.
- Show an “Evaluation notes” / “Status explanation” tooltip when a rule status is `NOT_APPLICABLE_MISSING_BASE` (if this status is persisted on rules; see Open Questions).

### Error messaging expectations
- Field-level errors for required/malformed values.
- Form-level banner for 403/401/500.
- Conflict error must be actionable (identify overlapping window/target/condition).

---

## 8. Data Requirements

### Entities involved (frontend view models)
- `PriceBook`
- `PriceBookRule`
- `AuditLog` (or equivalent audit entity)
- Reference entities (read-only lookups):
  - Product (for SKU/productId)
  - Category taxonomy
  - Location
  - CustomerTier

### Fields (type, required, defaults)

#### PriceBook (minimal UI contract)
- `priceBookId` (UUID, read-only)
- `name` (string, required)
- `scope` (enum: COMPANY_DEFAULT | LOCATION | CUSTOMER_TIER | LOCATION_AND_TIER?; **see Open Questions**)
- `scopeId` (UUID/string depending on scope; required when scope != COMPANY_DEFAULT)
- `isDefault` (boolean, required)
- `status` (enum ACTIVE|INACTIVE, required)

Defaults (UI-only ergonomics):
- `status = ACTIVE` on create
- `isDefault = false` on create (unless explicitly creating company default; **do not assume**)

#### PriceBookRule (minimal UI contract)
- `ruleId` (UUID, read-only)
- `priceBookId` (UUID, required, hidden from user when inside a book)
- `targetType` (enum SKU|CATEGORY|GLOBAL, required)
- `targetId` (UUID/string, required unless GLOBAL)
- `pricingLogic` (JSON object, required)
  - `type` (enum MARKUP_OVER_COST | DISCOUNT_FROM_MSRP | FIXED_PRICE; **see Open Questions**)
  - `value` (decimal/percent or amount)
  - `currency` (ISO 4217, required for FIXED_PRICE if backend requires)
- `conditionType` (enum CUSTOMER_TIER|LOCATION|NONE, required)
- `conditionValue` (string/UUID, required if conditionType != NONE)
- `priority` (int, required)
- `effectiveStartAt` (timestamp, required)
- `effectiveEndAt` (timestamp, optional)
- `status` (enum ACTIVE|INACTIVE|NOT_APPLICABLE_MISSING_BASE?, required)
- `createdAt/createdBy`, `updatedAt/updatedBy` (read-only display)

Read-only vs editable by state/role
- Users without pricing manage permission: read-only access (or no access) depending on security policy (see Open Questions).
- Immutable historical rule: all fields read-only.

Derived/calculated fields (UI)
- Human-readable summaries:
  - Target label (GLOBAL or resolved product/category name)
  - Condition label (e.g., “Location: Dallas”)
  - Pricing logic summary (“Fixed $89.99”, “Cost + 20%”, “MSRP - 10%”)

---

## 9. Service Contracts (Frontend Perspective)

> Note: Backend story references REST APIs for other domains, but does not specify pricing CRUD endpoints. For Moqui buildability, endpoint/service names must be confirmed (Open Questions). Below is the **required shape** the frontend needs, not exact URLs.

### Load/view calls
- List PriceBooks
  - Input: filters (status, scope, scopeId, isDefault), pagination
  - Output: array of PriceBook summary + total
- Get PriceBook detail
  - Input: priceBookId
  - Output: PriceBook
- List PriceBookRules for a PriceBook
  - Input: priceBookId + filters (status, targetType, effectiveAsOf optional), pagination/sort
  - Output: array of PriceBookRule summary + total
- Get Rule detail
  - Input: ruleId
  - Output: PriceBookRule full

### Create/update calls
- Create PriceBook
- Update PriceBook
- Create PriceBookRule
- Update PriceBookRule
- Deactivate PriceBookRule (either update with status/effectiveEndAt or dedicated action)

### Submit/transition calls
- If backend models activation/deactivation as transitions:
  - `activateRule(ruleId)`
  - `deactivateRule(ruleId, effectiveEndAt?)`

### Error handling expectations
- 400 validation errors return field map usable for inline errors.
- 409 conflict for overlapping rule windows, includes conflicting rule references.
- 403 for unauthorized; UI routes to an “Unauthorized” screen or shows error banner.
- 5xx show retry guidance and preserve unsaved form state.

---

## 10. State Model & Transitions

### Allowed states
PriceBook:
- ACTIVE
- INACTIVE

PriceBookRule:
- ACTIVE
- INACTIVE
- (Possibly) NOT_APPLICABLE_MISSING_BASE (if persisted vs evaluation-only; see Open Questions)

### Role-based transitions
- Pricing admin:
  - create/update/deactivate
- Read-only roles:
  - view only

### UI behavior per state
- INACTIVE rules show disabled styling and cannot be activated unless backend supports re-activation (Open Question).
- Immutable historical: regardless of ACTIVE/INACTIVE, if `effectiveEndAt < now`, UI is read-only.

---

## 11. Alternate / Error Flows

### Validation failures
- Missing required fields → prevent submit; highlight fields.
- `effectiveEndAt < effectiveStartAt` → inline error on end date field.
- Invalid percent/amount format → inline numeric error.

### Concurrency conflicts
- If backend uses optimistic locking (e.g., version field), on 409 the UI must:
  - show “This rule was updated by someone else”
  - offer reload and re-apply edits (best-effort; do not auto-merge)

### Unauthorized access
- If user lacks permission:
  - On navigation: show 403 page or redirect (implementation choice per app conventions; Open Question)
  - Hide create/edit buttons if permissions info available in session.

### Empty states
- No price books: show empty state with “Create Price Book” if authorized.
- No rules in a book: show empty rules state with “Add Rule”.

---

## 12. Acceptance Criteria (Gherkin)

### Scenario 1: View base price books list
Given I am an authenticated user with permission to view pricing configuration  
When I navigate to the Base Price Books list screen  
Then I see a paginated list of existing price books with status and scope information  
And I can open a price book detail screen

### Scenario 2: Create a new base price book
Given I have permission to manage price books  
When I create a new price book with required fields (name, scope, status, default flag)  
Then the system saves the price book successfully  
And the new price book appears in the list  
And an audit record is created for the operation

### Scenario 3: Create a global rule (e.g., markup-based)
Given I have permission to manage price book rules  
And I am viewing a price book detail page  
When I add a new rule with targetType "GLOBAL", a markup-based pricing logic, a priority, and an effectiveStartAt  
And I save the rule  
Then the rule is persisted and appears in the rules list  
And the rule status is displayed as returned by the backend  
And an audit record is visible in the audit view

### Scenario 4: Create a SKU fixed-price rule
Given I have permission to manage price book rules  
And I am viewing a price book detail page  
When I add a new rule with targetType "SKU" and a selected productId and fixed price values  
And I save the rule  
Then the rule is persisted and appears in the rules list with target label resolved to the product  
And the pricing logic summary displays the fixed price

### Scenario 5: Reject conflicting rule due to overlapping effective window
Given I have permission to manage price book rules  
And a rule already exists for the same target and condition with an overlapping effective date range  
When I attempt to save a new rule that overlaps  
Then the save fails with a conflict/validation error  
And the UI displays an actionable error message indicating a conflicting rule exists  
And the user’s entered form values remain intact for correction

### Scenario 6: Enforce immutability for historical rules
Given I am viewing a rule whose effectiveEndAt is in the past  
When I open the rule detail screen  
Then the rule fields are read-only  
And the Save action is disabled  
And the UI instructs me to create a new rule to change pricing

### Scenario 7: Unauthorized user cannot edit
Given I am an authenticated user without permission to manage pricing rules  
When I navigate to a rule edit URL directly  
Then the UI prevents editing and shows an unauthorized message or page  
And no update call is made

---

## 13. Audit & Observability

### User-visible audit data
- Audit tab/list shows entries for create/update/deactivate:
  - timestamp
  - actor (userId/display name if available)
  - entity type (PriceBook / PriceBookRule)
  - entity id
  - change summary (field diffs if provided)

### Status history
- For each rule, show createdAt/updatedAt and status changes if audit provides them.

### Traceability expectations
- UI must include correlation/request id in error details display (if returned by backend headers/body).
- All create/update actions should log frontend telemetry event (if app has standard logging) containing priceBookId/ruleId and outcome (success/failure), without sensitive data.

---

## 14. Non-Functional UI Requirements

- **Performance:** Rules table should support pagination and server-side filtering; avoid loading all rules for large datasets.
- **Accessibility:** All form controls must be keyboard accessible and have labels; validation errors must be announced to screen readers.
- **Responsiveness:** Usable on tablet widths; tables should adapt (stack or horizontal scroll).
- **i18n/timezone/currency:**
  - Display timestamps in user locale but submit in backend-required format.
  - Currency display must follow ISO 4217; formatting consistent with app conventions.
  - Timezone for effective dates must be consistent (Open Question).

---

## 15. Applied Safe Defaults
- SD-UI-EMPTY-STATE: Provide explicit empty states for “no price books” and “no rules” with CTA if authorized; qualifies as safe UX ergonomics; impacts UX Summary, Error Flows.
- SD-UI-PAGINATION: Use standard server-driven pagination/sorting on list tables; qualifies as safe ergonomics and scalability; impacts UX Summary, Service Contracts.
- SD-ERR-STANDARD-MAPPING: Map 400→field errors, 401/403→unauthorized UX, 409→conflict banner, 5xx→retry banner; qualifies as standard error handling; impacts Service Contracts, Error Flows, Acceptance Criteria.

---

## 16. Open Questions

1. **Moqui routing & screen conventions:** What is the required screen path/module naming in `durion-moqui-frontend` for pricing admin screens (e.g., `/apps/pos/pricing/...` vs `/pricing/...`), and what existing menu screen should be extended?
2. **Authorization model (denylist item):** What permissions/roles gate *view* vs *manage* for price books/rules in the frontend, and what is the exact permission string(s) to check (e.g., `pricing:pricebook:manage`)?
3. **Backend CRUD contract:** What are the exact Moqui services or REST endpoints for `PriceBook` and `PriceBookRule` list/get/create/update/deactivate, including request/response schemas and error formats (400 vs 409 conflict payload)?
4. **Conditions model mismatch:** Backend reference shows `conditionType` as a single enum (`CUSTOMER_TIER`, `LOCATION`, `NONE`), but the original story description says rules can be tiered by customer tier and mentions location conditions as well. Can a rule have **both** location and customer tier simultaneously, or must it be expressed via separate scoped PriceBooks (BR5) and single-condition rules?
5. **PriceBook scope enums:** Backend BR5 selection includes `(locationId + customerTier)` precedence, implying a combined scope. Does `PriceBook.scope` support a combined scope (location+tier), and if so what is the exact representation (`scope=LOCATION_TIER` with two IDs vs composite scopeId)?
6. **Pricing logic schema:** What is the exact shape of `pricingLogic` JSON (field names, allowed types, percent vs basis points, decimal precision)? Frontend must not guess monetary precision/rounding.
7. **Currency handling:** Are base price books single-currency (e.g., USD only) or multi-currency? For FIXED_PRICE rules, must UI require/select currency, and what currencies are allowed?
8. **Effective dating timezone:** Are `effectiveStartAt/effectiveEndAt` interpreted as UTC timestamps, local store time, or user timezone? Should UI use date-only or date-time pickers?
9. **Deactivation mechanism:** Should deactivation be done by setting `status=INACTIVE`, setting `effectiveEndAt`, or either? If either, which should UI prefer and how should it message the behavior?
10. **Audit retrieval:** What entity/service provides audit entries for rule changes, and what fields are available (diffs, actor display name, correlation id)?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Pricing: Define Base Company Price Book Rules — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/118

====================================================================================================

FRONTEND STORY (FULL CONTEXT)

====================================================================================================

Title: [FRONTEND] [STORY] Pricing: Define Base Company Price Book Rules
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/118
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Pricing: Define Base Company Price Book Rules

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Pricing Analyst**, I want base price books so that pricing is consistent across stores with controlled variation.

## Details
- Rule types: markup over cost, discount from MSRP, fixed price.
- Price books can be tiered by customer tier.
- Versioning + effective dating.

## Acceptance Criteria
- Deterministic rule evaluation.
- Version changes audited.

## Integrations
- Workexec calls PriceQuote with location + customer tier; store overrides layered after base.

## Data / Entities
- PriceBook, PriceRule, PriceBookVersion, AuditLog

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

====================================================================================================