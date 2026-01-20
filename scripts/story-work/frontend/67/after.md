STOP: Clarification required before finalization

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:billing
- status:draft

### Recommended
- agent:billing-domain-agent
- agent:story-authoring

### Blocking / Risk
- blocked:clarification
- risk:incomplete-requirements

**Rewrite Variant:** accounting-strict

---

# 1. Story Header

## Title
[FRONTEND] Enforce PO Requirement & Billing Rules During Checkout (PO capture, override approval, credit limit blocks)

## Primary Persona
POS Clerk / Service Advisor

## Business Value
Prevent non-compliant commercial checkouts by enforcing customer billing rules at the point of sale; ensure PO capture/override is auditable and credit terms are applied correctly to reduce AR risk and downstream reconciliation effort.

---

# 2. Story Intent

## As a / I want / So that
- **As a** POS Clerk  
- **I want** the checkout flow to enforce customer billing rules (PO requirement, uniqueness, and credit/terms rules)  
- **So that** commercial accounts comply with contractual terms and finance can audit who captured/overrode requirements and why.

## In-scope
- Gate **order finalization** on billing-rule enforcement results.
- Display billing rule evaluation results in checkout: PO required, PO format, PO uniqueness policy, charge account eligibility, credit limit outcomes.
- Capture PO number (and lock it once saved).
- Support override workflow with permission enforcement and 1- or 2-approver capture.
- Surface clear, actionable error messages for blocked checkout.
- Persist and show (read-only) captured rule version + override audit metadata on the order.
- Moqui screen/form/service wiring for the above.

## Out-of-scope
- Defining or changing billing rules (admin/config UI).
- Accounting GL posting and reconciliation workflows.
- Payment gateway capture/void/refund behavior (not part of this checkout-rule enforcement story).
- Designing the backend policy engine; frontend consumes backend decisions/contracts.

---

# 3. Actors & Stakeholders
- **POS Clerk / Service Advisor (primary)**: finalizes orders; enters PO; requests override when needed.
- **Manager / Finance Manager (approver)**: performs override approval (may be 2nd approver).
- **Finance / AR**: audits overrides and policy adherence; cares about traceability.
- **Billing domain services (SoR for billing rules, credit/terms decisions)**: provides active rule version and enforcement results.
- **CRM snapshot (read-only consumer, optional for UX)**: may provide cached flags but is not authoritative for enforcement.

---

# 4. Preconditions & Dependencies
- User is authenticated in POS and has an identity available to the frontend (`actorUserId`).
- An **Order/Work Order checkout context** exists (a unique `orderId` or `workOrderId` used in checkout).
- Backend endpoints exist (or will exist) to:
  - evaluate billing rules for the checkout context,
  - save PO reference and/or override,
  - attempt finalization and return blocking validation errors.
- Authorization model exists for permission `OVERRIDE_PO_REQUIREMENT`.
- Backend provides the authoritative `billingRuleVersion` and any `policyVersion` used for override.

**Dependency note:** The provided backend reference includes concrete behaviors; the frontend story must not invent missing API shapes. See Open Questions.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From existing checkout screen for an order/work order: user clicks **Finalize** (or equivalent).

## Screens to create/modify
- **Modify existing Checkout screen** (likely under a POS/Order/WorkOrder screen tree):
  - Add a **Billing Requirements** section/panel in the checkout page.
  - Add modal/dialog for **PO entry** when required.
  - Add modal/dialog for **Override PO requirement** with approver capture.
- Add/extend a read-only **Order Summary / Order Details** area to display:
  - `billingRuleVersion` used,
  - PO number + captured metadata,
  - override flag + approvers + reason + policyVersion.

## Navigation context
- User remains on checkout screen; blocking conditions show inline errors and/or modal prompts; successful save returns to checkout and allows finalization.

## User workflows

### Happy path A: PO required → capture PO → finalize
1. Clerk clicks Finalize.
2. UI calls “evaluate billing rules” and gets `poRequired=true`.
3. UI prompts for PO entry; validates basic format client-side (only if contract confirmed).
4. UI submits PO to backend; backend returns success + stored metadata.
5. Clerk clicks Finalize again (or auto-retry finalization after save if supported); finalization succeeds.

