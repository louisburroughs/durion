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

**Title:** Promotions: Apply Promotion Code During Estimate Pricing (Frontend / Moqui)

**Primary Persona:** Service Advisor

**Business Value:** Enables Service Advisors to apply a promotion code to an estimate and immediately see the resulting discount and updated totals, ensuring accurate customer-facing pricing before estimate approval and preserving traceability of the applied offer.

---

## 2. Story Intent

### As a / I want / So that
- **As a** Service Advisor  
- **I want** to apply a promotion code to an existing estimate and see the resulting discount reflected in the estimate totals  
- **So that** customers receive correct pricing before approving the work and the applied offer is recorded for audit/traceability.

### In-scope
- UI entry to input a promotion code for a specific estimate.
- Client-side validation (required/non-empty, basic trimming).
- Call Moqui backend to apply a promotion code to an estimate (via Pricing/Workexec integration behind Moqui).
- Display applied promotion and the resulting pricing adjustment line(s) and updated totals.
- Display stable error codes returned by backend with user-friendly messages.
- Idempotent re-apply behavior handling in UI (same code → no duplicate discount lines).
- Ability to clear/remove a promotion **only if** an explicit backend capability exists (otherwise out-of-scope; see Open Questions).

### Out-of-scope
- Creating/editing promotions, eligibility rules, or pricing formulas (Pricing/CRM domain back-office).
- Defining discount stacking/precedence beyond “single promotion per estimate” (already defined).
- Manual price override workflows and approvals.
- Offline support.

---

## 3. Actors & Stakeholders

- **Service Advisor (Primary):** Applies promotion codes while preparing an estimate.
- **Customer (Beneficiary):** Receives accurate discounted pricing.
- **Pricing Service (System):** Validates/apply promotion, computes discount adjustment and totals (authoritative on math).
- **Work Execution / Estimate Owner (System):** Persists estimate state, applied promotion reference, and pricing adjustment lines.
- **CRM (Supporting):** System of record for promotion definitions and eligibility (invoked by Pricing per backend reference).

---

## 4. Preconditions & Dependencies

### Preconditions
- User is authenticated.
- User has permission to modify the target estimate.
- An estimate exists and is in an allowed state: `DRAFT` or `PENDING_APPROVAL` (names must match backend; see Open Questions).
- Estimate has at least one priceable line item.

### Dependencies
- Moqui screens for estimate detail and totals exist (or will be created as part of this story).
- Backend endpoint(s) exist to:
  - Load estimate header + pricing totals + applied adjustments.
  - Apply a promotion code to an estimate and return updated totals/adjustments with stable error codes.
- Stable backend error codes: `PROMO_NOT_FOUND`, `PROMO_NOT_APPLICABLE`, `PROMO_MULTIPLE_NOT_ALLOWED`, `SERVICE_UNAVAILABLE` (from backend story reference).

---

## 5. UX Summary (Moqui-Oriented)

### Entry points
- From **Estimate Detail** screen (Service Advisor workflow), within a “Pricing / Totals” context.

### Screens to create/modify
- **Modify:** `EstimateDetail` (existing) to add a “Promotion” section and bind to apply action.
- **Create (if missing):** A sub-screen/component for promotions within estimate context, e.g.:
  - `EstimateDetail/PromotionApply` (embedded screen)
- **Modify:** Totals/adjustments display area to include promotion adjustment line item(s) and applied promotion metadata.

### Navigation context
- Route includes `estimateId` parameter.
- Promotion application happens in-context; no separate full-page navigation required.
- After apply, remain on same estimate screen and refresh totals/adjustments panel.

### User workflows
**Happy path**
1. Open Estimate Detail.
2. Enter promotion code (e.g., `SAVE10`).
3. Click “Apply”.
4. UI shows applied promotion label/code and adds a discount adjustment line (negative amount) in totals; total decreases accordingly.

**Alternate paths**
- Enter invalid code → show error and do not change displayed totals.
- Enter valid but ineligible code → show error and do not change totals.
- Attempt to apply a second promotion when one already applied → show error; keep existing promotion.
- Backend unavailable → show retryable error; keep current totals.

---

## 6. Functional Behavior

### Triggers
- User submits promotion code via an “Apply” action.

### UI actions
- Promotion code input field (string).
- Apply button.
- While applying:
  - Disable Apply button.
  - Show in-place loading indicator in the promotion section.
- On success:
  - Refresh estimate pricing model in UI (adjustments + totals + applied promotion reference).
