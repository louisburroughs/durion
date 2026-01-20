## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:draft

### Recommended
- agent:pricing-domain-agent
- agent:story-authoring

### Blocking / Risk
- risk:incomplete-requirements

**Rewrite Variant:** pricing-strict

---

# 1. Story Header

## Title
[FRONTEND] [STORY] Enforce Product Restriction Rules by Location/Service Context with Override Modal (Pricing)

## Primary Persona
Service Advisor (primary); Shop Manager (for overrides and rule management)

## Business Value
Prevent unsafe/incompatible/regulatory-noncompliant products from being added/finalized in POS flows, while enabling controlled exceptions with auditable override records.

---

# 2. Story Intent

## As a / I want / So that
**As a** Service Advisor,  
**I want** the POS UI to evaluate product restriction rules when I add or finalize items,  
**so that** restricted products are blocked (or require a manager override) and all decisions are traceable.

## In-scope
- Frontend integration with Pricing restriction evaluation API when adding/editing line items and when finalizing/committing.
- UI handling of restriction decisions per line item: `ALLOW`, `BLOCK`, `ALLOW_WITH_OVERRIDE`, and degraded state `RESTRICTION_UNKNOWN`.
- Override modal flow (permission-gated) calling Pricing override API and storing `overrideId` onto the line item context (frontend state + submission payload to downstream flow where applicable).
- Rule management screens for Shop Manager: create/view/update/deactivate `RestrictionRule` via Pricing service (Moqui screens/forms/transitions).
- Display rule match reasons (`reasonCodes`, `ruleIds`) in user-facing messages (non-sensitive).

## Out-of-scope
- Authoring/maintaining the “initial enum sets” for location/service tags beyond consuming them.
- Implementing backend rule evaluation, override approval, or audit/event emission (assumed provided by Pricing service).
- Workexec backend changes (this story only ensures frontend passes context and consumes responses).
- Caching restriction rules client-side (backend allows optional caching; frontend will not implement rule cache beyond normal HTTP caching semantics).

---

# 3. Actors & Stakeholders
- **Service Advisor:** adds products to estimate/quote/work order/invoice; sees blocks/warnings and cannot finalize when restricted/unknown.
- **Shop Manager:** can override restrictions (permission `pricing:restriction:override`), and manages restriction rules (create/update/deactivate).
- **Pricing Service (SoR):** owns `RestrictionRule` and `OverrideRecord`; provides evaluation + override APIs.
- **Workexec (consumer):** receives line items + `overrideId` (where applicable) and may provide optional service context tags.

---

# 4. Preconditions & Dependencies
- User is authenticated; roles/permissions are available to frontend (Moqui user context / permissions).
- Current operational context is available in UI state:
  - `tenantId`, `locationId`
  - service context tag (`serviceTag`) depending on flow (ESTIMATE/WORKORDER/INVOICE/POS_SALE/DELIVERY)
  - optional `customerAccountId`
  - optional context info (`vehicleType`, `workType`, `salesChannel`) if available in the POS flow
- Pricing service endpoints available:
  - `POST /pricing/v1/restrictions:evaluate`
  - `POST /pricing/v1/restrictions:override`
  - CRUD endpoints for restriction rules (exact paths TBD; see Open Questions)
- Product line item contains or can derive: `productId`, `quantity`, `uom`, `unitPrice` for evaluation request.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
1. **POS transaction flows** (estimate/quote builder, work order editor, invoice finalize, counter sale cart):
   - On “Add item” and “Edit quantity/price/uom” actions.
   - On “Finalize/Commit” actions (invoice finalize / checkout / commit sale).
2. **Admin/Manager area**:
   - “Pricing → Restriction Rules” list screen
   - “Create Restriction Rule” screen
   - “Edit Restriction Rule” screen (including deactivate)

## Screens to create/modify (Moqui)
- **Modify existing transaction screens** (names depend on repo conventions):
  - Add restriction status indicator and actions per line item.
  - Hook transitions to call evaluation service on relevant triggers.
- **New screen set** (suggested pathing):
  - `apps/pos/screen/pricing/restriction/RestrictionRuleList.xml`
  - `apps/pos/screen/pricing/restriction/RestrictionRuleDetail.xml`
  - `apps/pos/screen/pricing/restriction/RestrictionRuleEdit.xml`
  - `apps/pos/screen/pricing/restriction/RestrictionRuleCreate.xml`