### Happy path B: PO not required → finalize
1. Clerk clicks Finalize.
2. UI evaluates rules; `poRequired=false`; no prompt.
3. Finalization proceeds.

### Alternate path C: override required → single approver
1. PO required, missing → clerk selects Override.
2. UI verifies permission (by backend response or user permission set).
3. UI collects override reason; submits override; backend returns stored audit data.
4. Finalization proceeds.

### Alternate path D: override required → two-person approval
1. PO required, missing → clerk selects Override.
2. Backend indicates 2nd approver required; UI prompts for second approver authentication (mechanism TBD).
3. UI submits override with both approvers and reason; finalization proceeds.

### Alternate path E: charge account credit limit block
1. Checkout evaluates rules includes charge account eligibility/terms and credit limit evaluation.
2. If backend returns credit limit exceeded, UI blocks finalization and shows message; user must choose different billing method (if such choice exists) or stop.

---

# 6. Functional Behavior

## Triggers
- User action: click **Finalize checkout**.
- User action: submit **PO number**.
- User action: submit **Override** (single or two-person).

## UI actions (required)
- On entering checkout or on Finalize click (see Open Question), call backend to fetch:
  - active billing rule version,
  - enforcement flags (po required, uniqueness policy),
  - override policy evaluation result (single vs two-person),
  - charge account eligibility and credit-limit evaluation outcome (if applicable).
- If `poRequired=true` and no PO is already attached:
  - disable/stop Finalize and present PO capture prompt OR present error with action button “Enter PO”.
- Provide “Override PO requirement” action only when:
  - PO is required and missing, and
  - backend indicates override is allowed for this user OR user has permission (source TBD).
- If PO was captured successfully:
  - display PO as read-only and prevent edits (immutable).
- If override succeeded:
  - display “PO Overridden” flag and read-only audit metadata.

## State changes (frontend-visible)
- Order gains `poNumber` + captured metadata OR `poOverridden=true` + override metadata.
- Order retains `billingRuleVersion` used at time of checkout (read-only after capture/finalize).

## Service interactions (frontend)
- `evaluateBillingRules(orderId)` (read)
- `savePoReference(orderId, poNumber)` (write)
- `overridePoRequirement(orderId, reasonCode, approver(s))` (write)
- `finalizeCheckout(orderId)` (transition/submit) which may return blocking validation errors.

(Exact names/paths are Open Questions; Moqui service mappings are defined below as screen actions.)

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- PO number validation must be enforced server-side; UI may provide pre-validation **only** for format to reduce round-trips.
- PO format (from reference): 3–30 chars; letters/numbers plus `-` and `_`; case-insensitive.
- If uniqueness violation occurs, UI must show backend-provided message including conflicting order reference (if provided).

## Enable/disable rules
- Finalize button must be disabled or must fail fast with a blocking message when:
  - PO required and missing AND no override is applied.
  - Billing rules are missing/invalid for B2B and backend instructs to block.
  - Credit limit exceeded for charge account billing method (if backend returns this condition).
- Override action must be disabled/hidden when user lacks permission; unauthorized attempts must show “Contact a manager” message.

## Visibility rules
- Billing Requirements panel always visible for commercial accounts (how “commercial/B2B” is detected is Open Question; must follow backend signal).
- Override modal appears only after backend indicates override is permitted and whether 2-person approval is required.

## Error messaging expectations
- Use specific, non-technical messages:
  - Missing PO: “PO number required for this customer. Enter PO or request override.”
  - Invalid PO format: “PO must be 3–30 characters: letters, numbers, dashes, underscores.”
  - Uniqueness: “PO number is already in use for another order. Use a unique PO.”
  - Missing billing config: “Billing rules not configured for this customer. Contact an administrator.”
  - Unauthorized override: “Insufficient permissions to override PO requirement. Contact a manager.”
  - Credit limit exceeded: “Order total exceeds available credit limit. Select a different payment method or contact finance.”
- Error messages should include a stable `errorCode` (if backend provides) for test assertions (Open Question).

---

# 8. Data Requirements

## Entities involved (frontend perspective)
- **Order/Checkout context** (existing): `orderId` (or `workOrderId`).
- **Billing rule evaluation result** (read model returned by billing service).
- **PO Reference** attached to order.
- **Override Audit** attached to order.

## Fields (type, required, defaults)