- On failure:
  - Show error message mapped from `errorCode`.
  - Do not mutate local estimate totals; optionally re-fetch estimate to ensure consistency only if backend indicates partial updates (should not happen per backend story).

### State changes (frontend)
- Local view-model updates:
  - `appliedPromotion` display (code/label/sourceId if provided).
  - `appliedAdjustments[]` includes a `type=PROMOTION` entry.
  - Totals updated from backend response.

### Service interactions
- `GET` load estimate (header + line items + pricing summary + applied adjustments).
- `POST` apply promotion to estimate (promotion code + estimateId).
- Optional `GET` refresh after apply if apply endpoint does not return full pricing payload (see Open Questions).

---

## 7. Business Rules (Translated to UI Behavior)

### Validation
- Promotion code:
  - Required (non-empty after trim).
  - Max length limit **TBD** (see Open Questions). If unknown, do not enforce max length beyond reasonable UI input constraints; allow backend to validate.
- Estimate state constraint:
  - Show promotion input enabled only when estimate state is in allowed set (Draft/Pending Approval).
  - If state disallowed, show read-only messaging: “Promotions can only be applied to Draft or Pending Approval estimates.”

### Enable/disable rules
- Disable Apply button when:
  - Code is empty after trim
  - Request in-flight
  - Estimate state not eligible

### Visibility rules
- Show “Applied Promotion” summary only if backend indicates a promotion is applied (either via `appliedPromotion` reference or presence of `PricingAdjustment(type=PROMOTION)`).
- If one promotion per estimate is enforced:
  - If promotion already applied, either:
    - Keep input visible but applying another code will error with `PROMO_MULTIPLE_NOT_ALLOWED`, or
    - Hide/disable input and show applied promotion summary (preferred UX)  
  (Exact behavior depends on whether “replace promotion” is allowed; see Open Questions.)

### Error messaging expectations
- Use backend `errorCode` as primary mapping key; message copy can be user-friendly but must preserve code for support/debug.
- Minimum mapping:
  - `PROMO_NOT_FOUND` → “Promotion code not found or expired.”
  - `PROMO_NOT_APPLICABLE` → “This promotion doesn’t apply to the current estimate.”
  - `PROMO_MULTIPLE_NOT_ALLOWED` → “Only one promotion can be applied to an estimate.”
  - `SERVICE_UNAVAILABLE` → “Promotion service is temporarily unavailable. Please try again.”
  - Unknown → “Unable to apply promotion. Please try again or contact support.” (include error code if present)

---

## 8. Data Requirements

> Note: Exact Moqui entity names are not provided in inputs; this story specifies a frontend-facing data model and requires mapping to actual Moqui entities/services during implementation.

### Entities involved (conceptual)
- **Estimate** (Workexec-owned)
- **PricingAdjustment** (Pricing-authored, Workexec-persisted association to estimate)
- **AppliedPromotion** reference (association on estimate pointing to promotion/offer id)

### Fields (type, required, defaults)

**Estimate (subset needed by UI)**
- `estimateId`: UUID, required, read-only
- `status`: string enum, required, read-only
- `customerId`: UUID, required, read-only (for context display only)
- `currencyUomId` or `currency`: string (ISO 4217), required, read-only
- `subtotal`: decimal/money, read-only
- `total`: decimal/money, read-only
- `taxTotal`: decimal/money, read-only (if available)
- `appliedPromotion` (optional):
  - `promotionId`/`sourceId`: UUID, read-only
  - `promotionCode`: string, read-only (if returned)
  - `label`: string, read-only (if returned)
- `appliedAdjustments[]`: array, read-only

**PricingAdjustment (promotion)**
- `type`: string, required, read-only; expected `PROMOTION`
- `sourceId`: UUID, required, read-only (promotion/offer id)
- `label`: string, required for display (if not provided, UI must still show “Promotion” and code/sourceId)
- `amount`: decimal/money, required, read-only; negative value
- `metadata`: object/json, optional, read-only (for audit display if provided)

**Promotion code input (UI-only)**
- `promotionCode`: string, required on submit; trimmed

### Read-only vs editable by state/role
- Only the **promotionCode input** is editable, and only in eligible estimate states.
- All totals and adjustments are read-only.

### Derived/calculated fields
- Discount amount and totals are calculated by Pricing (backend). UI does not compute amounts.

---

## 9. Service Contracts (Frontend Perspective)

