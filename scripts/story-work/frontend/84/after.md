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
[FRONTEND] [STORY] Order: Apply Price Override with Permission and Reason

## Primary Persona
Service Advisor (POS user)

## Business Value
Enable compliant exception handling by allowing authorized users to override an order line’s price with reason capture and (when required) manager approval, while ensuring auditability and correct downstream totals/commission signaling.

---

# 2. Story Intent

## As a / I want / So that
- **As a** Service Advisor  
- **I want** to override a line item’s price by entering a new price and selecting a reason (and obtaining manager approval when required)  
- **So that** I can resolve pricing exceptions while remaining compliant and auditable.

## In-scope
- POS frontend UI to initiate a line price override for an existing Order Line.
- Permission-gated override entry, with reason code required.
- Support for “immediate apply” vs “pending approval” outcomes based on backend threshold evaluation.
- Display of override status and metadata (who/why/when/approval) on the line item.
- Integration with Moqui backend services/endpoints for:
  - loading baseline line pricing context
  - normalizing entered amounts (via pricing service)
  - creating override requests
  - applying approved overrides (if applicable from UI)
- User-visible errors for unauthorized/validation failures.
- Basic audit/observability integration hooks (correlation/idempotency key propagation).

## Out-of-scope
- Creating/editing price override thresholds or guardrail configuration.
- Manager approval workflow UI (separate story unless already exists); this story will only support passing an existing `managerApprovalId` token/record if provided and reacting to `PendingApproval`.
- Reporting dashboards (“Reporting includes override usage”) beyond ensuring data is captured and visible in line details.
- Promotion/discount stacking rules and any pricing formulas (owned by Pricing backend).

---

# 3. Actors & Stakeholders

- **Service Advisor (primary):** requests override at POS.
- **Manager / Approver:** may approve overrides; may provide approval token/record.
- **Pricing Domain Services (backend):** SoR for rounding/normalization and override policy decisions (thresholds, status).
- **Order/Checkout (backend area):** persists override adjustments and recalculates totals; exposes order/line state to UI.
- **Finance/Reporting:** consumes stored override data (not implemented here, but depends on capture).
- **Security/Access Control:** provides permission assertions to UI and enforces on backend.

---

# 4. Preconditions & Dependencies

## Preconditions
- User is authenticated in POS and has an active session.
- An **Order** exists with at least one **Line Item** that has a baseline price from Pricing.
- UI can determine whether the current user has `price.override` capability (either via a permissions payload in session/user context or via an authorization check endpoint).

## Dependencies (must exist or be implemented in parallel)
- Backend endpoints/services corresponding to backend story #20 behaviors:
  - pricing normalization (`/api/v1/pricing/normalize` or Moqui service equivalent)
  - create override request (returns status: Applied vs PendingApproval, etc.)
  - (optional) apply override if approval already granted
  - lookup of valid override reason codes (`PriceOverrideReason`)
- Backend should provide deterministic outcomes and messages for:
  - 403 unauthorized
  - 409 concurrency/version conflict
  - 422 validation errors (invalid amount/reason/status)
- Moqui security artifacts for permission string `price.override` (or mapped equivalent) available to frontend.

---

# 5. UX Summary (Moqui-Oriented)

## Entry points
- From **Order Detail / Cart / Checkout** screen, on each line item:
  - Action: **“Override Price”** (only shown/enabled when allowed; see rules below)

## Screens to create/modify
- **Modify existing screen:** `OrderDetail` (or equivalent POS order screen)
  - Add override action per line
  - Add display of override status/metadata in line details
- **New dialog/screenlet:** `OrderLinePriceOverrideDialog`
  - Form inputs: new price, currency display, reason code, optional manager approval reference (if provided)

> Moqui implementation note: Prefer a **screenlet** with a **form-single** in a modal/dialog pattern; submit via transition to a service.

## Navigation context
- User remains within the Order context; dialog opens atop current order screen.
- On success, UI refreshes line and totals in-place.

## User workflows

### Happy path: immediate apply
1. Service Advisor clicks “Override Price” on a line.
2. Dialog shows baseline price and current effective price, plus input for requested price and reason.
3. User enters requested price and selects reason code.
4. Submit.
5. Backend responds `Applied` (or equivalent success result) and returns updated line/totals.
6. UI shows updated effective price and “Override Applied” metadata.