- **Modal**:
  - Override modal component used from transaction line item UI when decision allows/needs override.

## Navigation context
- POS transaction area remains primary context; restriction checks should not navigate away.
- Manager rule management screens live under Pricing admin navigation, require manage permission (see Business Rules).

## User workflows
### Happy path (ALLOW)
1. Service Advisor adds product.
2. UI calls evaluate API with context + items.
3. Response `ALLOW`: item remains in cart/quote; no extra UI required.

### Blocked path (BLOCK)
1. Service Advisor adds product.
2. Evaluate returns `BLOCK` with reasons.
3. UI prevents item addition OR marks item blocked and disallows proceeding (see Functional Behavior).
4. If override is allowed by backend semantics, UI may show “Request override” action but only if user has permission.

### Override path (ALLOW_WITH_OVERRIDE or BLOCK with override allowed)
1. UI shows restriction message and “Override” action.
2. Shop Manager opens modal, enters reason code + notes (+ second approver if required).
3. UI calls override API; on `APPROVED` receives `overrideId`.
4. UI updates line item to allowed state and stores `overrideId` on line item metadata.

### Degraded path (evaluation unavailable in non-commit flows)
1. Evaluate fails due to timeout/error.
2. UI allows adding item but marks `RESTRICTION_UNKNOWN`.
3. Finalize action is blocked until evaluation succeeds.

---

# 6. Functional Behavior

## Triggers
- **T1 Add line item**: when user adds a product to cart/quote/work order.
- **T2 Edit line item**: quantity/uom/unitPrice changes for existing item.
- **T3 Pre-finalize/commit**: when user clicks finalize/commit (invoice finalize / checkout / commit sale).
- **T4 Manager rule CRUD**: list/create/update/deactivate restriction rules.

## UI actions
### Transaction line item evaluation
- On T1/T2:
  - Call evaluate API with current context and the affected item(s).
  - Update each line item with:
    - `restrictionDecision` (ALLOW/BLOCK/ALLOW_WITH_OVERRIDE/RESTRICTION_UNKNOWN)
    - `restrictionReasonCodes[]`
    - `restrictionRuleIds[]`
    - `restrictionPolicyVersion` (if returned)
    - `restrictionConfidence` (if returned; otherwise `AUTHORITATIVE` assumed only if call succeeded)
- If decision is `BLOCK`:
  - UI must prevent finalize.
  - UI must clearly indicate why (message including reason codes).
  - UI must not silently remove item without informing user.
- If decision is `ALLOW_WITH_OVERRIDE`:
  - UI must prevent finalize until override is approved OR item removed.
  - Show “Override” action only if user has permission `pricing:restriction:override`.
- If decision is `ALLOW`:
  - UI proceeds normally.

### Override modal
- Launch condition: line item decision `ALLOW_WITH_OVERRIDE` (and optionally `BLOCK` where backend indicates override possible; see Open Questions).
- Fields collected:
  - `overrideReasonCode` (required; from enum list supplied by backend or configured list—see Open Questions)
  - `notes` (required free text)
  - `secondApprover` (optional; shown only if required by API response / rule metadata—see Open Questions)
- On submit:
  - Call override API.
  - On `APPROVED`:
    - attach `overrideId` to line item
    - set decision to `ALLOW` (but retain audit display info: ruleIds/reasonCodes/overrideId)
  - On `DENIED` or error: keep item restricted and show error.

### Pre-finalize/commit behavior
- On T3, UI must ensure **authoritative evaluation** was performed successfully for all line items:
  - If any item is `RESTRICTION_UNKNOWN` → block finalize with message: “Restrictions must be evaluated before finalizing.”
  - If any item is `BLOCK` → block finalize.
  - If any item is `ALLOW_WITH_OVERRIDE` without `overrideId` → block finalize.
- If evaluation API is unavailable during T3:
  - Fail closed: block finalize and show message per backend guidance: “Restriction service unavailable; cannot complete transaction.”

### Rule management (Shop Manager)
- List rules with filters: active/inactive, conditionType, conditionValue, effective date.
- Create rule:
  - select conditionType, conditionValue (from allowed enums)
  - choose one or more productIds (product picker)
  - set active flag and effectiveFrom/effectiveTo
  - save via Pricing service