> Moqui service names/paths are not provided; these contracts must be wired to existing Moqui services/endpoints.

### Load/view calls
- `GET /estimates/{estimateId}` (or Moqui screen transition/service call) returning:
  - Estimate header, status
  - Line items summary (optional for this UI)
  - Totals and appliedAdjustments
  - Applied promotion reference (if available)

### Create/update calls (apply promotion)
- `POST /estimates/{estimateId}/promotion:apply` (or equivalent)
  - Request:
    - `estimateId`: UUID (path or body)
    - `promotionCode`: string
  - Response (success):
    - Updated totals + appliedAdjustments including promotion adjustment
    - Applied promotion reference (promotionId/sourceId, label/code if available)
  - Response (failure):
    - `errorCode`: string (stable)
    - `message`: string (provisional)
    - `details`: optional object

### Submit/transition calls
- None beyond apply action.

### Error handling expectations
- HTTP 400 for business validation failures (`PROMO_*` errors) per backend reference.
- HTTP 503 for `SERVICE_UNAVAILABLE`.
- UI must:
  - Keep current estimate view unchanged on 4xx/5xx.
  - Display error feedback with the errorCode.
  - Allow retry after 503.

---

## 10. State Model & Transitions

### Allowed states (for promotion application)
- `DRAFT`
- `PENDING_APPROVAL`

(Exact enum values must match backend/Workexec; see Open Questions.)

### Role-based transitions
- No state transitions are initiated by applying a promotion (per backend reference).  
- Authorization is assumed to be covered by estimate modify permission; any separate permission for promotions is **TBD**.

### UI behavior per state
- Eligible states: show editable promotion input and Apply button.
- Ineligible states: show applied promotion (if any) read-only; hide/disable Apply.

---

## 11. Alternate / Error Flows

### Validation failures (client-side)
- Empty code:
  - Prevent submit.
  - Show inline validation: “Enter a promotion code.”

### Backend validation failures (business)
- `PROMO_NOT_FOUND`:
  - Show error; keep totals unchanged.
- `PROMO_NOT_APPLICABLE`:
  - Show error; keep totals unchanged.
- `PROMO_MULTIPLE_NOT_ALLOWED`:
  - Show error; keep existing promotion/totals unchanged.

### Concurrency conflicts
- If estimate is modified elsewhere and backend rejects apply due to version conflict:
  - Behavior **TBD** (need error code/contract).  
  - UI should prompt user to refresh estimate and retry (see Open Questions).

### Unauthorized access
- If backend returns 401/403:
  - Show “You don’t have permission to apply promotions to this estimate.”
  - Do not reveal promotion validity/eligibility details.

### Empty states
- If estimate has no priceable line items:
  - Disable Apply and show message: “Add at least one line item before applying a promotion.”
  (Backed by backend precondition; if backend also enforces, display its error code if returned.)

---

## 12. Acceptance Criteria

### Scenario 1: Apply a valid promotion successfully
**Given** I am a Service Advisor viewing an estimate in `DRAFT` state with at least one priceable line item  
**And** the estimate pricing subtotal is available  
**When** I enter a valid promotion code `SAVE10` and click Apply  
**Then** the UI sends an apply request for that `estimateId` with `promotionCode=SAVE10`  
**And** the UI displays an applied promotion indicator (code/label)  
**And** the estimate totals display includes a `PricingAdjustment` line with `type=PROMOTION` and a negative amount  
**And** the displayed estimate total is updated to the backend-returned value

### Scenario 2: Reject an invalid/expired promotion code
**Given** I am viewing an eligible estimate  
**When** I apply the promotion code `FAKECODE`  
**And** the backend responds with errorCode `PROMO_NOT_FOUND`  
**Then** the UI shows an error message for `PROMO_NOT_FOUND`  
**And** the estimate totals and adjustment lines shown remain unchanged from before the attempt

### Scenario 3: Reject a valid code that is not applicable to this estimate
**Given** I am viewing an eligible estimate that does not meet eligibility for code `OILSPECIAL`  
**When** I apply the promotion code `OILSPECIAL`  
**And** the backend responds with errorCode `PROMO_NOT_APPLICABLE`  
**Then** the UI shows an error message for `PROMO_NOT_APPLICABLE`  
**And** no promotion adjustment line is added  
**And** totals remain unchanged