### BillingRuleEvaluation (read-only DTO)
- `customerAccountId` (string/UUID, required)
- `isB2B` (boolean, required) **or** `customerType` (enum) (Open Question)
- `billingRuleId` (string/UUID, required when configured)
- `billingRuleVersion` (string/int, required when configured; required for audit)
- `poRequired` (boolean, required)
- `poUniquenessPolicy` (enum: `PER_ORDER|PER_ACCOUNT_OPEN_ORDERS|PER_ACCOUNT_ALL_TIME`, required if poRequired true)
- `poFormatPrefix` (string, optional; if present, UI should display as guidance only unless backend requires client validation)
- `chargeAccountEligible` (boolean, optional)
- `paymentTerms` (string, optional)
- `creditLimit` (decimal, optional)
- `currentOutstandingBalance` (decimal, optional)
- `creditLimitExceeded` (boolean, optional)
- `overrideAllowed` (boolean, required)
- `overrideRequiresSecondApprover` (boolean, required when overrideAllowed true)
- `overridePolicyVersion` (string/int, optional unless override happens)
- `messages[]` (array, optional): `code`, `severity`, `text`

### PoReference (write once, then read-only)
- `poNumber` (string, required if capturing PO)
- `poCapturedAt` (datetime, server-set)
- `poCapturedBy` (userId, server-set)
- `billingRuleVersion` (string/int, server-set linkage)

### PoOverride (write once, then read-only)
- `poOverridden` (boolean, server-set)
- `overrideReasonCode` (string, required)
- `overrideApproverIds` (string[] length 1 or 2, required)
- `overrideAt` (datetime, server-set)
- `overridePolicyVersion` (string/int, server-set)
- `billingRuleVersion` (string/int, server-set linkage)

## Read-only vs editable by state/role
- PO Number: editable only until successfully saved; thereafter read-only always.
- Override fields: editable only within override modal prior to submission; after submission read-only.
- Display of approver IDs: visible to authorized staff (permission model Open Question; default to showing to POS roles only).

## Derived/calculated fields
- `overrideRequiresSecondApprover` derived from backend policy evaluation using order total/risk tier; UI must not calculate.

---

# 9. Service Contracts (Frontend Perspective)

> Moqui implementation note: define screen actions calling services; services may proxy to backend microservices if applicable. Exact service names below are placeholders; must match actual component/service naming conventions in repo.

## Load/view calls
1. `BillingServices.getCheckoutBillingContext`
   - Input: `orderId`
   - Output: `BillingRuleEvaluation` DTO + existing `PoReference/PoOverride` if any.

## Create/update calls
2. `BillingServices.capturePoReference`
   - Input: `orderId`, `poNumber`
   - Output: saved PO reference + updated order billing metadata
   - Errors:
     - `422` invalid format
     - `409` uniqueness violation (include conflicting order ref if available)
     - `403` unauthorized

3. `BillingServices.overridePoRequirement`
   - Input: `orderId`, `overrideReasonCode`, `approver1UserId`, `approver2UserId?` (+ authentication proof for 2nd approver; Open Question)
   - Output: saved override audit + updated order billing metadata
   - Errors:
     - `403` unauthorized override
     - `422` missing reason / missing 2nd approver when required
     - `409` state conflict if PO already captured or order already finalized

## Submit/transition calls
4. `OrderServices.finalizeCheckout`
   - Input: `orderId`
   - Output: success with final order status, or failure with blocking errors (po required, credit limit exceeded, missing billing config, etc.)
   - Errors:
     - `409` conflict (order state changed, already finalized)
     - `422` rule violations / unmet requirements
     - `503` billing service unavailable

## Error handling expectations
- Map backend errors to:
  - field-level errors for PO input when possible,
  - banner/toast for general blocking errors,
  - include `correlationId` from response headers/body in an expandable “Details” section (if available).
- Do not display internal stack traces.

---

# 10. State Model & Transitions

## Allowed states (order lifecycle — only those relevant to UI gating)
- `CHECKOUT_IN_PROGRESS` (or equivalent existing state)
- `FINALIZED` (or equivalent)

(Exact order states are Open Questions; UI must rely on backend’s “finalizable” flag or returned state.)

## Role-based transitions
- POS Clerk: may capture PO and finalize if requirements met.
- User with `OVERRIDE_PO_REQUIREMENT`: may override; may require second approver.