- Edit rule:
  - update fields per backend constraints (historical/versioning handled by backend)
  - deactivate rule (set inactive)

## State changes (frontend-owned)
- Line item local state gains restriction metadata + overrideId.
- Transaction-level “canFinalize” derived from line item restriction states.

## Service interactions
- Evaluate: `POST /pricing/v1/restrictions:evaluate`
- Override: `POST /pricing/v1/restrictions:override`
- Rule CRUD: Pricing service endpoints (TBD).

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- Do not allow override submission unless:
  - user has permission `pricing:restriction:override`
  - `overrideReasonCode` is provided
  - `notes` is non-empty (trimmed length > 0)
  - `secondApprover` provided if backend indicates required
- When creating/editing restriction rules:
  - enforce required fields client-side (name, conditionType, conditionValue, product list, effectiveFrom)
  - conditionType/value must be selected from enumerated sets (no free-form)
  - effectiveFrom <= effectiveTo when effectiveTo present

## Enable/disable rules
- Disable “Finalize” when any line item state is not finalizable as described in Functional Behavior.
- Disable “Override” action when user lacks permission or line item decision is not override-eligible.

## Visibility rules
- Restriction reason message visible when decision != ALLOW.
- Override modal action visible only when override-eligible and user has permission.
- Rule management screens visible only to users with `pricing:pricebook:manage` or a dedicated permission for restriction rules (see Open Questions).

## Error messaging expectations
- BLOCK/ALLOW_WITH_OVERRIDE must show a clear message including product identifier/name and reason codes.
- Service unavailable messages must match backend-specified text on commit paths.

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- Pricing-owned:
  - `RestrictionRule`
  - `OverrideRecord` (read-only display / returned by override)
  - `AuditLog` (read-only; may be exposed via Pricing service)
- Transaction line item (Workexec/POS context): stores `overrideId` reference.

## Fields
### Restriction evaluation request (per backend reference)
- `tenantId` (UUID, required)
- `locationId` (UUID, required)
- `serviceTag` (enum string, required)
- `customerAccountId` (UUID, optional)
- `items[]` (required, non-empty)
  - `productId` (UUID, required)
  - `quantity` (decimal, required > 0)
  - `uom` (string, required)
  - `unitPrice` (decimal, required; do not assume rounding)
- `context` (object, optional)
  - `vehicleType` (string, optional)
  - `workType` (string, optional)
  - `salesChannel` (string, optional)

### Restriction evaluation response (minimum needed)
Per item:
- `decision` enum: `ALLOW` | `BLOCK` | `ALLOW_WITH_OVERRIDE`
- `ruleIds[]` (UUIDs)
- `reasonCodes[]` (strings/enums)
- optional: `confidence`, `policyVersion`

### Override request
- `tenantId`, `locationId` (if required by backend; TBD)
- `transactionId` (UUID, required)
- `productId` (UUID, required)
- `ruleId` (UUID, required if overriding specific rule; TBD if multiple)
- `overrideReasonCode` (enum string, required)
- `notes` (string, required)
- `secondApprover` (UUID, optional/conditional)
- `policyVersion` (int, optional/conditional)

### Override response
- `status` enum: `APPROVED` | `DENIED`
- `overrideId` (UUID) when approved

## Read-only vs editable
- In transaction UI, restriction decision fields are read-only (derived from service).
- `overrideId` is set by UI after approved override; cannot be manually edited.

## Derived/calculated fields
- `transaction.canFinalize = all(lineItems.finalizable)`
- `lineItem.finalizable` derived:
  - `ALLOW` => true
  - `BLOCK` => false
  - `ALLOW_WITH_OVERRIDE` => true only if `overrideId` present
  - `RESTRICTION_UNKNOWN` => false

---

# 9. Service Contracts (Frontend Perspective)

## Load/view calls
- On transaction screen load: may optionally evaluate all existing items (recommended) to populate restriction states.
- Rule list/detail: call Pricing rule list/get endpoints (TBD).

## Create/update calls
- Create restriction rule: POST to Pricing rule endpoint (TBD).
- Update restriction rule: PUT/PATCH (TBD).
- Deactivate: PATCH `isActive=false` or a deactivate endpoint (TBD).