### Alternate path: requires approval (pending)
1. Same steps as above, but backend evaluates thresholds and responds `PendingApproval`.
2. UI shows line as “Override Pending Approval” and does **not** present totals change unless backend includes updated totals (expected: totals unchanged until applied).
3. UI displays instructions: pending approval; cannot apply until approved.

### Alternate path: manager approval provided at submission
1. User includes `managerApprovalId` (if mechanism exists in UI context).
2. Backend accepts and applies override (if approval valid) or rejects (if invalid/insufficient).

---

# 6. Functional Behavior

## Triggers
- User action: click “Override Price” on a line item.
- User action: submit override form.

## UI actions
- Open override dialog for selected `orderId` + `lineItemId`.
- Validate inputs client-side (format, required fields).
- Call backend to normalize entered amount (if required by contract) and then submit override request OR submit once and backend handles normalization (must match actual backend API; see Open Questions).

## State changes (frontend-visible)
- Line item effective price updates when override is applied.
- Line item shows override status badge/state:
  - Requested / PendingApproval / Approved / Rejected / Applied (as provided by backend)
- Line item shows override metadata:
  - reason code
  - requested by/at
  - approved by/at (if applicable)

## Service interactions (Moqui)
- Load order + line pricing context (baseline/effective)
- Load reason code list
- Normalize entered price (Pricing service)
- Create override request / apply override
- Refresh order summary totals and line items

---

# 7. Business Rules (Translated to UI Behavior)

## Validation
- **Requested price is required** and must be a valid monetary amount for the order currency.
- **Reason code is required** and must be one of `PriceOverrideReason` values.
- **Currency is not editable**; it is derived from Order/Line context.
- If backend enforces ISO4217 minor units and HALF_EVEN rounding:
  - UI must either:
    - submit as decimal string and let backend normalize, or
    - call normalize first and submit minor units returned.
  - UI must display normalized value if it differs from user entry (e.g., show “Normalized to $X.XX”).

## Enable/disable rules
- “Override Price” action:
  - hidden or disabled when the line/order state does not allow overrides (must be supplied by backend; otherwise open question)
  - disabled if user lacks permission and has no manager approval mechanism available
- Submit button disabled until required fields are present and syntactically valid.

## Visibility rules
- Override metadata is visible on the line once any override exists (including pending/rejected), subject to user permissions if required by security policy (unclear; see Open Questions).

## Error messaging expectations
- 403: “You don’t have permission to override prices.”
- 422: show field-level errors (price invalid, reason invalid, approval invalid).
- 409: “This order was updated by another user. Refresh and try again.”
- Service unavailable for normalization/pricing: show “Pricing service unavailable; cannot apply override right now.” (do not retry indefinitely; allow manual retry).

---

# 8. Data Requirements

## Entities involved (conceptual; UI reads/writes via services)
- `Order`
- `OrderLineItem`
- `PriceOverride`
- `ApprovalRecord` (referenced by `managerApprovalId`)
- `AuditLog` (view-only; surfaced via metadata fields)
- `PriceOverrideReason` (lookup for reason codes)

## Fields (type, required, defaults)

### UI input model: `PriceOverrideRequest`
- `orderId` (UUID, required)
- `lineItemId` (UUID, required)
- `requestedPrice` (string decimal in UI, required)
- `currency` (string ISO4217, read-only, required)
- `reasonCode` (string, required)
- `managerApprovalId` (UUID/string token, optional)
- `idempotencyKey` (string/UUID, required for submission; generated client-side)

### UI display model: `PriceOverrideView`
- `status` (enum string: Requested | PendingApproval | Approved | Rejected | Applied)
- `baselinePriceMinor` (long) + `currency`
- `requestedPriceMinor` (long) + `currency`
- `appliedPriceMinor` (long, optional until applied)
- `requestedBy` (string/userId)
- `requestedAt` (datetime)
- `approvedBy` (string/userId, nullable)
- `approvedAt` (datetime, nullable)
- `reasonCode` (string)

## Read-only vs editable by state/role
- Editable: requested price, reason code, manager approval reference **only at creation time**.
- Non-editable: baseline price, currency, audit metadata always read-only.
- If an override is already `PendingApproval`/`Applied`, UI must not allow editing; only view details.

## Derived/calculated fields
- Display “delta” (before/after) is derived in UI from baseline vs requested/applied if provided; no business decisions based on it (thresholds are backend-owned).

---

# 9. Service Contracts (Frontend Perspective)

> Note: exact service names/paths may differ in Moqui; implementer should map to actual services. Contracts below are required behaviors.