## UI behavior per state
- In checkout state:
  - show billing requirements; allow PO capture or override.
- In finalized state:
  - show PO/override info read-only; no edits; no override actions.

---

# 11. Alternate / Error Flows

## Validation failures
- PO invalid → keep modal open; highlight field; show message from server (plus local format hint if enabled).
- Missing override reason → show inline required error; do not submit.

## Concurrency conflicts
- If PO captured by another user while modal open:
  - submission returns `409`; UI reloads billing context and shows “PO already captured; no changes made.”
- If order finalized elsewhere:
  - disable capture/override actions; show read-only state.

## Unauthorized access
- Override attempt without permission:
  - backend `403`; UI shows “Contact a manager” message and logs client-side event.

## Empty states
- If billing context cannot load (503):
  - show blocking message and disable finalization (“Billing rules service unavailable; try again”).
  - provide Retry action.

---

# 12. Acceptance Criteria

## Scenario 1: PO required blocks finalization until captured
**Given** a checkout order for a B2B customer where backend evaluation returns `poRequired=true` and no PO is attached  
**When** the POS Clerk clicks “Finalize”  
**Then** the UI blocks finalization and prompts for PO entry  
**And** displays a message indicating a PO is required  
**And** does not submit finalize successfully until PO is saved or override is completed.

## Scenario 2: Valid PO is accepted and becomes immutable
**Given** `poRequired=true` and the clerk enters `PO-12345`  
**When** the clerk submits the PO capture  
**Then** the UI calls the PO capture service with `orderId` and `poNumber`  
**And** on success displays the PO as read-only with captured timestamp/user (if provided)  
**And** finalization is allowed on the next finalize attempt (assuming no other blockers).

## Scenario 3: Invalid PO format is rejected
**Given** `poRequired=true`  
**When** the clerk submits a PO that fails server validation  
**Then** the UI shows the server-provided format error message  
**And** the PO is not saved  
**And** finalization remains blocked.

## Scenario 4: PO uniqueness violation is shown with conflict reference (if provided)
**Given** backend indicates `poUniquenessPolicy=PER_ACCOUNT_OPEN_ORDERS`  
**When** the clerk submits a PO that is already used by another open order  
**Then** the UI shows a uniqueness violation message  
**And** includes the conflicting order reference if returned  
**And** finalization remains blocked.

## Scenario 5: Override denied without permission
**Given** `poRequired=true` and no PO is attached  
**And** the current user lacks `OVERRIDE_PO_REQUIREMENT`  
**When** the user attempts to override  
**Then** the UI blocks the override and shows an “insufficient permissions” message  
**And** does not mark the order as overridden.

## Scenario 6: Override succeeds with single approver when allowed
**Given** `poRequired=true` and backend indicates `overrideAllowed=true` and `overrideRequiresSecondApprover=false`  
**When** an authorized user submits override with a reason  
**Then** the UI calls the override service  
**And** on success shows “PO Overridden” and displays reason + approver + policyVersion (if provided) read-only  
**And** finalization is allowed (assuming no other blockers).

## Scenario 7: Two-person approval required and enforced
**Given** backend indicates `overrideRequiresSecondApprover=true`  
**When** the user submits override without a second approver  
**Then** the UI shows an error indicating a second approver is required  
**And** does not submit successfully  
**When** the user provides a second approver per the configured authentication method  
**Then** override submission succeeds and records both approvers.

## Scenario 8: Missing/invalid billing configuration blocks checkout (fail-safe)
**Given** backend indicates B2B billing rules are missing/invalid (via explicit flag/error)  
**When** the POS Clerk attempts to finalize  
**Then** the UI blocks checkout and shows “Billing rules not configured…”  
**And** provides no path forward except Retry or authorized override (if backend allows).

## Scenario 9: Credit limit exceeded blocks CHARGE_ACCOUNT finalization
**Given** backend returns `creditLimitExceeded=true` for the order when `billingMethod=CHARGE_ACCOUNT`  
**When** the clerk attempts to finalize  
**Then** the UI blocks finalization and shows the credit-limit exceeded message  
**And** records no PO/override changes as a side effect.

---

# 13. Audit & Observability

## User-visible audit data
- On checkout and order summary views, display (read-only):
  - `billingRuleVersion` used
  - PO captured by/at OR override by/at with reason and approvers
  - policy version for overrides (if returned)

