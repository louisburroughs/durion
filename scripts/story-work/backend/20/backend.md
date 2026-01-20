Title: [BACKEND] [STORY] Order: Apply Price Override with Permission and Reason
URL: https://github.com/louisburroughs/durion-positivity-backend/issues/20
Labels: type:story, domain:pricing, status:ready-for-dev, agent:story-authoring, agent:pricing, risk:financial-inference, layer:domain

## 🏷️ Labels (Proposed)

### Required
- type:story
- domain:pricing
- status:ready-for-implementation

### Recommended
- agent:pricing
- agent:story-authoring

### Blocking / Risk
- risk:financial-inference

**Rewrite Variant:** pricing-strict

## Story Intent
As a Service Advisor, be able to apply a guarded price override on an order line (with permission and reason) so exceptions can be handled while preserving pricing integrity, auditability, and downstream pricing/settlement correctness.

## Actors & Stakeholders
- Service Advisor (primary actor): requests and applies overrides at point-of-sale.
- Manager / Approver: required for overrides above configured thresholds.
- Pricing service (domain owner): provides baseline prices, canonical rounding/normalization, and configuration for thresholds.
- Order/Checkout service: stores applied overrides as adjustments, recalculates totals, and emits downstream events.
- Finance/Reporting & Commissions: consumes override audit data for compliance and commission/rebate recalculation.
- Security/Access control: enforces permission checks.

## Preconditions
- `Order` and `LineItem` exist with `baselinePriceMinor` from Pricing service.
- Requesting user is authenticated and has role/permission metadata available.
- Pricing service normalization API is available for rounding/precision canonicalization.
- System supports idempotency keys for create/apply operations.

## Functional Behavior
1. Authorization: The API verifies the requesting user holds `price.override` permission or the request carries a valid manager approval token.
2. Create Override Request: Capture `requestedPriceMinor`, `reasonCode`, optional `managerApprovalId`, `requestedBy`, and `requestedAt`. Validate currency and call Pricing normalization API before any threshold evaluation or persistence.
3. Threshold Evaluation (config-driven): The system evaluates configured thresholds using OR logic:
   - `absoluteThresholdMinor = 5000` (=$50.00)
   - `percentageThreshold = 10` (10%)
   - `perBusinessUnit = true` (BU-specific overrides permitted)
   - Evaluation semantics = OR (exceeding either threshold requires approval)
   - Thresholds compare `requestedPriceMinor` to `baselinePriceMinor` (baseline, not previously-overridden price).
4. Approval Flow: If thresholds are exceeded, create request as `PendingApproval` and require manager approval before applying. Otherwise, allow immediate apply if caller authorized.
5. Apply Override: Applying an approved override records an immutable adjustment, updates the effective line price for presentation, recalculates order totals, and emits an `OrderPriceOverrideApplied` event containing before/after amounts and metadata.
6. Commission & Rebate Impact: Any applied override must set `affectsCommission = true` on the `PriceOverride` record and emit a downstream `commission.OverrideFlagged.v1` event to trigger near-real-time commission recalculation (target ≤ 5 minutes). Failures mark the order for reconciliation.
7. Idempotency & Audit: All create/apply operations support an `idempotencyKey`. Persist immutable audit entries recording actor, reason, manager approval, before/after minor units, and correlation ids.

## Rounding & Normalization
- Rounding mode: `HALF_EVEN` (bankers rounding) and minor-unit normalization follow ISO4217 minor unit rules.
- Pricing service is the canonical authority for currency precision and rounding. Order/Checkout must call `/api/v1/pricing/normalize` (or equivalent) before persisting override amounts.

## Data Requirements
- `PriceOverride` entity (table: `price_override`):
  - `id` (UUID PK)
  - `orderId`, `lineItemId` (FK)
  - `baselinePriceMinor`, `requestedPriceMinor`, `appliedPriceMinor` (long)
  - `currency` (ISO4217)
  - `reasonCode` (string)
  - `requestedBy`, `requestedAt`
  - `managerApprovalId` (nullable), `approvedBy`, `approvedAt`
  - `status` ENUM {Requested, PendingApproval, Approved, Rejected, Applied}
  - `affectsCommission` (boolean)
  - `idempotencyKey` (nullable)
  - `version` (optimistic-lock integer)
  - Audit fields: `created_at`, `created_by`, `updated_at`, `updated_by`

- `PriceOverrideReason` lookup table for valid `reasonCode`s.

## Acceptance Criteria
- Authorized immediate override
  - Given a Service Advisor with `price.override` permission and a line with baseline $100.00
  - When they submit an override to $90.00 with `reasonCode=PRICE_MATCH`
  - Then the override is persisted as `Applied`, order totals are recalculated, an `OrderPriceOverrideApplied` event is emitted, audit entry created, and `commission.OverrideFlagged.v1` is emitted.

- Override requiring approval
  - Given a request exceeding configured thresholds
  - When submitted without manager approval
  - Then the system persists `PendingApproval` and does not change order totals until Approved.

- Unauthorized attempt
  - Given a user without `price.override` permission
  - When they attempt override without valid manager approval
  - Then API returns 403 and no override is recorded.

- Idempotent submission
  - Given the same `idempotencyKey` is used twice
  - When duplicate request is received
  - Then the second request returns the original request/result without double-apply.

## Audit & Observability
- Immutable audit entries for every override request, approval, rejection, and application, including `before` and `after` price minor units, `actorId`, `reasonCode`, and `correlationId`.
- Metrics: `price.override.requests`, `price.override.applied`, `price.override.rejections`, `price.override.pending`
- Tracing: spans for override request → approval flow → apply, tagged with `orderId`, `lineItemId`, `idempotencyKey`.

## Resolved Decisions (from clarification #409)
1. Thresholds & Config
   - `pricing.override.threshold.absoluteMinor = 5000` (=$50.00)
   - `pricing.override.threshold.percentage = 10` (10%)
   - `pricing.override.threshold.evaluation = OR`
   - `pricing.override.threshold.perBusinessUnit = true`
   - Thresholds compare against `baselinePriceMinor` (not previously overridden price).

2. Rounding & Normalization
   - `rounding = HALF_EVEN` and `minorUnitPolicy = ISO4217`.
   - Pricing service exposes a normalization API (`/api/v1/pricing/normalize`) and is the canonical authority for rounding.

3. Commission & Rebate Impact
   - Applied overrides automatically set `affectsCommission = true` and emit `commission.OverrideFlagged.v1`.
   - Commission recalculation target SLA: ≤ 5 minutes (near-real-time). Failures mark orders for reconciliation.

**Recommended next steps:**
- Remove `STOP: Clarification required before finalization` and `blocked:clarification` label; set `status:ready-for-implementation` (done).
- Add example config keys to deployments and include the `commission.OverrideFlagged.v1` event schema appendix in the implementation PR.

---

## Original Story (Unmodified – For Traceability)

**Original Story**: [STORY] Order: Apply Price Override with Permission and Reason

(Original story body preserved below for auditability.)