## Load/view calls
1. **Load order context**
   - Input: `orderId`
   - Output must include for each line:
     - `lineItemId`
     - `baselinePriceMinor` + `currency`
     - current effective price (either `appliedPriceMinor` or current line price)
     - existing `PriceOverride` summary if present (status + metadata)
2. **Load reason codes**
   - Output: list of `{reasonCode, description, activeFlag}`

## Create/update calls
3. **Normalize price amount (Pricing canonical)**
   - Input: `{amount: <decimal string>, currency}`
   - Output: `{normalizedMinor, normalizedDisplay}` OR `{normalizedAmountDecimal}`
   - Errors: 422 for invalid format/currency; 503 if unavailable.

4. **Create override request**
   - Input: `PriceOverrideRequest` (with normalized amount representation as required by backend)
   - Output: created override record with `status`, and updated order/line totals if applicable.
   - Errors:
     - 403 unauthorized (no permission/no valid manager approval)
     - 422 validation (invalid reason, invalid amount, invalid approval token)
     - 409 optimistic lock/version conflict (if order/line changed)

## Submit/transition calls
5. **Apply approved override** (optional if backend returns Applied immediately when approved)
   - Input: `{priceOverrideId, idempotencyKey}`
   - Output: updated override status Applied + updated order totals

## Error handling expectations
- All service calls must surface:
  - `correlationId` (if provided) in logs
  - user-friendly message + technical detail for console/log
- Idempotency:
  - client must send `idempotencyKey` on create/apply; retries must not double-apply.

---

# 10. State Model & Transitions

## Allowed states (as displayed/handled by UI)
- `Requested`
- `PendingApproval`
- `Approved`
- `Rejected`
- `Applied`

## Role-based transitions (frontend enforcement + backend authoritative)
- Service Advisor with `price.override`:
  - can submit override request
  - may receive immediate `Applied` if within thresholds (backend decides)
- User without `price.override`:
  - cannot submit unless they provide a valid `managerApprovalId` (backend decides validity)
- Manager/Approver:
  - approval action is out-of-scope UI here, but UI must support reflecting `Approved/Rejected` when viewing refreshed order.

## UI behavior per state
- `PendingApproval`: show “pending” indicator; disable further override attempts on same line unless backend explicitly allows multiple requests (unclear; see Open Questions).
- `Applied`: show applied price and metadata; disable override edit for that override (new override request may or may not be allowed; unclear).
- `Rejected`: show rejected status + metadata; allow new request only if backend allows.

---

# 11. Alternate / Error Flows

## Validation failures
- Missing reason code → inline error, prevent submit.
- Invalid price format (non-numeric, negative) → inline error, prevent submit.
- Normalization returns different value → show normalized value and require user confirmation (see Open Questions; may be optional).

## Concurrency conflicts
- If backend returns 409:
  - UI prompts refresh; on confirm, reload order and reopen dialog with latest baseline price.

## Unauthorized access
- If override action invoked but backend returns 403:
  - show permission error; do not persist any UI-local override state.

## Empty states
- If reason code list is empty/unavailable:
  - block submission and show “No override reasons available; contact admin.”
- If line has no baseline price in response:
  - hide/disable override action and show “Pricing not available for this line.”

---

# 12. Acceptance Criteria

## Scenario 1: Authorized immediate override applies and updates line
**Given** a Service Advisor is viewing an Order with a line item that has a baseline price and the user has `price.override` permission  
**When** the user opens “Override Price”, enters a requested price, selects a reason code, and submits  
**Then** the UI sends an idempotent override request including `orderId`, `lineItemId`, requested amount, currency, and `reasonCode`  
**And** the UI displays the override status as `Applied` when returned  
**And** the line item effective price updates to the applied price  
**And** the UI shows override metadata (who/when/reason) on the line.

## Scenario 2: Override blocked without permission
**Given** a user without `price.override` permission is viewing an Order line  
**When** they attempt to submit a price override without a valid manager approval reference  
**Then** the backend responds 403  
**And** the UI shows “You don’t have permission to override prices.”  
**And** the order and line prices remain unchanged.

## Scenario 3: Override requires approval and becomes pending
**Given** a Service Advisor submits an override that requires approval per backend threshold policy  
**When** the override request is submitted without manager approval  
**Then** the UI displays the resulting override status as `PendingApproval`  
**And** the UI indicates totals are not changed until approval (unless backend returns changed totals)  
**And** the UI prevents editing the pending override request from this dialog.