## Submit/transition calls
- Evaluate restrictions:
  - Endpoint: `POST /pricing/v1/restrictions:evaluate`
  - Timeout handling: treat >800ms as unavailable for UX purposes; no synchronous retries on commit path.
- Override:
  - Endpoint: `POST /pricing/v1/restrictions:override`
  - On success: update UI and attach overrideId to line item.

## Error handling expectations
- HTTP 4xx: show validation/permission error; keep item in restricted state.
- HTTP 503/timeout:
  - Non-commit: set `RESTRICTION_UNKNOWN`, allow adding/editing but block finalize.
  - Commit: block action with message “Restriction service unavailable; cannot complete transaction.”
- Conflict between cached/previous evaluation and authoritative: always use latest API result.

---

# 10. State Model & Transitions

## Allowed states (line item restriction state)
- `ALLOW`
- `BLOCK`
- `ALLOW_WITH_OVERRIDE`
- `RESTRICTION_UNKNOWN` (frontend-local degraded state)

## Role-based transitions
- Service Advisor:
  - can trigger evaluation
  - cannot override without permission
- Shop Manager (or anyone with `pricing:restriction:override`):
  - can transition `ALLOW_WITH_OVERRIDE` → `ALLOW` by creating an approved override (overrideId stored)
- Admin/Shop Manager with rule manage permission:
  - can create/update/deactivate restriction rules (separate screen set)

## UI behavior per state
- `ALLOW`: normal
- `BLOCK`: highlight as blocked; disallow finalize; show remove item action
- `ALLOW_WITH_OVERRIDE`: show “Override” action if permitted; disallow finalize until override approved
- `RESTRICTION_UNKNOWN`: warning state; disallow finalize; show “Retry evaluation” action

---

# 11. Alternate / Error Flows

## Validation failures
- Override submitted without reason/notes → inline field errors; do not call API.
- Rule create/edit missing required fields → inline errors.

## Concurrency conflicts
- If rule updated between evaluation and override and backend rejects due to policyVersion mismatch:
  - show error “Restriction policy changed; re-evaluate required.”
  - force re-evaluate before allowing another override attempt.

## Unauthorized access
- If user without permission attempts override:
  - UI hides action; if forced (deep link) then backend 403 shown; keep blocked.

## Empty states
- Rule list empty: show “No restriction rules found” and CTA “Create rule” (if permitted).
- Evaluation returns no rules and ALLOW: no special UI.

---

# 12. Acceptance Criteria

```gherkin
Scenario: Line item is blocked due to a location-based restriction
  Given I am a Service Advisor in a location tagged "RETAIL_STORE"
  And the current serviceTag is "ESTIMATE"
  When I add a product to the estimate
  And the frontend calls POST /pricing/v1/restrictions:evaluate with tenantId, locationId, serviceTag, and the product line item
  Then the UI reflects the returned decision "BLOCK" for that line item
  And the UI shows a user-facing restriction message including the reasonCodes
  And the UI prevents finalizing the estimate while the blocked item remains

Scenario: Line item is allowed when no restriction applies
  Given I am a Service Advisor in a location tagged "WAREHOUSE"
  When I add a product to the estimate
  And the evaluation response decision is "ALLOW"
  Then the item remains added with restriction state "ALLOW"
  And the UI allows finalizing provided all other items are finalizable

Scenario: Override modal is available only to authorized users
  Given a line item has decision "ALLOW_WITH_OVERRIDE"
  And I am logged in without permission "pricing:restriction:override"
  Then the UI does not show the Override action
  And attempting to proceed to finalize remains blocked due to the override-required item

Scenario: Authorized user successfully overrides a restriction and overrideId is stored
  Given a line item has decision "ALLOW_WITH_OVERRIDE"
  And I am logged in with permission "pricing:restriction:override"
  When I open the override modal
  And I enter overrideReasonCode "MANAGER_APPROVAL" and notes "Customer approved special order"
  And I submit and the frontend calls POST /pricing/v1/restrictions:override
  And the override response returns status "APPROVED" and an overrideId
  Then the UI stores overrideId on the line item
  And the line item becomes finalizable
  And the UI allows finalizing if no other line items are blocking

Scenario: Evaluation service unavailable during quote building marks item as unknown and blocks finalize
  Given I am building an estimate (non-commit path)
  When the frontend calls POST /pricing/v1/restrictions:evaluate and receives a timeout or 503
  Then the UI allows the item to remain in the estimate
  And marks the line item restriction state as "RESTRICTION_UNKNOWN"
  And the UI blocks finalizing until a successful evaluation occurs

Scenario: Evaluation service unavailable during commit fails closed
  Given I am finalizing an invoice (commit path)
  When the frontend attempts to evaluate restrictions and receives a timeout or 503
  Then the UI blocks the finalize action
  And displays the message "Restriction service unavailable; cannot complete transaction."

Scenario: Shop Manager can manage restriction rules
  Given I am logged in with the restriction rule manage permission
  When I navigate to Pricing > Restriction Rules
  Then I can view a list of RestrictionRules
  And I can create a new rule by selecting conditionType/value from enums and selecting one or more products
  And saving calls the Pricing service rule create endpoint and shows the new rule in the list
```