### Scenario 4: Prevent multiple promotions on one estimate
**Given** I am viewing an eligible estimate that already has an applied promotion discount line  
**When** I attempt to apply a different promotion code  
**And** the backend responds with errorCode `PROMO_MULTIPLE_NOT_ALLOWED`  
**Then** the UI shows an error message for `PROMO_MULTIPLE_NOT_ALLOWED`  
**And** the originally applied promotion remains displayed  
**And** totals remain unchanged

### Scenario 5: Handle service unavailability safely
**Given** I am viewing an eligible estimate  
**When** I apply a promotion code and the backend responds with HTTP 503 and errorCode `SERVICE_UNAVAILABLE`  
**Then** the UI shows a retryable error message  
**And** the estimate remains unchanged  
**And** I can retry applying the promotion code after the error

---

## 13. Audit & Observability

### User-visible audit data
- Display (if provided by backend):
  - Applied promotion label/code
  - Applied promotion sourceId (for support, may be behind a “details” toggle)

### Status history
- Not a state transition; however, UI should show that promotion is applied as part of estimate pricing summary.

### Traceability expectations
- Frontend must include correlation/request id propagation if the Moqui frontend framework supports it (e.g., via headers) for:
  - apply request
  - subsequent refresh request
- Log (frontend console/logger) should include:
  - `estimateId`
  - `promotionCode` (consider sensitivity—promotion codes are not secrets but avoid over-logging; only log in debug level)
  - returned `errorCode` on failure

---

## 14. Non-Functional UI Requirements

- **Performance:** Apply action should provide feedback within 1500ms under normal conditions; show loading state while pending.
- **Accessibility:** Promotion input and Apply button must be keyboard operable; errors announced via appropriate ARIA/live region patterns supported by Quasar.
- **Responsiveness:** Works on tablet widths commonly used at service desks; promotion section should not require horizontal scrolling.
- **i18n/timezone/currency:** Currency display must use estimate currency from backend; do not assume USD formatting. (No additional currency conversion.)

---

## 15. Applied Safe Defaults

- SD-UX-EMPTY-STATE-01: Show a clear empty-state message and disable Apply when no priceable line items are present; qualifies as safe UX ergonomics with no domain policy changes; impacts UX Summary, Error Flows.
- SD-ERR-MAP-01: Map backend `errorCode` values to user-friendly messages while preserving the code for support; qualifies as safe error-handling boilerplate; impacts Business Rules, Error Flows, Acceptance Criteria.
- SD-UX-LOADING-01: Disable submit and show loading indicator during apply request to prevent double-submit; qualifies as safe UI ergonomics; impacts Functional Behavior, Error Flows.

---

## 16. Open Questions

1. **Backend endpoint contract (blocking):** What are the exact Moqui service names / REST paths for:
   - Loading an estimate with totals/adjustments
   - Applying a promotion code to an estimate  
   Does the apply endpoint return the full updated estimate pricing payload, or must the frontend re-fetch after applying?

2. **Estimate state enum values (blocking):** What are the exact allowed estimate statuses for applying promotions, and what are the canonical string values used by Moqui/workexec (e.g., `DRAFT` vs `Draft`)?

3. **Replace/remove promotion (blocking):** When one promotion is already applied, should the UI:
   - Block applying any new code (current rule: single promotion) and provide no replace option, or
   - Allow “Replace promotion” (which would still result in exactly one promotion), or
   - Allow “Remove promotion” action?  
   If allowed, what is the backend service contract and audit expectation?

4. **Concurrency/versioning (blocking):** If the estimate is changed concurrently, what error code/HTTP status is returned (e.g., 409), and should the UI auto-refresh or prompt the user?

5. **Promotion code constraints (non-blocking unless enforced in backend):** Is there a maximum length / allowed character set for promotion codes that should be validated client-side?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Promotions: Apply Offer During Estimate Pricing  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/159  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Promotions: Apply Offer During Estimate Pricing

**Domain**: user

### Story Description

/kiro  
# User Story

## Narrative
As a **Service Advisor**, I want **to apply a promotion code to an estimate and see the discount** so that **customers receive correct pricing before approval**.

## Details
- Validate code and eligibility.
- Record applied offer and discount parameters/lines.

## Acceptance Criteria
- Invalid code rejected.
- Discount line appears in estimate totals.
- Applied offer recorded for traceability.

## Integration Points (Workorder Execution)
- Workorder Execution calls CRM promotions API to validate/apply.
- CRM returns discount parameters or rule reference.

## Data / Entities
- AppliedPromotion reference
- PricingAdjustment (WO domain)

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