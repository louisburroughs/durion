```markdown
# AGENT_GUIDE.md — Pricing Domain

---

## Purpose

The Pricing domain is responsible for defining, maintaining, and serving all pricing-related data and logic within the POS system. This includes MSRP management, base price books and rules, location-specific store price overrides, promotions and eligibility, pricing calculations (totals/adjustments), restriction enforcement, and immutable pricing snapshots used for auditability and explainability.

The domain ensures pricing is consistent, deterministic, and reproducible at any point in time, while supporting controlled exceptions (overrides) with strong authorization and audit trails.

---

## Domain Boundaries

### Owned Entities & Logic (Pricing is the System of Record)
- **MSRP** per product with effective dating and overlap prevention.
- **Base Price Books** and **Price Book Rules** (effective-dated, priority-based) used by quote/pricing engines.
- **Location Price Overrides** (store price overrides) with guardrails and approval outcomes.
- **Promotions** (offers/codes) and **Promotion Eligibility Rules** (rule sets that determine applicability).
- **Pricing Quote / Calculation** outputs (unit/extended prices, breakdown, warnings) as authoritative results.
- **Restriction Rules** (location/service-context restrictions) and **Override Records** for controlled exceptions.
- **Immutable Pricing Snapshots** for estimate/work order lines (read-only drilldown; creation is backend/workexec-triggered but schema is Pricing-owned).

### Authoritative Data Ownership (Other Domains)
- **Inventory/Product Catalog**: product identity, SKU/name, UOMs; cost data may be Inventory-owned but consumed by Pricing.
- **CRM**: customer accounts and customer tier.
- **Workexec**: estimate/work order lifecycle, line items, and persistence of references (e.g., `pricingSnapshotId`, `overrideId`).

### Integration Boundaries (How Pricing Interacts)
- Pricing validates product references against Inventory (synchronous API or cached reference).
- Pricing uses CRM tier/account context for tiered pricing and promotion eligibility.
- Workexec consumes Pricing outputs:
  - price quotes for line editing
  - totals/adjustments after promotion application
  - restriction decisions and override IDs
  - snapshot IDs for drilldown

### Excluded Responsibilities (Do Not Implement in Pricing)
- Product lifecycle management and search UX (Inventory domain / shared UI components).
- Customer tier management (CRM).
- Estimate/work order state transitions and persistence (Workexec).
- UI routing conventions and menu placement (frontend repo conventions; see Open Questions).

---

## Key Entities / Concepts

| Entity / Concept               | Description |
|-------------------------------|-------------|
| **ProductMSRP**               | MSRP records per product with effective start/end dates; temporal uniqueness enforced. |
| **PriceBook**                 | Container for base pricing rules; scoped (company default, location, customer tier, combined scope TBD). |
| **PriceBookRule**             | Effective-dated rule with target (GLOBAL/CATEGORY/SKU), priority, optional condition (location or tier), and `pricingLogic` JSON. |
| **LocationPriceOverride**     | Location-specific override price for a product; backend enforces guardrails and may return `ACTIVE` or `PENDING_APPROVAL`. |
| **GuardrailPolicy**           | Read-only policy used to validate overrides (min margin/max discount/auto-approval thresholds). |
| **PromotionOffer**            | Promotion definition (code, type/value, validity window, status). |
| **PromotionEligibilityRule**  | Eligibility constraints for a promotion (account/vehicle/etc.); combination logic AND/OR TBD. |
| **PriceQuote**                | Authoritative quote result for a line: MSRP, unit price, extended price, breakdown trace, warnings, price source. |
| **PricingAdjustment**         | Adjustment line(s) applied to an estimate (e.g., promotion discount). |
| **PricingSnapshot**           | Immutable record capturing line pricing at time of creation (prices, cost/MSRP-at-time, applied rules, policy version, source context). |
| **RestrictionRule**           | Rule restricting product sale/install by location/service context; evaluated synchronously. |
| **OverrideRecord**            | Audit record for restriction overrides; referenced by `overrideId` stored on the transaction line. |

### Relationships (Concrete)
- `PriceBook (1) -> (N) PriceBookRule`
- `LocationPriceOverride` references `(locationId, productId)` and affects quote precedence after base rules.
- `PromotionOffer (1) -> (N) PromotionEligibilityRule`
- `Workexec LineItem -> pricingSnapshotId` (read-only drilldown) and optionally `overrideId` (restriction override).
- `RestrictionRule` evaluation returns `decision + ruleIds + reasonCodes`; override creates `OverrideRecord` and returns `overrideId`.

---

## Invariants / Business Rules

### MSRP
- **MSRP Temporal Uniqueness:** No overlapping effective date ranges per product.
- **MSRP Product Validation:** Product IDs must exist in Inventory; invalid references rejected.
- **MSRP Historical Immutability:** MSRP records with past effectiveEndDate are immutable or require special permissions (permission string TBD; see Open Questions).

### Base Price Books / Rules
- **Deterministic Precedence:** Most specific target wins (SKU > Category > Global). Within same specificity, use deterministic tie-breakers:
  - higher priority wins (exact ordering rules must be documented by backend; TODO if not already).
- **Effective Dating:** Rules apply only within `[effectiveStartAt, effectiveEndAt)` (boundary semantics CLARIFY).
- **Condition Model:** Backend currently indicates a single `conditionType` (CUSTOMER_TIER | LOCATION | NONE). Whether a rule can have both tier and location simultaneously is **CLARIFY** (see Open Questions).
- **Historical Rule Immutability:** Rules whose effective window ended must be read-only; changes require creating a new rule.
- **Missing Base Data:** If rule requires missing cost/MSRP, backend may mark as not applicable and/or return warnings; UI must not invent fallback math.

### Price Quotes (Workexec consumer)
- **UI Must Not Recompute:** UI must display `unitPrice` and `extendedPrice` as returned; do not compute extended price unless explicitly provided.
- **Race Safety:** Multiple quote requests can be in-flight; UI must ignore out-of-order responses (requestId/hash).
- **Warnings Are Non-Blocking:** Warnings (e.g., MSRP fallback) should be displayed but not treated as errors unless backend indicates.

### Location Price Overrides
- **Guardrails Enforced Server-Side:** UI must not implement margin/discount formulas; only display backend-provided computed values and errors.
- **Outcome States:** Submissions result in `ACTIVE` or `PENDING_APPROVAL` (or validation failure). Pending overrides should be treated as non-effective unless backend returns otherwise.
- **Location Scoping:** Users may only manage overrides for authorized locations; enforce in UI and backend.

### Promotions
- **Promotion Code Uniqueness:** Codes unique and immutable once created.
- **Availability:** Only `ACTIVE` promotions within valid date range are applicable.
- **Single Promotion per Estimate:** Only one promotion can be applied per estimate (replace/remove behavior CLARIFY).
- **Estimate State Gate:** Promotions can only be applied in allowed estimate statuses (exact enum values CLARIFY).
- **Idempotency:** Re-applying the same code must not duplicate adjustments.

### Restrictions & Overrides
- **Synchronous Enforcement on Commit:** On finalize/commit paths, restriction evaluation must fail closed if unavailable.
- **Degraded State in Non-Commit:** During add/edit flows, evaluation failures may be represented as `RESTRICTION_UNKNOWN` (frontend-local) but must block finalize until resolved.
- **Override Requires Permission + Reason:** Override requires `pricing:restriction:override` and mandatory reason code + notes.
- **Override Traceability:** Approved override returns `overrideId` which must be persisted on the line item payload to downstream systems.

### Snapshots
- **Immutability:** Pricing snapshots are immutable once created; no update/delete APIs should exist for UI.
- **Explainability:** Snapshot must include enough breakdown/rule trace to explain historical pricing without referencing mutable current rules.

---

## Key Workflows

### MSRP Management
- Create/update MSRP with overlap validation.
- Validate product existence via Inventory.
- Retrieve active MSRP for a product as-of a date/time (timezone semantics CLARIFY).

### Base Price Book & Rule Management (Pricing Admin)
- List/search PriceBooks and Rules with pagination.
- Create/update/deactivate PriceBooks and Rules.
- Enforce effective dating and immutability of historical rules.
- Display audit history for changes (audit entity/service CLARIFY).

### Workexec: Quote Pricing for Estimate Line
- Trigger quote on product/quantity/context changes (debounced).
- Request includes `productId`, `quantity`, `locationId`, `customerTierId`, optional `effectiveTimestamp`.
- Response includes MSRP, unit price, extended price, breakdown entries (ordered), warnings, and price source indicator.

### Store Price Override (Store Manager)
- Select location + product; load base/effective price context.
- Submit override price; backend returns `ACTIVE` or `PENDING_APPROVAL` or validation errors.
- Display status history/audit if provided.

### Promotions: Apply Code During Estimate Pricing
- Load estimate totals/adjustments.
- Apply promotion code; backend returns updated totals/adjustments or stable error codes.
- UI must not compute totals; always render backend totals.

### Restrictions: Evaluate and Override
- Evaluate restrictions on add/edit and before finalize:
  - `ALLOW`, `BLOCK`, `ALLOW_WITH_OVERRIDE`
  - frontend-local `RESTRICTION_UNKNOWN` when evaluation unavailable in non-commit flows
- Override flow creates `OverrideRecord` and returns `overrideId`.
- Finalize blocked if any line is `BLOCK`, `ALLOW_WITH_OVERRIDE` without `overrideId`, or `RESTRICTION_UNKNOWN`.

### Pricing Snapshot Drilldown (Read-only)
- From estimate/work order line, open snapshot view if `pricingSnapshotId` present.
- Fetch `GET /pricing/v1/snapshots/{snapshotId}` and render read-only breakdown, applied rules, policy version, and timestamps.
- Handle 403/404/5xx with safe UX.

---

## Events / Integrations

> Event names below are conceptual; actual event bus/topic naming must match platform conventions. Use these as guidance for what should exist and what to log/trace.

### Domain Events (Pricing emits)
- MSRP: `MSRP.Created`, `MSRP.Updated`
- Price books/rules: `PriceBook.Created`, `PriceBook.Updated`, `PriceBookRule.Created`, `PriceBookRule.Updated`, `PriceBookRule.Deactivated`
- Overrides: `LocationPriceOverride.Submitted`, `.Activated`, `.PendingApproval`, `.Rejected`
- Promotions: `PromotionOffer.Created`, `.Activated`, `.Deactivated`, `PromotionAppliedToEstimate`
- Restrictions: `RestrictionEvaluated`, `RestrictionOverrideApproved`, `RestrictionOverrideDenied`, `RestrictionEvaluationFailed`
- Snapshots: `PricingSnapshotCreated` (emitted when created; consumed by Workexec for reference persistence)

### Integration Patterns
- **Synchronous APIs** for:
  - price quote retrieval
  - restriction evaluation/override
  - snapshot retrieval
  - promotion apply
  - admin CRUD (price books/rules, restriction rules, overrides)
- **Fail-closed on commit** for restrictions and (typically) promotion application.
- **Correlation IDs** must be propagated across Moqui → Pricing → downstream calls.

---

## API Expectations (Concrete Patterns)

> Some endpoints are referenced by frontend stories; others remain TBD. Where TBD, mark as TODO/CLARIFY and do not guess schemas.

### Price Quote
- `POST /v1/price-quotes` (as referenced by story #115; base path CLARIFY whether `/pricing/v1/...` is used)
- Request (minimum):
  - `productId`, `quantity`, `locationId`, `customerTierId`, optional `effectiveTimestamp`
- Response (minimum):
  - `msrp`, `unitPrice`, `extendedPrice` as money objects (amount + currency)
  - `pricingBreakdown[]` ordered
  - `warnings[]` optional
  - `priceSource` optional/required (CLARIFY)

### Pricing Snapshot
- `GET /pricing/v1/snapshots/{snapshotId}`
- Response schema **CLARIFY**:
  - whether `unitPrice` vs `extendedPrice` are provided
  - currency field naming and representation
  - whether `sourceContext` is present and intended for end users

### Restrictions
- `POST /pricing/v1/restrictions:evaluate`
  - Request includes `tenantId`, `locationId`, `serviceTag`, optional `customerAccountId`, `items[]` with `productId`, `quantity`, `uom`, `unitPrice`, optional `context`
  - Response per item includes `decision`, `ruleIds[]`, `reasonCodes[]`, optional `confidence`, `policyVersion`
- `POST /pricing/v1/restrictions:override`
  - Request/response schema **CLARIFY** (ruleId required? multiple ruleIds? transactionId availability?)

### Store Price Overrides
- Endpoints/services **TODO** (frontend story assumes “get pricing context” + “submit override” + “audit/history”).
- Required behaviors:
  - return base/effective price, optional cost, currency
  - submit returns `overrideId` + status (`ACTIVE` or `PENDING_APPROVAL`) or field errors
  - conflicts return 409 (CLARIFY payload)

### Promotions (Apply to Estimate)
- Endpoints/services **CLARIFY**:
  - load estimate with totals/adjustments
  - apply promotion code to estimate
- Error codes expected (from story): `PROMO_NOT_FOUND`, `PROMO_NOT_APPLICABLE`, `PROMO_MULTIPLE_NOT_ALLOWED`, `SERVICE_UNAVAILABLE`
- Concurrency/versioning behavior **CLARIFY** (409 vs other)

### Admin CRUD (PriceBooks/Rules, RestrictionRules, Promotion config, MSRP)
- CRUD endpoints/services and schemas are **TODO/CLARIFY**; do not implement UI without confirmed contracts.
- Standard patterns:
  - list endpoints support pagination + filters
  - create/update return full entity
  - deactivate is either `status=INACTIVE` or setting `effectiveEndAt` (CLARIFY preference)

---

## Security / Authorization Assumptions

### Authentication
- All Pricing APIs require authenticated requests (Moqui session/auth proxy).
- UI must treat IDs as opaque and avoid leaking sensitive data in logs.

### Authorization (Permission Strings)
Existing assumptions (keep, but confirm exact strings with backend):
- MSRP management: `pricing:msrp:manage`
- Price book rule management: `pricing:pricebook:manage`
- Location override submission: `pricing:override:manage`
- Override approval: `pricing:override:approve` (approval UI may be out-of-scope)
- Promotion management: `pricing:promotion:manage`
- Promotion eligibility configuration: `pricing:promotion:eligibility:manage`
- Restriction override: `pricing:restriction:override`

New/clarified needs from frontend stories:
- **Snapshot view permission**: `pricing:snapshot:view` (example) is **CLARIFY**; may be inherited from estimate/work order access.
- **Restriction rule management permission**: dedicated `pricing:restriction:manage` is **CLARIFY** (story currently references `pricing:pricebook:manage` as a fallback, which is likely incorrect).
- **View vs manage split** for price books/rules and promotions/eligibility rules is **CLARIFY**:
  - UI should hide manage actions unless manage permission is present.
  - UI should still handle 403 defensively even if actions are hidden.

### Secure-by-default UI/Service Behavior
- Do not show override actions unless permission present.
- Do not display cost unless backend includes it and user is authorized (treat cost as sensitive).
- Do not log free-text override notes (may contain sensitive info); log only presence/length and correlation IDs.

---

## Observability (Logs / Metrics / Tracing)

### Logging (Structured)
Log at decision points with correlation IDs; avoid PII and sensitive financial internals (especially cost).
Recommended log events (frontend + backend):
- `priceQuote.requested|succeeded|failed` with productId, locationId, customerTierId, quantity, requestId/hash, httpStatus, correlationId
- `promotion.apply.requested|succeeded|failed` with estimateId, promoCode (consider hashing), httpStatus, errorCode, correlationId
- `restriction.evaluate.requested|succeeded|failed` with transactionId (if available), productIds, decision summary, httpStatus, correlationId
- `restriction.override.requested|succeeded|failed` with transactionId, productId, overrideId (if approved), httpStatus, correlationId
- `snapshot.view.requested|succeeded|failed` with snapshotId, document context (estimateId/workOrderId/lineId), httpStatus, correlationId

### Metrics
Backend/service metrics to implement (and frontend telemetry if available):
- Quote latency histogram (P50/P95/P99) and error rate by status code.
- Restriction evaluation latency and timeout rate; count of `RESTRICTION_UNKNOWN` occurrences (frontend-derived).
- Override approval/denial counts; override submission failures by reason (permission/validation/conflict).
- Promotion apply success/failure counts by `errorCode`.
- Snapshot retrieval success/failure counts (403/404/5xx).

### Tracing
- Propagate correlation/request IDs from Moqui to Pricing and downstream calls.
- Trace spans should include:
  - `pricing.quote`
  - `pricing.restrictions.evaluate`
  - `pricing.restrictions.override`
  - `pricing.promotions.apply`
  - `pricing.snapshots.get`
- Ensure timeouts are explicit (e.g., restriction evaluate ~800ms UX threshold on commit paths).

---

## Testing Guidance

### Unit Tests (Domain Logic + DTO Validation)
- MSRP overlap validation and immutability checks.
- PriceBookRule precedence and effective dating boundary conditions.
- Promotion eligibility combination logic (AND/OR) once confirmed.
- Restriction decision mapping and override eligibility semantics.

### Contract Tests (Strongly Recommended)
Because multiple frontend stories are blocked on schema clarity:
- Snapshot response schema contract test for `GET /pricing/v1/snapshots/{id}`.
- Price quote request/response schema contract test for `POST /v1/price-quotes`.
- Restriction evaluate/override schema contract tests.
- Promotion apply endpoint contract test including stable `errorCode` mapping.

### Integration / E2E Tests (Moqui UI)
- Quote panel:
  - debounced calls
  - out-of-order response handling
  - 400/404/5xx mapping
- Restriction enforcement:
  - add/edit evaluation updates line state
  - finalize blocked on `BLOCK`, `ALLOW_WITH_OVERRIDE` without overrideId, and `RESTRICTION_UNKNOWN`
  - commit path fails closed on timeout/503
- Override modal:
  - permission gating
  - required fields validation
  - policyVersion mismatch forces re-evaluate
- Promotion apply:
  - allowed estimate statuses
  - single promotion enforcement
  - error code mapping
  - concurrency conflict handling (once contract known)
- Snapshot drilldown:
  - action hidden when no snapshotId
  - 403/404/5xx states
  - no stale data after error

### Performance Tests
- Quote API under load (target SLA referenced previously: P95 < 150ms; confirm for quote vs restrictions).
- Restriction evaluate on commit path must meet UX timeout threshold; test with injected latency.

### Security Tests
- Permission enforcement for:
  - override submission
  - rule management screens
  - snapshot view
  - cost visibility
- Verify 403 responses do not leak sensitive details (e.g., rule internals beyond reason codes).

---

## Common Pitfalls

- **Guessing schemas:** Frontend stories highlight multiple unknown payload shapes (snapshot, price book CRUD, promotion apply). Do not ship UI that guesses field names or currency representation. Use contract tests and shared DTOs.
- **Recomputing money values in UI:** Do not compute extended price, discounts, or totals client-side. Display backend-provided values only.
- **Timezone ambiguity:** Effective dating and “today” semantics appear in multiple stories (MSRP, price books, promotions). CLARIFY whether UTC vs store-local vs user-local is authoritative; otherwise you will get off-by-one-day bugs.
- **Fail-open on commit restrictions:** Non-commit flows may degrade to `RESTRICTION_UNKNOWN`, but commit/finalize must fail closed.
- **Logging sensitive data:** Do not log cost, override notes, or full sourceContext JSON. Log IDs and correlation IDs only.
- **Permission mismatch between UI and backend:** Always handle 403 even if UI hides actions; permissions are frequently clarified late.
- **Concurrency handling ignored:** Promotion apply and override submissions may require optimistic locking/versioning. Ensure UI handles 409 with reload prompts.
- **Condition model mismatch for rules:** Backend indicates single conditionType; story language implies multiple dimensions. Do not implement multi-condition UI until backend confirms representation.

---

## Open Questions from Frontend Stories

### Pricing Snapshot Drilldown (Story #114)
1. **API schema:** What is the exact response payload for `GET /pricing/v1/snapshots/{snapshotId}` (field names and currency representation)? Specifically: are prices `unitPrice` vs `extendedPrice` provided, and what is the currency code field name?
2. **Permissions:** What permission/role gates viewing a snapshot in the UI? Is it a dedicated permission (e.g., `pricing:snapshot:view`) or inherited from document access?
3. **Entry point scope:** Should the drilldown be added to **both** Estimate and Work Order line UIs in this story, or only one (and which is priority)?
4. **Navigation pattern:** Should snapshot drilldown open as a modal dialog or a dedicated route/screen? Is there an existing project convention for drilldowns in this repo?
5. **Source context rendering:** Should `sourceContext` be shown to end users (collapsed) or hidden behind an “Advanced” toggle, or omitted entirely?

### Price Books / Rules Admin (Story #118)
1. **Moqui routing & screen conventions:** What is the required screen path/module naming in `durion-moqui-frontend` for pricing admin screens (e.g., `/apps/pos/pricing/...` vs `/pricing/...`), and what existing menu screen should be extended?
2. **Authorization model:** What permissions/roles gate *view* vs *manage* for price books/rules in the frontend, and what is the exact permission string(s) to check?
3. **Backend CRUD contract:** What are the exact Moqui services or REST endpoints for `PriceBook` and `PriceBookRule` list/get/create/update/deactivate, including request/response schemas and error formats (400 vs 409 conflict payload)?
4. **Conditions model mismatch:** Can a rule have **both** location and customer tier simultaneously, or must it be expressed via separate scoped PriceBooks and single-condition rules?
5. **PriceBook scope enums:** Does `PriceBook.scope` support a combined scope (location+tier), and if so what is the exact representation?
6. **Pricing logic schema:** What is the exact shape of `pricingLogic` JSON (field names, allowed types, percent vs basis points, decimal precision)?
7. **Currency handling:** Are base price books single-currency or multi-currency? For FIXED_PRICE rules, must UI require/select currency, and what currencies are allowed?
8. **Effective dating timezone:** Are `effectiveStartAt/effectiveEndAt` interpreted as UTC, store local time, or user timezone? Should UI use date-only or date-time pickers?
9. **Deactivation mechanism:** Should deactivation be done by setting `status=INACTIVE`, setting `effectiveEndAt`, or either? If either, which should UI prefer?
10. **Audit retrieval:** What entity/service provides audit entries for rule changes, and what fields are available (diffs, actor display name, correlation id)?

### Promotions: Apply Code to Estimate (Story #159)
1. **Backend endpoint contract (blocking):** What are the exact Moqui service names / REST paths for:
   - Loading an estimate with totals/adjustments
   - Applying a promotion code to an estimate  
   Does the apply endpoint return the full updated estimate pricing payload, or must the frontend re-fetch after applying?
2. **Estimate state enum values (blocking):** What are the exact allowed estimate statuses for applying promotions, and what are the canonical string values used by Moqui/workexec?
3. **Replace/remove promotion (blocking):** When one promotion is already applied, should the UI block applying a new code, allow “Replace promotion”, and/or allow “Remove promotion”? If allowed, what is the backend service contract and audit expectation?
4. **Concurrency/versioning (blocking):** If the estimate is changed concurrently, what error code/HTTP status is returned (e.g., 409), and should the UI auto-refresh or prompt the user?
5. **Promotion code constraints:** Is there a maximum length / allowed character set for promotion codes that should be validated client-side?

### Promotion Eligibility Rules Admin (Story: eligibility rules)
1. **Service contracts:** What are the exact Moqui service names/endpoints for:
   - list rules by `promotionId`
   - create/update/delete rule
   - evaluate eligibility (`promotionId`, `accountId`, `vehicleId`) and return shape (`isEligible`, `reasonCode`, optional explanation text)?
2. **Rule combination logic:** When multiple rules exist for a promotion, is eligibility evaluated with **AND** or **OR**? Is this configurable per promotion?
3. **Operator matrix:** Which operators are valid per `conditionType` and what are their semantics?
4. **Identifier formats:** What is the canonical format for `accountId` and `vehicleId` in UI input (UUID vs human code)? Should UI provide lookup/search selectors?
5. **No-rules behavior:** If a promotion has zero eligibility rules, is it eligible for all contexts or ineligible until rules are defined?
6. **Reason codes catalog:** What is the authoritative initial enum set for `reasonCode` and should UI map them to user-friendly messages?
7. **Permissions:** What permission(s) should gate view vs manage eligibility rules in the Moqui frontend?

### Promotions Admin (Create/Activate/Deactivate)
1. **Permission model (blocking):** What permission string(s) should the frontend enforce for create/activate/deactivate (e.g., `pricing:promotion:manage`), and is view access allowed without manage permission?
2. **Store scope semantics (blocking):** Is `storeCode` required? If optional, does null/empty mean “all locations”, and how should UI represent this?
3. **Promotion value constraints (blocking):** For percent types, what is the allowed range? For fixed invoice amount, must it be > 0 and is there a maximum?
4. **Currency and formatting (blocking):** For `FIXED_INVOICE`, which currency applies and what decimal precision should UI enforce/display?
5. **Status `EXPIRED` behavior (blocking):** Is `EXPIRED` persisted (job-updated) or computed on read?
6. **Eligibility scope:** Is eligibility out of scope for basic create flow, or must create screen capture eligibility fields now?

### MSRP Admin / Effective Dating
1. **Inventory product lookup contract (blocking):** What is the supported way to select/validate `productId` (endpoint/screen/service) and minimal product fields for display (SKU/name)?
2. **Historical immutability policy (blocking):** Are MSRP records with `effectiveEndDate` in the past editable at all? If yes, what permission is required and what UI behavior should occur?
3. **Indefinite end-date rule details (blocking):** Confirm BR2 “null end date only if latest-starting record” is enforced. If violated, what error code/message should UI show?
4. **Date “today” timezone (blocking):** Are effective dates evaluated in UTC, store local time, or user locale?
5. **Backend endpoint shapes (blocking):** Confirm endpoints/service names and error payload format for field errors and conflicts.
6. **Authorization model (blocking):** Confirm permission string(s) and whether read-only viewing requires a separate permission.

### Restrictions (Story #107)
1. **RestrictionRule CRUD endpoints:** What are the exact Pricing service endpoints for `RestrictionRule` list/get/create/update/deactivate, including request/response schemas?
2. **Restriction rule management permission:** What permission(s) gate restriction rule management screens (dedicated `pricing:restriction:manage` vs reuse of another permission)?
3. **Override eligibility semantics:** Can `BLOCK` ever be override-eligible, or is override only via `ALLOW_WITH_OVERRIDE`? If `BLOCK` can be overridden, how does the evaluate response indicate that?
4. **Override reason codes:** What is the authoritative list of `overrideReasonCode` values and how should UI fetch it (enum endpoint vs static config)?
5. **Override request shape:** Does override require `ruleId` when multiple rules match? If so, does UI choose one or can backend accept multiple?
6. **transactionId availability:** Where in the POS frontend is `transactionId` guaranteed to exist at add-item time? If not persisted yet, should override be disabled until persisted?

---

*End of AGENT_GUIDE.md*
```