## Status history
- UI must show a simple “Billing Compliance Status” derived from backend:
  - `COMPLIANT` (PO present or not required)
  - `OVERRIDDEN`
  - `BLOCKED` (missing PO / credit limit / misconfig)

## Traceability expectations
- Include `orderId`, `customerAccountId`, and any backend-provided `correlationId` in client logs for:
  - evaluation load,
  - PO capture,
  - override submit,
  - finalize attempt.

---

# 14. Non-Functional UI Requirements
- **Performance:** billing context load on checkout should complete within 1s p95 under normal conditions (frontend timeout configurable; exact SLA Open Question).
- **Accessibility:** dialogs and errors must be keyboard navigable; focus management on modal open/close; errors announced via ARIA live region where applicable.
- **Responsiveness:** usable on standard POS tablet widths; dialogs must not overflow viewport.
- **i18n/timezone/currency:** display timestamps in store/user timezone per existing app conventions (Open Question); currency formatting for credit limit messages should follow existing formatting utilities.

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Provide a retryable “Unable to load billing requirements” blocking state when billing context fetch fails; qualifies as UI ergonomics and does not define business policy. (Impacted: UX Summary, Alternate/Error Flows)
- SD-UX-PREVALIDATE-FORMAT: Add optional client-side PO format hinting matching server rules to reduce round-trips; server remains authoritative. (Impacted: Business Rules, Error Flows)
- SD-OBS-CORRELATION-ID: Surface correlationId in an expandable error details area if provided by backend headers/body; observability boilerplate only. (Impacted: Service Contracts, Audit & Observability)

---

# 16. Open Questions
1. **Backend contract (blocking):** What are the exact Moqui-facing service names/endpoints and request/response schemas for:
   - billing rules evaluation for checkout,
   - PO capture,
   - override submit (including second approver auth proof),
   - finalize checkout?
2. **Checkout identifier (blocking):** Does the frontend checkout operate on `orderId`, `workOrderId`, or both? Which is the required identifier for billing enforcement?
3. **Second approver authentication (blocking):** How is the second approver captured in POS?
   - Re-authenticate via password/PIN?
   - Badge scan?
   - Existing “switch user” flow?
   The UI must match the established POS auth pattern.
4. **Commercial/B2B detection (blocking):** Does backend explicitly return `isB2B`/customer type and the “missing billing configuration” condition, or must UI infer from CRM/customer data? (UI must not infer policy.)
5. **Finalize gating behavior (blocking):** Should billing evaluation occur:
   - on screen load,
   - on every finalize click,
   - or both (with caching + invalidation)?
6. **Order billing method selection (blocking for AC9):** Where/how is `billingMethod = CHARGE_ACCOUNT | IMMEDIATE_PAYMENT` chosen in the POS UI today? Is it in-scope to add/modify that selection, or only to block based on current value?
7. **Permission discovery (blocking):** How does frontend determine user permissions?
   - Provided in session payload?
   - Must call an authz service?
   Without this, override button visibility/behavior is ambiguous.
8. **Error code contract (non-blocking but recommended):** Will backend return stable `errorCode` values (e.g., `PO_REQUIRED`, `PO_INVALID_FORMAT`, `PO_NOT_UNIQUE`, `CREDIT_LIMIT_EXCEEDED`) to support deterministic UI handling and tests?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Customer: Enforce PO Requirement and Billing Rules During Checkout — URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/67

Labels: frontend, story-implementation, payment

## Frontend Implementation for Story

**Original Story**: [STORY] Customer: Enforce PO Requirement and Billing Rules During Checkout

**Domain**: payment

### Story Description

/kiro
# User Story

## Narrative
As a **POS Clerk**, I want billing rules enforced so that compliance is maintained for commercial accounts.

## Details
- If PO required, block finalization until captured.
- Apply terms/charge account flow optional.
- Override requires permission.

## Acceptance Criteria
- Rule enforced consistently.
- Override requires permission.
- Audit includes who/why.

## Integrations
- CRM billing rules; accounting terms may apply.

## Data / Entities
- BillingRuleCheck, PoReference, AuditLog

## Classification (confirm labels)
- Type: Story
- Layer: Experience
- domain :  Point of Sale

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