---

# 13. Audit & Observability

## User-visible audit data
- On line item with restriction decision != ALLOW, user can view:
  - decision
  - reasonCodes
  - ruleIds
  - (if overridden) overrideId and override status

## Status history
- Rule management screens show (if provided by API):
  - createdAt/createdBy, updatedAt/updatedBy, policyVersion, isActive, effectiveFrom/effectiveTo

## Traceability expectations
- Frontend includes correlation/request ID headers per workspace convention (if established) when calling Pricing APIs.
- Log (frontend console/logger) structured events for:
  - `restriction.evaluate.requested` / `.succeeded` / `.failed`
  - `restriction.override.requested` / `.succeeded` / `.failed`
  - include transactionId, productId(s), decision, and error category (avoid PII in notes)

---

# 14. Non-Functional UI Requirements
- **Performance:** evaluation call should not block UI thread; show loading indicator per line item; commit path must surface timeout quickly (<= ~800ms + UI overhead).
- **Accessibility:** modal fields labeled; errors announced; keyboard navigable; focus trapping in modal.
- **Responsiveness:** works on tablet widths typical for POS.
- **i18n/timezone/currency:** do not hardcode tag labels; display enum labels via mapping table; no assumptions on currency formatting for unitPrice beyond existing POS formatting.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Rule list shows standard empty state with permitted CTA; qualifies as safe UI ergonomics; impacts UX Summary, Alternate/Empty states.
- SD-UX-LOADING-INDICATOR: Per-line-item loading state during evaluation; safe ergonomics; impacts Functional Behavior, Error Flows.
- SD-ERR-HTTP-GENERIC: Map unknown 5xx to generic “Service unavailable” while preserving backend message when provided; safe error-handling; impacts Service Contracts, Alternate/Error Flows.

---

# 16. Open Questions
1. What are the exact Pricing service endpoints for `RestrictionRule` CRUD (list/get/create/update/deactivate), including request/response schemas?
2. What permission(s) gate restriction rule management screens? (Backend doc lists several pricing permissions but not a dedicated `pricing:restriction:manage`.)
3. For overrides: can `BLOCK` ever be override-eligible, or is override only via `ALLOW_WITH_OVERRIDE`? If `BLOCK` can be overridden, how does the evaluate response indicate that?
4. What is the authoritative list of `overrideReasonCode` values and how should the UI fetch it (enum endpoint vs static config)?
5. Override API request shape: does it require `ruleId` when multiple rules match a product? If so, does UI choose one, or does backend accept multiple ruleIds?
6. Where in the POS frontend is `transactionId` guaranteed to exist at add-item time (estimate draft vs persisted)? If not persisted yet, should override be disabled until transaction is persisted?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Rules: Enforce Location Restrictions and Service Rules for Products — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/107

Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Rules: Enforce Location Restrictions and Service Rules for Products

**Domain**: user

### Story Description

/kiro
# User Story
## Narrative
As a **Shop Manager**, I want restriction rules so that unsafe or incompatible items are not sold/installed.

## Details
- Block based on location tags or service type.
- Override requires permission + rationale.

## Acceptance Criteria
- Restrictions enforced in pricing/quote APIs.
- Override permission required.
- Decision recorded in trace.

## Integrations
- Workexec receives warnings/errors; shopmgr provides service context tags (optional).

## Data / Entities
- RestrictionRule, OverrideRecord, AuditLog

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