## Scenario 4: Invalid reason code prevented/handled
**Given** the override dialog is open  
**When** the user does not select a reason code and tries to submit  
**Then** the UI shows a required-field validation error and does not call the create override service.

## Scenario 5: Concurrency conflict handling
**Given** the override dialog is open for a line item  
**When** the user submits and the backend responds with 409 due to a version conflict  
**Then** the UI prompts the user to refresh  
**And** upon refresh, the UI reloads the order and allows the user to retry with updated baseline data.

## Scenario 6: Idempotent retry does not double-apply
**Given** the user submits an override and a network timeout occurs after request submission  
**When** the UI retries submission with the same `idempotencyKey`  
**Then** the backend returns the original result  
**And** the UI displays a single override record (no duplicate applied overrides shown).

---

# 13. Audit & Observability

## User-visible audit data
- On the line item (or line details panel), display:
  - override status
  - reason code
  - requestedBy/requestedAt
  - approvedBy/approvedAt (if present)

## Status history
- If backend provides multiple audit entries/status history, UI should display a simple chronological list in a collapsible “Override History” section (otherwise show current metadata only).

## Traceability expectations
- Frontend generates and sends:
  - `idempotencyKey` per create/apply action
  - correlation/request ID header if the project standard exists (Moqui often uses request attributes; implementer to align)
- Frontend logs (console/dev logging or app logging) include `orderId`, `lineItemId`, `priceOverrideId` (if returned), and correlationId.

---

# 14. Non-Functional UI Requirements

- **Performance:** Override dialog should open with cached order data; reason codes may be cached per session. Service calls should not block UI; show loading states.
- **Accessibility:** Dialog and form controls must be keyboard navigable; errors announced via accessible helper text.
- **Responsiveness:** Must work on tablet widths commonly used in POS.
- **i18n/timezone/currency:** Currency formatting must respect order currency; timestamps displayed in user’s locale/timezone (no assumptions about timezone conversions beyond display).

---

# 15. Applied Safe Defaults
- SD-UX-EMPTY-STATE: Show explicit empty-state messaging for missing reason codes because it is UI ergonomics and does not change domain policy. Impacted sections: UX Summary, Alternate/Error Flows.
- SD-ERR-STD-MAPPING: Map 403/409/422/503 to standard user-facing notifications and field errors because it is standard error handling implied by backend contract. Impacted sections: Business Rules, Service Contracts, Alternate/Error Flows.
- SD-OBS-CORRELATION: Generate a client idempotency key and propagate correlation identifiers when available because it is observability boilerplate and recoverable. Impacted sections: Service Contracts, Audit & Observability, Acceptance Criteria.

---

# 16. Open Questions

1. **Backend API shape:** What are the exact Moqui service names/REST paths and payload formats for:
   - pricing normalization
   - create override request
   - apply approved override  
   (Needed to implement transitions/forms without guessing.)

2. **Manager approval capture UX:** How is `managerApprovalId` obtained in the POS?
   - manager login re-auth modal?
   - scanned approval code/token?
   - selection from pending approvals?
   (Story mentions “optional manager approval” but not the mechanism.)

3. **Order/line eligibility constraints:** Are there order states or line types where overrides are forbidden (e.g., finalized/paid/refunded, promo lines, tax lines)? If yes, backend should return an explicit flag per line or an error code to guide UI.

4. **Multiple overrides per line:** If an override is already `Applied` or `PendingApproval`, can a new override request be created for the same line? If allowed, what is the rule (supersede, stack, only latest active)?

5. **Reason code catalog:** Where is `PriceOverrideReason` sourced and does it include descriptions/localization/active flags? Is the list global or location/business-unit scoped?

---

## Original Story (Unmodified – For Traceability)

Title: [FRONTEND] [STORY] Order: Apply Price Override with Permission and Reason  
URL: https://github.com/louisburroughs/durion-moqui-frontend/issues/84  
Labels: frontend, story-implementation, user

## Frontend Implementation for Story

**Original Story**: [STORY] Order: Apply Price Override with Permission and Reason

**Domain**: user

### Story Description

/kiro
# User Story

## Narrative
As a **Service Advisor**, I want to override a line price with reason and permission so that I can resolve exceptions while staying compliant.

## Details
- Override requires role/permission.
- Capture reason code and optional manager approval.

## Acceptance Criteria
- Override blocked without permission.
- Override recorded with who/why.
- Reporting includes override usage.

## Integrations
- Pricing service returns baseline; override stored as adjustment.

## Data / Entities
- PriceOverride, ApprovalRecord, AuditLog

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